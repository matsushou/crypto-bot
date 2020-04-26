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
	private int trailLine = -1;
	private int lossCutLine = -1;
	private boolean trailing = false;
	private int entry = -1;
	private BuySellEnum side;
	private double size;
	private int collateral = -1;
	private int mid = -1;

	private static Logger LOGGER = LogManager.getLogger(ProfitTrailDealingLogic.class);

	public ProfitTrailDealingLogic(BitFlyerAPIWrapper wrapper, SlackNotifier notifier, Map<String, Double> paramMap,
			Map<String, Object> settings) {
		this.WRAPPER = wrapper;
		this.NOTIFIER = notifier;
		this.TRAIL_PERCENTAGE = paramMap.get("trailPercentage");
		this.LOSS_CUT_PERCENTAGE = paramMap.get("lossCutPercentage");
		@SuppressWarnings("unchecked")
		Map<String, Double> logicParam = (Map<String, Double>) settings.get("logic");
		// $B%Q%i%a!<%?=PNO(B
		StringBuilder sb = new StringBuilder();
		sb.append("LogicParams");
		logicParam.forEach((k, v) -> sb.append(" " + k + ":" + v));
		LOGGER.info(sb.toString());
		this.SPREAD = logicParam.get("spread");
		this.INTERVAL = logicParam.get("notifyInterval").intValue();
	}

	public void execute() {
		// $B=i4|2=(B
		init();
		// $BJ,B-:n@.%9%l%C%I$N3+;O(B(1$BJ,Kh$N=hM}$b<99TH=CG$b$3$NCf$G9T$&(B)
		startOhlcvThread();
		// $BDj4|DLCN%9%l%C%I$N3+;O(B($BDj4|E*$K(BSlack$BDLCN(B)
		startPeriodicalNotifyThread(INTERVAL);
	}

	private void init() {
		// $B%]%8%7%g%s%/%j%"(B
		positionClear();
		// $B=hM}H?1G$^$G>/$7BT$D(B
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		// $B>Z5r6bI>2A3[<hF@(B
		int collateral = getCollateral();
		LOGGER.info("$B>Z5r6bI>2A3[(B:" + collateral);
		// $B:G=i$O$H$j$"$($:Gc$&(B
		buy();
	}

	private void startOhlcvThread() {
		// $BKhIC(BMid$B$r<hF@$7!"J,B-$r:n@.$9$k%9%l%C%I(B
		Thread t = new Thread(() -> {
			int lastSecond = -1;
			int count = 0;
			int[] ohlcv = new int[4];
			// $B%*!<%P!<%X%C%I8:$i$9$?$a!":G=i0l2s%j%/%(%9%H$7$F$*$/(B
			getMidPrice();
			while (true) {
				LocalDateTime now = LocalDateTime.now();
				int second = now.getSecond();
				if (second != lastSecond) {
					// $BIC$,JQ$o$C$?$i(BMid$B<hF@(B
					int mid = getMidPrice();
					LOGGER.debug("Mid$B<hF@7k2L!'(B" + mid + " $B;~9o(B:" + now);
					this.mid = mid;
					// High,Low$B$N99?7%A%'%C%/(B
					ohlcv = OHLCVUtil.replaceHighAndLow(ohlcv, mid);
					if (count % 60 == 0) {
						// 1$BJ,Kh$N=hM}(B
						if (count != 0) {
							// $B=i2s$O=|$/(B
							// $BA0$NJ,B-$K(Bclose$B$r@_Dj(B
							ohlcv = OHLCVUtil.setClose(ohlcv, mid);
							// $B<99TH=CG(B
							judge();
							LOGGER.debug(OHLCVUtil.toString(ohlcv));
						}
						// OHLCV$B$N=i4|2=$H(Bopen,high,low$B$N@_Dj(B
						ohlcv = new int[4];
						ohlcv = OHLCVUtil.setOpen(ohlcv, mid);
						ohlcv = OHLCVUtil.setHigh(ohlcv, mid);
						ohlcv = OHLCVUtil.setLow(ohlcv, mid);
					}
					lastSecond = second;
					count++;
				}
				try {
					// $B$@$s$@$s%:%l$F$$$+$J$$$h$&$K(B50ms$BC10L$H$9$k(B
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
			// $B%m%s%0%]%8%7%g%s(B
			// Mid$B$K%9%W%l%C%IJRB&J,$r8:;;(B
			int bid = (int) (mid - mid * SPREAD / 200);
			if (bid < lossCutLine || (trailing && bid <= trailLine)) {
				// $B%m%9%+%C%H%i%$%s$r2<2s$C$?!"$^$?$O%H%l!<%kCf$K%H%l!<%k%i%$%s$r2<2s$C$?(B
				// $B%]%8%7%g%s%/%m!<%:$7$F%I%F%s$5$;$k(B
				if (bid < lossCutLine) {
					LOGGER.info("$B%m%s%0%]%8%7%g%s!'%m%9%+%C%H$7$^$9!#(BBid:" + bid);
					NOTIFIER.sendMessage("$B%m%s%0%]%8%7%g%s!'%m%9%+%C%H$7$^$9!#(BBid:" + bid);
				} else {
					LOGGER.info("$B%m%s%0%]%8%7%g%s!'Mx3N$7$^$9!#(BBid:" + bid);
					NOTIFIER.sendMessage("$B%m%s%0%]%8%7%g%s!'Mx3N$7$^$9!#(BBid:" + bid);
				}
				// $B%I%F%s$5$;$k(B
				sell();
			} else if (bid > trailLine) {
				if (!trailing) {
					// $B%H%l!<%k3+;O(B
					LOGGER.info("$B%m%s%0%]%8%7%g%s!'%H%l!<%k3+;O$7$^$9!#(BBid:" + bid);
					NOTIFIER.sendMessage("$B%m%s%0%]%8%7%g%s!'%H%l!<%k3+;O$7$^$9!#(BBid:" + bid);
					trailing = true;
				} else {
					// $B%H%l!<%k99?7(B
					LOGGER.debug("$B%m%s%0%]%8%7%g%s!'%H%l!<%k99?7$7$^$9!#(BBid:" + bid);
				}
				trailLine = bid;
			}
		} else {
			// $B%7%g!<%H%]%8%7%g%s(B
			// Mid$B$K%9%W%l%C%IJRB&J,$r2C;;(B
			int ask = (int) (mid + mid * SPREAD / 200);
			if (ask > lossCutLine || (trailing && ask >= trailLine)) {
				// $B%m%9%+%C%H%i%$%s$r>e2s$C$?!"$^$?$O%H%l!<%kCf$K%H%l!<%k%i%$%s$r>e2s$C$?(B
				// $B%]%8%7%g%s%/%m!<%:$7$F%I%F%s$5$;$k(B
				if (ask > lossCutLine) {
					LOGGER.info("$B%7%g!<%H%]%8%7%g%s!'%m%9%+%C%H$7$^$9!#(BAsk:" + ask);
					NOTIFIER.sendMessage("$B%7%g!<%H%]%8%7%g%s!'%m%9%+%C%H$7$^$9!#(BAsk:" + ask);
				} else {
					LOGGER.info("$B%7%g!<%H%]%8%7%g%s!'Mx3N$7$^$9!#(BAsk:" + ask);
					NOTIFIER.sendMessage("$B%7%g!<%H%]%8%7%g%s!'Mx3N$7$^$9!#(BAsk:" + ask);
				}
				// $B%I%F%s$5$;$k(B
				buy();
			} else if (ask < trailLine) {
				if (!trailing) {
					// $B%H%l!<%k3+;O(B
					LOGGER.info("$B%7%g!<%H%]%8%7%g%s!'%H%l!<%k3+;O$7$^$9!#(BAsk:" + ask);
					NOTIFIER.sendMessage("$B%7%g!<%H%]%8%7%g%s!'%H%l!<%k3+;O$7$^$9!#(BAsk:" + ask);
					trailing = true;
				} else {
					// $B%H%l!<%k99?7(B
					LOGGER.debug("$B%7%g!<%H%]%8%7%g%s!'%H%l!<%k99?7$7$^$9!#(BAsk:" + ask);
				}
				trailLine = ask;
			}
		}
	}

	private void buy() {
		LOGGER.debug("buy!");
		assert (this.side == null || this.side == BuySellEnum.SELL);
		// $B?tNL7W;;(B
		// $B%I%F%sJ,$N%7%g!<%H%]%8%7%g%s$r<hF@(B
		double positionSize = getPositionTotalSize(BuySellEnum.SELL);
		int mid = getMidPrice();
		this.mid = getMidPrice();
		// Mid$B$K%9%W%l%C%IJRB&J,$r2C;;(B
		int ask = (int) (mid + mid * SPREAD / 200);
		int collateral = getCollateral();

		// $B8m:9GS=|$9$k$?$a(B1000$BG\$K$9$k(B
		int qtyX1000 = collateral * 1000 / ask;
		if (qtyX1000 < 1) {
			// 0.001$B0J2<$N>l9g$OH/Cm$7$J$$(B
			LOGGER.info("$BH/Cm?tNL$,(B0.001$B0J2<$G$9!#(B");
		} else {
			double qty = qtyX1000 / 1000.000;
			// $B%I%F%sJ,$r2C;;(B
			double qtyWithDoten = qty + positionSize;
			String qtyStr = String.format("%.3f", qtyWithDoten);
			// $B9-$a$K2A3J$r7hDj(B(Mid$B$K(B1%$B>h$;$k(B)
			int orderPrice = (int) (mid + mid * 0.01);
			// $BGcH/Cm!J@.8y$7$?$H$_$J$9!K(B
			order(BuySellEnum.BUY, orderPrice, Double.valueOf(qtyStr));
			resetPositionFields(ask, qty, BuySellEnum.BUY);
		}
	}

	private void sell() {
		LOGGER.debug("sell!");
		assert (this.side == null || this.side == BuySellEnum.BUY);
		// $B?tNL7W;;(B
		// $B%I%F%sJ,$N%7%g!<%H%]%8%7%g%s$r<hF@(B
		double positionSize = getPositionTotalSize(BuySellEnum.BUY);
		int mid = getMidPrice();
		this.mid = getMidPrice();
		// Mid$B$K%9%W%l%C%IJRB&J,$r8:;;(B
		int bid = (int) (mid - mid * SPREAD / 200);
		int collateral = getCollateral();

		// $B8m:9GS=|$9$k$?$a(B1000$BG\$K$9$k(B
		int qtyX1000 = collateral * 1000 / bid;
		if (qtyX1000 < 1) {
			// 0.001$B0J2<$N>l9g$OH/Cm$7$J$$(B
			LOGGER.info("$BH/Cm?tNL$,(B0.001$B0J2<$G$9!#(B");
		} else {
			double qty = qtyX1000 / 1000.000;
			// $B%I%F%sJ,$r2C;;(B
			double qtyWithDoten = qty + positionSize;
			String qtyStr = String.format("%.3f", qtyWithDoten);
			// $B9-$a$K2A3J$r7hDj(B(Mid$B$+$i(B1%$B0z$/(B)
			int orderPrice = (int) (mid - mid * 0.01);
			// $BGdH/Cm!J@.8y$7$?$H$_$J$9!K(B
			order(BuySellEnum.SELL, orderPrice, Double.valueOf(qtyStr));
			resetPositionFields(bid, qty, BuySellEnum.SELL);
		}
	}

	private void positionClear() {
		double longPositionSize = getPositionTotalSize(BuySellEnum.BUY);
		if (longPositionSize != 0) {
			LOGGER.info("$B%m%s%0%]%8%7%g%s$r%9%/%(%"$K$7$^$9!#?tNL!'(B" + longPositionSize);
			NOTIFIER.sendMessage("$B%m%s%0%]%8%7%g%s$r%9%/%(%"$K$7$^$9!#?tNL!'(B" + longPositionSize);
			int mid = getMidPrice();
			// $B9-$a$K2A3J$r7hDj(B(Mid$B$+$i(B1%$B0z$/(B)
			int orderPrice = (int) (mid - mid * 0.01);
			// $BGdH/Cm!J@.8y$7$?$H$_$J$9!K(B
			order(BuySellEnum.SELL, orderPrice, longPositionSize);
		} else {
			double shortPositionSize = getPositionTotalSize(BuySellEnum.SELL);
			if (shortPositionSize != 0) {
				LOGGER.info("$B%7%g!<%H%]%8%7%g%s$r%9%/%(%"$K$7$^$9!#?tNL!'(B" + shortPositionSize);
				NOTIFIER.sendMessage("$B%7%g!<%H%]%8%7%g%s$r%9%/%(%"$K$7$^$9!#?tNL!'(B" + shortPositionSize);
				int mid = getMidPrice();
				// $B9-$a$K2A3J$r7hDj(B(Mid$B$K(B1%$B>h$;$k(B)
				int orderPrice = (int) (mid + mid * 0.01);
				// $BGcH/Cm!J@.8y$7$?$H$_$J$9!K(B
				order(BuySellEnum.BUY, orderPrice, shortPositionSize);
			}
		}
	}

	private ChildOrderResponse order(BuySellEnum side, int price, double size) {
		LOGGER.info("[order] side:" + side + " price:" + price + " size:" + size);
		NOTIFIER.sendMessage("[order] side:" + side + " price:" + price + " size:" + size);
		ChildOrderResponse response = WRAPPER.sendChildOrder(side, price, size);
		return response;
	}

	private void resetPositionFields(int entry, double size, BuySellEnum side) {
		if (side == BuySellEnum.BUY) {
			// $BGc$N>l9g!"%H%l!<%k%i%$%s$,9b$/!"%m%9%+%C%H%i%$%s$,0B$/(B
			this.trailLine = entry + (int) (entry * TRAIL_PERCENTAGE / 100);
			this.lossCutLine = entry - (int) (entry * LOSS_CUT_PERCENTAGE / 100);
		} else {
			// $BGd$N>l9g!"%H%l!<%k%i%$%s$,0B$/!"%m%9%+%C%H%i%$%s$,9b$/(B
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
		LOGGER.info("[current status] collateral:" + this.collateral + " entry:" + this.entry + " trailLine:"
				+ this.trailLine + " lossCutLine:" + this.lossCutLine + " side:" + this.side + " size:" + this.size
				+ " trailing:" + this.trailing + " mid:" + this.mid);
	}

	private void outputCurrentStatusSlack() {
		NOTIFIER.sendMessage("[current status] collateral:" + this.collateral + " entry:" + this.entry + " trailLine:"
				+ this.trailLine + " lossCutLine:" + this.lossCutLine + " side:" + this.side + " size:" + this.size
				+ " trailing:" + this.trailing + " mid:" + this.mid);
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

}
