package core;

import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

import exchange.BitFlyerAPIWrapper;
import logic.DealingLogicBase;
import notification.SlackNotifier;

public class BotMain {

	private static BitFlyerAPIWrapper wrapper;
	private static SlackNotifier notifier;
	private static Logger LOGGER = LogManager.getLogger(BotMain.class);
	private static Map<String, Object> settings;

	public static void main(String[] args) throws Throwable {
		LOGGER.info("bot起動します…");
		// 設定値読み込み
		settings = loadSettings();
		wrapper = BitFlyerAPIWrapper.getInstance(settings);
		System.out.println(wrapper.getCollateral().getCollateral());
		notifier = SlackNotifier.getInstance(settings);

		// スレッドのログ出力設定
		Thread.UncaughtExceptionHandler dueh = Thread.getDefaultUncaughtExceptionHandler();
		if (dueh == null) {
			Thread.setDefaultUncaughtExceptionHandler((Thread t, Throwable e) -> {
				LOGGER.error("例外発生！", e);
				notifier.sendMessage("例外発生！ " + e);
				System.exit(1);
			});
		}

		@SuppressWarnings("unchecked")
		Map<String, Object> paramMap = (Map<String, Object>) settings.get("common");
		// パラメータ出力
		StringBuilder sb = new StringBuilder();
		sb.append("CommonParams");
		paramMap.forEach((k, v) -> sb.append(" " + k + ":" + v));
		LOGGER.info(sb.toString());

		// ロジックのインスタンス取得
		DealingLogicBase logic = getLogic((String) paramMap.get("logicClass"), wrapper, notifier, paramMap, settings);
		LOGGER.info("ロジック開始します…");
		notifier.sendMessage("LOGIC START!");
		logic.execute();
	}

	private static Map<String, Object> loadSettings() {
		InputStreamReader reader = new InputStreamReader(ClassLoader.getSystemResourceAsStream("bot_settings.yml"));
		Yaml yaml = new Yaml();
		return yaml.<Map<String, Object>>load(reader);
	}

	private static DealingLogicBase getLogic(String logicClass, BitFlyerAPIWrapper wrapper, SlackNotifier notifier,
			Map<String, Object> paramMap, Map<String, Object> settings) throws Throwable {
		@SuppressWarnings("unchecked")
		Class<? extends DealingLogicBase> clazz = (Class<? extends DealingLogicBase>) Class.forName(logicClass);
		@SuppressWarnings({ "rawtypes" })
		Constructor constructor = clazz.getDeclaredConstructor(BitFlyerAPIWrapper.class, SlackNotifier.class, Map.class,
				Map.class);
		return (DealingLogicBase) constructor.newInstance(wrapper, notifier, paramMap, settings);
	}
}
