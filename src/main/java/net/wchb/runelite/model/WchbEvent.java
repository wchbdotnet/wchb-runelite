package net.wchb.runelite.model;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

@Getter
public class WchbEvent
{
	private String id;
	private String source;

	@SerializedName("created_date")
	private String createdDate;

	private List<WchbItem> items = Collections.emptyList();

	@SerializedName("total_value")
	private long totalValue;

	@SerializedName("rerolled_items")
	private List<WchbItem> rerolledItems = Collections.emptyList();

	@SerializedName("rerolled_value")
	private long rerolledValue;

	@SerializedName("reroll_nothing")
	private boolean rerollNothing;

	@SerializedName("highlight_type")
	private String highlightType = "normal";
}
