package com.gearmeup;

import net.runelite.api.gameval.SpriteID;

/**
 * Offensive prayers and their combat multipliers (OSRS Wiki). Only offensive prayers are covered:
 * protection prayer advice isn't allowed in Plugin Hub plugins.
 *
 * <p>For melee, {@code accuracy} multiplies Attack and {@code damage} multiplies Strength. For ranged
 * they multiply Ranged for accuracy and for damage. For magic, {@code accuracy} multiplies Magic and
 * {@code magicDamage} is a magic damage bonus in percent.</p>
 */
public enum OffensivePrayer
{
	CLARITY_OF_THOUGHT("Clarity of Thought", "melee", 7, 1.05, 1.0, 0, SpriteID.Prayeron.CLARITY_OF_THOUGHT, Unlock.NONE),
	BURST_OF_STRENGTH("Burst of Strength", "melee", 4, 1.0, 1.05, 0, SpriteID.Prayeron.BURST_OF_STRENGTH, Unlock.NONE),
	IMPROVED_REFLEXES("Improved Reflexes", "melee", 16, 1.10, 1.0, 0, SpriteID.Prayeron.IMPROVED_REFLEXES, Unlock.NONE),
	SUPERHUMAN_STRENGTH("Superhuman Strength", "melee", 13, 1.0, 1.10, 0, SpriteID.Prayeron.SUPERHUMAN_STRENGTH, Unlock.NONE),
	INCREDIBLE_REFLEXES("Incredible Reflexes", "melee", 34, 1.15, 1.0, 0, SpriteID.Prayeron.INCREDIBLE_REFLEXES, Unlock.NONE),
	ULTIMATE_STRENGTH("Ultimate Strength", "melee", 31, 1.0, 1.15, 0, SpriteID.Prayeron.ULTIMATE_STRENGTH, Unlock.NONE),
	CHIVALRY("Chivalry", "melee", 60, 1.15, 1.18, 0, SpriteID.Prayeron.CHIVALRY, Unlock.KNIGHT_WAVES),
	PIETY("Piety", "melee", 70, 1.20, 1.23, 0, SpriteID.Prayeron.PIETY, Unlock.KNIGHT_WAVES),
	ZEAL("Zeal", "melee", 84, 1.25, 1.28, 0, SpriteID.Prayeron.ZEAL, Unlock.ZEAL),

	SHARP_EYE("Sharp Eye", "ranged", 8, 1.05, 1.05, 0, SpriteID.Prayeron.SHARP_EYE, Unlock.NONE),
	HAWK_EYE("Hawk Eye", "ranged", 26, 1.10, 1.10, 0, SpriteID.Prayeron.HAWK_EYE, Unlock.NONE),
	EAGLE_EYE("Eagle Eye", "ranged", 44, 1.15, 1.15, 0, SpriteID.Prayeron.EAGLE_EYE, Unlock.NONE),
	DEADEYE("Deadeye", "ranged", 62, 1.18, 1.18, 0, SpriteID.Prayeron.DEADEYE, Unlock.DEADEYE),
	RIGOUR("Rigour", "ranged", 74, 1.20, 1.23, 0, SpriteID.Prayeron.RIGOUR, Unlock.RIGOUR),

	MYSTIC_WILL("Mystic Will", "magic", 9, 1.05, 1.0, 0, SpriteID.Prayeron.MYSTIC_WILL, Unlock.NONE),
	MYSTIC_LORE("Mystic Lore", "magic", 27, 1.10, 1.0, 1, SpriteID.Prayeron.MYSTIC_LORE, Unlock.NONE),
	MYSTIC_MIGHT("Mystic Might", "magic", 45, 1.15, 1.0, 2, SpriteID.Prayeron.MYSTIC_MIGHT, Unlock.NONE),
	MYSTIC_VIGOUR("Mystic Vigour", "magic", 63, 1.18, 1.0, 3, SpriteID.Prayeron.MYSTIC_VIGOUR, Unlock.MYSTIC_VIGOUR),
	AUGURY("Augury", "magic", 77, 1.25, 1.0, 4, SpriteID.Prayeron.AUGURY, Unlock.AUGURY);

	/** Extra unlock needed on top of the Prayer level. */
	public enum Unlock
	{
		NONE, KNIGHT_WAVES, ZEAL, RIGOUR, AUGURY, DEADEYE, MYSTIC_VIGOUR
	}

	private final String displayName;
	private final String group;
	private final int level;
	private final double accuracy;
	private final double damage;
	private final int magicDamage;
	private final int sprite;
	private final Unlock unlock;

	OffensivePrayer(String displayName, String group, int level, double accuracy, double damage, int magicDamage, int sprite,
		Unlock unlock)
	{
		this.displayName = displayName;
		this.group = group;
		this.level = level;
		this.accuracy = accuracy;
		this.damage = damage;
		this.magicDamage = magicDamage;
		this.sprite = sprite;
		this.unlock = unlock;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	/** "melee", "ranged" or "magic". */
	public String getGroup()
	{
		return group;
	}

	public int getLevel()
	{
		return level;
	}

	public double getAccuracy()
	{
		return accuracy;
	}

	public double getDamage()
	{
		return damage;
	}

	public int getMagicDamage()
	{
		return magicDamage;
	}

	public int getSprite()
	{
		return sprite;
	}

	public Unlock getUnlock()
	{
		return unlock;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
