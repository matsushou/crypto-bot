package exchange;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import model.BalanceResponse;
import model.BoardResponse;
import model.BuySellEnum;
import model.ChildOrderResponse;
import model.CollateralResponse;
import model.HealthResponse;
import model.PositionResponse;

public class BitFlyerAPIWrapper {

	private final String ENDPOINT = "https://api.bitflyer.com";

	private final String GETBOARD_API = "/v1/getboard?product_code=FX_BTC_JPY";

	private final String GETBALANCE_API = "/v1/me/getbalance";

	private final String SENDCHILDORDER_API = "/v1/me/sendchildorder";

	private final String GETPOSITIONS_API = "/v1/me/getpositions?product_code=FX_BTC_JPY";

	private final String GETCOLLATERAL_API = "/v1/me/getcollateral";

	private final String GETHEALTH_API = "/v1/gethealth?product_code=FX_BTC_JPY";

	private final String ACCESS_KEY_HEADER = "ACCESS-KEY";

	private final String ACCESS_TIMESTAMP_HEADER = "ACCESS-TIMESTAMP";

	private final String ACCESS_SIGN_HEADER = "ACCESS-SIGN";

	private final String CONTENT_TYPE_HEADER = "Content-Type";

	private final String APPLICATION_JSON = "application/json";

	private final int RETRY_COUNT = 5;

	private final int RETRY_INTERVAL_MSEC = 100;

	private final String API_KEY;

	private final String API_SECRET;

	private final HttpClient CLIENT = HttpClient.newBuilder().build();

	private static BitFlyerAPIWrapper INSTANCE;

	private static Logger LOGGER = LogManager.getLogger(BitFlyerAPIWrapper.class);

	private BitFlyerAPIWrapper(String apiKey, String apiSecret) {
		super();
		this.API_KEY = apiKey;
		this.API_SECRET = apiSecret;
	}

	public static BitFlyerAPIWrapper getInstance(Map<String, Object> settings) {
		if (INSTANCE == null) {
			@SuppressWarnings("unchecked")
			Map<String, String> exchangeParam = (Map<String, String>) settings.get("exchange");
			// パラメータ出力
			StringBuilder sb = new StringBuilder();
			sb.append("ExchangeParams");
			exchangeParam.forEach((k, v) -> sb.append(" " + k + ":" + v));
			LOGGER.info(sb.toString());
			INSTANCE = new BitFlyerAPIWrapper(exchangeParam.get("apiKey"), exchangeParam.get("secret"));
		}
		return INSTANCE;
	}

	public BoardResponse getBoard() {
		BoardResponse boardResponse = null;
		String responseBody = null;
		for (int i = 0; i < RETRY_COUNT; i++) {
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT + GETBOARD_API)).build();

				BodyHandler<String> bodyHandler = HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
				HttpResponse<String> response = CLIENT.send(request, bodyHandler);
				int status = response.statusCode();
				if (status >= 500) {
					// 500番台ならリトライ
					LOGGER.info("[getboard]try retry...");
					Thread.sleep(RETRY_INTERVAL_MSEC);
					continue;
				}
				responseBody = response.body();

				ObjectMapper mapper = new ObjectMapper();
				boardResponse = mapper.readValue(responseBody, BoardResponse.class);
				break;
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return boardResponse;
	}

	public BalanceResponse[] getBalance() {
		BalanceResponse[] balanceResponses = null;
		for (int i = 0; i < RETRY_COUNT; i++) {

			String timestamp = String.valueOf(System.currentTimeMillis());
			String sign = createSign(timestamp, "GET", GETBALANCE_API, "");
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT + GETBALANCE_API))
						.headers(ACCESS_KEY_HEADER, API_KEY, ACCESS_TIMESTAMP_HEADER, timestamp, ACCESS_SIGN_HEADER,
								sign, CONTENT_TYPE_HEADER, APPLICATION_JSON)
						.GET().build();

				BodyHandler<String> bodyHandler = HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
				HttpResponse<String> response = CLIENT.send(request, bodyHandler);
				int status = response.statusCode();
				if (status >= 500) {
					// 500番台ならリトライ
					LOGGER.info("[getbalance]try retry...");
					Thread.sleep(RETRY_INTERVAL_MSEC);
					continue;
				}
				String responseBody = response.body();

				ObjectMapper mapper = new ObjectMapper();
				balanceResponses = mapper.readValue(responseBody, BalanceResponse[].class);
				break;
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return balanceResponses;
	}

	public ChildOrderResponse sendChildOrder(BuySellEnum side, int price, double size) {
		ChildOrderResponse childOrderResponse = null;
		String body = "{" //
				+ "\"product_code\": \"FX_BTC_JPY\", " //
				+ "\"child_order_type\": \"LIMIT\", " //
				+ "\"side\": \"" + side + "\", " //
				+ "\"price\": " + price + ", " //
				+ "\"size\": " + size + ", " //
				+ "\"time_in_force\": \"IOC\"" //
				+ "}";
		for (int i = 0; i < RETRY_COUNT; i++) {
			String timestamp = String.valueOf(System.currentTimeMillis());
			String sign = createSign(timestamp, "POST", SENDCHILDORDER_API, body);
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT + SENDCHILDORDER_API))
						.headers(ACCESS_KEY_HEADER, API_KEY, ACCESS_TIMESTAMP_HEADER, timestamp, ACCESS_SIGN_HEADER,
								sign, CONTENT_TYPE_HEADER, APPLICATION_JSON)
						.POST(BodyPublishers.ofString(body)).build();

				BodyHandler<String> bodyHandler = HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
				HttpResponse<String> response = CLIENT.send(request, bodyHandler);
				int status = response.statusCode();
				if (status >= 500) {
					// 500番台ならリトライ
					LOGGER.info("[sendchildorder]try retry...");
					Thread.sleep(RETRY_INTERVAL_MSEC);
					continue;
				}
				String responseBody = response.body();

				ObjectMapper mapper = new ObjectMapper();
				childOrderResponse = mapper.readValue(responseBody, ChildOrderResponse.class);
				break;
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return childOrderResponse;
	}

	public PositionResponse[] getPositions() {
		PositionResponse[] positionResponses = null;
		for (int i = 0; i < RETRY_COUNT; i++) {
			String timestamp = String.valueOf(System.currentTimeMillis());
			String sign = createSign(timestamp, "GET", GETPOSITIONS_API, "");
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT + GETPOSITIONS_API))
						.headers(ACCESS_KEY_HEADER, API_KEY, ACCESS_TIMESTAMP_HEADER, timestamp, ACCESS_SIGN_HEADER,
								sign, CONTENT_TYPE_HEADER, APPLICATION_JSON)
						.GET().build();

				BodyHandler<String> bodyHandler = HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
				HttpResponse<String> response = CLIENT.send(request, bodyHandler);
				int status = response.statusCode();
				if (status >= 500) {
					// 500番台ならリトライ
					LOGGER.info("[getpositions]try retry...");
					Thread.sleep(RETRY_INTERVAL_MSEC);
					continue;
				}
				String responseBody = response.body();

				ObjectMapper mapper = new ObjectMapper();
				positionResponses = mapper.readValue(responseBody, PositionResponse[].class);
				break;
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return positionResponses;
	}

	public CollateralResponse getCollateral() {
		CollateralResponse collateralResponse = null;
		for (int i = 0; i < RETRY_COUNT; i++) {
			String timestamp = String.valueOf(System.currentTimeMillis());
			String sign = createSign(timestamp, "GET", GETCOLLATERAL_API, "");
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT + GETCOLLATERAL_API))
						.headers(ACCESS_KEY_HEADER, API_KEY, ACCESS_TIMESTAMP_HEADER, timestamp, ACCESS_SIGN_HEADER,
								sign, CONTENT_TYPE_HEADER, APPLICATION_JSON)
						.GET().build();

				BodyHandler<String> bodyHandler = HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
				HttpResponse<String> response = CLIENT.send(request, bodyHandler);
				int status = response.statusCode();
				if (status >= 500) {
					// 500番台ならリトライ
					LOGGER.info("[getcollateral]try retry...");
					Thread.sleep(RETRY_INTERVAL_MSEC);
					continue;
				}
				String responseBody = response.body();

				ObjectMapper mapper = new ObjectMapper();
				collateralResponse = mapper.readValue(responseBody, CollateralResponse.class);
				break;
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return collateralResponse;
	}

	public HealthResponse getHealth() {
		HealthResponse healthResponse = null;
		String responseBody = null;
		for (int i = 0; i < RETRY_COUNT; i++) {
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT + GETHEALTH_API)).build();

				BodyHandler<String> bodyHandler = HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
				HttpResponse<String> response = CLIENT.send(request, bodyHandler);
				int status = response.statusCode();
				if (status >= 500) {
					// 500番台ならリトライ
					LOGGER.info("[gethealth]try retry...");
					Thread.sleep(RETRY_INTERVAL_MSEC);
					continue;
				}
				responseBody = response.body();

				ObjectMapper mapper = new ObjectMapper();
				healthResponse = mapper.readValue(responseBody, HealthResponse.class);
				break;
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return healthResponse;
	}

	public boolean isHealthy() {
		HealthResponse response = getHealth();
		String status = response.getStatus();
		return status.equals("NORMAL") || status.equals("BUSY") || status.equals("VERY BUSY");
	}

	private String createSign(String timestamp, String method, String path, String body) {
		String plaintext = timestamp + method + path + body;
		SecretKeySpec sk = new SecretKeySpec(API_SECRET.getBytes(), "HmacSHA256");
		Mac mac;
		try {
			mac = Mac.getInstance("HmacSHA256");
			mac.init(sk);
			byte[] mac_bytes = mac.doFinal(plaintext.getBytes());
			StringBuilder sb = new StringBuilder(2 * mac_bytes.length);
			for (byte b : mac_bytes) {
				sb.append(String.format("%02x", b & 0xff));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		}
		return null;
	}

}
