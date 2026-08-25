package net.wchb.runelite;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(WchbConfig.GROUP)
public interface WchbConfig extends Config
{
	String GROUP = "wchb-companion";

	@ConfigItem(
		keyName = "connectToWchb",
		name = "Connect to WCHB",
		description = "Fetch your processed WCHB rerolls from wchb.net",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default boolean connectToWchb()
	{
		return false;
	}

	@ConfigItem(
		keyName = "connectionToken",
		name = "WCHB connection token",
		description = "Links this RuneLite profile to your WCHB profile. Normally configured through the WCHB setup flow.",
		secret = true
	)
	default String connectionToken()
	{
		return "";
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show overlay",
		description = "Display the latest WCHB reroll in the game view"
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "temporaryOverlay",
		name = "Temporary overlay",
		description = "Only show the selected overlay when a new reroll arrives, then fade it away"
	)
	default boolean temporaryOverlay()
	{
		return false;
	}

	@Range(min = 60, max = 140)
	@ConfigItem(
		keyName = "overlayScale",
		name = "Overlay size",
		description = "Scale the WCHB overlay from 60% to 140%"
	)
	default int overlayScale()
	{
		return 100;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "overlayOpacity",
		name = "Overlay opacity",
		description = "Set the opacity of the entire WCHB overlay from 0% to 100%"
	)
	default int overlayOpacity()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "minimalOverlay",
		name = "Minimal overlay",
		description = "Show a single-line overlay with only the logo, boss, drop, and WCHB roll"
	)
	default boolean minimalOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overlayStyle",
		name = "Overlay style",
		description = "Choose the overlay presentation. Default keeps only the WCHB medallion visible between rerolls."
	)
	default WchbOverlayStyle overlayStyle()
	{
		return WchbOverlayStyle.DRAWER;
	}

	@ConfigItem(
		keyName = "unlockOverlay",
		name = "Unlock overlay movement",
		description = "Allow the WCHB overlay to be dragged. Its position is saved by RuneLite for this profile."
	)
	default boolean unlockOverlay()
	{
		return false;
	}

}
