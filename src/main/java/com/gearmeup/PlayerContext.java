package com.gearmeup;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What the DPS maths needs to know about the player: levels, unlocked prayers and owned consumables.
 * Built on the client thread, then read anywhere.
 */
public final class PlayerContext
{
	private final int attack;
	private final int strength;
	private final int ranged;
	private final int magic;
	private final int prayer;
	private final int[] current;
	private final Set<OffensivePrayer.Unlock> unlocks;
	private final Map<String, Long> owned;
	private final Map<String, Integer> itemIds;

	/**
	 * @param levels  real Attack, Strength, Ranged, Magic, Prayer levels
	 * @param current current (boosted) Attack, Strength, Ranged, Magic levels
	 * @param owned   item name -> quantity owned (any case)
	 * @param itemIds item name -> item id, for icons
	 */
	public PlayerContext(int[] levels, int[] current, Set<OffensivePrayer.Unlock> unlocks, Map<String, Long> owned,
		Map<String, Integer> itemIds)
	{
		this.attack = levels[0];
		this.strength = levels[1];
		this.ranged = levels[2];
		this.magic = levels[3];
		this.prayer = levels[4];
		this.current = current.clone();
		this.unlocks = unlocks.isEmpty() ? EnumSet.noneOf(OffensivePrayer.Unlock.class) : EnumSet.copyOf(unlocks);
		this.unlocks.add(OffensivePrayer.Unlock.NONE);
		Map<String, Long> o = new HashMap<>();
		owned.forEach((k, v) -> o.merge(norm(k), v, Long::sum));
		this.owned = o;
		Map<String, Integer> ids = new HashMap<>();
		itemIds.forEach((k, v) -> ids.put(norm(k), v));
		this.itemIds = ids;
	}

	/** Maxed account with every prayer, for tests. */
	static PlayerContext maxed()
	{
		return new PlayerContext(new int[]{99, 99, 99, 99, 99}, new int[]{99, 99, 99, 99},
			EnumSet.allOf(OffensivePrayer.Unlock.class), Collections.emptyMap(), Collections.emptyMap());
	}

	public int getAttack()
	{
		return attack;
	}

	public int getStrength()
	{
		return strength;
	}

	public int getRanged()
	{
		return ranged;
	}

	public int getMagic()
	{
		return magic;
	}

	/** Current (boosted or drained) levels: Attack, Strength, Ranged, Magic. */
	public int current(int index)
	{
		return current[index];
	}

	public boolean canUse(OffensivePrayer p)
	{
		return prayer >= p.getLevel() && unlocks.contains(p.getUnlock());
	}

	/** True if the player owns at least one of the names (matched like item names: case and spaces ignored, "contains"). */
	public boolean ownsAny(Iterable<String> names)
	{
		for (String n : names)
		{
			String q = norm(n);
			for (Map.Entry<String, Long> e : owned.entrySet())
			{
				if (e.getValue() > 0 && e.getKey().contains(q))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** Item id of an owned item whose name contains the fragment, or -1. */
	public int itemId(String fragment)
	{
		String q = norm(fragment);
		for (Map.Entry<String, Integer> e : itemIds.entrySet())
		{
			if (e.getKey().contains(q))
			{
				return e.getValue();
			}
		}
		return -1;
	}

	private static String norm(String s)
	{
		return s.toLowerCase(Locale.ROOT).replace(" ", "");
	}
}
