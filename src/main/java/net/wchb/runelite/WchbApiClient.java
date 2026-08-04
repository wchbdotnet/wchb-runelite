package net.wchb.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.function.Consumer;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.wchb.runelite.model.WchbFeed;
import net.wchb.runelite.model.WchbRegistration;
import net.wchb.runelite.model.WchbClaimLink;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
class WchbApiClient
{
	private static final String FUNCTION_ROOT =
		"https://wchb.net/api/apps/6a660458b904f2c599f30a97/functions/";
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	private final OkHttpClient httpClient;
	private final Gson gson;

	@Inject
	WchbApiClient(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	void fetchFeed(String token, String playerName, Consumer<WchbFeed> onSuccess, Consumer<String> onError)
	{
		JsonObject payload = new JsonObject();
		payload.addProperty("token", token);
		if (playerName != null && !playerName.trim().isEmpty())
		{
			payload.addProperty("player_name", playerName.trim());
		}
		post("getPluginFeed", payload, WchbFeed.class, onSuccess, onError);
	}

	void registerProfile(String installationToken, Consumer<WchbRegistration> onSuccess, Consumer<String> onError)
	{
		JsonObject payload = new JsonObject();
		payload.addProperty("installation_token", installationToken);
		post("registerPluginProfile", payload, WchbRegistration.class, onSuccess, onError);
	}

	void beginClaim(String installationToken, Consumer<WchbClaimLink> onSuccess, Consumer<String> onError)
	{
		JsonObject payload = new JsonObject();
		payload.addProperty("installation_token", installationToken);
		post("beginPluginClaim", payload, WchbClaimLink.class, onSuccess, onError);
	}

	private <T> void post(String function, JsonObject payload, Class<T> responseType,
		Consumer<T> onSuccess, Consumer<String> onError)
	{
		Request request = new Request.Builder()
			.url(FUNCTION_ROOT + function)
			.post(RequestBody.create(JSON, gson.toJson(payload)))
			.build();

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException error)
			{
				log.debug("WCHB feed request failed", error);
				onError.accept("Unable to reach WCHB");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response closeable = response)
				{
					if (!response.isSuccessful() || response.body() == null)
					{
						onError.accept(response.code() == 404
							? "Connection token not recognised"
							: "WCHB returned " + response.code());
						return;
					}

					T result = gson.fromJson(response.body().charStream(), responseType);
					if (result == null)
					{
						onError.accept("WCHB returned an empty response");
						return;
					}
					onSuccess.accept(result);
				}
				catch (RuntimeException error)
				{
					log.debug("Unable to read WCHB feed", error);
					onError.accept("WCHB returned an unreadable response");
				}
			}
		});
	}
}
