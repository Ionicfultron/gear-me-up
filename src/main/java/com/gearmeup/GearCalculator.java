package com.gearmeup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemEquipmentStats;

/**
 * Picks the best owned item for every slot. Pure logic with no client access.
 *
 * <p>For each slot the first owned item from these lists wins, in order:
 * required items, slayer helmet (when on task), the boss's own preferences, items that are strong
 * against the boss's monster type (demon, dragon, undead...), elemental items (magic only), the
 * style's weapon tier list. If none are owned, items are scored by stats for the combat style.</p>
 */
public final class GearCalculator
{
	private static final List<EquipmentInventorySlot> ARMOUR_SLOTS = Arrays.asList(
		EquipmentInventorySlot.AMULET,
		EquipmentInventorySlot.HEAD,
		EquipmentInventorySlot.CAPE,
		EquipmentInventorySlot.BODY,
		EquipmentInventorySlot.LEGS,
		EquipmentInventorySlot.GLOVES,
		EquipmentInventorySlot.BOOTS,
		EquipmentInventorySlot.RING);

	private static final String[] PROJECTILE_WORDS = {"arrow", "bolt", "javelin", "dart", "knife", "chinchompa", "thrownaxe"};
	private static final String[] NEEDS_CHARGES = {"uncharged", "(empty)", "(inactive)",
		"Craw's bow (u)", "Webweaver bow (u)", "Viggora's chainmace (u)", "Ursine chainmace (u)",
		"Thammaron's sceptre (u)", "Accursed sceptre (u)"};

	/** Not usable in normal gameplay (leagues, minigames, Gauntlet) - never suggested. */
	private static final String[] ALWAYS_EXCLUDED = {"(Last Man Standing)", "(Deadman)", "(Pete Kayer", "(basic)",
		"(attuned)", "(perfected)", "Drygore blowpipe", "Infernal tecpatl", "Nature's recurve", "Shadowflame quadrant",
		"Lithic sceptre", "Fang of the hound", "V's helm", "Searing boots", "Demonic sceptre"};

	/** Powered staves cast their own spell, so elemental tomes don't help them. */
	private static final String[] POWERED_STAVES = {"Tumeken's shadow", "Eye of ayak", "Zorya's tome", "Sanguinesti staff",
		"Trident of the", "Accursed sceptre", "Thammaron's sceptre", "Warped sceptre", "Bone staff"};

	private static final List<String> CRYSTAL_ARMOUR = Arrays.asList("Crystal helm", "Crystal body", "Crystal legs");

	/** Bows that can fire dragon arrows; crossbows that can fire dragon bolts. */
	private static final String[] DRAGON_ARROW_BOWS = {"Twisted bow", "Dark bow", "Venator bow", "Scorching bow", "3rd age bow"};
	private static final String[] DRAGON_BOLT_CROSSBOWS = {"Zaryte crossbow", "Armadyl crossbow", "Dragon crossbow", "Dragon hunter crossbow"};

	private final GearSetup setup;
	private final CombatStyle style;
	private final boolean onSlayerTask;
	private final BossData.Knowledge knowledge;
	private final Map<Integer, List<Candidate>> bySlot = new HashMap<>();
	private final Map<EquipmentInventorySlot, Candidate> picks = new EnumMap<>(EquipmentInventorySlot.class);
	private final List<String> warnings = new ArrayList<>();
	private Candidate forcedWeapon;

	private GearCalculator(GearSetup setup, Collection<Candidate> owned, boolean onSlayerTask, List<String> ignored,
		BossData.Knowledge knowledge)
	{
		this.setup = setup;
		this.style = setup.getStyle();
		this.onSlayerTask = onSlayerTask;
		this.knowledge = knowledge == null ? BossData.Knowledge.empty() : knowledge;
		for (Candidate c : owned)
		{
			if (ignored.stream().anyMatch(c::nameContains) || containsAny(c, ALWAYS_EXCLUDED))
			{
				continue;
			}
			bySlot.computeIfAbsent(c.getSlot(), k -> new ArrayList<>()).add(c);
		}
	}

	public static GearResult calculate(GearSetup setup, Collection<Candidate> owned, boolean onSlayerTask,
		List<String> ignoredNames, BossData.Knowledge knowledge)
	{
		return calculate(setup, owned, onSlayerTask, ignoredNames, knowledge, null);
	}

	/** As above, but builds the setup around the given weapon (from {@link #weaponOptions}). */
	public static GearResult calculate(GearSetup setup, Collection<Candidate> owned, boolean onSlayerTask,
		List<String> ignoredNames, BossData.Knowledge knowledge, Candidate weapon)
	{
		GearCalculator calc = new GearCalculator(setup, owned, onSlayerTask, ignoredNames, knowledge);
		calc.forcedWeapon = weapon;
		calc.run();
		return new GearResult(setup, calc.picks, calc.warnings);
	}

	/**
	 * Owned weapons worth trying for this setup, best stat score first (at most {@code limit}).
	 * Empty when the boss requires a specific weapon.
	 */
	public static List<Candidate> weaponOptions(GearSetup setup, Collection<Candidate> owned, List<String> ignoredNames,
		BossData.Knowledge knowledge, int limit)
	{
		if (setup.getRequired().containsKey(EquipmentInventorySlot.WEAPON))
		{
			return Collections.emptyList();
		}
		GearCalculator calc = new GearCalculator(setup, owned, false, ignoredNames, knowledge);
		return calc.candidates(EquipmentInventorySlot.WEAPON).stream()
			.filter(calc::suitsStyle)
			.sorted(Comparator.comparingDouble(calc::weaponScore).reversed())
			.limit(limit)
			.collect(Collectors.toList());
	}

	private void run()
	{
		pickWeaponAndShield();
		pickAmmo();
		for (EquipmentInventorySlot slot : ARMOUR_SLOTS)
		{
			pickSlot(slot, candidates(slot));
		}

		for (Candidate c : picks.values())
		{
			for (String word : NEEDS_CHARGES)
			{
				if (c.nameContains(word))
				{
					warnings.add(c.getName() + " needs charging before use.");
					break;
				}
			}
		}
	}

	// ---------------------------------------------------------------- preference lists

	/** Preference lists for a slot, most important first (not including required items). */
	private List<List<String>> preferences(EquipmentInventorySlot slot)
	{
		List<List<String>> out = new ArrayList<>();
		Candidate amulet = picks.get(EquipmentInventorySlot.AMULET);
		boolean wearingSalve = amulet != null && amulet.nameContains("Salve amulet");
		if (slot == EquipmentInventorySlot.HEAD && onSlayerTask && !wearingSalve)
		{
			// Salve and slayer helmet don't stack (salve wins), so skip the helm when wearing a salve.
			out.add(knowledge.slayerHelm(style.group()));
		}
		Candidate weapon = picks.get(EquipmentInventorySlot.WEAPON);
		if (style == CombatStyle.RANGED && weapon != null && usesCrystalArmour(weapon))
		{
			out.add(CRYSTAL_ARMOUR);
		}
		SlotRule bossRule = setup.getPreferred().get(slot);
		if (bossRule != null)
		{
			out.add(bossRule.getNames());
		}
		Boss boss = setup.getBoss();
		if (boss != null)
		{
			for (String attribute : boss.getAttributes())
			{
				out.add(knowledge.attributeItems(attribute, style.group(), slot));
			}
			if (style == CombatStyle.MAGIC && (weapon == null || !containsAny(weapon, POWERED_STAVES)))
			{
				out.add(knowledge.elementItems(boss.getElementalWeakness(), slot));
			}
		}
		if (slot == EquipmentInventorySlot.WEAPON)
		{
			out.add(knowledge.styleWeapons(style));
		}
		else
		{
			out.add(knowledge.styleGear(style.group(), slot));
		}
		return out;
	}

	private Candidate firstPreferred(List<Candidate> options, EquipmentInventorySlot slot, ToDoubleFunction<Candidate> scorer)
	{
		for (List<String> names : preferences(slot))
		{
			Candidate c = firstMatch(options, names, scorer);
			if (c != null)
			{
				return c;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- weapon + shield

	private void pickWeaponAndShield()
	{
		SlotRule requiredShield = setup.getRequired().get(EquipmentInventorySlot.SHIELD);
		// Only rule out two-handers if the player owns a required shield to wear with a one-hander.
		boolean mustUseShield = requiredShield != null
			&& firstMatch(candidates(EquipmentInventorySlot.SHIELD), requiredShield.getNames(), this::score) != null;

		List<Candidate> weapons = candidates(EquipmentInventorySlot.WEAPON).stream()
			.filter(this::suitsStyle)
			.filter(w -> !(mustUseShield && w.getStats().isTwoHanded()))
			.collect(Collectors.toList());

		Candidate weapon = forcedWeapon;
		SlotRule requiredWeapon = setup.getRequired().get(EquipmentInventorySlot.WEAPON);
		if (weapon == null && requiredWeapon != null)
		{
			weapon = firstMatch(candidates(EquipmentInventorySlot.WEAPON), requiredWeapon.getNames(), this::weaponScore);
			if (weapon == null)
			{
				warnings.add("Missing required weapon: " + String.join(" / ", requiredWeapon.getNames()));
			}
		}
		if (weapon == null)
		{
			weapon = firstPreferred(weapons, EquipmentInventorySlot.WEAPON, this::weaponScore);
		}
		if (weapon == null)
		{
			// Compare the best two-hander against the best one-hander plus shield.
			double bestShield = best(candidates(EquipmentInventorySlot.SHIELD), this::score)
				.map(this::score).filter(s -> s > 0).orElse(0.0);
			Candidate best = null;
			double bestScore = 0;
			for (Candidate w : weapons)
			{
				double s = weaponScore(w) + (w.getStats().isTwoHanded() ? 0 : bestShield);
				if (best == null || s > bestScore || (s == bestScore && w.getSource().ordinal() < best.getSource().ordinal()))
				{
					best = w;
					bestScore = s;
				}
			}
			weapon = best;
		}

		if (weapon != null)
		{
			picks.put(EquipmentInventorySlot.WEAPON, weapon);
		}
		else if (requiredWeapon == null)
		{
			warnings.add("No " + style.getDisplayName().toLowerCase(Locale.ROOT) + " weapon found.");
		}

		if (weapon == null || !weapon.getStats().isTwoHanded())
		{
			pickSlot(EquipmentInventorySlot.SHIELD, candidates(EquipmentInventorySlot.SHIELD));
		}
		else if (requiredShield != null)
		{
			warnings.add("Missing required shield: " + String.join(" / ", requiredShield.getNames()));
		}
	}

	private boolean suitsStyle(Candidate w)
	{
		ItemEquipmentStats s = w.getStats();
		switch (style)
		{
			case RANGED:
				return s.getArange() > 0;
			case MAGIC:
				return s.getAmagic() > 0;
			default:
				return meleeAccuracy(s) > 0 || s.getStr() > 0;
		}
	}

	/** Weapon score includes the best compatible ammo and is scaled by attack speed (4 ticks = 1.0). */
	private double weaponScore(Candidate w)
	{
		double s = score(w);
		if (style == CombatStyle.RANGED)
		{
			s += bestAmmoFor(w).map(this::score).orElse(0.0);
		}
		int speed = w.getStats().getAspeed();
		return speed > 0 ? s * 4.0 / speed : s;
	}

	// ---------------------------------------------------------------- ammo

	private void pickAmmo()
	{
		Candidate weapon = picks.get(EquipmentInventorySlot.WEAPON);
		List<Candidate> ammo = candidates(EquipmentInventorySlot.AMMO).stream()
			.filter(ammoFilter(weapon))
			.collect(Collectors.toList());

		boolean picked = pickSlot(EquipmentInventorySlot.AMMO, ammo);
		if (!picked && weapon != null && style == CombatStyle.RANGED && !ammoWord(weapon).isEmpty())
		{
			warnings.add("No " + ammoWord(weapon) + "s found for " + weapon.getName() + ".");
		}
	}

	private Optional<Candidate> bestAmmoFor(Candidate weapon)
	{
		return best(candidates(EquipmentInventorySlot.AMMO).stream().filter(ammoFilter(weapon)).collect(Collectors.toList()), this::score);
	}

	/**
	 * Ranged weapons only accept matching projectiles; everything else (blowpipe, thrown weapons,
	 * crystal bows, melee, magic) can only use non-projectile ammo-slot items like blessings.
	 */
	private Predicate<Candidate> ammoFilter(Candidate weapon)
	{
		String word = (weapon != null && style == CombatStyle.RANGED) ? ammoWord(weapon) : "";
		switch (word)
		{
			case "":
				return a -> !isProjectile(a);
			case "ascension bolt":
				return a -> a.nameContains("ascension bolts");
			case "antler bolt":
				return a -> a.nameContains("antler bolts");
			case "bolt":
			{
				boolean dragonBolts = containsAny(weapon, DRAGON_BOLT_CROSSBOWS);
				return a -> a.nameContains("bolts") && !a.nameContains("bolt rack") && !a.nameContains("ascension")
					&& !a.nameContains("antler") && (dragonBolts || !a.nameContains("dragon bolts"));
			}
			case "arrow":
			{
				boolean dragonArrows = containsAny(weapon, DRAGON_ARROW_BOWS);
				return a -> a.nameContains("arrow") && (dragonArrows || !a.nameContains("dragon arrow"));
			}
			default:
				return a -> a.nameContains(word);
		}
	}

	static String ammoWord(Candidate weapon)
	{
		String n = weapon.getName().toLowerCase(Locale.ROOT);
		if (n.contains("ballista"))
		{
			return "javelin";
		}
		if (n.contains("karil's crossbow"))
		{
			return "bolt rack";
		}
		if (n.contains("ascension crossbow"))
		{
			return "ascension bolt";
		}
		if (n.contains("sunlight crossbow"))
		{
			return "antler bolt";
		}
		if (n.contains("atlatl"))
		{
			return "atlatl dart";
		}
		if (n.contains("craw's bow") || n.contains("webweaver bow"))
		{
			return "";
		}
		if (n.contains("crossbow"))
		{
			return "bolt";
		}
		if (n.contains("crystal bow") || n.contains("bow of faerdhinen") || n.contains("blowpipe"))
		{
			return "";
		}
		if (n.contains("bow"))
		{
			return "arrow";
		}
		return "";
	}

	private static boolean usesCrystalArmour(Candidate weapon)
	{
		return weapon.nameContains("Bow of faerdhinen") || weapon.nameContains("Crystal bow");
	}

	private static boolean containsAny(Candidate c, String[] fragments)
	{
		for (String f : fragments)
		{
			if (c.nameContains(f))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean isProjectile(Candidate c)
	{
		for (String word : PROJECTILE_WORDS)
		{
			if (c.nameContains(word))
			{
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- generic slots

	/** Fills a slot from the given options. Returns true if something was picked. */
	private boolean pickSlot(EquipmentInventorySlot slot, List<Candidate> options)
	{
		Candidate weapon = picks.get(EquipmentInventorySlot.WEAPON);
		if (weapon == null || !usesCrystalArmour(weapon))
		{
			// Crystal armour's bonus only applies with crystal bows; otherwise it's just weak armour.
			options = options.stream()
				.filter(c -> CRYSTAL_ARMOUR.stream().noneMatch(c::nameContains))
				.collect(Collectors.toList());
		}
		if (slot == EquipmentInventorySlot.SHIELD && (weapon == null || containsAny(weapon, POWERED_STAVES)))
		{
			// Elemental tomes only boost standard spells, which powered staves can't cast.
			options = options.stream().filter(c -> !c.nameContains("Tome of")).collect(Collectors.toList());
		}
		SlotRule required = setup.getRequired().get(slot);
		Candidate pick;
		if (required != null)
		{
			pick = firstMatch(options, required.getNames(), this::score);
			if (pick == null)
			{
				warnings.add("Missing required " + slotName(slot) + ": " + String.join(" / ", required.getNames()));
				return false;
			}
		}
		else
		{
			pick = firstPreferred(options, slot, this::score);
			if (pick == null)
			{
				pick = best(options, this::score).filter(c -> score(c) > 0).orElse(null);
			}
		}
		if (pick != null)
		{
			picks.put(slot, pick);
			return true;
		}
		return false;
	}

	// ---------------------------------------------------------------- helpers

	private List<Candidate> candidates(EquipmentInventorySlot slot)
	{
		return bySlot.getOrDefault(slot.getSlotIdx(), Collections.emptyList());
	}

	/** The first name in the list the player owns; among several matching variants, the highest scoring. */
	private static Candidate firstMatch(List<Candidate> options, List<String> names, ToDoubleFunction<Candidate> scorer)
	{
		for (String name : names)
		{
			List<Candidate> matches = options.stream().filter(c -> c.nameContains(name)).collect(Collectors.toList());
			Optional<Candidate> b = best(matches, scorer);
			if (b.isPresent())
			{
				return b.get();
			}
		}
		return null;
	}

	private static Optional<Candidate> best(List<Candidate> options, ToDoubleFunction<Candidate> scorer)
	{
		return options.stream().max(Comparator
			.comparingDouble(scorer)
			.thenComparing(c -> -c.getSource().ordinal()));
	}

	/** Linear stat score for the setup's style. Higher is better. */
	double score(Candidate c)
	{
		ItemEquipmentStats s = c.getStats();
		double defence = (s.getDstab() + s.getDslash() + s.getDcrush() + s.getDmagic() + s.getDrange()) * 0.01;
		double prayer = s.getPrayer() * 0.1;
		switch (style)
		{
			case RANGED:
				return s.getRstr() * 1.2 + s.getArange() * 0.5 + prayer + defence;
			case MAGIC:
				return s.getMdmg() * 3.0 + s.getAmagic() * 0.6 + prayer + defence;
			default:
				return s.getStr() * 1.0 + meleeAccuracy(s) * 0.5 + prayer + defence;
		}
	}

	private int meleeAccuracy(ItemEquipmentStats s)
	{
		switch (style)
		{
			case STAB:
				return s.getAstab();
			case SLASH:
				return s.getAslash();
			case CRUSH:
				return s.getAcrush();
			default:
				return Math.max(s.getAstab(), Math.max(s.getAslash(), s.getAcrush()));
		}
	}

	private static String slotName(EquipmentInventorySlot slot)
	{
		String n = slot.name().toLowerCase(Locale.ROOT);
		return n.equals("amulet") ? "neck item" : n;
	}
}
