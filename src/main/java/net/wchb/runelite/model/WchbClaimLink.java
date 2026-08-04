package net.wchb.runelite.model;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

@Getter
public class WchbClaimLink
{
	@SerializedName("claim_url")
	private String claimUrl;

	@SerializedName("expires_at")
	private String expiresAt;
}
