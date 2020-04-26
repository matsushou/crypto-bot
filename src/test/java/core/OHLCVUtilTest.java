package core;

import static org.junit.Assert.assertEquals;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OHLCVUtilTest {

	@BeforeAll
	static void setUpBeforeClass() throws Exception {
	}

	@AfterAll
	static void tearDownAfterClass() throws Exception {
	}

	@BeforeEach
	void setUp() throws Exception {
	}

	@AfterEach
	void tearDown() throws Exception {
	}

	@Test
	void getOpenTest() {
		int[] ohlcv = new int[] { 100000, 150000, 80000, 120000 };
		int result = OHLCVUtil.getOpen(ohlcv);
		assertEquals(100000, result);
	}

	@Test
	void getHighTest() {
		int[] ohlcv = new int[] { 100000, 150000, 80000, 120000 };
		int result = OHLCVUtil.getHigh(ohlcv);
		assertEquals(150000, result);
	}

	@Test
	void getLowTest() {
		int[] ohlcv = new int[] { 100000, 150000, 80000, 120000 };
		int result = OHLCVUtil.getLow(ohlcv);
		assertEquals(80000, result);
	}

	@Test
	void getCloseTest() {
		int[] ohlcv = new int[] { 100000, 150000, 80000, 120000 };
		int result = OHLCVUtil.getClose(ohlcv);
		assertEquals(120000, result);
	}

	@Test
	void setTest() {
		int[] ohlcv = new int[4];
		int[] result = OHLCVUtil.setOpen(ohlcv, 100000);
		result = OHLCVUtil.setHigh(ohlcv, 150000);
		result = OHLCVUtil.setLow(ohlcv, 80000);
		result = OHLCVUtil.setClose(ohlcv, 120000);
		assertEquals(100000, result[0]);
		assertEquals(150000, result[1]);
		assertEquals(80000, result[2]);
		assertEquals(120000, result[3]);
	}

	@Test
	void replaceTest() {
		int[] ohlcv = new int[] { 100000, 150000, 80000, 120000 };
		int[] result = OHLCVUtil.replaceHigh(ohlcv, 160000);
		assertEquals(160000, result[1]);
		result = OHLCVUtil.replaceHigh(ohlcv, 155000);
		assertEquals(160000, result[1]);
		result = OHLCVUtil.replaceLow(ohlcv, 90000);
		assertEquals(80000, result[2]);
		result = OHLCVUtil.replaceLow(ohlcv, 75000);
		assertEquals(75000, result[2]);
		result = OHLCVUtil.replaceHighAndLow(ohlcv, 100000);
		assertEquals(160000, result[1]);
		assertEquals(75000, result[2]);
		result = OHLCVUtil.replaceHighAndLow(ohlcv, 170000);
		assertEquals(170000, result[1]);
		assertEquals(75000, result[2]);
		result = OHLCVUtil.replaceHighAndLow(ohlcv, 60000);
		assertEquals(170000, result[1]);
		assertEquals(60000, result[2]);
	}

	@Test
	void toStringTest() {
		int[] ohlcv = new int[] { 100000, 150000, 80000, 120000 };
		String result = OHLCVUtil.toString(ohlcv);
		assertEquals("open:100000 high:150000 low:80000 close:120000", result);
	}
}
