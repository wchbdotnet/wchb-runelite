package net.wchb.runelite;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.util.ImageUtil;
import net.wchb.runelite.model.WchbEvent;
import net.wchb.runelite.model.WchbFeed;
import net.wchb.runelite.model.WchbItem;

class WchbOverlay extends Overlay
{
	// These are native dimensions. The previous 78% appearance is now 100%,
	// so text and images are rasterised directly instead of being blurred by a
	// post-render transform.
	private static final int FULL_WIDTH = 372;
	private static final int FULL_HEIGHT = 60;
	private static final int BRAND_WIDTH = 100;
	private static final Color RED = new Color(205, 42, 35);
	private static final Color PANEL = new Color(14, 17, 21, 244);
	private static final Color MUTED = new Color(166, 171, 178);
	private static final long ROLL_DURATION_MS = 900L;
	private static final long RESULT_FADE_MS = 420L;
	private static final long REVEAL_DURATION_MS = 3_000L;
	private static final long TEMPORARY_ROLL_DURATION_MS = 1_200L;
	private static final long TEMPORARY_RESULT_HOLD_MS = 5_000L;
	private static final long TEMPORARY_FADE_OUT_MS = 650L;
	private static final long DRAWER_OPEN_MS = 380L;
	private static final long DRAWER_RESULT_HOLD_MS = 5_000L;
	private static final long DRAWER_CLOSE_MS = 420L;

	private final WchbPlugin plugin;
	private final ItemManager itemManager;
	private final BufferedImage logoMark;
	private final Map<Long, BufferedImage> logoSizes = new HashMap<>();
	private final Map<String, BufferedImage> accountBadges = new HashMap<>();
	private BufferedImage overlayBuffer;
	private volatile long animationStarted;
	private volatile String revealType = "normal";

	@Inject
	WchbOverlay(WchbPlugin plugin, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.logoMark = removeCornerFragments(
			ImageUtil.loadImageResource(WchbPlugin.class, "/wchb_mark.png"));
		loadBadge("ironman");
		loadBadge("hardcore_ironman");
		loadBadge("ultimate_ironman");
		loadBadge("group_ironman");
		loadBadge("hardcore_group_ironman");
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(OverlayPriority.MED);
		setMovable(false);
	}

	private void loadBadge(String type)
	{
		accountBadges.put(type, ImageUtil.loadImageResource(WchbPlugin.class, "/" + type + ".png"));
	}

	void setMovementUnlocked(boolean unlocked)
	{
		setMovable(unlocked);
	}

	void playNewEvent(WchbEvent event)
	{
		playReveal(event.getHighlightType());
	}

	private void playReveal(String type)
	{
		revealType = type == null ? "normal" : type;
		animationStarted = System.currentTimeMillis();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		WchbFeed feed = plugin.getFeed();
		boolean minimal = plugin.isMinimalOverlay();
		WchbOverlayStyle style = minimal ? plugin.getOverlayStyle() : WchbOverlayStyle.CLASSIC;
		boolean drawer = minimal && style == WchbOverlayStyle.DRAWER;
		boolean hasEvent = feed != null && feed.getEvents() != null && !feed.getEvents().isEmpty();
		if (!plugin.shouldShowOverlay() || (!drawer && !hasEvent))
		{
			return null;
		}

		WchbEvent event = hasEvent ? feed.getEvents().get(0) : null;
		// Drawer Reveal owns its visibility lifecycle: its medallion remains on
		// screen while the rails themselves are temporary.
		boolean temporary = plugin.isTemporaryOverlay() && !drawer;
		long elapsed = animationStarted == 0L
			? Long.MAX_VALUE : System.currentTimeMillis() - animationStarted;
		long rollDuration = temporary ? TEMPORARY_ROLL_DURATION_MS : ROLL_DURATION_MS;
		long animationElapsed = drawer && elapsed != Long.MAX_VALUE
			? Math.max(0L, elapsed - DRAWER_OPEN_MS) : elapsed;
		long drawerCloseStart = DRAWER_OPEN_MS + rollDuration + RESULT_FADE_MS
			+ DRAWER_RESULT_HOLD_MS;
		float drawerProgress = drawer ? drawerProgress(elapsed, drawerCloseStart) : 1f;
		long temporaryFadeStart = rollDuration + RESULT_FADE_MS + TEMPORARY_RESULT_HOLD_MS;
		if (temporary && (animationStarted == 0L
			|| elapsed >= temporaryFadeStart + TEMPORARY_FADE_OUT_MS))
		{
			return null;
		}
		int additionalItems = event == null ? 0
			: Math.max(0, validItemCount(event.getItems()) - 1)
				+ Math.max(0, validItemCount(event.getRerolledItems()) - 1);
		int width = (minimal ? style.getBaseWidth() : FULL_WIDTH)
			+ additionalItems * (minimal ? style.getExtraItemWidth() : 24);
		int height = minimal ? style.getHeight() : FULL_HEIGHT;
		int brandWidth = minimal ? style.getBrandWidth() : BRAND_WIDTH;
		boolean versus = minimal && style == WchbOverlayStyle.VERSUS;
		boolean floating = minimal && style == WchbOverlayStyle.FLOATING;
		double scale = plugin.getOverlayScale() / 100d;
		int renderedWidth = px(width, scale);
		int renderedHeight = px(height, scale);
		BufferedImage buffer = prepareOverlayBuffer(renderedWidth, renderedHeight);
		Graphics2D g = buffer.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			if (versus)
			{
				drawVersusShell(g, renderedWidth, renderedHeight, scale);
			}
			else if (floating)
			{
				drawFloatingShell(g, renderedWidth, renderedHeight, scale);
			}
			else if (drawer)
			{
				drawDrawerShell(g, renderedWidth, scale, drawerProgress);
			}
			else
			{
				Shape card = new RoundRectangle2D.Float(0, 0, renderedWidth, renderedHeight,
					px(9, scale), px(9, scale));
				g.setClip(card);
				g.setColor(PANEL);
				g.fill(card);
				g.setColor(RED);
				g.fillRect(0, 0, px(brandWidth, scale), renderedHeight);
				g.setComposite(AlphaComposite.SrcOver.derive(0.2f));
				g.setColor(Color.WHITE);
				g.fillRect(0, 0, renderedWidth, Math.max(1, px(1, scale)));
				g.setComposite(AlphaComposite.SrcOver);
			}

			if (minimal)
			{
				if (!versus && !floating && !drawer)
				{
					drawLogo(g, px(style.getLogoX(), scale), px(style.getLogoY(), scale),
						px(style.getLogoSize(), scale));
				}
			}
			else
			{
				drawLogo(g, px(30, scale), px(10, scale), px(40, scale));
			}

			boolean rolling = animationElapsed < rollDuration;
			float resultAlpha = animationElapsed == Long.MAX_VALUE
				? 1f : Math.min(1f, Math.max(0f,
					(animationElapsed - rollDuration) / (float) RESULT_FADE_MS));
			if (minimal && event != null && (!drawer || drawerProgress > 0f))
			{
				drawMinimal(g, event, width, scale, style, rolling, animationElapsed,
					resultAlpha, temporary, drawerProgress);
			}
			else if (!minimal && event != null)
			{
				drawFull(g, feed, event, width, scale, rolling, elapsed,
					resultAlpha, temporary);
			}
			if (!rolling)
			{
				if (versus)
				{
					drawVersusShimmer(g, renderedWidth, renderedHeight, scale);
				}
				else if (floating)
				{
					drawFloatingShimmer(g, renderedWidth, renderedHeight, scale);
				}
				else if (drawer && drawerProgress > 0f)
				{
					drawDrawerShimmer(g, renderedWidth, renderedHeight, scale,
						drawerProgress);
				}
				else if (!drawer)
				{
					drawShimmer(g, renderedWidth, renderedHeight);
				}
				long revealElapsed = drawer ? animationElapsed : elapsed;
				if (revealElapsed < rollDuration + REVEAL_DURATION_MS && !"normal".equals(revealType))
				{
					if (versus)
					{
						drawVersusCelebration(g, renderedWidth, renderedHeight,
							elapsed - rollDuration, revealType, scale);
					}
					else if (floating)
					{
						drawFloatingCelebration(g, renderedWidth, renderedHeight,
							elapsed - rollDuration, revealType, scale);
					}
					else if (drawer && drawerProgress > 0f)
					{
						drawDrawerCelebration(g, renderedWidth, renderedHeight,
							animationElapsed - rollDuration, revealType, scale,
							drawerProgress);
					}
					else
					{
						drawCelebration(g, renderedWidth, renderedHeight,
							elapsed - rollDuration, revealType, scale);
					}
				}
			}
		}
		finally
		{
			g.dispose();
		}

		Graphics2D output = (Graphics2D) graphics.create();
		try
		{
			float opacity = Math.max(0f, Math.min(1f, plugin.getOverlayOpacity() / 100f));
			if (temporary && elapsed > temporaryFadeStart)
			{
				opacity *= Math.max(0f, 1f
					- (elapsed - temporaryFadeStart) / (float) TEMPORARY_FADE_OUT_MS);
			}
			output.setComposite(AlphaComposite.SrcOver.derive(opacity));
			output.drawImage(buffer, 0, 0, null);
		}
		finally
		{
			output.dispose();
		}
		return new Dimension(renderedWidth, renderedHeight);
	}

	private BufferedImage prepareOverlayBuffer(int width, int height)
	{
		if (overlayBuffer == null || overlayBuffer.getWidth() != width || overlayBuffer.getHeight() != height)
		{
			overlayBuffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
		}

		Graphics2D clear = overlayBuffer.createGraphics();
		try
		{
			clear.setComposite(AlphaComposite.Clear);
			clear.fillRect(0, 0, width, height);
		}
		finally
		{
			clear.dispose();
		}
		return overlayBuffer;
	}

	private void drawLogo(Graphics2D graphics, int x, int y, int size)
	{
		if (size <= 0)
		{
			return;
		}

		long key = ((long) size << 32) | (size & 0xffffffffL);
		BufferedImage scaled = logoSizes.computeIfAbsent(key,
			ignored -> resizeHighQuality(logoMark, size, size));
		graphics.drawImage(scaled, x, y, null);
	}

	private void drawVersusShell(Graphics2D g, int width, int height, double scale)
	{
		int gap = px(42, scale);
		int panelWidth = (width - gap) / 2;
		int panelY = px(22, scale);
		int panelHeight = height - panelY;
		int rightX = width - panelWidth;
		int titleWidth = Math.min(width - px(86, scale), px(160, scale));
		int titleX = (width - titleWidth) / 2;
		int titleHeight = px(19, scale);
		int medallionSize = px(32, scale);
		int medallionX = (width - medallionSize) / 2;
		int medallionY = px(21, scale);

		g.setClip(null);
		g.setColor(new Color(0, 0, 0, 90));
		g.fillRoundRect(0, panelY + px(2, scale), panelWidth, panelHeight,
			px(14, scale), px(14, scale));
		g.fillRoundRect(rightX, panelY + px(2, scale), panelWidth, panelHeight,
			px(14, scale), px(14, scale));
		g.fillRoundRect(titleX, px(2, scale), titleWidth, titleHeight,
			px(10, scale), px(10, scale));

		g.setColor(new Color(18, 22, 27, 244));
		g.fillRoundRect(0, panelY, panelWidth, panelHeight,
			px(14, scale), px(14, scale));
		g.setColor(new Color(28, 16, 19, 246));
		g.fillRoundRect(rightX, panelY, panelWidth, panelHeight,
			px(14, scale), px(14, scale));
		g.setColor(new Color(14, 17, 21, 242));
		g.fillRoundRect(titleX, 0, titleWidth, titleHeight,
			px(10, scale), px(10, scale));

		g.setStroke(new BasicStroke(Math.max(1f, px(1, scale))));
		g.setColor(new Color(118, 126, 137, 115));
		g.drawRoundRect(0, panelY, panelWidth - 1, panelHeight - 1,
			px(14, scale), px(14, scale));
		g.setColor(new Color(224, 72, 62, 155));
		g.drawRoundRect(rightX, panelY, panelWidth - 1, panelHeight - 1,
			px(14, scale), px(14, scale));
		g.setColor(new Color(111, 118, 128, 110));
		g.drawRoundRect(titleX, 0, titleWidth - 1, titleHeight - 1,
			px(10, scale), px(10, scale));

		g.setColor(RED);
		g.fillOval(medallionX, medallionY, medallionSize, medallionSize);
		g.setColor(new Color(255, 255, 255, 75));
		g.drawOval(medallionX, medallionY, medallionSize - 1, medallionSize - 1);
	}

	private void drawFloatingShell(Graphics2D g, int width, int height, double scale)
	{
		g.setClip(null);
		int railX = px(38, scale);
		int railWidth = width - railX;
		int logoSize = px(32, scale);
		int logoY = px(7, scale);

		// The floating preset is deliberately assembled from detached HUD
		// elements rather than another enclosing notification card.
		g.setColor(new Color(0, 0, 0, 105));
		g.fillOval(px(1, scale), logoY + px(2, scale), logoSize, logoSize);
		g.fillRoundRect(railX + px(1, scale), px(2, scale), railWidth - px(1, scale),
			px(18, scale), px(12, scale), px(12, scale));
		g.fillRoundRect(railX + px(1, scale), px(24, scale), railWidth - px(1, scale),
			px(22, scale), px(12, scale), px(12, scale));

		g.setColor(new Color(12, 15, 19, 208));
		g.fillRoundRect(railX, 0, railWidth, px(18, scale),
			px(11, scale), px(11, scale));
		g.setColor(new Color(12, 15, 19, 226));
		g.fillRoundRect(railX, px(22, scale), railWidth, px(22, scale),
			px(11, scale), px(11, scale));

		g.setColor(new Color(18, 20, 24, 232));
		g.fillOval(0, logoY, logoSize, logoSize);
		g.setColor(new Color(225, 58, 49, 220));
		g.setStroke(new BasicStroke(Math.max(1f, px(1, scale))));
		g.drawOval(0, logoY, logoSize - 1, logoSize - 1);
		g.fillRoundRect(railX, px(26, scale), px(2, scale), px(14, scale),
			px(2, scale), px(2, scale));
	}

	private void drawDrawerShell(Graphics2D g, int width, double scale, float progress)
	{
		g.setClip(null);
		int railX = px(38, scale);
		int railWidth = Math.max(0, Math.round((width - railX) * progress));
		int logoSize = px(32, scale);
		int logoY = px(7, scale);

		if (railWidth > 0)
		{
			g.setColor(new Color(0, 0, 0, 105));
			g.fillRoundRect(railX + px(1, scale), px(2, scale), railWidth,
				px(18, scale), px(12, scale), px(12, scale));
			g.fillRoundRect(railX + px(1, scale), px(24, scale), railWidth,
				px(22, scale), px(12, scale), px(12, scale));

			g.setColor(new Color(12, 15, 19, 208));
			g.fillRoundRect(railX, 0, railWidth, px(18, scale),
				px(11, scale), px(11, scale));
			g.setColor(new Color(12, 15, 19, 226));
			g.fillRoundRect(railX, px(22, scale), railWidth, px(22, scale),
				px(11, scale), px(11, scale));

			g.setColor(new Color(225, 58, 49, 220));
			g.fillRoundRect(railX, px(26, scale),
				Math.min(px(2, scale), railWidth), px(14, scale),
				px(2, scale), px(2, scale));
		}

		// The medallion is deliberately independent of the rails so it remains
		// visible after the notification has fully retracted.
		g.setColor(new Color(0, 0, 0, 105));
		g.fillOval(px(1, scale), logoY + px(2, scale), logoSize, logoSize);
		g.setColor(new Color(18, 20, 24, 232));
		g.fillOval(0, logoY, logoSize, logoSize);
		g.setColor(new Color(225, 58, 49, 220));
		g.setStroke(new BasicStroke(Math.max(1f, px(1, scale))));
		g.drawOval(0, logoY, logoSize - 1, logoSize - 1);
		drawLogo(g, px(3, scale), px(10, scale), px(26, scale));
	}

	private static float drawerProgress(long elapsed, long closeStart)
	{
		if (elapsed == Long.MAX_VALUE || elapsed < 0L)
		{
			return 0f;
		}
		if (elapsed < DRAWER_OPEN_MS)
		{
			return easeInOut(elapsed / (float) DRAWER_OPEN_MS);
		}
		if (elapsed < closeStart)
		{
			return 1f;
		}
		if (elapsed < closeStart + DRAWER_CLOSE_MS)
		{
			return 1f - easeInOut((elapsed - closeStart) / (float) DRAWER_CLOSE_MS);
		}
		return 0f;
	}

	private static float easeInOut(float value)
	{
		float t = Math.max(0f, Math.min(1f, value));
		return t < 0.5f ? 2f * t * t
			: 1f - (float) Math.pow(-2f * t + 2f, 2d) / 2f;
	}

	private static BufferedImage resizeHighQuality(BufferedImage source, int targetWidth, int targetHeight)
	{
		BufferedImage current = source;
		int width = source.getWidth();
		int height = source.getHeight();

		// Downsample in stages. A direct 512px-to-22px bicubic draw aliases the
		// logo's diagonal edges and turns fine antialiasing into visible noise.
		while (width != targetWidth || height != targetHeight)
		{
			int nextWidth = width > targetWidth ? Math.max(targetWidth, width / 2) : targetWidth;
			int nextHeight = height > targetHeight ? Math.max(targetHeight, height / 2) : targetHeight;
			BufferedImage resized = new BufferedImage(nextWidth, nextHeight, BufferedImage.TYPE_INT_ARGB_PRE);
			Graphics2D graphics = resized.createGraphics();
			try
			{
				graphics.setComposite(AlphaComposite.Src);
				graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
				graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
				graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
				graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
				graphics.drawImage(current, 0, 0, nextWidth, nextHeight, null);
			}
			finally
			{
				graphics.dispose();
			}

			current = resized;
			width = nextWidth;
			height = nextHeight;
		}

		return current;
	}

	private static BufferedImage removeCornerFragments(BufferedImage source)
	{
		int width = source.getWidth();
		int height = source.getHeight();
		int cornerRadius = Math.max(1, Math.min(width, height) / 10);
		BufferedImage cleaned = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				int topRightDistance = width - 1 - x + y;
				int bottomLeftDistance = x + height - 1 - y;
				int bottomRightDistance = width - 1 - x + height - 1 - y;
				if (topRightDistance >= cornerRadius
					&& bottomLeftDistance >= cornerRadius
					&& bottomRightDistance >= cornerRadius)
				{
					cleaned.setRGB(x, y, source.getRGB(x, y));
				}
			}
		}
		return cleaned;
	}

	private void drawDiceAt(Graphics2D g, int centreX, int centreY, int size,
		long elapsed, boolean emphasized)
	{
		int x = centreX - size / 2;
		int y = centreY - size / 2;
		AffineTransform oldTransform = g.getTransform();
		g.rotate(Math.sin(elapsed / 95d) * (emphasized ? 0.15d : 0.10d),
			x + size / 2d, y + size / 2d);
		g.setColor(new Color(28, 32, 38));
		int radius = Math.max(3, Math.round(size * 0.22f));
		g.fillRoundRect(x, y, size, size, radius, radius);
		g.setColor(new Color(190, 196, 204));
		g.setStroke(new BasicStroke(Math.max(1f, size / 24f)));
		g.drawRoundRect(x, y, size - 1, size - 1, radius, radius);
		int face = (int) ((elapsed / 85L) % 6L) + 1;
		drawPips(g, x, y, size, face);
		g.setTransform(oldTransform);
	}

	private void drawPips(Graphics2D g, int x, int y, int size, int face)
	{
		int pip = Math.max(2, Math.round(size * 0.11f));
		int left = x + size / 4 - pip / 2;
		int centre = x + size / 2 - pip / 2;
		int right = x + size * 3 / 4 - pip / 2;
		int top = y + size / 4 - pip / 2;
		int middle = y + size / 2 - pip / 2;
		int bottom = y + size * 3 / 4 - pip / 2;
		g.setColor(new Color(238, 240, 243));
		if (face % 2 == 1)
		{
			g.fillOval(centre, middle, pip, pip);
		}
		if (face >= 2)
		{
			g.fillOval(left, top, pip, pip);
			g.fillOval(right, bottom, pip, pip);
		}
		if (face >= 4)
		{
			g.fillOval(right, top, pip, pip);
			g.fillOval(left, bottom, pip, pip);
		}
		if (face == 6)
		{
			g.fillOval(left, middle, pip, pip);
			g.fillOval(right, middle, pip, pip);
		}
	}

	private void drawCelebration(Graphics2D g, int width, int height, long elapsed, String type, double scale)
	{
		boolean pet = "pet".equals(type);
		Color accent = pet
			? Color.getHSBColor((elapsed % 900L) / 900f, 0.78f, 1f)
			: "unique".equals(type) ? new Color(255, 195, 42) : new Color(244, 84, 67);
		float pulse = (float) (0.56d + 0.24d * (0.5d + 0.5d * Math.sin(elapsed / 150d)));
		g.setClip(null);
		g.setComposite(AlphaComposite.SrcOver.derive(pulse * 0.35f));
		g.setColor(accent);
		g.setStroke(new BasicStroke(Math.max(4f, px(5, scale))));
		g.drawRoundRect(px(2, scale), px(2, scale), width - px(4, scale), height - px(4, scale), px(10, scale), px(10, scale));
		g.setComposite(AlphaComposite.SrcOver.derive(pulse));
		g.setColor(accent);
		g.setStroke(new BasicStroke(Math.max(1f, px(2, scale))));
		g.drawRoundRect(px(1, scale), px(1, scale), width - px(3, scale), height - px(3, scale), px(9, scale), px(9, scale));
		g.setComposite(AlphaComposite.SrcOver);

		String label = pet ? "PET REROLL!" : "unique".equals(type) ? "UNIQUE REROLL!" : "BIG REROLL!";
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, textPx(9, scale)));
		int labelWidth = g.getFontMetrics().stringWidth(label);
		int labelX = Math.max(px(104, scale), width - labelWidth - px(8, scale));
		g.setColor(accent);
		g.drawString(label, labelX, px(10, scale));

		int sweepX = Math.floorMod((int) (elapsed / 5L), width + px(80, scale)) - px(40, scale);
		g.setComposite(AlphaComposite.SrcOver.derive(0.12f));
		g.setPaint(new GradientPaint(sweepX - px(25, scale), 0, new Color(255, 255, 255, 0), sweepX + px(25, scale), 0, accent));
		g.fillRect(sweepX - px(25, scale), 0, px(50, scale), height);
		g.setComposite(AlphaComposite.SrcOver);
	}

	private void drawVersusCelebration(Graphics2D g, int width, int height,
		long elapsed, String type, double scale)
	{
		boolean pet = "pet".equals(type);
		Color accent = pet
			? Color.getHSBColor((elapsed % 900L) / 900f, 0.78f, 1f)
			: "unique".equals(type) ? new Color(255, 195, 42) : new Color(244, 84, 67);
		float pulse = (float) (0.62d + 0.28d * (0.5d + 0.5d * Math.sin(elapsed / 150d)));
		Composite previousComposite = g.getComposite();
		java.awt.Stroke previousStroke = g.getStroke();
		Shape previousClip = g.getClip();
		g.setClip(null);

		Area outline = createVersusArea(width, height, scale);
		g.setColor(accent);
		g.setComposite(AlphaComposite.SrcOver.derive(pulse * 0.30f));
		g.setStroke(new BasicStroke(Math.max(4f, px(5, scale))));
		g.draw(outline);
		g.setComposite(AlphaComposite.SrcOver.derive(pulse));
		g.setStroke(new BasicStroke(Math.max(1f, px(2, scale))));
		g.draw(outline);

		g.setClip(previousClip);
		g.setStroke(previousStroke);
		g.setComposite(previousComposite);
	}

	private void drawFull(Graphics2D g, WchbFeed feed, WchbEvent event, int width,
		double scale, boolean rolling, long elapsed, float resultAlpha, boolean emphasized)
	{
		String player = feed.getPlayerName() == null ? "" : feed.getPlayerName();
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, textPx(11, scale)));
		player = fitText(g, player, px(82, scale));
		int playerWidth = g.getFontMetrics().stringWidth(player);
		int playerX = px(width, scale) - playerWidth - px(10, scale);
		BufferedImage badge = accountBadges.get(normaliseType(feed.getAccountType()));
		if (badge != null)
		{
			playerX -= px(15, scale);
			g.drawImage(badge, playerX, px(9, scale), px(12, scale), px(12, scale), null);
		}
		g.setColor(new Color(220, 223, 227));
		g.drawString(player, playerX + (badge == null ? 0 : px(15, scale)), px(20, scale));

		int bossX = px(113, scale);
		int bossMaxWidth = Math.max(px(55, scale), playerX - bossX - px(8, scale));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, textPx(14, scale)));
		g.setColor(Color.WHITE);
		g.drawString(fitText(g, event.getSource(), bossMaxWidth), bossX, px(20, scale));

		int x = drawValue(g, "DROP", event.getItems(), event.getTotalValue(), 113, false, scale);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, textPx(11, scale)));
		g.setColor(MUTED);
		g.drawString("\u2192", px(x + 8, scale), px(48, scale));
		int wchbX = x + 30;
		if (rolling)
		{
			g.setFont(new Font(Font.MONOSPACED, Font.BOLD, textPx(8, scale)));
			g.setColor(new Color(224, 72, 62));
			g.drawString("WCHB", px(wchbX, scale), px(48, scale));
			int resultLeft = px(wchbX + 38, scale);
			int resultRight = px(width - 8, scale);
			int diceSize = Math.min(px(emphasized ? 24 : 20, scale), px(24, scale));
			drawDiceAt(g, resultLeft + Math.max(diceSize / 2,
				(resultRight - resultLeft) / 2), px(40, scale), diceSize, elapsed, emphasized);
		}
		else
		{
			Composite previous = g.getComposite();
			g.setComposite(AlphaComposite.SrcOver.derive(resultAlpha));
			drawValue(g, "WCHB", event.getRerolledItems(), event.getRerolledValue(), wchbX, true, scale);
			g.setComposite(previous);
		}
	}

	private void drawMinimal(Graphics2D g, WchbEvent event, int width, double scale,
		WchbOverlayStyle style, boolean rolling, long elapsed, float resultAlpha,
		boolean emphasized, float drawerProgress)
	{
		switch (style)
		{
			case VERSUS:
				drawVersusMinimal(g, event, width, scale, rolling, elapsed,
					resultAlpha, emphasized);
				break;
			case FLOATING:
				drawFloatingMinimal(g, event, width, scale, rolling, elapsed,
					resultAlpha, emphasized);
				break;
			case DRAWER:
				drawDrawerMinimal(g, event, width, scale, rolling, elapsed,
					resultAlpha, emphasized, drawerProgress);
				break;
			case CLASSIC:
			default:
				drawSingleLineMinimal(g, event, width, scale,
					50, 12, 22, 2, 4, 7, 22, 6, 12, 13, 9, 34,
					rolling, elapsed, resultAlpha, emphasized);
				break;
		}
	}

	private void drawFloatingMinimal(Graphics2D g, WchbEvent event, int width, double scale,
		boolean rolling, long elapsed, float resultAlpha, boolean emphasized)
	{
		drawFloatingContent(g, event, width, scale, rolling, elapsed,
			resultAlpha, emphasized, true);
	}

	private void drawDrawerMinimal(Graphics2D g, WchbEvent event, int width, double scale,
		boolean rolling, long elapsed, float resultAlpha, boolean emphasized,
		float progress)
	{
		Shape previousClip = g.getClip();
		g.setClip(createDrawerRailsArea(px(width, scale), scale, progress));
		drawFloatingContent(g, event, width, scale, rolling, elapsed,
			resultAlpha, emphasized, false);
		g.setClip(previousClip);
	}

	private void drawFloatingContent(Graphics2D g, WchbEvent event, int width, double scale,
		boolean rolling, long elapsed, float resultAlpha, boolean emphasized,
		boolean drawMedallionLogo)
	{
		int renderedWidth = px(width, scale);
		int contentLeft = px(42, scale);
		int contentRight = renderedWidth - px(8, scale);
		int contentWidth = Math.max(1, contentRight - contentLeft);
		String source = event.getSource() == null || event.getSource().isEmpty()
			? "Unknown source" : event.getSource();

		int sourceFontSize = textPx(12, scale);
		int minimumSourceFontSize = textPx(7, scale);
		do
		{
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, sourceFontSize));
			if (g.getFontMetrics().stringWidth(source) <= contentWidth
				|| sourceFontSize <= minimumSourceFontSize)
			{
				break;
			}
			sourceFontSize--;
		}
		while (sourceFontSize > 1);
		source = fitText(g, source, contentWidth);
		int sourceX = contentLeft + Math.max(0,
			(contentWidth - g.getFontMetrics().stringWidth(source)) / 2);
		g.setColor(new Color(0, 0, 0, 220));
		g.drawString(source, sourceX + px(1, scale), px(15, scale) + px(1, scale));
		g.setColor(Color.WHITE);
		g.drawString(source, sourceX, px(15, scale));

		if (drawMedallionLogo)
		{
			int logoSize = px(26, scale);
			drawLogo(g, px(3, scale), px(10, scale), logoSize);
		}

		int itemSize = px(17, scale);
		int itemGap = px(1, scale);
		int valueGap = px(3, scale);
		int arrowGap = px(6, scale);
		int itemY = px(24, scale);
		int baseline = px(39, scale);
		Font valueFont = new Font(Font.SANS_SERIF, Font.BOLD, textPx(10, scale));
		g.setFont(valueFont);
		int actualWidth = inlineValueWidth(g, event.getItems(), event.getTotalValue(),
			itemSize, itemGap, valueGap);
		int rerollWidth = inlineValueWidth(g, event.getRerolledItems(), event.getRerolledValue(),
			itemSize, itemGap, valueGap);
		int arrowWidth = g.getFontMetrics().stringWidth("\u2192");
		int lootWidth = actualWidth + arrowGap + arrowWidth + arrowGap + rerollWidth;
		int lootX = contentLeft + Math.max(0, (contentWidth - lootWidth) / 2);
		int actualEnd = drawInlineValue(g, event.getItems(), event.getTotalValue(), lootX,
			false, itemSize, itemGap, valueGap, itemY, baseline, valueFont);
		g.setFont(valueFont);
		g.setColor(new Color(226, 68, 58));
		int arrowX = actualEnd + arrowGap;
		g.drawString("\u2192", arrowX, baseline);
		int rerollX = arrowX + arrowWidth + arrowGap;
		if (rolling)
		{
			// Leave enough inset for the rotating corners at every overlay scale.
			// The result rail is 22px high, so a 16px die is the largest safe
			// emphasized size without visually escaping its rounded bounds.
			int diceSize = px(emphasized ? 16 : 15, scale);
			drawDiceAt(g, rerollX + rerollWidth / 2, px(33, scale), diceSize,
				elapsed, emphasized);
		}
		else
		{
			Composite previous = g.getComposite();
			g.setComposite(AlphaComposite.SrcOver.derive(resultAlpha));
			drawInlineValue(g, event.getRerolledItems(), event.getRerolledValue(), rerollX,
				true, itemSize, itemGap, valueGap, itemY, baseline, valueFont);
			g.setComposite(previous);
		}
	}

	private void drawVersusMinimal(Graphics2D g, WchbEvent event, int width, double scale,
		boolean rolling, long elapsed, float resultAlpha, boolean emphasized)
	{
		int renderedWidth = px(width, scale);
		int gap = px(42, scale);
		int panelWidth = (renderedWidth - gap) / 2;
		int rightX = renderedWidth - panelWidth;
		int titleWidth = Math.min(renderedWidth - px(86, scale), px(160, scale));
		int titleX = (renderedWidth - titleWidth) / 2;
		String source = event.getSource() == null || event.getSource().isEmpty()
			? "Unknown source" : event.getSource();

		int sourceFontSize = textPx(11, scale);
		int minimumSourceFontSize = textPx(7, scale);
		do
		{
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, sourceFontSize));
			if (g.getFontMetrics().stringWidth(source) <= titleWidth - px(14, scale)
				|| sourceFontSize <= minimumSourceFontSize)
			{
				break;
			}
			sourceFontSize--;
		}
		while (sourceFontSize > 1);
		source = fitText(g, source, titleWidth - px(14, scale));
		int sourceX = titleX + (titleWidth - g.getFontMetrics().stringWidth(source)) / 2;
		g.setColor(Color.WHITE);
		g.drawString(source, sourceX, px(13, scale));

		Font labelFont = new Font(Font.MONOSPACED, Font.BOLD, textPx(7, scale));
		g.setFont(labelFont);
		g.setColor(new Color(174, 180, 188));
		g.drawString("ACTUAL", px(9, scale), px(31, scale));
		String wchbLabel = "WCHB";
		g.setColor(new Color(242, 82, 70));
		g.drawString(wchbLabel, renderedWidth - px(9, scale)
			- g.getFontMetrics().stringWidth(wchbLabel), px(31, scale));

		int itemSize = px(16, scale);
		int itemGap = px(1, scale);
		int valueGap = px(3, scale);
		int itemY = px(34, scale);
		int baseline = px(48, scale);
		Font valueFont = new Font(Font.SANS_SERIF, Font.BOLD, textPx(10, scale));
		g.setFont(valueFont);
		int actualWidth = inlineValueWidth(g, event.getItems(), event.getTotalValue(),
			itemSize, itemGap, valueGap);
		int rerollWidth = inlineValueWidth(g, event.getRerolledItems(), event.getRerolledValue(),
			itemSize, itemGap, valueGap);
		int actualX = Math.max(px(7, scale), (panelWidth - actualWidth) / 2);
		int rerollX = rightX + Math.max(px(7, scale), (panelWidth - rerollWidth) / 2);
		drawInlineValue(g, event.getItems(), event.getTotalValue(), actualX,
			false, itemSize, itemGap, valueGap, itemY, baseline, valueFont);
		if (rolling)
		{
			int diceSize = px(emphasized ? 21 : 18, scale);
			drawDiceAt(g, rightX + panelWidth / 2, px(38, scale), diceSize,
				elapsed, emphasized);
		}
		else
		{
			Composite previous = g.getComposite();
			g.setComposite(AlphaComposite.SrcOver.derive(resultAlpha));
			drawInlineValue(g, event.getRerolledItems(), event.getRerolledValue(), rerollX,
				true, itemSize, itemGap, valueGap, itemY, baseline, valueFont);
			g.setComposite(previous);
		}

		int logoSize = px(18, scale);
		drawLogo(g, (renderedWidth - logoSize) / 2, px(28, scale), logoSize);
	}

	private void drawSingleLineMinimal(Graphics2D g, WchbEvent event, int width, double scale,
		int nameLeftNative, int rightPaddingNative, int itemSizeNative, int itemGapNative,
		int valueGapNative, int arrowGapNative, int baselineNative, int itemYNative,
		int valueFontNative, int nameFontNative, int minimumNameFontNative,
		int panelHeightNative,
		boolean rolling, long elapsed, float resultAlpha, boolean emphasized)
	{
		int renderedWidth = px(width, scale);
		int itemSize = px(itemSizeNative, scale);
		int itemGap = px(itemGapNative, scale);
		int valueGap = px(valueGapNative, scale);
		int arrowGap = px(arrowGapNative, scale);
		int baseline = px(baselineNative, scale);
		int itemY = px(itemYNative, scale);
		Font valueFont = new Font(Font.SANS_SERIF, Font.BOLD, textPx(valueFontNative, scale));
		g.setFont(valueFont);
		int actualWidth = inlineValueWidth(g, event.getItems(), event.getTotalValue(), itemSize, itemGap, valueGap);
		int rerollWidth = inlineValueWidth(g, event.getRerolledItems(), event.getRerolledValue(), itemSize, itemGap, valueGap);
		int arrowWidth = g.getFontMetrics().stringWidth("\u2192");
		int lootWidth = actualWidth + arrowGap + arrowWidth + arrowGap + rerollWidth;
		int lootX = renderedWidth - px(rightPaddingNative, scale) - lootWidth;

		int nameLeft = px(nameLeftNative, scale);
		int nameRight = lootX - px(8, scale);
		int nameWidth = Math.max(1, nameRight - nameLeft);
		String source = event.getSource() == null || event.getSource().isEmpty()
			? "Unknown source" : event.getSource();
		int fontSize = textPx(nameFontNative, scale);
		int minimumFontSize = textPx(minimumNameFontNative, scale);
		do
		{
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
			if (g.getFontMetrics().stringWidth(source) <= nameWidth || fontSize <= minimumFontSize)
			{
				break;
			}
			fontSize--;
		}
		while (fontSize > 1);
		source = fitText(g, source, nameWidth);
		int nameX = nameLeft + Math.max(0, (nameWidth - g.getFontMetrics().stringWidth(source)) / 2);
		g.setColor(Color.WHITE);
		g.drawString(source, nameX, baseline);

		int actualEnd = drawInlineValue(g, event.getItems(), event.getTotalValue(), lootX,
			false, itemSize, itemGap, valueGap, itemY, baseline, valueFont);
		g.setFont(valueFont);
		g.setColor(MUTED);
		int arrowX = actualEnd + arrowGap;
		g.drawString("\u2192", arrowX, baseline);
		int rerollX = arrowX + arrowWidth + arrowGap;
		if (rolling)
		{
			int maximumDiceSizeNative = Math.max(1, panelHeightNative - 7);
			int diceSize = px(Math.min(emphasized ? itemSizeNative + 3 : itemSizeNative,
				maximumDiceSizeNative), scale);
			drawDiceAt(g, rerollX + rerollWidth / 2,
				px(panelHeightNative, scale) / 2, diceSize, elapsed, emphasized);
		}
		else
		{
			Composite previous = g.getComposite();
			g.setComposite(AlphaComposite.SrcOver.derive(resultAlpha));
			drawInlineValue(g, event.getRerolledItems(), event.getRerolledValue(),
				rerollX, true, itemSize, itemGap, valueGap, itemY, baseline, valueFont);
			g.setComposite(previous);
		}
	}

	private static int inlineValueWidth(Graphics2D g, List<WchbItem> items, long value,
		int itemSize, int itemGap, int valueGap)
	{
		int itemsWidth = validItemCount(items) * (itemSize + itemGap);
		return itemsWidth + valueGap + g.getFontMetrics().stringWidth(formatGp(value));
	}

	private int drawInlineValue(Graphics2D g, List<WchbItem> items, long value, int x,
		boolean wchb, int itemSize, int itemGap, int valueGap, int itemY, int baseline, Font valueFont)
	{
		int cursor = x;
		if (items != null)
		{
			for (WchbItem item : items)
			{
				if (item == null || item.getId() <= 0)
				{
					continue;
				}

				BufferedImage image = itemManager.getImage(item.getId(), Math.max(1, item.getQuantity()), false);
				if (image != null)
				{
					g.drawImage(image, cursor, itemY, itemSize, itemSize, null);
				}
				cursor += itemSize + itemGap;
			}
		}

		g.setFont(valueFont);
		g.setColor(wchb ? new Color(242, 82, 70) : new Color(232, 234, 237));
		String formatted = formatGp(value);
		int valueX = cursor + valueGap;
		g.drawString(formatted, valueX, baseline);
		return valueX + g.getFontMetrics().stringWidth(formatted);
	}

	private int drawValue(Graphics2D g, String label, List<WchbItem> items, long value, int x, boolean wchb, double scale)
	{
		g.setFont(new Font(Font.MONOSPACED, Font.BOLD, textPx(8, scale)));
		g.setColor(wchb ? new Color(224, 72, 62) : MUTED);
		g.drawString(label, px(x, scale), px(48, scale));
		int valueX = drawItemStrip(g, items, x + 38, 30, 22, scale);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, textPx(12, scale)));
		g.setColor(wchb ? new Color(242, 82, 70) : new Color(232, 234, 237));
		String formatted = formatGp(value);
		g.drawString(formatted, px(valueX + 4, scale), px(48, scale));
		return valueX + 4 + (int) Math.ceil(g.getFontMetrics().stringWidth(formatted) / scale);
	}

	private int drawItemStrip(Graphics2D g, List<WchbItem> items, int x, int y, int size, double scale)
	{
		int cursor = x;
		if (items != null)
		{
			for (WchbItem item : items)
			{
				if (item == null || item.getId() <= 0)
				{
					continue;
				}
				BufferedImage image = itemManager.getImage(item.getId(), Math.max(1, item.getQuantity()), false);
				if (image == null)
				{
					continue;
				}
				g.drawImage(image, px(cursor, scale), px(y, scale), px(size, scale), px(size, scale), null);
				cursor += size + 2;
			}
		}
		return cursor;
	}

	private static int px(int value, double scale)
	{
		return Math.max(1, (int) Math.round(value * scale));
	}

	// Typography scales much more gently than the card. Small presets stay
	// readable, while larger presets gain only modestly larger type.
	private static int textPx(int value, double scale)
	{
		return Math.max(1, (int) Math.round(value * (0.65d + 0.35d * scale)));
	}

	private void drawShimmer(Graphics2D g, int width, int height)
	{
		long phase = System.currentTimeMillis() % 10_000L;
		if (phase > 900L)
		{
			return;
		}
		float progress = phase / 900f;
		int x = (int) (-100 + progress * (width + 200));
		Composite previous = g.getComposite();
		g.setComposite(AlphaComposite.SrcOver.derive(0.14f));
		g.setPaint(new GradientPaint(x - 55, 0, new Color(255, 255, 255, 0), x + 55, 0, Color.WHITE));
		g.fillRect(x - 55, 0, 110, height);
		g.setComposite(previous);
	}

	private void drawVersusShimmer(Graphics2D g, int width, int height, double scale)
	{
		Shape previousClip = g.getClip();
		g.setClip(createVersusArea(width, height, scale));
		drawShimmer(g, width, height);
		g.setClip(previousClip);
	}

	private void drawFloatingShimmer(Graphics2D g, int width, int height, double scale)
	{
		Shape previousClip = g.getClip();
		g.setClip(createFloatingArea(width, scale));
		drawShimmer(g, width, height);
		g.setClip(previousClip);
	}

	private void drawDrawerShimmer(Graphics2D g, int width, int height, double scale,
		float progress)
	{
		Shape previousClip = g.getClip();
		g.setClip(createDrawerArea(width, scale, progress));
		drawShimmer(g, width, height);
		g.setClip(previousClip);
	}

	private void drawFloatingCelebration(Graphics2D g, int width, int height,
		long elapsed, String type, double scale)
	{
		drawFloatingCelebration(g, elapsed, type, scale,
			createFloatingArea(width, scale));
	}

	private void drawDrawerCelebration(Graphics2D g, int width, int height,
		long elapsed, String type, double scale, float progress)
	{
		drawFloatingCelebration(g, elapsed, type, scale,
			createDrawerArea(width, scale, progress));
	}

	private void drawFloatingCelebration(Graphics2D g, long elapsed, String type,
		double scale, Area outline)
	{
		boolean pet = "pet".equals(type);
		Color accent = pet
			? Color.getHSBColor((elapsed % 900L) / 900f, 0.78f, 1f)
			: "unique".equals(type) ? new Color(255, 195, 42) : new Color(244, 84, 67);
		float pulse = (float) (0.55d + 0.30d * (0.5d + 0.5d * Math.sin(elapsed / 150d)));
		Composite previousComposite = g.getComposite();
		java.awt.Stroke previousStroke = g.getStroke();
		Shape previousClip = g.getClip();
		g.setClip(null);

		g.setColor(accent);
		g.setComposite(AlphaComposite.SrcOver.derive(pulse * 0.25f));
		g.setStroke(new BasicStroke(Math.max(3f, px(4, scale))));
		g.draw(outline);
		g.setComposite(AlphaComposite.SrcOver.derive(pulse));
		g.setStroke(new BasicStroke(Math.max(1f, px(1, scale))));
		g.draw(outline);

		g.setClip(previousClip);
		g.setStroke(previousStroke);
		g.setComposite(previousComposite);
	}

	private static Area createFloatingArea(int width, double scale)
	{
		int railX = px(38, scale);
		int railWidth = width - railX;
		int logoSize = px(32, scale);
		Area area = new Area(new java.awt.geom.Ellipse2D.Float(0, px(7, scale),
			logoSize, logoSize));
		area.add(new Area(new RoundRectangle2D.Float(railX, 0, railWidth,
			px(18, scale), px(11, scale), px(11, scale))));
		area.add(new Area(new RoundRectangle2D.Float(railX, px(22, scale), railWidth,
			px(22, scale), px(11, scale), px(11, scale))));
		return area;
	}

	private static Area createDrawerArea(int width, double scale, float progress)
	{
		int logoSize = px(32, scale);
		Area area = new Area(new java.awt.geom.Ellipse2D.Float(0, px(7, scale),
			logoSize, logoSize));
		area.add(createDrawerRailsArea(width, scale, progress));
		return area;
	}

	private static Area createDrawerRailsArea(int width, double scale, float progress)
	{
		int railX = px(38, scale);
		int railWidth = Math.max(0, Math.round((width - railX)
			* Math.max(0f, Math.min(1f, progress))));
		Area area = new Area();
		if (railWidth <= 0)
		{
			return area;
		}
		area.add(new Area(new RoundRectangle2D.Float(railX, 0, railWidth,
			px(18, scale), px(11, scale), px(11, scale))));
		area.add(new Area(new RoundRectangle2D.Float(railX, px(22, scale), railWidth,
			px(22, scale), px(11, scale), px(11, scale))));
		return area;
	}

	private static Area createVersusArea(int width, int height, double scale)
	{
		int gap = px(42, scale);
		int panelWidth = (width - gap) / 2;
		int panelY = px(22, scale);
		int panelHeight = height - panelY;
		int rightX = width - panelWidth;
		int titleWidth = Math.min(width - px(86, scale), px(160, scale));
		int titleX = (width - titleWidth) / 2;
		int titleHeight = px(19, scale);
		int medallionSize = px(32, scale);
		int medallionX = (width - medallionSize) / 2;
		int medallionY = px(21, scale);

		Area area = new Area(new RoundRectangle2D.Float(0, panelY, panelWidth,
			panelHeight, px(14, scale), px(14, scale)));
		area.add(new Area(new RoundRectangle2D.Float(rightX, panelY, panelWidth,
			panelHeight, px(14, scale), px(14, scale))));
		area.add(new Area(new RoundRectangle2D.Float(titleX, 0, titleWidth,
			titleHeight, px(10, scale), px(10, scale))));
		area.add(new Area(new java.awt.geom.Ellipse2D.Float(medallionX, medallionY,
			medallionSize, medallionSize)));
		return area;
	}

	private static String normaliseType(String value)
	{
		return value == null ? "main" : value.toLowerCase().replace('-', '_').replace(' ', '_');
	}

	private static int validItemCount(List<WchbItem> items)
	{
		return items == null ? 0 : (int) items.stream().filter(item -> item != null && item.getId() > 0).count();
	}

	private static String truncate(String value, int max)
	{
		if (value == null)
		{
			return "Unknown source";
		}
		return value.length() <= max ? value : value.substring(0, max - 1) + "…";
	}

	private static String fitText(Graphics2D g, String value, int maxWidth)
	{
		if (value == null || value.isEmpty())
		{
			return "";
		}
		if (g.getFontMetrics().stringWidth(value) <= maxWidth)
		{
			return value;
		}
		String ellipsis = "…";
		int end = value.length();
		while (end > 1 && g.getFontMetrics().stringWidth(value.substring(0, end) + ellipsis) > maxWidth)
		{
			end--;
		}
		return value.substring(0, end) + ellipsis;
	}

	static String formatGp(long value)
	{
		if (value >= 1_000_000_000L)
		{
			return String.format("%.1fb", value / 1_000_000_000d);
		}
		if (value >= 1_000_000L)
		{
			return String.format("%.1fm", value / 1_000_000d);
		}
		if (value >= 1_000L)
		{
			return String.format("%.1fk", value / 1_000d);
		}
		return Long.toString(value);
	}
}
