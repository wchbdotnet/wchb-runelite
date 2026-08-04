package net.wchb.runelite;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import net.wchb.runelite.model.WchbFeed;
import net.wchb.runelite.model.WchbEvent;

@Slf4j
@PluginDescriptor(
	name = "What Could Have Been",
	description = "Rerolls Dink-delivered drops into a fun fictional loot profile with an overlay and recent feed",
	tags = {"loot", "drops", "dink", "overlay", "wchb"}
)
public class WchbPlugin extends Plugin implements MouseListener
{
	@Inject private WchbConfig config;
	@Inject private WchbApiClient apiClient;
	@Inject private WchbLiveClient liveClient;
	@Inject private WchbOverlay overlay;
	@Inject private OverlayManager overlayManager;
	@Inject private ClientToolbar clientToolbar;
	@Inject private ConfigManager configManager;
	@Inject private ClientThread clientThread;
	@Inject private WchbConnectionStore connectionStore;
	@Inject private Client client;
	@Inject private ItemManager itemManager;
	@Inject private MouseManager mouseManager;

	@Getter
	private volatile WchbFeed feed;

	private WchbPanel panel;
	private NavigationButton navigationButton;
	private volatile String lastEventId;
	private volatile String currentPlayerName;
	private final AtomicBoolean refreshInFlight = new AtomicBoolean();
	private final AtomicBoolean reconnectLookupInFlight = new AtomicBoolean();
	private volatile boolean reconnectAttempted;
	private volatile boolean running;
	private boolean draggingOverlay;
	private Point overlayDragOffset;

	@Override
	protected void startUp()
	{
		running = true;
		BufferedImage icon = ImageUtil.loadImageResource(WchbPlugin.class, "/wchb.png");
		panel = new WchbPanel(this, itemManager);
		panel.setConnectionEnabled(config.connectToWchb());
		navigationButton = NavigationButton.builder()
			.tooltip("What Could Have Been")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);
		overlay.setMovementUnlocked(config.unlockOverlay());
		overlayManager.add(overlay);
		mouseManager.registerMouseListener(this);
		if (isLoggedIn())
		{
			currentPlayerName = client.getLocalPlayer().getName();
			reconnectAttempted = false;
			refreshNow();
		}
		log.debug("WCHB companion started");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null)
		{
			currentPlayerName = client.getLocalPlayer().getName();
			reconnectAttempted = false;
			refreshNow();
		}
		else
		{
			currentPlayerName = null;
			liveClient.stop();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (currentPlayerName == null && isLoggedIn())
		{
			currentPlayerName = client.getLocalPlayer().getName();
			reconnectAttempted = false;
			refreshNow();
		}
	}

	@Override
	protected void shutDown()
	{
		running = false;
		refreshInFlight.set(false);
		reconnectLookupInFlight.set(false);
		liveClient.stop();
		mouseManager.unregisterMouseListener(this);
		overlayManager.remove(overlay);
		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
		}
		feed = null;
		panel = null;
		navigationButton = null;
		log.debug("WCHB companion stopped");
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (WchbConfig.GROUP.equals(event.getGroup()))
		{
			if ("unlockOverlay".equals(event.getKey()))
			{
				overlay.setMovementUnlocked(config.unlockOverlay());
				return;
			}
			if (panel != null)
			{
				panel.setConnectionEnabled(config.connectToWchb());
			}
			liveClient.stop();
			if (config.connectToWchb() && isLoggedIn()) refreshNow();
		}
	}

	void refreshNow()
	{
		if (!isLoggedIn())
		{
			updateStatus("Log in to RuneScape to connect");
			return;
		}
		String token = config.connectionToken().trim();
		if (!config.connectToWchb())
		{
			updateStatus("Connection disabled");
			return;
		}
		if (token.length() < 8)
		{
			attemptSavedReconnect(token);
			return;
		}

		updateStatus("Checking WCHB…");
		if (!refreshInFlight.compareAndSet(false, true))
		{
			return;
		}
		apiClient.fetchFeed(token, currentPlayerName,
			result -> clientThread.invoke(() -> acceptFeed(token, result)),
			error -> clientThread.invoke(() -> acceptFeedError(token, error)));
	}

	private void acceptFeed(String token, WchbFeed result)
	{
		refreshInFlight.set(false);
		if (!running || !isLoggedIn() || !config.connectToWchb()
			|| !token.equals(config.connectionToken().trim()))
		{
			return;
		}
			WchbEvent newest = result.getEvents() == null || result.getEvents().isEmpty()
				? null : result.getEvents().get(0);
			if (newest != null)
			{
				if (lastEventId != null && !lastEventId.equals(newest.getId()))
				{
					overlay.playNewEvent(newest);
				}
				lastEventId = newest.getId();
			}
			feed = result;
			connectionStore.remember(currentPlayerName, token);
			connectionStore.remember(result.getPlayerName(), token);
			liveClient.start(result.getLiveUrl(),
				event -> clientThread.invoke(() -> acceptLiveEvent(event)),
				this::updateStatus,
				() -> clientThread.invoke(this::refreshNow),
				() -> clientThread.invoke(this::refreshNow));
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.updateFeed(result);
				}
			});
	}

	private void acceptFeedError(String token, String error)
	{
		refreshInFlight.set(false);
		if (!running || !isLoggedIn() || !config.connectToWchb()
			|| !token.equals(config.connectionToken().trim()))
		{
			return;
		}
		if ("Connection token not recognised".equals(error))
		{
			attemptSavedReconnect(token);
		}
		else
		{
			updateStatus(error);
		}
	}

	private void attemptSavedReconnect(String rejectedToken)
	{
		if (reconnectAttempted || currentPlayerName == null || !reconnectLookupInFlight.compareAndSet(false, true))
		{
			updateStatus("No saved WCHB profile for this character");
			return;
		}
		reconnectAttempted = true;
		updateStatus("Looking for your saved WCHB profile…");
		connectionStore.find(currentPlayerName, savedToken -> clientThread.invoke(() ->
		{
			reconnectLookupInFlight.set(false);
			if (savedToken == null || savedToken.length() < 8 || savedToken.equals(rejectedToken))
			{
				updateStatus("No saved WCHB profile for this character");
				return;
			}
			configManager.setConfiguration(WchbConfig.GROUP, "connectionToken", savedToken);
			updateStatus("Saved WCHB profile found—reconnecting…");
			refreshNow();
		}));
	}

	void createTemporaryProfile()
	{
		if (!config.connectToWchb())
		{
			updateStatus("Enable Connect to WCHB first");
			return;
		}

		String token = config.connectionToken().trim();
		if (token.isEmpty())
		{
			byte[] bytes = new byte[32];
			new SecureRandom().nextBytes(bytes);
			token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		}
		final String installationToken = token;
		updateStatus("Creating temporary profile…");
		apiClient.registerProfile(installationToken,
			registration -> clientThread.invoke(() ->
			{
				if (!running || !config.connectToWchb()) return;
				configManager.setConfiguration(WchbConfig.GROUP, "connectionToken", installationToken);
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null) panel.updateRegistration(registration);
				});
				refreshNow();
			}), this::updateStatus);
	}

	void openExistingAccountPage()
	{
		String token = ensureInstallationToken();
		LinkBrowser.browse("https://wchb.net/connect-runelite?token=" + token);
		updateStatus("WCHB account connection opened in your browser");
	}

	private String ensureInstallationToken()
	{
		String token = config.connectionToken().trim();
		if (token.length() == 43) return token;
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		configManager.setConfiguration(WchbConfig.GROUP, "connectionToken", token);
		return token;
	}

	void openClaimPage()
	{
		String token = config.connectionToken().trim();
		if (token.length() != 43)
		{
			updateClaimStatus("Temporary profile token missing", true);
			return;
		}
		updateClaimStatus("Creating secure claim link…", false);
		apiClient.beginClaim(token, link -> SwingUtilities.invokeLater(() ->
		{
			if (link.getClaimUrl() == null || !link.getClaimUrl().startsWith("https://wchb.net/"))
			{
				updateClaimStatus("WCHB returned an invalid claim link", true);
				return;
			}
			LinkBrowser.browse(link.getClaimUrl());
			updateClaimStatus("Claim page opened in your browser. This secure link is valid for 15 minutes.", false);
		}), error -> updateClaimStatus(error, true));
	}

	private void updateClaimStatus(String message, boolean error)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.updateClaimStatus(message, error);
			}
		});
	}

	boolean shouldShowOverlay()
	{
		return config.connectToWchb() && config.showOverlay();
	}

	int getOverlayScale()
	{
		return config.overlayScale();
	}

	boolean isMinimalOverlay()
	{
		return config.minimalOverlay();
	}

	boolean isOverlayMovementUnlocked()
	{
		return config.unlockOverlay();
	}

	@Override
	public MouseEvent mousePressed(MouseEvent event)
	{
		if (!config.unlockOverlay() || !shouldShowOverlay()) return event;
		Rectangle bounds = overlay.getBounds();
		if (bounds == null || !bounds.contains(event.getPoint())) return event;
		draggingOverlay = true;
		overlayDragOffset = new Point(event.getX() - bounds.x, event.getY() - bounds.y);
		return null;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent event)
	{
		if (!draggingOverlay || overlayDragOffset == null) return event;
		overlay.setPosition(net.runelite.client.ui.overlay.OverlayPosition.DYNAMIC);
		overlay.setPreferredLocation(new Point(
			event.getX() - overlayDragOffset.x,
			event.getY() - overlayDragOffset.y));
		overlay.revalidate();
		return null;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent event)
	{
		if (!draggingOverlay) return event;
		draggingOverlay = false;
		overlayDragOffset = null;
		overlayManager.saveOverlay(overlay);
		return null;
	}

	@Override public MouseEvent mouseClicked(MouseEvent event) { return event; }
	@Override public MouseEvent mouseEntered(MouseEvent event) { return event; }
	@Override public MouseEvent mouseExited(MouseEvent event) { return event; }
	@Override public MouseEvent mouseMoved(MouseEvent event) { return event; }

	private void acceptLiveEvent(WchbEvent event)
	{
		if (!isLoggedIn() || feed == null || event == null) return;
		if (lastEventId != null && lastEventId.equals(event.getId())) return;
		lastEventId = event.getId();
		feed.markDinkConnected();
		feed.prependEvent(event);
		overlay.playNewEvent(event);
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null) panel.updateFeed(feed);
		});
	}

	private boolean isLoggedIn()
	{
		return client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null;
	}

	private void updateStatus(String status)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.updateStatus(status);
			}
		});
	}

	@Provides
	WchbConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(WchbConfig.class);
	}
}
