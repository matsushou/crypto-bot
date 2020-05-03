package core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import exchange.BitFlyerAPIWrapper;

class BitFlyerAPIWrapperTest {

	static BitFlyerAPIWrapper WRAPPER;

	@BeforeAll
	static void setUpBeforeClass() throws Exception {
		Map<String, Object> settings = new HashMap<>();
		Map<String, String> exchangeParam = new HashMap<>();
		exchangeParam.put("apiKey", "dummy");
		exchangeParam.put("secret", "dummy");
		settings.put("exchange", exchangeParam);
		WRAPPER = BitFlyerAPIWrapper.getInstance(settings);
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
	void isMaintenanceTimeTest() {
		LocalDate date = LocalDate.of(2020, 5, 1);

		LocalDateTime time035700 = LocalDateTime.of(date, LocalTime.of(3, 57, 0));
		assertFalse(WRAPPER.isMaintenanceTime(time035700));

		LocalDateTime time035729 = LocalDateTime.of(date, LocalTime.of(3, 57, 29));
		assertFalse(WRAPPER.isMaintenanceTime(time035729));

		LocalDateTime time035759 = LocalDateTime.of(date, LocalTime.of(3, 57, 59));
		assertTrue(WRAPPER.isMaintenanceTime(time035759));

		LocalDateTime time035800 = LocalDateTime.of(date, LocalTime.of(3, 58, 0));
		assertTrue(WRAPPER.isMaintenanceTime(time035800));

		LocalDateTime time040000 = LocalDateTime.of(date, LocalTime.of(4, 00, 0));
		assertTrue(WRAPPER.isMaintenanceTime(time040000));

		LocalDateTime time041100 = LocalDateTime.of(date, LocalTime.of(4, 11, 0));
		assertTrue(WRAPPER.isMaintenanceTime(time041100));

		LocalDateTime time041159 = LocalDateTime.of(date, LocalTime.of(4, 11, 59));
		assertTrue(WRAPPER.isMaintenanceTime(time041159));

		LocalDateTime time041200 = LocalDateTime.of(date, LocalTime.of(4, 12, 0));
		assertTrue(WRAPPER.isMaintenanceTime(time041200));

		LocalDateTime time041230 = LocalDateTime.of(date, LocalTime.of(4, 12, 30));
		assertTrue(WRAPPER.isMaintenanceTime(time041230));

		LocalDateTime time041231 = LocalDateTime.of(date, LocalTime.of(4, 12, 31));
		assertFalse(WRAPPER.isMaintenanceTime(time041231));

		LocalDateTime time120000 = LocalDateTime.of(date, LocalTime.of(12, 0, 0));
		assertFalse(WRAPPER.isMaintenanceTime(time120000));

		// AM,PM確認
		LocalDateTime time160000 = LocalDateTime.of(date, LocalTime.of(16, 0, 0));
		assertFalse(WRAPPER.isMaintenanceTime(time160000));

		// Date部分が関係ない確認
		LocalDate yesterday = LocalDate.of(2020, 4, 30);
		LocalDateTime time040000_y = LocalDateTime.of(yesterday, LocalTime.of(4, 0, 0));
		assertTrue(WRAPPER.isMaintenanceTime(time040000_y));
	}

}
