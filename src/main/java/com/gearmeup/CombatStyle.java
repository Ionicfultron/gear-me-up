package com.gearmeup;

/**
 * The attack style a setup is built around. Each style knows how to score a piece of gear.
 */
public enum CombatStyle
{
	STAB("Melee (stab)"),
	SLASH("Melee (slash)"),
	CRUSH("Melee (crush)"),
	RANGED("Ranged"),
	MAGIC("Magic");

	private final String displayName;

	CombatStyle(String displayName)
	{
		this.displayName = displayName;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public boolean isMelee()
	{
		return this == STAB || this == SLASH || this == CRUSH;
	}

	/** "melee", "ranged" or "magic": the key used in bosses.json. */
	public String group()
	{
		return isMelee() ? "melee" : this == RANGED ? "ranged" : "magic";
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
