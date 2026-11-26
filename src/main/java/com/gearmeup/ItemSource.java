package com.gearmeup;

import java.awt.Color;

/**
 * Where an owned item currently is. Lower ordinal = more convenient, so it wins when the
 * same item is found in several places.
 */
public enum ItemSource
{
	EQUIPPED("Equipped", new Color(0x4CAF50)),
	INVENTORY("Inventory", new Color(0xFFC107)),
	BANK("Bank", new Color(0xFF9800));

	private final String displayName;
	private final Color color;

	ItemSource(String displayName, Color color)
	{
		this.displayName = displayName;
		this.color = color;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public Color getColor()
	{
		return color;
	}
}
