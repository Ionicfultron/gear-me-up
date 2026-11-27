package com.gearmeup;

import java.util.Collections;
import java.util.List;

/**
 * Damage per second for one setup against one boss, with the numbers behind it.
 */
public final class DpsResult
{
	private final double dps;
	private final String maxHit;
	private final double accuracy;
	private final String stance;
	private final int speedTicks;
	private final int prayerBonus;
	private final List<String> notes;

	DpsResult(double dps, String maxHit, double accuracy, String stance, int speedTicks, int prayerBonus, List<String> notes)
	{
		this.dps = dps;
		this.maxHit = maxHit;
		this.accuracy = accuracy;
		this.stance = stance;
		this.speedTicks = speedTicks;
		this.prayerBonus = prayerBonus;
		this.notes = Collections.unmodifiableList(notes);
	}

	public double getDps()
	{
		return dps;
	}

	/** e.g. "48" or "48/24/12" for multi-hit weapons. */
	public String getMaxHit()
	{
		return maxHit;
	}

	/** Chance to hit, 0 to 1. */
	public double getAccuracy()
	{
		return accuracy;
	}

	/** Attack style to use, e.g. "Aggressive" or "Rapid". */
	public String getStance()
	{
		return stance;
	}

	public int getSpeedTicks()
	{
		return speedTicks;
	}

	public int getPrayerBonus()
	{
		return prayerBonus;
	}

	public List<String> getNotes()
	{
		return notes;
	}
}
