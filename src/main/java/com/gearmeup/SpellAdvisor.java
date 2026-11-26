package com.gearmeup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Picks the spell to autocast with the suggested magic weapon (or, for powered staves, shows what a
 * charge costs) and works out the runes needed and how many the player owns. Pure logic.
 */
public final class SpellAdvisor
{
	public static final int BOOK_STANDARD = 0;
	public static final int BOOK_ANCIENT = 1;
	public static final int BOOK_LUNAR = 2;
	public static final int BOOK_ARCEUUS = 3;

	private final SpellData data;

	public SpellAdvisor(SpellData data)
	{
		this.data = data;
	}

	/**
	 * @param weapon      suggested weapon (null = nothing to advise)
	 * @param shield      suggested shield (tomes can supply runes)
	 * @param boss        the boss (for elemental weakness and demon type); may be null
	 * @param magicLevel  the player's Magic level
	 * @param spellbook   current spellbook (BOOK_*), or -1 if unknown
	 * @param owned       item name (any case/spacing) -> quantity owned across bank, inventory and equipment
	 */
	public SpellAdvice advise(Candidate weapon, Candidate shield, Boss boss, int magicLevel, int spellbook, Map<String, Long> owned)
	{
		if (weapon == null)
		{
			return null;
		}
		Map<String, Long> have = normaliseKeys(owned);

		// Powered staves and charged weapons: show what one charge costs.
		for (SpellData.ChargedWeapon cw : data.chargedWeapons)
		{
			if (weapon.nameContains(cw.name))
			{
				List<String> notes = new ArrayList<>();
				notes.add("Uses its own built-in spell, so no spell to pick. The runes shown recharge it.");
				if (cw.notes != null && !cw.notes.isEmpty())
				{
					notes.add(cw.notes);
				}
				return build(cw.name + " charges", true, cw.perCharge, new HashSet<>(), have, notes);
			}
		}

		String weakness = boss == null ? null : boss.getElementalWeakness();
		int weakPercent = boss == null ? 0 : boss.getElementalWeaknessPercent();
		boolean demon = boss != null && boss.getAttributes().contains("demon");

		SpellData.Spell spell = null;
		String book = "standard";
		if (demon && listContains(data.autocastArceuus, weapon))
		{
			spell = best(magicLevel, "arceuus", null, 0, true);
			book = "arceuus";
		}
		// The Harmonised nightmare staff is standard-only, even though its name contains "Nightmare staff".
		boolean ancients = listContains(data.autocastAncients, weapon) && !weapon.nameContains("Harmonised");
		if (spell == null && spellbook == BOOK_ANCIENT && ancients)
		{
			spell = best(magicLevel, "ancient", null, 0, false);
			book = "ancient";
		}
		if (spell == null)
		{
			spell = best(magicLevel, "standard", weakness, weakPercent, false);
			book = "standard";
		}
		if (spell == null)
		{
			return null;
		}

		List<String> notes = new ArrayList<>();
		if (spell.element != null && spell.element.equalsIgnoreCase(weakness == null ? "" : weakness))
		{
			notes.add("Uses the boss's " + weakness + " weakness (+" + weakPercent + "% accuracy and damage).");
		}
		int wantBook = bookId(book);
		if (spellbook >= 0 && spellbook != wantBook)
		{
			notes.add("Switch to the " + capitalise(book) + " spellbook to cast this.");
		}
		if (spell.notes != null && !spell.notes.isEmpty())
		{
			notes.add(spell.notes);
		}
		notes.add("Runes in your rune pouch aren't counted.");

		Set<String> free = freeRunes(weapon, shield);
		String title = spell.name + " (" + capitalise(book) + ", level " + spell.level
			+ (spell.maxHit > 0 ? ", max hit up to " + spell.maxHit : "") + ")";
		return build(title, false, spell.runes, free, have, notes);
	}

	/** Highest-value spell of a book the player can cast. Elemental weakness boosts matching spells. */
	private SpellData.Spell best(int magicLevel, String book, String weakness, int weakPercent, boolean demonbaneOnly)
	{
		SpellData.Spell best = null;
		double bestValue = -1;
		for (SpellData.Spell s : data.spells)
		{
			if (s.book == null || !s.book.equalsIgnoreCase(book) || s.level > magicLevel || s.runes == null)
			{
				continue;
			}
			if (demonbaneOnly && !s.name.toLowerCase(Locale.ROOT).contains("demonbane"))
			{
				continue;
			}
			// Standard book: only the elemental Strike/Bolt/Blast/Wave/Surge line.
			if (book.equals("standard") && (s.element == null || s.element.isEmpty() || s.element.equals("null")))
			{
				continue;
			}
			double value = s.maxHit;
			if (weakness != null && weakness.equalsIgnoreCase(s.element))
			{
				value *= 1 + weakPercent / 100.0;
			}
			if (best == null || value > bestValue || (value == bestValue && s.level > best.level))
			{
				best = s;
				bestValue = value;
			}
		}
		return best;
	}

	private SpellAdvice build(String title, boolean charges, Map<String, Integer> costs, Set<String> free,
		Map<String, Long> have, List<String> notes)
	{
		List<SpellAdvice.Line> lines = new ArrayList<>();
		long casts = Long.MAX_VALUE;
		for (Map.Entry<String, Integer> e : costs.entrySet())
		{
			String rune = e.getKey();
			int per = e.getValue();
			boolean isFree = free.contains(norm(rune));
			long owned = count(rune, have);
			lines.add(new SpellAdvice.Line(rune, per, isFree, owned));
			if (!isFree && per > 0)
			{
				casts = Math.min(casts, owned / per);
			}
		}
		return new SpellAdvice(title, charges, lines, casts == Long.MAX_VALUE ? -1 : casts, notes);
	}

	/** Owned amount of a rune, including combination runes that count as it. */
	private long count(String rune, Map<String, Long> have)
	{
		long total = have.getOrDefault(norm(rune), 0L);
		for (Map.Entry<String, List<String>> combo : data.comboRunes.entrySet())
		{
			for (String counts : combo.getValue())
			{
				if (norm(counts).equals(norm(rune)))
				{
					total += have.getOrDefault(norm(combo.getKey()), 0L);
					break;
				}
			}
		}
		for (Map.Entry<String, SpellData.Substitute> sub : data.runeSubstitutes.entrySet())
		{
			if (sub.getValue().countsAs != null && norm(sub.getValue().countsAs).equals(norm(rune)))
			{
				total += have.getOrDefault(norm(sub.getKey()), 0L);
			}
		}
		return total;
	}

	private Set<String> freeRunes(Candidate weapon, Candidate shield)
	{
		Set<String> free = new HashSet<>();
		for (SpellData.RuneSource src : data.runeSources)
		{
			if (src.provides == null)
			{
				continue;
			}
			if (weapon.nameContains(src.item) || (shield != null && shield.nameContains(src.item)))
			{
				for (String r : src.provides)
				{
					free.add(norm(r));
				}
			}
		}
		return free;
	}

	private static boolean listContains(List<String> names, Candidate weapon)
	{
		for (String n : names)
		{
			if (weapon.nameContains(n))
			{
				return true;
			}
		}
		return false;
	}

	private static int bookId(String book)
	{
		switch (book)
		{
			case "ancient":
				return BOOK_ANCIENT;
			case "arceuus":
				return BOOK_ARCEUUS;
			default:
				return BOOK_STANDARD;
		}
	}

	private static Map<String, Long> normaliseKeys(Map<String, Long> in)
	{
		Map<String, Long> out = new java.util.HashMap<>();
		in.forEach((k, v) -> out.merge(norm(k), v, Long::sum));
		return out;
	}

	static String norm(String s)
	{
		return s.toLowerCase(Locale.ROOT).replace(" ", "");
	}

	private static String capitalise(String s)
	{
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
