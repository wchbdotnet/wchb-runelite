package net.wchb.runelite;

import com.google.gson.Gson;
import net.wchb.runelite.model.WchbEvent;
import net.wchb.runelite.model.WchbFeed;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WchbModelTest
{
	private final Gson gson = new Gson();

	@Test
	public void formatsGpValuesConsistently()
	{
		assertEquals("999", WchbOverlay.formatGp(999));
		assertEquals("1.0k", WchbOverlay.formatGp(1_000));
		assertEquals("61.0k", WchbOverlay.formatGp(61_000));
		assertEquals("61.0m", WchbOverlay.formatGp(61_000_000));
		assertEquals("1.1b", WchbOverlay.formatGp(1_100_000_000));
	}

	@Test
	public void prependsDeduplicatesAndLimitsLiveEvents()
	{
		WchbFeed feed = gson.fromJson(
			"{\"events\":[{\"id\":\"8\"},{\"id\":\"7\"},{\"id\":\"6\"},{\"id\":\"5\"},"
				+ "{\"id\":\"4\"},{\"id\":\"3\"},{\"id\":\"2\"},{\"id\":\"1\"}]}",
			WchbFeed.class);
		WchbEvent event = gson.fromJson("{\"id\":\"7\"}", WchbEvent.class);

		feed.prependEvent(event);

		assertEquals(8, feed.getEvents().size());
		assertEquals("7", feed.getEvents().get(0).getId());
		assertEquals("8", feed.getEvents().get(1).getId());
		assertEquals("1", feed.getEvents().get(7).getId());
	}
}
