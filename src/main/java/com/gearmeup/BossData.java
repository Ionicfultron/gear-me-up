package com.gearmeup;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.EquipmentInventorySlot;

/**
 * Loads {@code bosses.json}: the boss list plus shared knowledge (monster-type bonuses,
 * elemental items, default weapon tiers). Field names match the JSON keys.
 */
public final class BossData
{
	private final List<Boss> bosses;
	private final Knowledge knowledge;

	private BossData(List<Boss> bosses, Knowledge knowledge)
	{
		this.bosses = Collections.unmodifiableList(bosses);
		this.knowledge = knowledge;
	}

	public List<Boss> getBosses()
	{
		return bosses;
	}

	public Knowledge getKnowledge()
	{
		return knowledge;
	}

	static BossData load(Gson gson) throws IOException
	{
		try (InputStream in = BossData.class.getResourceAsStream("bosses.json"))
		{
			if (in == null)
			{
				throw new IOException("bosses.json is missing from the plugin");
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				return parse(gson.fromJson(reader, FileJson.class));
			}
		}
	}

	static BossData parse(FileJson file)
	{
		Knowledge knowledge = new Knowledge(
			nz(file.attributeItems),
			nz(file.elementItems),
			nz(file.styleWeapons),
			nz(file.slayerHelm),
			nz(file.styleGear));

		List<Boss> bosses = new ArrayList<>();
		for (BossJson b : nz(file.bosses))
		{
			List<GearSetup> setups = new ArrayList<>();
			for (SetupJson s : nz(b.setups))
			{
				Map<EquipmentInventorySlot, SlotRule> preferred = rules(s.preferred, false);
				Map<EquipmentInventorySlot, SlotRule> required = rules(s.required, true);
				if (s.weapons != null && !s.weapons.isEmpty() && !required.containsKey(EquipmentInventorySlot.WEAPON))
				{
					preferred.put(EquipmentInventorySlot.WEAPON, new SlotRule(s.weapons, false));
				}
				setups.add(new GearSetup(s.label, CombatStyle.valueOf(s.style.toUpperCase(Locale.ROOT)),
					preferred, required, s.notes == null ? "" : s.notes));
			}
			bosses.add(new Boss(b.name, b.category == null ? "Other" : b.category,
				lower(b.attributes), lower(b.immuneTo),
				b.elementalWeakness == null ? null : b.elementalWeakness.toLowerCase(Locale.ROOT),
				b.elementalWeaknessPercent, b.defence, b.slayerLevel, b.slayerOnly, b.info, nz(b.aliases), setups));
		}
		return new BossData(bosses, knowledge);
	}

	private static Map<EquipmentInventorySlot, SlotRule> rules(Map<String, List<String>> json, boolean required)
	{
		Map<EquipmentInventorySlot, SlotRule> out = new LinkedHashMap<>();
		if (json != null)
		{
			json.forEach((slot, names) ->
			{
				if (names != null && !names.isEmpty())
				{
					out.put(slot(slot), new SlotRule(names, required));
				}
			});
		}
		return out;
	}

	static EquipmentInventorySlot slot(String name)
	{
		switch (name.toLowerCase(Locale.ROOT))
		{
			case "neck":
				return EquipmentInventorySlot.AMULET;
			case "hands":
				return EquipmentInventorySlot.GLOVES;
			case "feet":
				return EquipmentInventorySlot.BOOTS;
			default:
				return EquipmentInventorySlot.valueOf(name.toUpperCase(Locale.ROOT));
		}
	}

	private static List<String> lower(List<String> in)
	{
		List<String> out = new ArrayList<>();
		for (String s : nz(in))
		{
			out.add(s.toLowerCase(Locale.ROOT));
		}
		return out;
	}

	private static <T> List<T> nz(List<T> l)
	{
		return l == null ? Collections.emptyList() : l;
	}

	private static <K, V> Map<K, V> nz(Map<K, V> m)
	{
		return m == null ? Collections.emptyMap() : m;
	}

	/**
	 * Shared item knowledge used for every boss.
	 * attributeItems: attribute -> "melee"/"ranged"/"magic"/"any" -> slot -> items (best first)
	 * elementItems: element -> slot -> items (magic setups only)
	 * styleWeapons: STAB/SLASH/CRUSH/RANGED/MAGIC -> weapons that beat their raw stats (best first)
	 * slayerHelm: "melee"/"ranged"/"magic" -> head items when on task
	 * styleGear: "melee"/"ranged"/"magic" -> slot -> general best-in-slot armour (best first)
	 */
	public static final class Knowledge
	{
		private final Map<String, Map<String, Map<String, List<String>>>> attributeItems;
		private final Map<String, Map<String, List<String>>> elementItems;
		private final Map<String, List<String>> styleWeapons;
		private final Map<String, List<String>> slayerHelm;
		private final Map<String, Map<String, List<String>>> styleGear;

		Knowledge(Map<String, Map<String, Map<String, List<String>>>> attributeItems,
			Map<String, Map<String, List<String>>> elementItems,
			Map<String, List<String>> styleWeapons,
			Map<String, List<String>> slayerHelm,
			Map<String, Map<String, List<String>>> styleGear)
		{
			this.styleGear = styleGear;
			this.attributeItems = attributeItems;
			this.elementItems = elementItems;
			this.styleWeapons = styleWeapons;
			this.slayerHelm = slayerHelm;
		}

		static Knowledge empty()
		{
			return new Knowledge(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
				Collections.emptyMap());
		}

		/** Items for a monster attribute, style group ("melee"/"ranged"/"magic") and slot. "any" applies to every style. */
		List<String> attributeItems(String attribute, String styleGroup, EquipmentInventorySlot slot)
		{
			Map<String, Map<String, List<String>>> byStyle = attributeItems.get(attribute);
			if (byStyle == null)
			{
				return Collections.emptyList();
			}
			List<String> out = new ArrayList<>();
			out.addAll(slotList(byStyle.get(styleGroup), slot));
			out.addAll(slotList(byStyle.get("any"), slot));
			return out;
		}

		List<String> elementItems(String element, EquipmentInventorySlot slot)
		{
			return element == null ? Collections.emptyList() : slotList(elementItems.get(element), slot);
		}

		List<String> styleWeapons(CombatStyle style)
		{
			return styleWeapons.getOrDefault(style.name(), Collections.emptyList());
		}

		/** General best-in-slot armour for a style group ("melee"/"ranged"/"magic"), best first. */
		List<String> styleGear(String styleGroup, EquipmentInventorySlot slot)
		{
			return slotList(styleGear.get(styleGroup), slot);
		}

		List<String> slayerHelm(String styleGroup)
		{
			return slayerHelm.getOrDefault(styleGroup, Collections.emptyList());
		}

		private static List<String> slotList(Map<String, List<String>> bySlot, EquipmentInventorySlot slot)
		{
			if (bySlot == null)
			{
				return Collections.emptyList();
			}
			for (Map.Entry<String, List<String>> e : bySlot.entrySet())
			{
				if (slot(e.getKey()) == slot && e.getValue() != null)
				{
					return e.getValue();
				}
			}
			return Collections.emptyList();
		}
	}

	// ---- JSON shapes (filled by Gson) ----

	static final class FileJson
	{
		Map<String, Map<String, Map<String, List<String>>>> attributeItems;
		Map<String, Map<String, List<String>>> elementItems;
		Map<String, List<String>> styleWeapons;
		Map<String, List<String>> slayerHelm;
		Map<String, Map<String, List<String>>> styleGear;
		List<BossJson> bosses;
	}

	static final class BossJson
	{
		String name;
		String category;
		List<String> attributes;
		List<String> immuneTo;
		String elementalWeakness;
		int elementalWeaknessPercent;
		Map<String, Integer> defence;
		int slayerLevel;
		boolean slayerOnly;
		String info;
		List<String> aliases;
		List<SetupJson> setups;
	}

	static final class SetupJson
	{
		String label;
		String style;
		List<String> weapons;
		Map<String, List<String>> preferred;
		Map<String, List<String>> required;
		String notes;
	}
}
