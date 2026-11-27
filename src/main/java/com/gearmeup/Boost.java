package com.gearmeup;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Combat stat boosts (OSRS Wiki): each raises a level by {@code flat + floor(level * percent / 100)}.
 * Raid-only boosts are made inside the raid, so they're assumed available there and never elsewhere.
 */
public enum Boost
{
	SUPER_COMBAT("Super combat", 5, 15, 5, 15, 0, 0, 0, 0, null,
		Collections.singletonList(Arrays.asList("Super combat potion", "Divine super combat potion"))),
	SUPER_ATTACK_STRENGTH("Super attack + strength", 5, 15, 5, 15, 0, 0, 0, 0, null,
		Arrays.asList(Arrays.asList("Super attack", "Divine super attack"), Arrays.asList("Super strength", "Divine super strength"))),
	SUPER_STRENGTH("Super strength", 0, 0, 5, 15, 0, 0, 0, 0, null,
		Collections.singletonList(Arrays.asList("Super strength", "Divine super strength"))),
	COMBAT("Combat potion", 3, 10, 3, 10, 0, 0, 0, 0, null,
		Collections.singletonList(Collections.singletonList("Combat potion"))),
	RANGING("Ranging potion", 0, 0, 0, 0, 4, 10, 0, 0, null,
		Collections.singletonList(Arrays.asList("Ranging potion", "Divine ranging potion", "Bastion potion", "Divine bastion potion"))),
	SATURATED_HEART("Saturated heart", 0, 0, 0, 0, 0, 0, 4, 10, null,
		Collections.singletonList(Collections.singletonList("Saturated heart"))),
	FORGOTTEN_BREW("Forgotten brew", 0, 0, 0, 0, 0, 0, 3, 8, null,
		Collections.singletonList(Collections.singletonList("Forgotten brew"))),
	IMBUED_HEART("Imbued heart", 0, 0, 0, 0, 0, 0, 1, 10, null,
		Collections.singletonList(Collections.singletonList("Imbued heart"))),
	MAGIC("Magic potion", 0, 0, 0, 0, 0, 0, 4, 0, null,
		Collections.singletonList(Arrays.asList("Magic potion", "Divine magic potion", "Battlemage potion", "Divine battlemage potion"))),
	ANCIENT_BREW("Ancient brew", 0, 0, 0, 0, 0, 0, 2, 5, null,
		Collections.singletonList(Collections.singletonList("Ancient brew"))),
	OVERLOAD_PLUS("Overload (+)", 6, 16, 6, 16, 6, 16, 6, 16, "cox", Collections.emptyList()),
	SMELLING_SALTS("Smelling salts", 11, 16, 11, 16, 11, 16, 11, 16, "toa", Collections.emptyList());

	private final String displayName;
	private final int attackFlat;
	private final int attackPercent;
	private final int strengthFlat;
	private final int strengthPercent;
	private final int rangedFlat;
	private final int rangedPercent;
	private final int magicFlat;
	private final int magicPercent;
	private final String raid;
	/** Every group must be owned (any one name per group). */
	private final List<List<String>> needs;

	Boost(String displayName, int attackFlat, int attackPercent, int strengthFlat, int strengthPercent, int rangedFlat,
		int rangedPercent, int magicFlat, int magicPercent, String raid, List<List<String>> needs)
	{
		this.displayName = displayName;
		this.attackFlat = attackFlat;
		this.attackPercent = attackPercent;
		this.strengthFlat = strengthFlat;
		this.strengthPercent = strengthPercent;
		this.rangedFlat = rangedFlat;
		this.rangedPercent = rangedPercent;
		this.magicFlat = magicFlat;
		this.magicPercent = magicPercent;
		this.raid = raid;
		this.needs = needs;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public int attack(int level)
	{
		return level + attackFlat + level * attackPercent / 100;
	}

	public int strength(int level)
	{
		return level + strengthFlat + level * strengthPercent / 100;
	}

	public int ranged(int level)
	{
		return level + rangedFlat + level * rangedPercent / 100;
	}

	public int magic(int level)
	{
		return level + magicFlat + level * magicPercent / 100;
	}

	/** True if this boost raises a stat used by the style group ("melee", "ranged" or "magic"). */
	public boolean helps(String group)
	{
		switch (group)
		{
			case "melee":
				return attackFlat + attackPercent + strengthFlat + strengthPercent > 0;
			case "ranged":
				return rangedFlat + rangedPercent > 0;
			default:
				return magicFlat + magicPercent > 0;
		}
	}

	/** "cox" or "toa" for boosts that only exist in that raid; null otherwise. */
	public String getRaid()
	{
		return raid;
	}

	public List<List<String>> getNeeds()
	{
		return needs;
	}

	/** The item whose icon represents this boost (first name of the first group), or null. */
	public String getIconItem()
	{
		return needs.isEmpty() ? null : needs.get(0).get(0);
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
