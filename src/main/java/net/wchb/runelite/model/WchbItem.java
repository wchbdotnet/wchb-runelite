package net.wchb.runelite.model;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

@Getter
public class WchbItem
{
	private int id;
	private String name;
	private int quantity;

	@SerializedName("priceEach")
	private long priceEach;

	private Double rarity;

	@SerializedName("is_pet")
	private boolean pet;
}
