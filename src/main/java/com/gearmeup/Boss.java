package com.gearmeup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A boss from bosses.json: its weaknesses and one or more gear setups.
 */
public final class Boss
{
	private final String name;
	private final String category;
	private final List<String> attributes;
	private final List<String> immuneTo;
	private final String elementalWeakness;
	private final int elementalWeaknessPercent;
	private final Map<String, Integer> defence;
	private final int slayerLevel;
	private final boolean slayerOnly;
	private final String info;
	private final List<String> searchTerms = new ArrayList<>();
	private final List<GearSetup> setups;
	private final Combat combat;

	/** The boss's combat stats (null when unknown, e.g. the generic entry). */
	public static final class Combat
	{
		private final int hitpoints;
		private final int defenceLevel;
		private final int magicLevel;
		private final int size;
		private final int flatArmour;
		private final String target;

		Combat(int hitpoints, int defenceLevel, int magicLevel, int size, int flatArmour, String target)
		{
			this.hitpoints = hitpoints;
			this.defenceLevel = defenceLevel;
			this.magicLevel = magicLevel;
			this.size = size;
			this.flatArmour = flatArmour;
			this.target = target;
		}

		public int getHitpoints()
		{
			return hitpoints;
		}

		public int getDefenceLevel()
		{
			return defenceLevel;
		}

		public int getMagicLevel()
		{
			return magicLevel;
		}

		public int getSize()
		{
			return size;
		}

		public int getFlatArmour()
		{
			return flatArmour;
		}

		/** e.g. "vs phase 3" when the stats are for one form of the boss; may be null. */
		public String getTarget()
		{
			return target;
		}
	}

	Boss(String name, String category, List<String> attributes, List<String> immuneTo, String elementalWeakness,
		int elementalWeaknessPercent, Map<String, Integer> defence, int slayerLevel, boolean slayerOnly, String info,
		List<String> aliases, List<GearSetup> setups, Combat combat)
	{
		this.combat = combat;
		this.name = name;
		this.category = category;
		this.attributes = Collections.unmodifiableList(attributes);
		this.immuneTo = Collections.unmodifiableList(immuneTo);
		this.elementalWeakness = elementalWeakness == null || elementalWeakness.equals("none") ? null : elementalWeakness;
		this.elementalWeaknessPercent = elementalWeaknessPercent;
		this.defence = defence == null ? Collections.emptyMap() : Collections.unmodifiableMap(defence);
		this.slayerLevel = slayerLevel;
		this.slayerOnly = slayerOnly;
		this.info = info == null ? "" : info;
		this.setups = Collections.unmodifiableList(setups);
		searchTerms.add(simplify(name));
		for (String alias : aliases)
		{
			searchTerms.add(simplify(alias));
		}
		for (GearSetup s : setups)
		{
			s.setBoss(this);
		}
	}

	public String getName()
	{
		return name;
	}

	public String getCategory()
	{
		return category;
	}

	public List<String> getAttributes()
	{
		return attributes;
	}

	public String getElementalWeakness()
	{
		return elementalWeakness;
	}

	public int getElementalWeaknessPercent()
	{
		return elementalWeaknessPercent;
	}

	public boolean isSlayerOnly()
	{
		return slayerOnly;
	}

	public List<GearSetup> getSetups()
	{
		return setups;
	}

	public Combat getCombat()
	{
		return combat;
	}

	public List<String> getImmuneTo()
	{
		return immuneTo;
	}

	/** Defence bonus for a key (stab, slash, crush, magic, rangedLight, rangedStandard, rangedHeavy); 0 if unknown. */
	public int getDefenceBonus(String key)
	{
		Integer v = defence.get(key);
		return v == null ? 0 : v;
	}

	/** "cox", "tob" or "toa" when the boss is fought in a raid; null otherwise. */
	public String getRaid()
	{
		String n = name.toLowerCase(Locale.ROOT);
		if (n.contains("chambers of xeric") || n.contains("great olm"))
		{
			return "cox";
		}
		if (n.contains("theatre of blood") || n.contains("verzik"))
		{
			return "tob";
		}
		if (n.contains("tombs of amascut") || n.contains("warden") || n.equals("akkha") || n.equals("zebak")
			|| n.equals("kephri") || n.equals("ba-ba"))
		{
			return "toa";
		}
		return null;
	}

	/** Demonbane effectiveness in percent (OSRS Wiki): 100 for most demons. */
	public int getDemonbaneVulnerability()
	{
		switch (name)
		{
			case "Duke Sucellus":
				return 70;
			case "Ocellus":
			case "Xarth":
				return 80;
			case "Dreadnoth the Jellied":
				return 25;
			case "Yama":
				return 120;
			default:
				return 100;
		}
	}

	/** A few NPCs (Verzik) use their Defence level instead of Magic level against spells. */
	public boolean usesDefenceAgainstMagic()
	{
		return name.equals("Verzik Vitur") || name.equals("Theatre of Blood");
	}

	/** True inside a raid, where raid-only boosts (overloads, smelling salts) work. */
	public boolean isRaid()
	{
		return category.startsWith("Raid");
	}

	/** File name of the bundled picture (npc/&lt;slug&gt;.png). Must match slug() in tools/fetch_images.py. */
	public String getImageSlug()
	{
		return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
	}

	/** A short line about the boss: type, weaknesses, immunities, slayer requirement. */
	public String describe()
	{
		List<String> parts = new ArrayList<>();
		if (!attributes.isEmpty())
		{
			parts.add("Type: " + String.join(", ", capitalise(attributes)));
		}
		String weakest = weakestMelee();
		if (weakest != null)
		{
			parts.add("Weakest melee: " + weakest);
		}
		String weakestRanged = weakestRanged();
		if (weakestRanged != null)
		{
			parts.add("Weakest ranged: " + weakestRanged);
		}
		if (elementalWeakness != null)
		{
			parts.add("Weak to " + elementalWeakness + " spells"
				+ (elementalWeaknessPercent > 0 ? " (+" + elementalWeaknessPercent + "%)" : ""));
		}
		if (!immuneTo.isEmpty())
		{
			parts.add("Immune to " + String.join(", ", immuneTo));
		}
		if (slayerLevel > 0)
		{
			parts.add(slayerLevel + " Slayer" + (slayerOnly ? ", task only" : ""));
		}
		if (!info.isEmpty())
		{
			parts.add(info);
		}
		return String.join(". ", parts) + (parts.isEmpty() ? "" : ".");
	}

	private String weakestMelee()
	{
		return lowest("stab", "slash", "crush");
	}

	private String weakestRanged()
	{
		String key = lowest("rangedLight", "rangedStandard", "rangedHeavy");
		if (key == null)
		{
			return null;
		}
		switch (key)
		{
			case "rangedlight":
				return "light (darts, knives, thrown)";
			case "rangedstandard":
				return "standard (bows/arrows)";
			default:
				return "heavy (crossbows/bolts)";
		}
	}

	/** The key with the lowest defence among those present, or null if fewer than two are known or all equal. */
	private String lowest(String... keys)
	{
		String best = null;
		int bestValue = Integer.MAX_VALUE;
		int known = 0;
		boolean allEqual = true;
		Integer first = null;
		for (String k : keys)
		{
			Integer v = defence.get(k);
			if (v == null)
			{
				continue;
			}
			known++;
			if (first == null)
			{
				first = v;
			}
			else if (!first.equals(v))
			{
				allEqual = false;
			}
			if (v < bestValue)
			{
				bestValue = v;
				best = k.toLowerCase(Locale.ROOT);
			}
		}
		return known < 2 || allEqual ? null : best;
	}

	private static List<String> capitalise(List<String> words)
	{
		List<String> out = new ArrayList<>();
		for (String w : words)
		{
			out.add(w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1));
		}
		return out;
	}

	/**
	 * True if the search text matches the name or a nickname, ignoring case, spaces and punctuation
	 * (so "kril" finds K'ril Tsutsaroth and "tob" finds Theatre of Blood).
	 */
	public boolean matches(String query)
	{
		String q = simplify(query);
		if (q.isEmpty())
		{
			return true;
		}
		for (String term : searchTerms)
		{
			if (term.contains(q))
			{
				return true;
			}
		}
		return false;
	}

	/** How well the query matches: 0 = name starts with it, 1 = name contains it, 2 = nickname only. */
	public int matchRank(String query)
	{
		String q = simplify(query);
		String n = searchTerms.get(0);
		return n.startsWith(q) ? 0 : n.contains(q) ? 1 : 2;
	}

	private static String simplify(String s)
	{
		return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	@Override
	public String toString()
	{
		return name;
	}
}
