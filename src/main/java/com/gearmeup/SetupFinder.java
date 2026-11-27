package com.gearmeup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.EquipmentInventorySlot;

/**
 * Works out the best owned setup for every attack style against a boss: picks the gear (trying each
 * owned weapon and keeping the highest DPS), the offensive prayer, the potion and, for magic, the spell.
 * Pure logic.
 */
public final class SetupFinder
{
	private static final int WEAPONS_TO_TRY = 30;

	/** Choices from the panel. Null prayer/boost means "Auto (best)". */
	public static final class Options
	{
		OffensivePrayer prayer;
		boolean noPrayer;
		Boost boost;
		boolean noBoost;
		boolean currentLevels;
		boolean optimiseWeapon = true;
		boolean onSlayerTask;
		/** Style group ("melee"/"ranged"/"magic") -> label of the boss setup the player picked. */
		Map<String, String> setupLabels = Collections.emptyMap();
	}

	/** The answer for one attack style. */
	public static final class StyleResult
	{
		final CombatStyle style;
		final GearSetup setup;
		final List<GearSetup> choices;
		final boolean immune;
		final boolean recommended;
		final GearResult gear;
		final DpsResult dps;
		final List<OffensivePrayer> prayers;
		final Boost boost;
		final SpellAdvice spell;

		StyleResult(CombatStyle style, GearSetup setup, List<GearSetup> choices, boolean immune, boolean recommended,
			GearResult gear, DpsResult dps, List<OffensivePrayer> prayers, Boost boost, SpellAdvice spell)
		{
			this.style = style;
			this.setup = setup;
			this.choices = choices;
			this.immune = immune;
			this.recommended = recommended;
			this.gear = gear;
			this.dps = dps;
			this.prayers = prayers;
			this.boost = boost;
			this.spell = spell;
		}

		double dpsValue()
		{
			return dps == null ? -1 : dps.getDps();
		}
	}

	private final Boss boss;
	private final Boss generic;
	private final Collection<Candidate> owned;
	private final PlayerContext player;
	private final Options options;
	private final List<String> ignored;
	private final BossData.Knowledge knowledge;
	private final SpellAdvisor advisor;
	private final int spellbook;
	private final Map<String, Long> quantities;
	private final int dartStrength;

	public SetupFinder(Boss boss, Boss generic, Collection<Candidate> owned, PlayerContext player, Options options,
		List<String> ignored, BossData.Knowledge knowledge, SpellAdvisor advisor, int spellbook, Map<String, Long> quantities)
	{
		this.boss = boss;
		this.generic = generic;
		this.owned = owned;
		this.player = player;
		this.options = options;
		this.ignored = ignored;
		this.knowledge = knowledge;
		this.advisor = advisor;
		this.spellbook = spellbook;
		this.quantities = quantities;
		int darts = 0;
		for (Candidate c : owned)
		{
			if (c.nameContains("dart") && !c.nameContains("blowpipe") && !c.nameContains("atlatl"))
			{
				darts = Math.max(darts, c.getStats().getRstr());
			}
		}
		this.dartStrength = darts;
	}

	/** Results for every style, in CombatStyle order. */
	public Map<CombatStyle, StyleResult> findAll()
	{
		Map<CombatStyle, StyleResult> out = new EnumMap<>(CombatStyle.class);
		for (CombatStyle style : CombatStyle.values())
		{
			out.put(style, find(style));
		}
		return out;
	}

	/** The style with the highest DPS (ignoring styles the boss is immune to); null if none has DPS. */
	public static CombatStyle best(Map<CombatStyle, StyleResult> results)
	{
		CombatStyle best = null;
		double bestDps = 0;
		for (StyleResult r : results.values())
		{
			if (!r.immune && r.dpsValue() > bestDps)
			{
				best = r.style;
				bestDps = r.dpsValue();
			}
		}
		return best;
	}

	StyleResult find(CombatStyle style)
	{
		String group = style.group();
		List<GearSetup> choices = new ArrayList<>();
		for (GearSetup s : boss.getSetups())
		{
			if (s.getStyle().group().equals(group))
			{
				choices.add(s);
			}
		}
		boolean recommended = !choices.isEmpty();
		GearSetup setup = choose(choices, style, group);
		if (setup == null)
		{
			setup = genericSetup(style);
		}
		else if (setup.getStyle() != style)
		{
			setup = setup.withStyle(style, setup.getLabel());
		}
		boolean immune = isImmune(group);
		List<OffensivePrayer> prayers = prayers(group);
		Boost boost = boost(group);
		DpsCalculator.Fight fight = new DpsCalculator.Fight(boss, player, boost, options.currentLevels, prayers,
			options.onSlayerTask);

		Evaluated best = evaluate(setup, null, fight);
		if (options.optimiseWeapon && best.dps != null)
		{
			for (Candidate w : GearCalculator.weaponOptions(setup, owned, ignored, knowledge, WEAPONS_TO_TRY))
			{
				Evaluated e = evaluate(setup, w, fight);
				if (e.dps != null && e.dps.getDps() > best.dps.getDps() + 1e-9)
				{
					best = e;
				}
			}
		}
		return new StyleResult(style, setup, choices, immune, recommended, best.gear, best.dps, prayers, boost, best.spell);
	}

	private static final class Evaluated
	{
		GearResult gear;
		DpsResult dps;
		SpellAdvice spell;
	}

	private Evaluated evaluate(GearSetup setup, Candidate weapon, DpsCalculator.Fight fight)
	{
		Evaluated e = new Evaluated();
		e.gear = GearCalculator.calculate(setup, owned, options.onSlayerTask, ignored, knowledge, weapon);
		Candidate w = e.gear.get(EquipmentInventorySlot.WEAPON);
		if (setup.getStyle() == CombatStyle.MAGIC && w != null)
		{
			e.spell = advisor.advise(w, e.gear.get(EquipmentInventorySlot.SHIELD), boss, player.getMagic(), spellbook, quantities);
		}
		if (w != null)
		{
			e.dps = DpsCalculator.calculate(e.gear.getPicks(), setup.getStyle(), fight,
				e.spell == null ? null : e.spell.getSpell(), dartStrength);
		}
		return e;
	}

	private GearSetup choose(List<GearSetup> choices, CombatStyle style, String group)
	{
		String wanted = options.setupLabels.get(group);
		for (GearSetup s : choices)
		{
			if (s.getLabel().equals(wanted))
			{
				return s;
			}
		}
		for (GearSetup s : choices)
		{
			if (s.getStyle() == style)
			{
				return s;
			}
		}
		return choices.isEmpty() ? null : choices.get(0);
	}

	private GearSetup genericSetup(CombatStyle style)
	{
		if (generic != null)
		{
			for (GearSetup s : generic.getSetups())
			{
				if (s.getStyle() == style)
				{
					return s.forBoss(boss, s.getLabel());
				}
			}
		}
		return new GearSetup(style.getDisplayName(), style, Collections.emptyMap(), Collections.emptyMap(), "").forBoss(boss,
			style.getDisplayName());
	}

	private boolean isImmune(String group)
	{
		for (String i : boss.getImmuneTo())
		{
			if (i.toLowerCase(Locale.ROOT).startsWith(group))
			{
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- prayer and boost

	List<OffensivePrayer> prayers(String group)
	{
		if (options.noPrayer)
		{
			return Collections.emptyList();
		}
		if (options.prayer != null && options.prayer.getGroup().equals(group))
		{
			return Collections.singletonList(options.prayer);
		}
		return autoPrayers(group, player);
	}

	/** The strongest offensive prayer(s) the player can use for a style group. */
	static List<OffensivePrayer> autoPrayers(String group, PlayerContext player)
	{
		OffensivePrayer bestBoth = null;
		OffensivePrayer bestAccuracy = null;
		OffensivePrayer bestDamage = null;
		for (OffensivePrayer p : OffensivePrayer.values())
		{
			if (!p.getGroup().equals(group) || !player.canUse(p))
			{
				continue;
			}
			// Later entries in each family are stronger, so the last usable one wins.
			if (!group.equals("melee") || (p.getAccuracy() > 1 && p.getDamage() > 1))
			{
				bestBoth = p;
			}
			else if (p.getAccuracy() > 1)
			{
				bestAccuracy = p;
			}
			else
			{
				bestDamage = p;
			}
		}
		if (bestBoth != null)
		{
			return Collections.singletonList(bestBoth);
		}
		List<OffensivePrayer> out = new ArrayList<>();
		if (bestDamage != null)
		{
			out.add(bestDamage);
		}
		if (bestAccuracy != null)
		{
			out.add(bestAccuracy);
		}
		return out;
	}

	Boost boost(String group)
	{
		if (options.noBoost || options.currentLevels)
		{
			return null;
		}
		if (options.boost != null && options.boost.helps(group))
		{
			return options.boost;
		}
		Boost best = null;
		int bestValue = 0;
		for (Boost b : Boost.values())
		{
			if (!b.helps(group) || !available(b))
			{
				continue;
			}
			int value;
			switch (group)
			{
				case "melee":
					value = b.attack(player.getAttack()) + b.strength(player.getStrength()) * 2;
					break;
				case "ranged":
					value = b.ranged(player.getRanged());
					break;
				default:
					value = b.magic(player.getMagic());
					break;
			}
			if (best == null || value > bestValue)
			{
				best = b;
				bestValue = value;
			}
		}
		return best;
	}

	private boolean available(Boost b)
	{
		if (b.getRaid() != null)
		{
			return b.getRaid().equals(boss.getRaid());
		}
		for (List<String> names : b.getNeeds())
		{
			if (!player.ownsAny(names))
			{
				return false;
			}
		}
		return true;
	}
}
