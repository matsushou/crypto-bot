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
		LOGGER.info("bot起動します…");
		// スレッドのログ出力設定
		Thread.UncaughtExceptionHandler dueh = Thread.getDefaultUncaughtExceptionHandler();
		if (dueh == null) {
			Thread.setDefaultUncaughtExceptionHandler((Thread t, Throwable e) -> {
				LOGGER.error("例外発生！", e);
			});
		}
		// 設定値読み込み
		settings = loadSettings();
		wrapper = BitFlyerAPIWrapper.getInstance(settings);
		System.out.println(wrapper.getCollateral().getCollateral());
		notifier = SlackNotifier.getInstance(settings);
		@SuppressWarnings("unchecked")
		Map<String, Double> paramMap = (Map<String, Double>) settings.get("common");
		// パラメータ出力
		StringBuilder sb = new StringBuilder();
		sb.append("CommonParams");
		paramMap.forEach((k, v) -> sb.append(" " + k + ":" + v));
		LOGGER.info(sb.toString());
		ProfitTrailDealingLogic logic = new ProfitTrailDealingLogic(wrapper, notifier, paramMap, settings);
		LOGGER.info("ロジック開始します…");
		notifier.sendMessage("LOGIC START!");
		logic.execute();
	}

	private static Map<String, Object> loadSettings() {
		InputStreamReader reader = new InputStreamReader(ClassLoader.getSystemResourceAsStream("bot_settings.yml"));
		Yaml yaml = new Yaml();
		return yaml.<Map<String, Object>>load(reader);
	}
}
