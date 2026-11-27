package com.gearmeup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemEquipmentStats;

/**
 * Player-vs-NPC damage per second, following the OSRS Wiki DPS calculator (weirdgloop/osrs-dps-calc):
 * same multipliers, same order, same rounding. Pure logic.
 *
 * <p>Covered: prayers, potions, stances, salve/slayer helm, demonbane, dragonbane, keris, inquisitor's,
 * obsidian, crystal armour, twisted bow, scythe, fang, colossal blade, powered staves, Tumeken's shadow,
 * tomes, chaos gauntlets, elemental weaknesses, flat armour, Corporeal Beast, Kraken and Zulrah rules.
 * Not covered: special attacks, enchanted bolt procs, void, wilderness weapons, set effects not listed.</p>
 */
public final class DpsCalculator
{
	/** Everything about the fight except the gear. */
	public static final class Fight
	{
		final Boss boss;
		final PlayerContext player;
		final Boost boost;
		final boolean useCurrentLevels;
		final List<OffensivePrayer> prayers;
		final boolean onSlayerTask;

		public Fight(Boss boss, PlayerContext player, Boost boost, boolean useCurrentLevels, List<OffensivePrayer> prayers,
			boolean onSlayerTask)
		{
			this.boss = boss;
			this.player = player;
			this.boost = boost;
			this.useCurrentLevels = useCurrentLevels;
			this.prayers = prayers;
			this.onSlayerTask = onSlayerTask;
		}
	}

	private static final int DAMAGE_CAP = 200;

	private final Map<EquipmentInventorySlot, Candidate> picks;
	private final CombatStyle style;
	private final Fight fight;
	private final Boss boss;
	private final Boss.Combat npc;
	private final SpellData.Spell spell;
	private final int dartStrength;
	private final List<String> notes = new ArrayList<>();

	private int astab;
	private int aslash;
	private int acrush;
	private int amagic;
	private int arange;
	private int str;
	private int rstr;
	private double mdmg;
	private int prayerBonus;

	private DpsCalculator(Map<EquipmentInventorySlot, Candidate> picks, CombatStyle style, Fight fight, SpellData.Spell spell,
		int dartStrength)
	{
		this.picks = picks;
		this.style = style;
		this.fight = fight;
		this.boss = fight.boss;
		this.npc = boss == null ? null : boss.getCombat();
		this.spell = spell;
		this.dartStrength = dartStrength;
		for (Candidate c : picks.values())
		{
			ItemEquipmentStats s = c.getStats();
			astab += s.getAstab();
			aslash += s.getAslash();
			acrush += s.getAcrush();
			amagic += s.getAmagic();
			arange += s.getArange();
			str += s.getStr();
			rstr += s.getRstr();
			mdmg += s.getMdmg();
			prayerBonus += s.getPrayer();
		}
	}

	/**
	 * @param spell        spell being autocast (magic with a non-powered weapon), else null
	 * @param dartStrength ranged strength of the darts loaded in a blowpipe (0 if none)
	 * @return the result, or null when the boss's stats are unknown or the setup can't attack
	 */
	public static DpsResult calculate(Map<EquipmentInventorySlot, Candidate> picks, CombatStyle style, Fight fight,
		SpellData.Spell spell, int dartStrength)
	{
		if (fight.boss == null || fight.boss.getCombat() == null)
		{
			return null;
		}
		return new DpsCalculator(picks, style, fight, spell, dartStrength).run();
	}

	private DpsResult run()
	{
		if (style.isMelee())
		{
			return best(melee("Accurate", 3, 0), melee("Aggressive", 0, 3), melee("Controlled", 1, 1));
		}
		if (style == CombatStyle.RANGED)
		{
			return best(ranged(false), ranged(true));
		}
		return magic();
	}

	private static DpsResult best(DpsResult... results)
	{
		DpsResult best = null;
		for (DpsResult r : results)
		{
			if (r != null && (best == null || r.getDps() > best.getDps()))
			{
				best = r;
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- levels and prayers

	private int level(int index, int real)
	{
		if (fight.useCurrentLevels)
		{
			return fight.player.current(index);
		}
		Boost b = fight.boost;
		if (b == null)
		{
			return real;
		}
		switch (index)
		{
			case 0:
				return b.attack(real);
			case 1:
				return b.strength(real);
			case 2:
				return b.ranged(real);
			default:
				return b.magic(real);
		}
	}

	/** Applies every active prayer's accuracy (or damage) multiplier, each floored, as the game does. */
	private int prayed(int level, boolean damage)
	{
		int eff = level;
		for (OffensivePrayer p : fight.prayers)
		{
			double m = damage ? p.getDamage() : p.getAccuracy();
			if (m != 1.0)
			{
				eff = eff * (int) Math.round(m * 100) / 100;
			}
		}
		return eff;
	}

	private int prayerMagicDamage()
	{
		int d = 0;
		for (OffensivePrayer p : fight.prayers)
		{
			d += p.getMagicDamage();
		}
		return d;
	}

	// ---------------------------------------------------------------- gear checks

	private Candidate pick(EquipmentInventorySlot slot)
	{
		return picks.get(slot);
	}

	private boolean wearing(EquipmentInventorySlot slot, String fragment)
	{
		Candidate c = picks.get(slot);
		return c != null && c.nameContains(fragment);
	}

	private boolean weapon(String fragment)
	{
		return wearing(EquipmentInventorySlot.WEAPON, fragment);
	}

	private boolean is(String attribute)
	{
		return boss.getAttributes().contains(attribute);
	}

	/** 3 = Salve (e)/(ei), 2 = Salve/(i), 0 = none. Imbued only matters for ranged and magic. */
	private int salve(boolean needImbued)
	{
		if (!is("undead"))
		{
			return 0;
		}
		Candidate a = pick(EquipmentInventorySlot.AMULET);
		if (a == null || !a.nameContains("Salve amulet"))
		{
			return 0;
		}
		boolean ei = a.nameContains("(ei)");
		boolean e = ei || a.nameContains("(e)");
		boolean imbued = ei || a.nameContains("(i)");
		if (needImbued && !imbued)
		{
			return 0;
		}
		return e ? 3 : 2;
	}

	private boolean slayerHelm(boolean needImbued)
	{
		if (!fight.onSlayerTask)
		{
			return false;
		}
		Candidate h = pick(EquipmentInventorySlot.HEAD);
		if (h == null || !(h.nameContains("Slayer helmet") || h.nameContains("Black mask")))
		{
			return false;
		}
		return !needImbued || h.nameContains("(i)");
	}

	private int demonbane(int basePercent)
	{
		return is("demon") ? basePercent * boss.getDemonbaneVulnerability() / 100 : 0;
	}

	private static int pct(int value, int percent)
	{
		return value + value * percent / 100;
	}

	// ---------------------------------------------------------------- melee

	private DpsResult melee(String stanceName, int stanceAttack, int stanceStrength)
	{
		int accBonus = style == CombatStyle.STAB ? astab : style == CombatStyle.SLASH ? aslash : acrush;
		boolean tzhaar = weapon("Tzhaar") || weapon("Toktz");
		boolean obsidian = tzhaar && wearing(EquipmentInventorySlot.HEAD, "Obsidian helmet")
			&& wearing(EquipmentInventorySlot.BODY, "Obsidian platebody") && wearing(EquipmentInventorySlot.LEGS, "Obsidian platelegs");
		boolean arclight = weapon("Arclight") || weapon("Emberlight");
		boolean lance = weapon("Dragon hunter lance") && is("dragon");
		boolean kerisBreach = weapon("Keris partisan of breaching") && is("kalphite");
		boolean keris = weapon("Keris partisan") && is("kalphite");
		int inquisitor = 0;
		if (style == CombatStyle.CRUSH)
		{
			inquisitor += wearing(EquipmentInventorySlot.HEAD, "Inquisitor's great helm") ? 1 : 0;
			inquisitor += wearing(EquipmentInventorySlot.BODY, "Inquisitor's hauberk") ? 2 : 0;
			inquisitor += wearing(EquipmentInventorySlot.LEGS, "Inquisitor's plateskirt") ? 2 : 0;
		}
		int salve = salve(false);
		boolean helm = salve == 0 && slayerHelm(false);

		int effA = prayed(level(0, fight.player.getAttack()), false) + 8 + stanceAttack;
		int base = effA * (accBonus + 64);
		int roll = base;
		if (salve == 3)
		{
			roll = roll * 6 / 5;
		}
		else if (salve == 2 || helm)
		{
			roll = roll * 7 / 6;
		}
		if (obsidian)
		{
			roll += base / 10;
		}
		if (arclight)
		{
			roll = pct(roll, demonbane(70));
		}
		if (lance)
		{
			roll = roll * 6 / 5;
		}
		if (kerisBreach)
		{
			roll = roll * 133 / 100;
		}
		if (inquisitor > 0)
		{
			roll = roll * (200 + inquisitor) / 200;
		}

		int effS = prayed(level(1, fight.player.getStrength()), true) + 8 + stanceStrength;
		int baseMax = (effS * (str + 64) + 320) / 640;
		int max = baseMax;
		if (salve == 3)
		{
			max = max * 6 / 5;
		}
		else if (salve == 2 || helm)
		{
			max = max * 7 / 6;
		}
		if (arclight)
		{
			max = pct(max, demonbane(70));
		}
		if (obsidian)
		{
			max += baseMax / 10;
		}
		if (lance)
		{
			max = max * 6 / 5;
		}
		if (keris)
		{
			max = max * 133 / 100;
		}
		if (weapon("Colossal blade"))
		{
			max += Math.min(2 * npc.getSize(), 10);
		}
		if (inquisitor > 0)
		{
			max = max * (200 + inquisitor) / 200;
		}
		int min = 0;
		boolean fang = weapon("Osmumten's fang") && style == CombatStyle.STAB;
		if (fang)
		{
			min = max * 3 / 20;
			max -= min;
		}

		String defKey = style == CombatStyle.STAB ? "stab" : style == CombatStyle.SLASH ? "slash" : "crush";
		long defRoll = defenceRoll(npc.getDefenceLevel(), boss.getDefenceBonus(defKey));
		double acc = fang && boss.getRaid() == null ? fangChance(roll, defRoll) : fang ? 1 - sq(1 - hitChance(roll, defRoll))
			: hitChance(roll, defRoll);

		boolean corpbane = style == CombatStyle.STAB && (weapon("spear") || weapon("hasta") || weapon("halberd") || fang);
		boolean berserker = tzhaar && wearing(EquipmentInventorySlot.AMULET, "Berserker necklace");
		IntUnaryOperator post = d ->
		{
			if (berserker)
			{
				d = d * 6 / 5;
			}
			if (isCorp() && !corpbane)
			{
				d = d / 2;
			}
			return armour(d);
		};

		int hits = weapon("Scythe of vitur") ? Math.min(Math.max(npc.getSize(), 1), 3) : 1;
		double expected = 0;
		StringBuilder maxText = new StringBuilder();
		for (int i = 0; i < hits; i++)
		{
			int hitMax = max >> i;
			expected += expected(min, hitMax, acc, post);
			maxText.append(i == 0 ? "" : "/").append(post.applyAsInt(hitMax + min));
		}
		return result(expected, maxText.toString(), acc, stanceName, speed());
	}

	// ---------------------------------------------------------------- ranged

	private DpsResult ranged(boolean rapid)
	{
		boolean crystalBow = weapon("Bow of faerdhinen") || weapon("Crystal bow");
		int crystal = 0;
		if (crystalBow)
		{
			crystal += wearing(EquipmentInventorySlot.HEAD, "Crystal helm") ? 1 : 0;
			crystal += wearing(EquipmentInventorySlot.LEGS, "Crystal legs") ? 2 : 0;
			crystal += wearing(EquipmentInventorySlot.BODY, "Crystal body") ? 3 : 0;
		}
		int salve = salve(true);
		boolean helm = salve == 0 && slayerHelm(true);
		boolean tbow = weapon("Twisted bow");
		boolean dhcb = weapon("Dragon hunter crossbow") && is("dragon");
		boolean scorching = weapon("Scorching bow");
		int stance = rapid ? 0 : 3;

		int effA = prayed(level(2, fight.player.getRanged()), false) + stance + 8;
		int roll = effA * (arange + 64);
		if (crystal > 0)
		{
			roll = roll * (20 + crystal) / 20;
		}
		if (salve == 3)
		{
			roll = roll * 6 / 5;
		}
		else if (salve == 2)
		{
			roll = roll * 7 / 6;
		}
		else if (helm)
		{
			roll = roll * 23 / 20;
		}
		int magicLevel = tbowMagic();
		if (tbow)
		{
			roll = roll * tbowPercent(magicLevel, true) / 100;
		}
		if (dhcb)
		{
			roll = roll * 13 / 10;
		}
		if (scorching)
		{
			roll = pct(roll, demonbane(30));
		}

		int rangedStrength = rstr + (weapon("blowpipe") ? dartStrength : 0);
		int effS = prayed(level(2, fight.player.getRanged()), true) + stance + 8;
		int max = (effS * (rangedStrength + 64) + 320) / 640;
		if (crystal > 0)
		{
			max = max * (40 + crystal) / 40;
		}
		if (salve == 3)
		{
			max = max * 6 / 5;
		}
		else if (salve == 2)
		{
			max = max * 7 / 6;
		}
		else if (helm)
		{
			max = max * (23 + (dhcb ? 5 : 0) + (scorching && is("demon") ? 6 : 0)) / 20;
		}
		if (tbow)
		{
			max = max * tbowPercent(magicLevel, false) / 100;
		}
		if (dhcb && !helm)
		{
			max = max * 5 / 4;
		}
		if (scorching && !helm)
		{
			max = pct(max, demonbane(30));
		}

		long defRoll = defenceRoll(npc.getDefenceLevel(), boss.getDefenceBonus(rangedDefenceKey()));
		double acc = hitChance(roll, defRoll);
		IntUnaryOperator post = d ->
		{
			if (isCorp())
			{
				d = d / 2;
			}
			if (boss.getName().equals("Kraken"))
			{
				d = Math.max(1, d / 7);
			}
			return armour(d);
		};
		int speed = Math.max(1, speed() - (rapid ? 1 : 0));
		return result(expected(0, max, acc, post), String.valueOf(post.applyAsInt(max)), acc, rapid ? "Rapid" : "Accurate", speed);
	}

	private int tbowMagic()
	{
		int cap = is("xerician") ? 350 : 250;
		return Math.min(cap, npc.getMagicLevel());
	}

	private static int tbowPercent(int m, boolean accuracy)
	{
		int t2 = (3 * m - (accuracy ? 10 : 14)) / 100;
		int t3 = sqInt(3 * m / 10 - (accuracy ? 100 : 140)) / 100;
		int top = accuracy ? 140 : 250;
		return Math.max(0, Math.min(top, top + t2 - t3));
	}

	private String rangedDefenceKey()
	{
		Candidate w = pick(EquipmentInventorySlot.WEAPON);
		String n = w == null ? "" : w.getName().toLowerCase(Locale.ROOT);
		if (n.contains("crossbow") || n.contains("ballista") || n.contains("chinchompa"))
		{
			return "rangedHeavy";
		}
		if (n.contains("dart") || n.contains("knife") || n.contains("thrownaxe") || n.contains("blowpipe")
			|| n.contains("javelin") || n.contains("atlatl"))
		{
			return "rangedLight";
		}
		return "rangedStandard";
	}

	// ---------------------------------------------------------------- magic

	private DpsResult magic()
	{
		Candidate w = pick(EquipmentInventorySlot.WEAPON);
		int magicLevel = level(3, fight.player.getMagic());
		int powered = poweredMax(w, magicLevel);
		boolean isPowered = powered >= 0;
		int base;
		int speed;
		String element = null;
		boolean demonbaneSpell = false;
		if (isPowered)
		{
			base = powered;
			speed = speed();
		}
		else if (spell != null && spell.maxHit > 0)
		{
			base = spell.maxHit;
			element = spell.element == null || spell.element.equals("null") ? null : spell.element.toLowerCase(Locale.ROOT);
			demonbaneSpell = spell.name.toLowerCase(Locale.ROOT).contains("demonbane");
			speed = w != null && w.nameContains("Harmonised") ? 4 : 5;
			if (spell.name.contains("Bolt") && wearing(EquipmentInventorySlot.GLOVES, "Chaos gauntlets"))
			{
				base += 3;
			}
		}
		else
		{
			return null;
		}

		boolean shadow = w != null && w.nameContains("Tumeken's shadow");
		int magicAttack = amagic;
		int damageTenths = (int) Math.round(mdmg * 10);
		if (shadow)
		{
			int mult = "toa".equals(boss.getRaid()) ? 4 : 3;
			magicAttack *= mult;
			damageTenths = Math.min(1000, damageTenths * mult);
		}
		int salve = salve(true);
		boolean helm = salve == 0 && slayerHelm(true);

		int effM = prayed(magicLevel, false) + (isPowered ? 2 : 0) + 9;
		int baseRoll = effM * (magicAttack + 64);
		int roll = baseRoll;
		int additive = salve == 3 ? 20 : salve == 2 ? 15 : 0;
		if (additive > 0)
		{
			roll = roll * (100 + additive) / 100;
		}
		boolean dhWand = w != null && w.nameContains("Dragon hunter wand") && is("dragon");
		if (dhWand)
		{
			roll = roll * 7 / 4;
		}
		if (helm)
		{
			roll = roll * 23 / 20;
		}
		if (demonbaneSpell && is("demon"))
		{
			boolean purging = w != null && w.nameContains("Purging staff");
			roll = pct(roll, 20 * (purging ? 2 : 1) * boss.getDemonbaneVulnerability() / 100);
		}
		boolean weak = element != null && element.equalsIgnoreCase(boss.getElementalWeakness());
		int severity = weak ? boss.getElementalWeaknessPercent() : 0;
		if ("water".equals(element) && wearing(EquipmentInventorySlot.SHIELD, "Tome of water"))
		{
			roll = roll * 6 / 5;
		}
		roll += baseRoll * severity / 100;

		damageTenths += (salve == 3 ? 200 : salve == 2 ? 150 : 0) + prayerMagicDamage() * 10;
		int max = base + base * damageTenths / 1000;
		if (helm)
		{
			max = max * 23 / 20;
		}
		if (dhWand)
		{
			max = max * 7 / 5;
		}
		max += base * severity / 100;
		if (element != null && !isPowered && tome(element))
		{
			max = max * 11 / 10;
		}

		int npcMagic = boss.usesDefenceAgainstMagic() ? npc.getDefenceLevel() : npc.getMagicLevel();
		long defRoll = defenceRoll(npcMagic, boss.getDefenceBonus("magic"));
		double acc = hitChance(roll, defRoll);
		IntUnaryOperator post = d -> d;
		String stance = isPowered ? "Accurate" : "Autocast";
		return result(expected(0, max, acc, post), String.valueOf(max), acc, stance, speed);
	}

	private boolean tome(String element)
	{
		Candidate s = pick(EquipmentInventorySlot.SHIELD);
		return s != null && s.nameContains("Tome of " + element) && !s.nameContains("(empty)");
	}

	/** Built-in spell max hit of a powered staff at this Magic level, or -1 if the weapon isn't one. */
	static int poweredMax(Candidate w, int level)
	{
		if (w == null)
		{
			return -1;
		}
		int third = level / 3;
		if (w.nameContains("Trident of the seas"))
		{
			return Math.max(1, third - 5);
		}
		if (w.nameContains("Trident of the swamp") || w.nameContains("Dawnbringer"))
		{
			return third - 2;
		}
		if (w.nameContains("Sanguinesti staff"))
		{
			return third;
		}
		if (w.nameContains("Tumeken's shadow"))
		{
			return third + 1;
		}
		if (w.nameContains("Eye of ayak") || w.nameContains("Accursed sceptre"))
		{
			return third - 6;
		}
		if (w.nameContains("Thammaron's sceptre"))
		{
			return third - 8;
		}
		if (w.nameContains("Warped sceptre"))
		{
			return (8 * level + 96) / 37;
		}
		if (w.nameContains("Zorya's tome"))
		{
			return third - 10;
		}
		return -1;
	}

	// ---------------------------------------------------------------- shared

	private int speed()
	{
		Candidate w = pick(EquipmentInventorySlot.WEAPON);
		int s = w == null ? 4 : w.getStats().getAspeed();
		return s > 0 ? s : 4;
	}

	private boolean isCorp()
	{
		return boss.getName().equals("Corporeal Beast");
	}

	private int armour(int d)
	{
		int flat = npc.getFlatArmour();
		return flat > 0 ? Math.max(0, d - flat) : d;
	}

	private static long defenceRoll(int level, int bonus)
	{
		return (long) (level + 9) * (bonus + 64);
	}

	static double hitChance(long attack, long defence)
	{
		attack = Math.max(0, attack);
		defence = Math.max(0, defence);
		if (attack > defence)
		{
			return 1 - (defence + 2) / (2.0 * (attack + 1));
		}
		return attack / (2.0 * (defence + 1));
	}

	private static double fangChance(long a, long d)
	{
		if (a > d)
		{
			return 1 - (double) (d + 2) * (2 * d + 3) / (6.0 * (a + 1) * (a + 1));
		}
		return (double) a * (4 * a + 5) / (6.0 * (a + 1) * (d + 1));
	}

	private static double sq(double x)
	{
		return x * x;
	}

	private static int sqInt(int x)
	{
		return x * x;
	}

	/** Expected damage of one attack: accurate rolls are uniform min..max (an accurate 0 becomes 1), then post-roll effects. */
	private double expected(int min, int max, double acc, IntUnaryOperator post)
	{
		if (max < min)
		{
			return 0;
		}
		double sum = 0;
		for (int d = min; d <= max; d++)
		{
			int dmg = post.applyAsInt(Math.max(1, d));
			if (boss.getName().equals("Zulrah") && dmg > 50)
			{
				dmg = 47; // Zulrah turns any hit over 50 into 45-50
			}
			sum += Math.min(DAMAGE_CAP, dmg);
		}
		return acc * sum / (max - min + 1);
	}

	private DpsResult result(double expected, String maxHit, double acc, String stance, int speed)
	{
		double dps = expected / (speed * 0.6);
		return new DpsResult(dps, maxHit, acc, stance, speed, prayerBonus, notes);
	}
}
