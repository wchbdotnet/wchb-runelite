package net.wchb.runelite;

import com.google.gson.Gson;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.wchb.runelite.model.WchbEvent;
import net.wchb.runelite.model.WchbLiveMessage;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

@Slf4j
class WchbLiveClient
{
	private final OkHttpClient httpClient;
	private final Gson gson;
	private final ScheduledExecutorService executor;
	private WebSocket socket;
	private ScheduledFuture<?> retryTask;
	private ScheduledFuture<?> heartbeatTask;
	private String url;
	private Consumer<WchbEvent> eventConsumer;
	private Consumer<String> statusConsumer;
	private Runnable profileUpdated;
	private Runnable ticketExpired;
	private boolean active;
	private int retryAttempt;

	@Inject
	WchbLiveClient(OkHttpClient httpClient, Gson gson, ScheduledExecutorService executor)
	{
		this.httpClient = httpClient;
		this.gson = gson;
		this.executor = executor;
	}

	synchronized void start(String liveUrl, Consumer<WchbEvent> onEvent,
		Consumer<String> onStatus, Runnable onProfileUpdated, Runnable onTicketExpired)
	{
		stop();
		if (liveUrl == null || !liveUrl.startsWith("wss://live.wchb.workers.dev/"))
		{
			log.debug("WCHB live URL was unavailable or rejected");
			onStatus.accept("Live updates unavailable");
			return;
		}
		url = liveUrl;
		eventConsumer = onEvent;
		statusConsumer = onStatus;
		profileUpdated = onProfileUpdated;
		ticketExpired = onTicketExpired;
		active = true;
		retryAttempt = 0;
		log.debug("Opening WCHB live connection");
		open();
	}

	synchronized void stop()
	{
		active = false;
		if (retryTask != null) retryTask.cancel(false);
		if (heartbeatTask != null) heartbeatTask.cancel(false);
		retryTask = null;
		heartbeatTask = null;
		if (socket != null) socket.close(1000, "RuneLite logged out");
		socket = null;
	}

	private synchronized void open()
	{
		if (!active) return;
		Request request = new Request.Builder().url(url).build();
		socket = httpClient.newWebSocket(request, new Listener());
	}

	private synchronized void scheduleReconnect(WebSocket failedSocket)
	{
		if (!active || socket != failedSocket || retryTask != null) return;
		if (heartbeatTask != null) heartbeatTask.cancel(false);
		heartbeatTask = null;
		long baseDelay = Math.min(60L, 1L << Math.min(6, retryAttempt++));
		long delay = baseDelay + (long) (Math.random() * Math.max(1L, baseDelay));
		statusConsumer.accept("Live updates reconnecting");
		retryTask = executor.schedule(() ->
		{
			synchronized (WchbLiveClient.this)
			{
				retryTask = null;
				open();
			}
		}, delay, TimeUnit.SECONDS);
	}

	private class Listener extends WebSocketListener
	{
		@Override
		public void onOpen(WebSocket webSocket, Response response)
		{
			synchronized (WchbLiveClient.this)
			{
				if (!active || socket != webSocket) return;
				retryAttempt = 0;
				if (heartbeatTask != null) heartbeatTask.cancel(false);
				heartbeatTask = executor.scheduleWithFixedDelay(
					() ->
					{
						synchronized (WchbLiveClient.this)
						{
							if (active && socket == webSocket) webSocket.send("ping");
						}
					}, 4, 4, TimeUnit.SECONDS);
			}
			log.debug("WCHB live connection opened");
			statusConsumer.accept("Live updates connected");
		}

		@Override
		public void onMessage(WebSocket webSocket, String text)
		{
			if ("pong".equals(text)) return;
			synchronized (WchbLiveClient.this)
			{
				if (!active || socket != webSocket) return;
			}
			try
			{
				WchbLiveMessage message = gson.fromJson(text, WchbLiveMessage.class);
				if (message != null && "loot".equals(message.getType()) && message.getEvent() != null)
				{
					eventConsumer.accept(message.getEvent());
				}
				else if (message != null && "profile_updated".equals(message.getType()))
				{
					profileUpdated.run();
				}
			}
			catch (RuntimeException error)
			{
				log.debug("Unable to read WCHB live event", error);
			}
		}

		@Override
		public void onFailure(WebSocket webSocket, Throwable error, Response response)
		{
			log.debug("WCHB live connection failed", error);
			synchronized (WchbLiveClient.this)
			{
				if (!active || socket != webSocket) return;
			}
			if (response != null && response.code() == 401)
			{
				synchronized (WchbLiveClient.this)
				{
					active = false;
					if (heartbeatTask != null) heartbeatTask.cancel(false);
					heartbeatTask = null;
					socket = null;
				}
				ticketExpired.run();
				return;
			}
			scheduleReconnect(webSocket);
		}

		@Override
		public void onClosing(WebSocket webSocket, int code, String reason)
		{
			webSocket.close(code, reason);
		}

		@Override
		public void onClosed(WebSocket webSocket, int code, String reason)
		{
			log.debug("WCHB live connection closed with code {}", code);
			scheduleReconnect(webSocket);
		}
	}
}
