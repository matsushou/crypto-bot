package core;

public class OHLCVUtil {

	public static int[] setOpen(int[] ohlcv, int open) {
		ohlcv[0] = open;
		return ohlcv;
	}

	public static int[] setHigh(int[] ohlcv, int high) {
		ohlcv[1] = high;
		return ohlcv;
	}

	public static int[] setLow(int[] ohlcv, int low) {
		ohlcv[2] = low;
		return ohlcv;
	}

	public static int[] setClose(int[] ohlcv, int close) {
		ohlcv[3] = close;
		return ohlcv;
	}

	public static int getOpen(int[] ohlcv) {
		return ohlcv[0];
	}

	public static int getHigh(int[] ohlcv) {
		return ohlcv[1];
	}

	public static int getLow(int[] ohlcv) {
		return ohlcv[2];
	}

	public static int getClose(int[] ohlcv) {
		return ohlcv[3];
	}

	public static int[] replaceHigh(int[] ohlcv, int price) {
		int currentHigh = getHigh(ohlcv);
		if (price > currentHigh) {
			return setHigh(ohlcv, price);
		}
		return ohlcv;
	}

	public static int[] replaceLow(int[] ohlcv, int price) {
		int currentLow = getLow(ohlcv);
		if (price < currentLow) {
			return setLow(ohlcv, price);
		}
		return ohlcv;
	}

	public static int[] replaceHighAndLow(int[] ohlcv, int price) {
		return replaceHigh(replaceLow(ohlcv, price), price);
	}

	public static String toString(int[] ohlcv) {
		return "open:" + getOpen(ohlcv) + " high:" + getHigh(ohlcv) + " low:" + getLow(ohlcv) + " close:"
				+ getClose(ohlcv);
	}
}
