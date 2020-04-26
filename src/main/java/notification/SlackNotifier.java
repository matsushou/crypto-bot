package notification;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SlackNotifier {

	private final String INCOMING_WEBHOOK;

	private static SlackNotifier INSTANCE;

	private final HttpClient CLIENT = HttpClient.newBuilder().build();

	private static Logger LOGGER = LogManager.getLogger(SlackNotifier.class);

	public static SlackNotifier getInstance(Map<String, Object> settings) {
		if (INSTANCE == null) {
			INSTANCE = new SlackNotifier(settings);
		}
		return INSTANCE;
	}

	private SlackNotifier(Map<String, Object> settings) {
		@SuppressWarnings("unchecked")
		Map<String, String> slackParam = (Map<String, String>) settings.get("slack");
		// パラメータ出力
		StringBuilder sb = new StringBuilder();
		sb.append("SlackParams");
		slackParam.forEach((k, v) -> sb.append(" " + k + ":" + v));
		LOGGER.info(sb.toString());
		this.INCOMING_WEBHOOK = slackParam.get("incomingWebhook");
	}

	public void sendMessage(String message) {
		String body = "{" //
				+ "\"text\": \"" + message + "\"" //
				+ "}";
		HttpRequest request = HttpRequest.newBuilder(URI.create(INCOMING_WEBHOOK))
				.headers("Content-type", "application/json").POST(BodyPublishers.ofString(body)).build();

		BodyHandler<String> bodyHandler = HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
		try {
			CLIENT.send(request, bodyHandler);
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}
}
