package logic;

import java.time.LocalDateTime;
import java.util.Map;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import exchange.BitFlyerAPIWrapper;
import model.BuySellEnum;
import model.ChildOrderResponse;
import model.DirectionEnum;
import model.ExecutionResponse;
import model.OrderTypeEnum;
import model.PriceDirectionPair;
import notification.SlackNotifier;

public class ScalpingDealingLogic extends DealingLogicBase {

	private final double LEVERAGE;
	private final double LOSS_CUT_PERCENTAGE;
	private final double PROFIT_TAKE_PERCENTAGE;
	private final Map<String, Double> LOGIC_PARAM;
	private final double SPREAD_PERCENTAGE;
	private final int INTERVAL;
	private final double DIRECTION_JUDGE_PERCENTAGE;
	private final double COUNT_JUDGE_RATIO;
	private final int JUDGE_SECOND;
	private final int CLOSE_SECOND;
	private final CircularFifoQueue<PriceDirectionPair> QUEUE;

	private volatile int entry = -1;
	private volatile BuySellEnum side;
	private volatile double size;
	private volatile int price = -1;
	private volatile boolean hasPosition;
	private volatile int profitTakePrice;
	private volatile int lossCutPrice;
	private volatile LocalDateTime closeTime;

	private static Logger PRICE_DIRECTION_LOGGER = LogManager.getLogger("price_direction_logger");

	@SuppressWarnings("unchecked")
	public ScalpingDealingLogic(BitFlyerAPIWrapper wrapper, SlackNotifier notifier, Map<String, Object> paramMap,
			Map<String, Object> settings) {
		super(wrapper, notifier, paramMap, settings);
		this.LEVERAGE = (Double) (paramMap.get("leverage"));
		this.LOSS_CUT_PERCENTAGE = (Double) (paramMap.get("lossCutPercentage"));
		this.LOGIC_PARAM = (Map<String, Double>) settings.get("logic");
		// パラメータ出力
		StringBuilder sb = new StringBuilder();
		sb.append("LogicParams");
		this.LOGIC_PARAM.forEach((k, v) -> sb.append(" " + k + ":" + v));
		LOGGER.info(sb.toString());
		this.PROFIT_TAKE_PERCENTAGE = this.LOGIC_PARAM.get("profitTakePercentage");
		this.SPREAD_PERCENTAGE = this.LOGIC_PARAM.get("spread");
		this.INTERVAL = this.LOGIC_PARAM.get("notifyInterval").intValue();
		this.DIRECTION_JUDGE_PERCENTAGE = this.LOGIC_PARAM.get("directionJudgePercentage");
		this.COUNT_JUDGE_RATIO = this.LOGIC_PARAM.get("countJudgeRatio");
		this.JUDGE_SECOND = this.LOGIC_PARAM.get("judgeSecond").intValue();
		this.CLOSE_SECOND = this.LOGIC_PARAM.get("closeSecond").intValue();
		this.QUEUE = new CircularFifoQueue<>(this.JUDGE_SECOND);
	}

	@Override
	public void execute() {
		super.execute();
		// 初期化
		init();
		// 約定価格取得スレッドの開始(執行判断もこの中で行う)
		startJudgeThread();
		// 定期通知スレッドの開始(定期的にSlack通知)
		startPeriodicalNotifyThread(INTERVAL);
	}

	private void init() {
		// 証拠金評価額取得
		resetCollateral();
		LOGGER.info("証拠金評価額:" + this.collateral);
		// ポジションクリア
		positionClear();
		// 処理反映まで少し待つ
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		outputCurrentStatus();
		outputCurrentStatusSlack();
	}

	private void startJudgeThread() {
		// 毎秒約定価格を取得し、執行判断するスレッド
		Thread t = new Thread(() -> {
			int lastSecond = -1;
			int lastPrice = -1;
			while (true) {
				LocalDateTime now = LocalDateTime.now();
				int second = now.getSecond();
				if (second != lastSecond) {
					// 秒が変わったら約定価格取得
					int price = getExecutionPrice();
					if (price == -1) {
						// 板情報が取れなければ処理しない
						LOGGER.debug("約定価格取得に失敗したので前回約定価格を利用します。");
						price = lastPrice;
					}
					this.price = price;
					DirectionEnum direction;
					if (lastPrice == -1) {
						direction = DirectionEnum.STAY;
					} else if (lastPrice < price) {
						direction = DirectionEnum.UP;
					} else if (lastPrice > price) {
						direction = DirectionEnum.DOWN;
					} else {
						direction = DirectionEnum.STAY;
					}
					LOGGER.debug("約定価格取得結果：" + price + " 前回約定価格:" + lastPrice + " 変動方向:" + direction + " 時刻:" + now);
					PRICE_DIRECTION_LOGGER.debug("{},{},{}", System.currentTimeMillis() / 1000, price, direction);

					if (!this.hasPosition) {
						// ポジションがない場合
						// 約定価格と変動方向をキューに詰める
						PriceDirectionPair pair = new PriceDirectionPair(price, direction);
						this.QUEUE.add(pair);
						if (this.QUEUE.isAtFullCapacity()) {
							// キューが全て埋まっている場合
							// ポジションオープン判断
							openJudge();
						}
					} else {
						// ポジションがある場合、ポジションクローズ判断
						closeJudge();
					}

				}
				lastSecond = second;
				lastPrice = price;
				try {
					// だんだんズレていかないように50ms単位とする
					Thread.sleep(50);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}, "judgeThread");
		t.start();
	}

	private int getExecutionPrice() {
		ExecutionResponse[] executions = WRAPPER.getExecutions();
		if (executions == null || executions.length == 0) {
			LOGGER.info("Execution Response is null.");
			NOTIFIER.sendMessage("約定情報の取得に失敗しました。");
			return -1;
		}
		return executions[0].getPrice();
	}

	private void openJudge() {
		LOGGER.debug("open judge!");
		PriceDirectionPair first = this.QUEUE.peek();
		PriceDirectionPair last = this.QUEUE.get(JUDGE_SECOND - 1);
		int diff = last.getPrice() - first.getPrice();
		int judgeRangePrice = (int) (last.getPrice() * DIRECTION_JUDGE_PERCENTAGE / 100);
		BuySellEnum side = null;
		// 方向判定割合で値動き幅を確認
		if (diff > judgeRangePrice) {
			side = BuySellEnum.BUY;
		} else if (diff < -1 * judgeRangePrice) {
			side = BuySellEnum.SELL;
		}
		LOGGER.debug("値動き幅判定 diff:" + diff + " range:" + judgeRangePrice);
		if (side == null) {
			// 値動き幅が条件を満たさなければ処理終了
			return;
		}
		// 回数判定割合で回数を確認
		int upCount = 0;
		int downCount = 0;
		for (int i = 0; i < JUDGE_SECOND; i++) {
			PriceDirectionPair p = this.QUEUE.get(i);
			switch (p.getDirection()) {
			case UP:
				upCount++;
				break;
			case DOWN:
				downCount++;
				break;
			default:
				break;
			}
		}
		LOGGER.debug("回数判定 up:" + upCount + " down:" + downCount);
		// 上昇(下降)回数割合満たしたら発注
		if (side == BuySellEnum.BUY) {
			if ((double) upCount / JUDGE_SECOND >= COUNT_JUDGE_RATIO) {
				buy();
				// スプレッド分高く
				int priceWithSpread = (int) (last.getPrice() * (1 + SPREAD_PERCENTAGE / 100));
				this.profitTakePrice = (int) (priceWithSpread * (1 + (double) PROFIT_TAKE_PERCENTAGE / 100));
				this.lossCutPrice = (int) (priceWithSpread * (1 - (double) LOSS_CUT_PERCENTAGE / 100));
				this.closeTime = LocalDateTime.now().plusSeconds(CLOSE_SECOND);
				this.QUEUE.clear();
			}
		} else if (side == BuySellEnum.SELL) {
			if ((double) downCount / JUDGE_SECOND >= COUNT_JUDGE_RATIO) {
				sell();
				// スプレッド分安く
				int priceWithSpread = (int) (last.getPrice() * (1 - SPREAD_PERCENTAGE / 100));
				this.profitTakePrice = (int) (priceWithSpread * (1 - (double) PROFIT_TAKE_PERCENTAGE / 100));
				this.lossCutPrice = (int) (priceWithSpread * (1 + (double) LOSS_CUT_PERCENTAGE / 100));
				this.closeTime = LocalDateTime.now().plusSeconds(CLOSE_SECOND);
				this.QUEUE.clear();
			}
		}
	}

	private void closeJudge() {
		LOGGER.debug("close judge!");
		LOGGER.debug("price:" + price + " profitTake:" + profitTakePrice + " lossCutPrice:" + lossCutPrice
				+ " closeTime:" + closeTime);
		if (side == BuySellEnum.BUY) {
			// ロングポジション
			// スプレッド分安く
			int priceWithSpread = (int) (this.price * (1 - SPREAD_PERCENTAGE / 100));
			if (priceWithSpread > this.profitTakePrice || priceWithSpread < this.lossCutPrice
					|| LocalDateTime.now().isAfter(this.closeTime)) {
				if (priceWithSpread > this.profitTakePrice) {
					LOGGER.info("利確します。");
					NOTIFIER.sendMessage("利確します。");
				} else if (priceWithSpread < this.lossCutPrice) {
					LOGGER.info("損切します。");
					NOTIFIER.sendMessage("損切します。");
				} else {
					LOGGER.info("時間経過のためクローズします。");
					NOTIFIER.sendMessage("時間経過のためクローズします。");
				}
				positionClear();
				this.hasPosition = false;
				this.side = null;
				this.size = 0;
				this.entry = -1;
				this.profitTakePrice = -1;
				this.lossCutPrice = -1;
				this.closeTime = null;
				outputCurrentStatus();
				outputCurrentStatusSlack();
			}
		} else {
			// ショートポジション
			int priceWithSpread = (int) (price * (1 + SPREAD_PERCENTAGE / 100));
			if (priceWithSpread < this.profitTakePrice || priceWithSpread > this.lossCutPrice
					|| LocalDateTime.now().isAfter(this.closeTime)) {
				if (priceWithSpread < this.profitTakePrice) {
					LOGGER.info("利確します。");
					NOTIFIER.sendMessage("利確します。");
				} else if (priceWithSpread > this.lossCutPrice) {
					LOGGER.info("損切します。");
					NOTIFIER.sendMessage("損切します。");
				} else {
					LOGGER.info("時間経過のためクローズします。");
					NOTIFIER.sendMessage("時間経過のためクローズします。");
				}
				positionClear();
				this.hasPosition = false;
				this.side = null;
				this.size = 0;
				this.entry = -1;
				this.profitTakePrice = -1;
				this.lossCutPrice = -1;
				this.closeTime = null;
				outputCurrentStatus();
				outputCurrentStatusSlack();
			}
		}
	}

	private void buy() {
		LOGGER.debug("buy!");
		if (this.side == BuySellEnum.BUY) {
			throw new IllegalStateException("sideがBUYの時に買注文を出そうとしています。");
		}
		double longPositionSize = getPositionTotalSize(BuySellEnum.BUY);
		if (longPositionSize != 0) {
			throw new IllegalStateException(
					"ロングポジションがある状態で買注文を出そうとしています。数量：" + String.format("%.3f", longPositionSize));
		}
		if (!isHealthy()) {
			String status = getHealthStatus();
			LOGGER.info("取引所の状態が通常ではない、、またはメンテナンス時間のため、買発注をスキップします。ステータス：" + status);
			NOTIFIER.sendMessage("取引所の状態が通常ではない、またはメンテナンス時間のため、買発注をスキップします。ステータス：" + status);
			return;
		}
		// 価格計算
		int mid = getMidPrice();
		if (mid == -1) {
			// 板情報が取れなければ処理しない
			LOGGER.debug("板情報取得に失敗したので買発注をスキップします。");
			return;
		}
		this.price = mid;
		// Midにスプレッド片側分を加算
		int ask = (int) (mid + mid * SPREAD_PERCENTAGE / 200);
		resetCollateral();

		// 今の証拠金から発注数量を計算
		// (誤差排除するため1000倍にする)
		int qtyX1000 = this.collateral * 1000 / ask;
		// レバレッジ倍率を加味
		double qty = qtyX1000 * this.LEVERAGE / 1000.000;
		// 広めに価格を決定(Midに1%乗せる)
		int orderPrice = (int) (mid + mid * 0.01);

		// 買発注
		String qtyStr = String.format("%.3f", qty);
		ChildOrderResponse response = order(BuySellEnum.BUY, orderPrice, Double.valueOf(qtyStr), OrderTypeEnum.MARKET);
		if (response != null) {
			this.hasPosition = true;
			this.entry = orderPrice;
			this.side = BuySellEnum.BUY;
			this.size = qty;
			LOGGER.info("買発注成功!");
			NOTIFIER.sendMessage("買発注成功!");
			outputCurrentStatus();
			outputCurrentStatusSlack();
		} else {
			LOGGER.info("買発注失敗!");
			NOTIFIER.sendMessage("買発注失敗!");
		}
	}

	private void sell() {
		LOGGER.debug("sell!");
		if (this.side == BuySellEnum.SELL) {
			throw new IllegalStateException("sideがSELLの時に売注文を出そうとしています。");
		}
		double shortPositionSize = getPositionTotalSize(BuySellEnum.SELL);
		if (shortPositionSize != 0) {
			throw new IllegalStateException(
					"ショートポジションがある状態で売注文を出そうとしています。数量：" + String.format("%.3f", shortPositionSize));
		}
		if (!isHealthy()) {
			String status = getHealthStatus();
			LOGGER.info("取引所の状態が通常ではない、またはメンテナンス時間のため、売発注をスキップします。ステータス：" + status);
			NOTIFIER.sendMessage("取引所の状態が通常ではない、またはメンテナンス時間のため、売発注をスキップします。ステータス：" + status);
			return;
		}
		// 価格計算
		int mid = getMidPrice();
		if (mid == -1) {
			// 板情報が取れなければ処理しない
			LOGGER.debug("板情報取得に失敗したので売発注をスキップします。");
			return;
		}
		this.price = mid;
		// Midにスプレッド片側分を減算
		int bid = (int) (mid - mid * SPREAD_PERCENTAGE / 200);
		resetCollateral();

		// 今の証拠金から発注数量を計算
		// (誤差排除するため1000倍にする)
		int qtyX1000 = this.collateral * 1000 / bid;
		// レバレッジ倍率を加味
		double qty = qtyX1000 * this.LEVERAGE / 1000.000;
		// 広めに価格を決定(Midから1%引く)
		int orderPrice = (int) (mid - mid * 0.01);

		// 売発注
		String qtyStr = String.format("%.3f", qty);
		ChildOrderResponse response = order(BuySellEnum.SELL, orderPrice, Double.valueOf(qtyStr), OrderTypeEnum.MARKET);
		if (response != null) {
			this.hasPosition = true;
			this.entry = orderPrice;
			this.side = BuySellEnum.SELL;
			this.size = qty;
			LOGGER.info("売発注成功!");
			NOTIFIER.sendMessage("売発注成功!");
			outputCurrentStatus();
			outputCurrentStatusSlack();
		} else {
			LOGGER.info("売発注失敗!");
			NOTIFIER.sendMessage("売発注失敗!");
		}
	}

	private void outputCurrentStatus() {
		LOGGER.info("[current status] collateral:" + this.collateral + " OpenPL:" + this.openPl + " side:"
				+ (this.side == BuySellEnum.BUY ? "買" : "売") + " size:" + this.size + " entry:" + this.entry
				+ " profitTakePrice:" + this.profitTakePrice + " lossCutPrice:" + this.lossCutPrice + " closeTime:"
				+ this.closeTime + " price:" + this.price);
	}

	@Override
	protected void outputCurrentStatusSlack() {
		NOTIFIER.sendMessage("[current status] collateral:" + this.collateral + " OpenPL:" + this.openPl + " side:"
				+ (this.side == BuySellEnum.BUY ? "買" : "売") + " size:" + this.size + " entry:" + this.entry
				+ " profitTakePrice:" + this.profitTakePrice + " lossCutPrice:" + this.lossCutPrice + " closeTime:"
				+ this.closeTime + " price:" + this.price);
	}

}
