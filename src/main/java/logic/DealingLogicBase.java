package logic;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import exchange.BitFlyerAPIWrapper;
import model.BoardResponse;
import model.BuySellEnum;
import model.ChildOrderResponse;
import model.CollateralResponse;
import model.OrderTypeEnum;
import model.PositionResponse;
import notification.SlackNotifier;

public class DealingLogicBase {

	protected final BitFlyerAPIWrapper WRAPPER;
	protected final SlackNotifier NOTIFIER;
	protected final Logger LOGGER = LogManager.getLogger(getClass());
	protected volatile int collateral = -1;
	protected volatile int openPl = 0;

	public DealingLogicBase(BitFlyerAPIWrapper wrapper, SlackNotifier notifier, Map<String, Object> paramMap,
			Map<String, Object> settings) {
		super();
		this.WRAPPER = wrapper;
		this.NOTIFIER = notifier;
	}

	public void execute() {
		// Overrideする
	}

	protected void startPeriodicalNotifyThread(int intervalMin) {
		Thread t = new Thread(() -> {
			while (true) {
				try {
					Thread.sleep(intervalMin * 60 * 1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				resetCollateral();
				outputCurrentStatusSlack();
			}
		}, "notifyThread");
		t.start();
	}

	protected void outputCurrentStatusSlack() {
		// Overrideする
	}

	protected int getMidPrice() {
		BoardResponse board = WRAPPER.getBoard();
		if (board == null) {
			LOGGER.info("Board Response is null.");
			NOTIFIER.sendMessage("板情報の取得に失敗しました。");
			return -1;
		}
		return (int) board.getMidPrice();
	}

	protected void positionClear() {
		double longPositionSize = getPositionTotalSize(BuySellEnum.BUY);
		if (longPositionSize != 0) {
			LOGGER.info("ロングポジションをスクエアにします。数量：" + longPositionSize);
			NOTIFIER.sendMessage("ロングポジションをスクエアにします。数量：" + longPositionSize);
			int mid = getMidPrice();
			if (mid == -1) {
				// 板情報が取れなければ処理停止
				throw new IllegalStateException("板情報取得に失敗したのでポジションクリアに失敗しました。");
			}
			// 広めに価格を決定(Midから1%引く)
			int orderPrice = (int) (mid - mid * 0.01);
			// リトライありで売発注
			orderWithRetry(BuySellEnum.SELL, orderPrice, longPositionSize, OrderTypeEnum.MARKET);
		} else {
			double shortPositionSize = getPositionTotalSize(BuySellEnum.SELL);
			if (shortPositionSize != 0) {
				LOGGER.info("ショートポジションをスクエアにします。数量：" + shortPositionSize);
				NOTIFIER.sendMessage("ショートポジションをスクエアにします。数量：" + shortPositionSize);
				int mid = getMidPrice();
				if (mid == -1) {
					// 板情報が取れなければ処理停止
					throw new IllegalStateException("板情報取得に失敗したのでポジションクリアに失敗しました。");
				}
				// 広めに価格を決定(Midに1%乗せる)
				int orderPrice = (int) (mid + mid * 0.01);
				// リトライありで買発注
				orderWithRetry(BuySellEnum.BUY, orderPrice, shortPositionSize, OrderTypeEnum.MARKET);
			}
		}
	}

	protected ChildOrderResponse order(BuySellEnum side, int price, double size, OrderTypeEnum orderType) {
		if (!isHealthy()) {
			String status = getHealthStatus();
			LOGGER.info("取引所の状態が通常ではない、、またはメンテナンス時間のため、発注をスキップします。side:" + side + "ステータス：" + status);
			NOTIFIER.sendMessage("取引所の状態が通常ではない、、またはメンテナンス時間のため、発注をスキップします。side:" + side + "ステータス：" + status);
			return null;
		}
		ChildOrderResponse response = WRAPPER.sendChildOrder(side, price, size, orderType);
		LOGGER.info("[order] side:" + side + " price:" + price + " size:" + size + " orderType:" + orderType + " id:"
				+ (response != null ? response.getChildOrderAcceptanceId() : "null"));
		NOTIFIER.sendMessage("[order] side:" + side + " price:" + price + " size:" + size + " orderType:" + orderType
				+ " id:" + (response != null ? response.getChildOrderAcceptanceId() : "null"));
		return response;
	}

	protected ChildOrderResponse orderWithRetry(BuySellEnum side, int price, double size, OrderTypeEnum orderType) {
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
		ChildOrderResponse response = WRAPPER.sendChildOrder(side, price, size, orderType);
		LOGGER.info("[order] side:" + side + " price:" + price + " size:" + size + " orderType:" + orderType + " id:"
				+ (response != null ? response.getChildOrderAcceptanceId() : "null"));
		NOTIFIER.sendMessage("[order] side:" + side + " price:" + price + " size:" + size + " orderType:" + orderType
				+ " id:" + (response != null ? response.getChildOrderAcceptanceId() : "null"));
		return response;
	}

	protected double getPositionTotalSize(BuySellEnum side) {
		PositionResponse[] responses = WRAPPER.getPositions();
		BigDecimal size = BigDecimal.ZERO;
		String sideStr = side == BuySellEnum.BUY ? "BUY" : "SELL";
		for (PositionResponse response : responses) {
			if (response.getProductCode().equals("FX_BTC_JPY")) {
				if (response.getSide().equals(sideStr)) {
					size = size.add(new BigDecimal(response.getSize()));
				}
			}
		}
		return size.doubleValue();
	}

	protected void resetCollateral() {
		CollateralResponse response = WRAPPER.getCollateral();
		this.collateral = response.getCollateral();
		this.openPl = response.getOpenPositionPnl();
	}

	protected String getHealthStatus() {
		return WRAPPER.getHealth().getStatus();
	}

	protected boolean isHealthy() {
		return WRAPPER.isHealthy() && !isMaintenanceTime();
	}

	protected boolean isMaintenanceTime() {
		LocalDateTime now = LocalDateTime.now();
		return WRAPPER.isMaintenanceTime(now);
	}

}
