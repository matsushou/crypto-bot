package logic;

import java.time.LocalDateTime;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import core.OHLCVUtil;
import exchange.BitFlyerAPIWrapper;
import model.BoardResponse;
import model.BuySellEnum;
import model.ChildOrderResponse;
import model.PositionResponse;
import notification.SlackNotifier;

public class ProfitTrailDealingLogic {

	private final BitFlyerAPIWrapper WRAPPER;
	private final SlackNotifier NOTIFIER;
	private final double TRAIL_PERCENTAGE;
	private final double LOSS_CUT_PERCENTAGE;
	private final double SPREAD;
	private final int INTERVAL;
	private volatile int trailLine = -1;
	private volatile int lossCutLine = -1;
	private volatile boolean trailing = false;
	private volatile int entry = -1;
	private volatile BuySellEnum side;
	private volatile double size;
	private volatile int collateral = -1;
	private volatile int mid = -1;

	private static Logger LOGGER = LogManager.getLogger(ProfitTrailDealingLogic.class);

	public ProfitTrailDealingLogic(BitFlyerAPIWrapper wrapper, SlackNotifier notifier, Map<String, Double> paramMap,
			Map<String, Object> settings) {
		this.WRAPPER = wrapper;
		this.NOTIFIER = notifier;
		this.TRAIL_PERCENTAGE = paramMap.get("trailPercentage");
		this.LOSS_CUT_PERCENTAGE = paramMap.get("lossCutPercentage");
		@SuppressWarnings("unchecked")
		Map<String, Double> logicParam = (Map<String, Double>) settings.get("logic");
		// パラメータ出力
		StringBuilder sb = new StringBuilder();
		sb.append("LogicParams");
		logicParam.forEach((k, v) -> sb.append(" " + k + ":" + v));
		LOGGER.info(sb.toString());
		this.SPREAD = logicParam.get("spread");
		this.INTERVAL = logicParam.get("notifyInterval").intValue();
	}

	public void execute() {
		// 初期化
		init();
		// 分足作成スレッドの開始(1分毎の処理も執行判断もこの中で行う)
		startOhlcvThread();
		// 定期通知スレッドの開始(定期的にSlack通知)
		startPeriodicalNotifyThread(INTERVAL);
	}

	private void init() {
		// ポジションクリア
		positionClear();
		// 処理反映まで少し待つ
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		// 証拠金評価額取得
		int collateral = getCollateral();
		LOGGER.info("証拠金評価額:" + collateral);
		// 最初はとりあえず買う(メンテナンス中の起動は想定しない)
		buy();
	}

	private void startOhlcvThread() {
		// 毎秒Midを取得し、分足を作成するスレッド
		Thread t = new Thread(() -> {
			int lastSecond = -1;
			int count = 0;
			int[] ohlcv = new int[4];
			// オーバーヘッド減らすため、最初一回リクエストしておく
			getMidPrice();
			while (true) {
				LocalDateTime now = LocalDateTime.now();
				int second = now.getSecond();
				if (second != lastSecond) {
					// 秒が変わったらMid取得
					int mid = getMidPrice();
					LOGGER.debug("Mid取得結果：" + mid + " 時刻:" + now);
					this.mid = mid;
					// High,Lowの更新チェック
					ohlcv = OHLCVUtil.replaceHighAndLow(ohlcv, mid);
					if (count % 60 == 0) {
						// 1分毎の処理
						if (count != 0) {
							// 初回は除く
							// 前の分足にcloseを設定
							ohlcv = OHLCVUtil.setClose(ohlcv, mid);
							// 執行判断
							judge();
							LOGGER.debug(OHLCVUtil.toString(ohlcv));
						}
						// OHLCVの初期化とopen,high,lowの設定
						ohlcv = new int[4];
						ohlcv = OHLCVUtil.setOpen(ohlcv, mid);
						ohlcv = OHLCVUtil.setHigh(ohlcv, mid);
						ohlcv = OHLCVUtil.setLow(ohlcv, mid);
					}
					lastSecond = second;
					count++;
				}
				try {
					// だんだんズレていかないように50ms単位とする
					Thread.sleep(50);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}, "ohlcvThread");
		t.start();
	}

	private void startPeriodicalNotifyThread(int intervalMin) {
		Thread t = new Thread(() -> {
			while (true) {
				outputCurrentStatusSlack();
				try {
					Thread.sleep(intervalMin * 60 * 1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}, "notifyThread");
		t.start();
	}

	private int getMidPrice() {
		BoardResponse board = WRAPPER.getBoard();
		if (board == null) {
			throw new IllegalStateException("Board Response is null.");
		}
		return (int) board.getMidPrice();
	}

	private void judge() {
		LOGGER.debug("judge!");
		outputCurrentStatus();
		int mid = getMidPrice();
		if (side == BuySellEnum.BUY) {
			// ロングポジション
			// Midにスプレッド片側分を減算
			int bid = (int) (mid - mid * SPREAD / 200);
			if (bid < lossCutLine || (trailing && bid <= trailLine)) {
				// ロスカットラインを下回った、またはトレール中にトレールラインを下回った
				// ポジションクローズしてドテンさせる
				if (bid < lossCutLine) {
					LOGGER.info("ロングポジション：ロスカットします。Bid:" + bid);
					NOTIFIER.sendMessage("ロングポジション：ロスカットします。Bid:" + bid);
				} else {
					LOGGER.info("ロングポジション：利確します。Bid:" + bid);
					NOTIFIER.sendMessage("ロングポジション：利確します。Bid:" + bid);
				}
				// ドテンさせる
				sell();
			} else if (bid > trailLine) {
				if (!trailing) {
					// トレール開始
					LOGGER.info("ロングポジション：トレール開始します。Bid:" + bid);
					NOTIFIER.sendMessage("ロングポジション：トレール開始します。Bid:" + bid);
					trailing = true;
				} else {
					// トレール更新
					LOGGER.debug("ロングポジション：トレール更新します。Bid:" + bid);
				}
				trailLine = bid;
			}
		} else {
			// ショートポジション
			// Midにスプレッド片側分を加算
			int ask = (int) (mid + mid * SPREAD / 200);
			if (ask > lossCutLine || (trailing && ask >= trailLine)) {
				// ロスカットラインを上回った、またはトレール中にトレールラインを上回った
				// ポジションクローズしてドテンさせる
				if (ask > lossCutLine) {
					LOGGER.info("ショートポジション：ロスカットします。Ask:" + ask);
					NOTIFIER.sendMessage("ショートポジション：ロスカットします。Ask:" + ask);
				} else {
					LOGGER.info("ショートポジション：利確します。Ask:" + ask);
					NOTIFIER.sendMessage("ショートポジション：利確します。Ask:" + ask);
				}
				// ドテンさせる
				buy();
			} else if (ask < trailLine) {
				if (!trailing) {
					// トレール開始
					LOGGER.info("ショートポジション：トレール開始します。Ask:" + ask);
					NOTIFIER.sendMessage("ショートポジション：トレール開始します。Ask:" + ask);
					trailing = true;
				} else {
					// トレール更新
					LOGGER.debug("ショートポジション：トレール更新します。Ask:" + ask);
				}
				trailLine = ask;
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
			LOGGER.info("取引所の状態が通常ではないため、買発注をスキップします。ステータス：" + status);
			NOTIFIER.sendMessage("取引所の状態が通常ではないため、買発注をスキップします。ステータス：" + status);
		}
		// 数量計算
		// ドテン分のショートポジションを取得
		double positionSize = getPositionTotalSize(BuySellEnum.SELL);
		int mid = getMidPrice();
		this.mid = getMidPrice();
		// Midにスプレッド片側分を加算
		int ask = (int) (mid + mid * SPREAD / 200);
		int collateral = getCollateral();

		// 誤差排除するため1000倍にする
		int qtyX1000 = collateral * 1000 / ask;
		if (qtyX1000 < 10) {
			// 0.01以下の場合は発注しない
			LOGGER.info("発注数量が0.01以下です。");
		} else {
			double qty = qtyX1000 / 1000.000;
			// ドテン分を加算
			double qtyWithDoten = qty + positionSize;
			String qtyStr = String.format("%.3f", qtyWithDoten);
			// 広めに価格を決定(Midに1%乗せる)
			int orderPrice = (int) (mid + mid * 0.01);
			// 買発注
			ChildOrderResponse response = order(BuySellEnum.BUY, orderPrice, Double.valueOf(qtyStr));
			if (response != null) {
				resetPositionFields(ask, qty, BuySellEnum.BUY);
			} else {
				LOGGER.info("買発注失敗!");
				NOTIFIER.sendMessage("買発注失敗!");
			}
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
			LOGGER.info("取引所の状態が通常ではないため、売発注をスキップします。ステータス：" + status);
			NOTIFIER.sendMessage("取引所の状態が通常ではないため、売発注をスキップします。ステータス：" + status);
		}
		// 数量計算
		// ドテン分のショートポジションを取得
		double positionSize = getPositionTotalSize(BuySellEnum.BUY);
		int mid = getMidPrice();
		this.mid = getMidPrice();
		// Midにスプレッド片側分を減算
		int bid = (int) (mid - mid * SPREAD / 200);
		int collateral = getCollateral();

		// 誤差排除するため1000倍にする
		int qtyX1000 = collateral * 1000 / bid;
		if (qtyX1000 < 10) {
			// 0.01以下の場合は発注しない
			LOGGER.info("発注数量が0.01以下です。");
		} else {
			double qty = qtyX1000 / 1000.000;
			// ドテン分を加算
			double qtyWithDoten = qty + positionSize;
			String qtyStr = String.format("%.3f", qtyWithDoten);
			// 広めに価格を決定(Midから1%引く)
			int orderPrice = (int) (mid - mid * 0.01);
			// 売発注
			ChildOrderResponse response = order(BuySellEnum.SELL, orderPrice, Double.valueOf(qtyStr));
			if (response != null) {
				resetPositionFields(bid, qty, BuySellEnum.SELL);
			} else {
				LOGGER.info("売発注失敗!");
				NOTIFIER.sendMessage("売発注失敗!");
			}
		}
	}

	private void positionClear() {
		double longPositionSize = getPositionTotalSize(BuySellEnum.BUY);
		if (longPositionSize != 0) {
			LOGGER.info("ロングポジションをスクエアにします。数量：" + longPositionSize);
			NOTIFIER.sendMessage("ロングポジションをスクエアにします。数量：" + longPositionSize);
			int mid = getMidPrice();
			// 広めに価格を決定(Midから1%引く)
			int orderPrice = (int) (mid - mid * 0.01);
			// リトライありで売発注
			orderWithRetry(BuySellEnum.SELL, orderPrice, longPositionSize);
		} else {
			double shortPositionSize = getPositionTotalSize(BuySellEnum.SELL);
			if (shortPositionSize != 0) {
				LOGGER.info("ショートポジションをスクエアにします。数量：" + shortPositionSize);
				NOTIFIER.sendMessage("ショートポジションをスクエアにします。数量：" + shortPositionSize);
				int mid = getMidPrice();
				// 広めに価格を決定(Midに1%乗せる)
				int orderPrice = (int) (mid + mid * 0.01);
				// リトライありで買発注
				orderWithRetry(BuySellEnum.BUY, orderPrice, shortPositionSize);
			}
		}
	}

	private ChildOrderResponse order(BuySellEnum side, int price, double size) {
		if (!isHealthy()) {
			String status = getHealthStatus();
			LOGGER.info("取引所の状態が通常ではないため、発注をスキップします。side:" + side + "ステータス：" + status);
			NOTIFIER.sendMessage("取引所の状態が通常ではないため、発注をスキップします。side:" + side + "ステータス：" + status);
			return null;
		}
		ChildOrderResponse response = WRAPPER.sendChildOrder(side, price, size);
		LOGGER.info("[order] side:" + side + " price:" + price + " size:" + size + " id:"
				+ (response != null ? response.getChildOrderAcceptanceId() : "null"));
		NOTIFIER.sendMessage("[order] side:" + side + " price:" + price + " size:" + size + " id:"
				+ (response != null ? response.getChildOrderAcceptanceId() : "null"));
		return response;
	}

	private ChildOrderResponse orderWithRetry(BuySellEnum side, int price, double size) {
		boolean healthy = isHealthy();
		if (!healthy) {
			for (int i = 0; i < 20; i++) {
				// 1分待つ
				try {
					Thread.sleep(60000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				healthy = isHealthy();
				if (healthy) {
					break;
				}
			}
		}
		if (!healthy) {
			// リトライしても正常にならない場合は例外送出
			throw new IllegalStateException("取引所の状態が異常な状態が続いています。ステータス:" + getHealthStatus());
		}
		ChildOrderResponse response = WRAPPER.sendChildOrder(side, price, size);
		LOGGER.info("[order] side:" + side + " price:" + price + " size:" + size + " id:"
				+ (response != null ? response.getChildOrderAcceptanceId() : "null"));
		NOTIFIER.sendMessage("[order] side:" + side + " price:" + price + " size:" + size + " id:"
				+ (response != null ? response.getChildOrderAcceptanceId() : "null"));
		return response;
	}

	private void resetPositionFields(int entry, double size, BuySellEnum side) {
		if (side == BuySellEnum.BUY) {
			// 買の場合、トレールラインが高く、ロスカットラインが安く
			this.trailLine = entry + (int) (entry * TRAIL_PERCENTAGE / 100);
			this.lossCutLine = entry - (int) (entry * LOSS_CUT_PERCENTAGE / 100);
		} else {
			// 売の場合、トレールラインが安く、ロスカットラインが高く
			this.trailLine = entry - (int) (entry * TRAIL_PERCENTAGE / 100);
			this.lossCutLine = entry + (int) (entry * LOSS_CUT_PERCENTAGE / 100);
		}
		this.trailing = false;
		this.entry = entry;
		this.side = side;
		this.size = size;
		this.collateral = getCollateral();
		outputCurrentStatus();
		outputCurrentStatusSlack();
	}

	private void outputCurrentStatus() {
		LOGGER.info(
				"[current status] collateral:" + this.collateral + " side:" + (this.side == BuySellEnum.BUY ? "買" : "売")
						+ " size:" + this.size + " entry:" + this.entry + " trailLine:" + this.trailLine
						+ " lossCutLine:" + this.lossCutLine + " trailing:" + this.trailing + " mid:" + this.mid);
	}

	private void outputCurrentStatusSlack() {
		NOTIFIER.sendMessage(
				"[current status] collateral:" + this.collateral + " side:" + (this.side == BuySellEnum.BUY ? "買" : "売")
						+ " size:" + this.size + " entry:" + this.entry + " trailLine:" + this.trailLine
						+ " lossCutLine:" + this.lossCutLine + " trailing:" + this.trailing + " mid:" + this.mid);
	}

	private double getPositionTotalSize(BuySellEnum side) {
		PositionResponse[] responses = WRAPPER.getPositions();
		double size = 0.000;
		String sideStr = side == BuySellEnum.BUY ? "BUY" : "SELL";
		for (PositionResponse response : responses) {
			if (response.getProductCode().equals("FX_BTC_JPY")) {
				if (response.getSide().equals(sideStr)) {
					size += Double.valueOf(response.getSize());
				}
			}
		}
		return size;
	}

	private int getCollateral() {
		this.collateral = WRAPPER.getCollateral().getCollateral();
		return this.collateral;
	}

	private String getHealthStatus() {
		return WRAPPER.getHealth().getStatus();
	}

	private boolean isHealthy() {
		return WRAPPER.isHealthy() && !isMaintenanceTime();
	}

	private boolean isMaintenanceTime() {
		LocalDateTime now = LocalDateTime.now();
		return WRAPPER.isMaintenanceTime(now);
	}

}
