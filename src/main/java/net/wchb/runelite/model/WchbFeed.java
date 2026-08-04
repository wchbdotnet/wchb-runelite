package net.wchb.runelite.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.Getter;

@Getter
public class WchbFeed
{
	@SerializedName("profile_id")
	private String profileId;

	@SerializedName("profile_status")
	private String profileStatus;

	@SerializedName("dink_connected")
	private boolean dinkConnected;

	@SerializedName("webhook_url")
	private String webhookUrl;

	@SerializedName("player_name")
	private String playerName;

	@SerializedName("account_type")
	private String accountType;

	@SerializedName("live_url")
	private String liveUrl;

	private volatile List<WchbEvent> events = Collections.emptyList();

	public synchronized void prependEvent(WchbEvent event)
	{
		List<WchbEvent> updated = new ArrayList<>();
		updated.add(event);
		for (WchbEvent existing : getEvents())
		{
			if (!Objects.equals(event.getId(), existing.getId()) && updated.size() < 8)
			{
				updated.add(existing);
			}
		}
		events = Collections.unmodifiableList(updated);
	}

	public void markDinkConnected()
	{
		dinkConnected = true;
	}

	public List<WchbEvent> getEvents()
	{
		List<WchbEvent> current = events;
		return current == null ? Collections.emptyList() : current;
	}
}
