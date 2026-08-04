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
	private static final int MINIMAL_WIDTH = 350;
	private static final int MINIMAL_HEIGHT = 34;
	private static final Color RED = new Color(205, 42, 35);
	private static final Color PANEL = new Color(14, 17, 21, 244);
	private static final Color MUTED = new Color(166, 171, 178);
	private static final long ROLL_DURATION_MS = 900L;
	private static final long RESULT_FADE_MS = 420L;
	private static final long REVEAL_DURATION_MS = 3_000L;

	private final WchbPlugin plugin;
	private final ItemManager itemManager;
	private final BufferedImage logoMark;
	private final Map<String, BufferedImage> accountBadges = new HashMap<>();
	private volatile long animationStarted;
	private volatile String revealType = "normal";

	@Inject
	WchbOverlay(WchbPlugin plugin, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.logoMark = ImageUtil.loadImageResource(WchbPlugin.class, "/wchb_mark.png");
		loadBadge("ironman");
		loadBadge("hardcore_ironman");
		loadBadge("ultimate_ironman");
		loadBadge("group_ironman");
		loadBadge("hardcore_group_ironman");
		setPosition(OverlayPosition.BOTTOM_LEFT);
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
		if (!plugin.shouldShowOverlay() || feed == null || feed.getEvents().isEmpty())
		{
			return null;
		}

		boolean minimal = plugin.isMinimalOverlay();
		WchbEvent event = feed.getEvents().get(0);
		int additionalItems = Math.max(0, validItemCount(event.getItems()) - 1)
			+ Math.max(0, validItemCount(event.getRerolledItems()) - 1);
		int width = (minimal ? MINIMAL_WIDTH : FULL_WIDTH) + additionalItems * 24;
		int height = minimal ? MINIMAL_HEIGHT : FULL_HEIGHT;
		double scale = plugin.getOverlayScale() / 100d;
		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			int renderedWidth = px(width, scale);
			int renderedHeight = px(height, scale);
			Shape card = new RoundRectangle2D.Float(0, 0, renderedWidth, renderedHeight, px(9, scale), px(9, scale));
			g.setClip(card);
			g.setColor(PANEL);
			g.fill(card);
			g.setColor(RED);
			g.fillRect(0, 0, px(minimal ? 42 : BRAND_WIDTH, scale), renderedHeight);
			g.setComposite(AlphaComposite.SrcOver.derive(0.2f));
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, renderedWidth, Math.max(1, px(1, scale)));
			g.setComposite(AlphaComposite.SrcOver);

			if (minimal)
			{
				g.drawImage(logoMark, px(9, scale), px(6, scale), px(24, scale), px(22, scale), null);
			}
			else
			{
				g.drawImage(logoMark, px(28, scale), px(10, scale), px(44, scale), px(40, scale), null);
			}

			long elapsed = animationStarted == 0L ? Long.MAX_VALUE : System.currentTimeMillis() - animationStarted;
			if (elapsed < ROLL_DURATION_MS)
			{
				drawDiceRoll(g, renderedWidth, renderedHeight, px(minimal ? 42 : BRAND_WIDTH, scale), elapsed, scale);
				return new Dimension(renderedWidth, renderedHeight);
			}

			float resultAlpha = elapsed == Long.MAX_VALUE
				? 1f : Math.min(1f, Math.max(0f, (elapsed - ROLL_DURATION_MS) / (float) RESULT_FADE_MS));
			g.setComposite(AlphaComposite.SrcOver.derive(resultAlpha));
			if (minimal)
			{
				drawMinimal(g, event, scale);
			}
			else
			{
				drawFull(g, feed, event, width, scale);
			}
			g.setComposite(AlphaComposite.SrcOver);
			drawShimmer(g, renderedWidth, renderedHeight);
			if (elapsed < ROLL_DURATION_MS + REVEAL_DURATION_MS && !"normal".equals(revealType))
			{
				drawCelebration(g, renderedWidth, renderedHeight, elapsed - ROLL_DURATION_MS, revealType, scale);
			}
		}
		finally
		{
			g.dispose();
		}
		return new Dimension((int) Math.ceil(width * scale), (int) Math.ceil(height * scale));
	}

	private void drawDiceRoll(Graphics2D g, int width, int height, int brandWidth, long elapsed, double scale)
	{
		int size = Math.min(px(28, scale), height - px(12, scale));
		int x = brandWidth + (width - brandWidth - size) / 2;
		int y = (height - size) / 2;
		AffineTransform oldTransform = g.getTransform();
		g.rotate(Math.sin(elapsed / 95d) * 0.10d, x + size / 2d, y + size / 2d);
		g.setColor(new Color(28, 32, 38));
		g.fillRoundRect(x, y, size, size, px(7, scale), px(7, scale));
		g.setColor(new Color(190, 196, 204));
		g.setStroke(new BasicStroke(Math.max(1f, px(1, scale))));
		g.drawRoundRect(x, y, size - 1, size - 1, px(7, scale), px(7, scale));
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
		if (face % 2 == 1) g.fillOval(centre, middle, pip, pip);
		if (face >= 2) { g.fillOval(left, top, pip, pip); g.fillOval(right, bottom, pip, pip); }
		if (face >= 4) { g.fillOval(right, top, pip, pip); g.fillOval(left, bottom, pip, pip); }
		if (face == 6) { g.fillOval(left, middle, pip, pip); g.fillOval(right, middle, pip, pip); }
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

	private void drawFull(Graphics2D g, WchbFeed feed, WchbEvent event, int width, double scale)
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
		drawValue(g, "WCHB", event.getRerolledItems(), event.getRerolledValue(), x + 30, true, scale);
	}

	private void drawMinimal(Graphics2D g, WchbEvent event, double scale)
	{
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, textPx(13, scale)));
		g.setColor(Color.WHITE);
		g.drawString(truncate(event.getSource(), 20), px(52, scale), px(22, scale));
		int x = drawInlineValue(g, event.getItems(), event.getTotalValue(), 190, false, scale);
		g.setColor(MUTED);
		g.drawString("\u2192", px(x + 7, scale), px(22, scale));
		drawInlineValue(g, event.getRerolledItems(), event.getRerolledValue(), x + 27, true, scale);
	}

	private int drawInlineValue(Graphics2D g, List<WchbItem> items, long value, int x, boolean wchb, double scale)
	{
		int valueX = drawItemStrip(g, items, x, 6, 22, scale);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, textPx(12, scale)));
		g.setColor(wchb ? new Color(242, 82, 70) : new Color(232, 234, 237));
		String formatted = formatGp(value);
		g.drawString(formatted, px(valueX + 4, scale), px(22, scale));
		return valueX + 4 + (int) Math.ceil(g.getFontMetrics().stringWidth(formatted) / scale);
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
				if (item == null || item.getId() <= 0) continue;
				BufferedImage image = itemManager.getImage(item.getId(), Math.max(1, item.getQuantity()), false);
				if (image == null) continue;
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
		if (phase > 900L) return;
		float progress = phase / 900f;
		int x = (int) (-100 + progress * (width + 200));
		Composite previous = g.getComposite();
		g.setComposite(AlphaComposite.SrcOver.derive(0.14f));
		g.setPaint(new GradientPaint(x - 55, 0, new Color(255, 255, 255, 0), x + 55, 0, Color.WHITE));
		g.fillRect(x - 55, 0, 110, height);
		g.setComposite(previous);
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
		if (value == null) return "Unknown source";
		return value.length() <= max ? value : value.substring(0, max - 1) + "…";
	}

	private static String fitText(Graphics2D g, String value, int maxWidth)
	{
		if (value == null || value.isEmpty()) return "";
		if (g.getFontMetrics().stringWidth(value) <= maxWidth) return value;
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
		if (value >= 1_000_000_000L) return String.format("%.1fb", value / 1_000_000_000d);
		if (value >= 1_000_000L) return String.format("%.1fm", value / 1_000_000d);
		if (value >= 1_000L) return String.format("%.1fk", value / 1_000d);
		return Long.toString(value);
	}
}
