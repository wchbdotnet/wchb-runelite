package net.wchb.runelite.model;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

@Getter
public class WchbRegistration
{
	private boolean ok;

	@SerializedName("profile_id")
	private String profileId;

	@SerializedName("profile_status")
	private String profileStatus;

	@SerializedName("player_name")
	private String playerName;

	@SerializedName("dink_connected")
	private boolean dinkConnected;

	@SerializedName("webhook_url")
	private String webhookUrl;
}
