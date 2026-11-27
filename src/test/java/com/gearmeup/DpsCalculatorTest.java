package com.gearmeup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemEquipmentStats;
import org.junit.BeforeClass;
import org.junit.Test;

public class DpsCalculatorTest
{
	private static BossData data;
	private static SpellAdvisor advisor;

	@BeforeClass
	public static void load() throws Exception
	{
		Gson gson = new Gson();
		data = BossData.load(gson);
		advisor = new SpellAdvisor(SpellData.load(gson));
	}

	private static Boss boss(String name)
	{
		return data.getBosses().stream().filter(b -> b.getName().equals(name)).findFirst().orElseThrow(AssertionError::new);
	}

	private static Candidate item(int id, String name, EquipmentInventorySlot slot, int stab, int slash, int crush, int magic,
		int range, int str, int rstr, float mdmg, int speed, boolean twoHanded)
	{
		ItemEquipmentStats s = ItemEquipmentStats.builder().slot(slot.getSlotIdx()).isTwoHanded(twoHanded)
			.astab(stab).aslash(slash).acrush(crush).amagic(magic).arange(range).str(str).rstr(rstr).mdmg(mdmg)
			.aspeed(speed).build();
		return new Candidate(id, name, ItemSource.BANK, s);
	}

	private static DpsCalculator.Fight fight(Boss boss, Boost boost, List<OffensivePrayer> prayers)
	{
		return new DpsCalculator.Fight(boss, PlayerContext.maxed(), boost, false, prayers, false);
	}

	@Test
	public void unarmedMaxHitWithPietyAndSuperCombat()
	{
		// 99 Str + super combat = 118; Piety: floor(118*1.23) = 145; +3 aggressive +8 = 156; floor((156*64+320)/640) = 16
		Map<EquipmentInventorySlot, Candidate> picks = new EnumMap<>(EquipmentInventorySlot.class);
		DpsResult r = DpsCalculator.calculate(picks, CombatStyle.CRUSH,
			fight(boss("General Graardor"), Boost.SUPER_COMBAT, Collections.singletonList(OffensivePrayer.PIETY)), null, 0);
		assertNotNull(r);
		assertEquals("Aggressive", r.getStance());
		assertEquals("16", r.getMaxHit());
	}

	@Test
	public void hitChance()
	{
		assertEquals(1 - 12.0 / 202, DpsCalculator.hitChance(100, 10), 1e-9);
		assertEquals(10.0 / 202, DpsCalculator.hitChance(10, 100), 1e-9);
	}

	@Test
	public void tridentMaxHit()
	{
		Candidate swamp = item(1, "Trident of the swamp", EquipmentInventorySlot.WEAPON, 0, 0, 0, 25, 0, 0, 0, 0, 4, false);
		assertEquals(31, DpsCalculator.poweredMax(swamp, 99));
		Candidate shadow = item(2, "Tumeken's shadow", EquipmentInventorySlot.WEAPON, 0, 0, 0, 35, 0, 0, 0, 1, 5, true);
		assertEquals(34, DpsCalculator.poweredMax(shadow, 99));
	}

	@Test
	public void finderPrefersLanceAtVorkath()
	{
		List<Candidate> owned = new ArrayList<>();
		owned.add(item(10, "Abyssal whip", EquipmentInventorySlot.WEAPON, 0, 82, 0, 0, 0, 82, 0, 0, 4, false));
		owned.add(item(11, "Dragon hunter lance", EquipmentInventorySlot.WEAPON, 85, 65, 65, 0, 0, 70, 0, 0, 4, false));
		owned.add(item(12, "Rune crossbow", EquipmentInventorySlot.WEAPON, 0, 0, 0, 0, 90, 0, 0, 0, 5, false));
		owned.add(item(13, "Runite bolts", EquipmentInventorySlot.AMMO, 0, 0, 0, 0, 0, 0, 115, 0, 0, false));
		owned.add(item(14, "Anti-dragon shield", EquipmentInventorySlot.SHIELD, 0, 0, 0, 0, 0, 0, 0, 0, 0, false));
		owned.add(item(15, "Trident of the swamp", EquipmentInventorySlot.WEAPON, 0, 0, 0, 25, 0, 0, 0, 0, 4, false));
		SetupFinder.Options o = new SetupFinder.Options();
		SetupFinder f = new SetupFinder(boss("Vorkath"), boss("Any monster (generic)"), owned, PlayerContext.maxed(), o,
			Collections.emptyList(), data.getKnowledge(), advisor, 0, Collections.emptyMap());
		Map<CombatStyle, SetupFinder.StyleResult> all = f.findAll();
		for (SetupFinder.StyleResult r : all.values())
		{
			Candidate w = r.gear.get(EquipmentInventorySlot.WEAPON);
			System.out.printf("%-14s %-22s %s%n", r.style, w == null ? "-" : w.getName(),
				r.dps == null ? "no dps" : String.format("%.2f dps, max %s, acc %.1f%%, %s, prayers %s, boost %s",
					r.dps.getDps(), r.dps.getMaxHit(), r.dps.getAccuracy() * 100, r.dps.getStance(), r.prayers, r.boost));
		}
		assertEquals("Dragon hunter lance", all.get(CombatStyle.STAB).gear.get(EquipmentInventorySlot.WEAPON).getName());
		assertTrue(all.get(CombatStyle.STAB).dps.getDps() > all.get(CombatStyle.SLASH).dps.getDps());
		assertEquals(Collections.singletonList(OffensivePrayer.ZEAL), all.get(CombatStyle.STAB).prayers);
		assertEquals(Collections.singletonList(OffensivePrayer.AUGURY), all.get(CombatStyle.MAGIC).prayers);
	}

	@Test
	public void everyBossWithStatsGetsDps()
	{
		List<Candidate> owned = new ArrayList<>();
		owned.add(item(10, "Abyssal whip", EquipmentInventorySlot.WEAPON, 0, 82, 0, 0, 0, 82, 0, 0, 4, false));
		owned.add(item(12, "Magic shortbow", EquipmentInventorySlot.WEAPON, 0, 0, 0, 0, 69, 0, 0, 0, 4, true));
		owned.add(item(13, "Amethyst arrow", EquipmentInventorySlot.AMMO, 0, 0, 0, 0, 0, 0, 55, 0, 0, false));
		owned.add(item(15, "Trident of the swamp", EquipmentInventorySlot.WEAPON, 0, 0, 0, 25, 0, 0, 0, 0, 4, false));
		for (Boss b : data.getBosses())
		{
			if (b.getCombat() == null)
			{
				continue;
			}
			SetupFinder f = new SetupFinder(b, boss("Any monster (generic)"), owned, PlayerContext.maxed(),
				new SetupFinder.Options(), Collections.emptyList(), data.getKnowledge(), advisor, 0, Collections.emptyMap());
			Map<CombatStyle, SetupFinder.StyleResult> all = f.findAll();
			CombatStyle best = SetupFinder.best(all);
			assertNotNull(b.getName(), best);
			double dps = all.get(best).dps.getDps();
			assertTrue(b.getName() + " " + dps, dps > 0 && dps < 30);
		}
	}
}
