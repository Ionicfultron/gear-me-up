package com.gearmeup;

import java.util.Collections;
import java.util.List;

/**
 * What to cast (or what the weapon's charges cost) and the runes involved.
 */
public final class SpellAdvice
{
	/** One rune (or other item) needed per cast / per charge. */
	public static final class Line
	{
		private final String item;
		private final int perCast;
		private final boolean free;
		private final long have;

		Line(String item, int perCast, boolean free, long have)
		{
			this.item = item;
			this.perCast = perCast;
			this.free = free;
			this.have = have;
		}

		public String getItem()
		{
			return item;
		}

		public int getPerCast()
		{
			return perCast;
		}

		/** Supplied by the equipped staff or tome, so none are needed. */
		public boolean isFree()
		{
			return free;
		}

		public long getHave()
		{
			return have;
		}
	}

	private final String title;
	private final boolean charges;
	private final List<Line> lines;
	private final long casts;
	private final List<String> notes;

	SpellAdvice(String title, boolean charges, List<Line> lines, long casts, List<String> notes)
	{
		this.title = title;
		this.charges = charges;
		this.lines = Collections.unmodifiableList(lines);
		this.casts = casts;
		this.notes = Collections.unmodifiableList(notes);
	}

	/** e.g. "Fire Surge (Standard, level 95)" or "Trident of the swamp charges". */
	public String getTitle()
	{
		return title;
	}

	/** True when the lines are the cost of one weapon charge rather than one spell cast. */
	public boolean isCharges()
	{
		return charges;
	}

	public List<Line> getLines()
	{
		return lines;
	}

	/** How many casts (or charges) the owned runes cover; -1 if unlimited. */
	public long getCasts()
	{
		return casts;
	}

	public List<String> getNotes()
	{
		return notes;
	}
}
