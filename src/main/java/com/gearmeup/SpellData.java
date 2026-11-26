package com.gearmeup;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Spells, charged weapons and rune sources from {@code spells.json} (OSRS Wiki data).
 * Field names match the JSON keys.
 */
public final class SpellData
{
	List<Spell> spells;
	List<ChargedWeapon> chargedWeapons;
	List<RuneSource> runeSources;
	/** Combination rune -> the elemental runes it counts as. */
	Map<String, List<String>> comboRunes;
	List<String> autocastAncients;
	List<String> autocastArceuus;
	/** e.g. "Sunfire rune" -> counts as "Fire rune". */
	Map<String, Substitute> runeSubstitutes;

	static SpellData load(Gson gson) throws IOException
	{
		try (InputStream in = SpellData.class.getResourceAsStream("spells.json"))
		{
			if (in == null)
			{
				throw new IOException("spells.json is missing from the plugin");
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				SpellData d = gson.fromJson(reader, SpellData.class);
				d.fillNulls();
				return d;
			}
		}
	}

	void fillNulls()
	{
		if (spells == null)
		{
			spells = Collections.emptyList();
		}
		if (chargedWeapons == null)
		{
			chargedWeapons = Collections.emptyList();
		}
		if (runeSources == null)
		{
			runeSources = Collections.emptyList();
		}
		if (comboRunes == null)
		{
			comboRunes = Collections.emptyMap();
		}
		if (autocastAncients == null)
		{
			autocastAncients = Collections.emptyList();
		}
		if (autocastArceuus == null)
		{
			autocastArceuus = Collections.emptyList();
		}
		if (runeSubstitutes == null)
		{
			runeSubstitutes = Collections.emptyMap();
		}
	}

	static final class Spell
	{
		String name;
		String book;
		int level;
		String element;
		int maxHit;
		Map<String, Integer> runes;
		String notes;
	}

	static final class ChargedWeapon
	{
		String name;
		Map<String, Integer> perCharge;
		String notes;
	}

	static final class Substitute
	{
		String countsAs;
		String notes;
	}

	static final class RuneSource
	{
		String item;
		List<String> provides;
		String notes;
	}
}
