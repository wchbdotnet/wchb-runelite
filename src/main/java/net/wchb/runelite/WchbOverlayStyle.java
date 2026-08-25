package net.wchb.runelite;

public enum WchbOverlayStyle
{
	DRAWER("Default", 280, 46, 0, 20, 0, 0, 0),
	FLOATING("Always Visible", 280, 46, 0, 20, 0, 0, 0),
	VERSUS("Vs", 330, 54, 0, 17, 0, 0, 0),
	CLASSIC("Classic", 350, 34, 42, 24, 22, 10, 6);

	private final String displayName;
	private final int baseWidth;
	private final int height;
	private final int brandWidth;
	private final int extraItemWidth;
	private final int logoSize;
	private final int logoX;
	private final int logoY;

	WchbOverlayStyle(String displayName, int baseWidth, int height, int brandWidth,
		int extraItemWidth, int logoSize, int logoX, int logoY)
	{
		this.displayName = displayName;
		this.baseWidth = baseWidth;
		this.height = height;
		this.brandWidth = brandWidth;
		this.extraItemWidth = extraItemWidth;
		this.logoSize = logoSize;
		this.logoX = logoX;
		this.logoY = logoY;
	}

	int getBaseWidth()
	{
		return baseWidth;
	}

	int getHeight()
	{
		return height;
	}

	int getBrandWidth()
	{
		return brandWidth;
	}

	int getExtraItemWidth()
	{
		return extraItemWidth;
	}

	int getLogoSize()
	{
		return logoSize;
	}

	int getLogoX()
	{
		return logoX;
	}

	int getLogoY()
	{
		return logoY;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
