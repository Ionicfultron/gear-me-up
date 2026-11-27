package com.gearmeup;

import java.util.Collections;
import java.util.Map;

/**
 * Everything the panel shows after "Find best setup": a result per attack style plus item info.
 */
final class GearReport
{
	final Boss boss;
	final Map<CombatStyle, SetupFinder.StyleResult> results;
	final CombatStyle best;
	final int ownedCount;
	final String bankStatus;
	/** Boost -> item id for its icon (only boosts the player owns). */
	final Map<Boost, Integer> boostIcons;

	GearReport(Boss boss, Map<CombatStyle, SetupFinder.StyleResult> results, CombatStyle best, int ownedCount, String bankStatus,
		Map<Boost, Integer> boostIcons)
	{
		this.boss = boss;
		this.results = Collections.unmodifiableMap(results);
		this.best = best;
		this.ownedCount = ownedCount;
		this.bankStatus = bankStatus;
		this.boostIcons = Collections.unmodifiableMap(boostIcons);
	}
}
