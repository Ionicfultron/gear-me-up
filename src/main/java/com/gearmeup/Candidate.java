package com.gearmeup;

import net.runelite.client.game.ItemEquipmentStats;

/**
 * An equippable item the player owns, with its stats and where it was found.
 */
public final class Candidate
{
	private final int itemId;
	private final String name;
	private final ItemSource source;
	private final ItemEquipmentStats stats;

	public Candidate(int itemId, String name, ItemSource source, ItemEquipmentStats stats)
	{
		this.itemId = itemId;
		this.name = name;
		this.source = source;
		this.stats = stats;
	}

	public int getItemId()
	{
		return itemId;
	}

	public String getName()
	{
		return name;
	}

	public ItemSource getSource()
	{
		return source;
	}

	public ItemEquipmentStats getStats()
	{
		return stats;
	}

	public int getSlot()
	{
		return stats.getSlot();
	}

	/** Case- and space-insensitive "contains", so "Salve amulet (e)" matches "Salve amulet(e)". */
	public boolean nameContains(String fragment)
	{
		return normalise(name).contains(normalise(fragment));
	}

	private static String normalise(String s)
	{
		return s.toLowerCase(java.util.Locale.ROOT).replace(" ", "").replace(" ", "");
	}
}
