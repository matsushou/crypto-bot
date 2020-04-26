package core;

import java.io.InputStreamReader;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

import exchange.BitFlyerAPIWrapper;
import logic.ProfitTrailDealingLogic;
import notification.SlackNotifier;

public class BotMain {

	private static BitFlyerAPIWrapper wrapper;
	private static SlackNotifier notifier;
	private static Logger LOGGER = LogManager.getLogger(BotMain.class);
	private static Map<String, Object> settings;

	public static void main(String[] args) {
		LOGGER.info("bot$B5/F0$7$^$9!D(B");
		// $B%9%l%C%I$N%m%0=PNO@_Dj(B
		Thread.UncaughtExceptionHandler dueh = Thread.getDefaultUncaughtExceptionHandler();
		if (dueh == null) {
			Thread.setDefaultUncaughtExceptionHandler((Thread t, Throwable e) -> {
				LOGGER.error("$BNc30H/@8!*(B", e);
			});
		}
		// $B@_DjCMFI$_9~$_(B
		settings = loadSettings();
		wrapper = BitFlyerAPIWrapper.getInstance(settings);
		System.out.println(wrapper.getCollateral().getCollateral());
		notifier = SlackNotifier.getInstance(settings);
		@SuppressWarnings("unchecked")
		Map<String, Double> paramMap = (Map<String, Double>) settings.get("common");
		// $B%Q%i%a!<%?=PNO(B
		StringBuilder sb = new StringBuilder();
		sb.append("CommonParams");
		paramMap.forEach((k, v) -> sb.append(" " + k + ":" + v));
		LOGGER.info(sb.toString());
		ProfitTrailDealingLogic logic = new ProfitTrailDealingLogic(wrapper, notifier, paramMap, settings);
		LOGGER.info("$B%m%8%C%/3+;O$7$^$9!D(B");
		notifier.sendMessage("LOGIC START!");
		logic.execute();
	}

	private static Map<String, Object> loadSettings() {
		InputStreamReader reader = new InputStreamReader(ClassLoader.getSystemResourceAsStream("bot_settings.yml"));
		Yaml yaml = new Yaml();
		return yaml.<Map<String, Object>>load(reader);
	}
}
