package com.gearmeup;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.EquipmentInventorySlot;

/**
 * One way to fight a boss (e.g. "Zulrah - Magic"): a combat style, per-slot item preferences
 * and requirements, and tips.
 */
public final class GearSetup
{
	private final String label;
	private final CombatStyle style;
	private final Map<EquipmentInventorySlot, SlotRule> preferred;
	private final Map<EquipmentInventorySlot, SlotRule> required;
	private final String notes;
	private Boss boss;

	GearSetup(String label, CombatStyle style, Map<EquipmentInventorySlot, SlotRule> preferred,
		Map<EquipmentInventorySlot, SlotRule> required, String notes)
	{
		this.label = label == null || label.isEmpty() ? style.getDisplayName() : label;
		this.style = style;
		this.preferred = Collections.unmodifiableMap(copy(preferred));
		this.required = Collections.unmodifiableMap(copy(required));
		this.notes = notes;
	}

	private static Map<EquipmentInventorySlot, SlotRule> copy(Map<EquipmentInventorySlot, SlotRule> m)
	{
		Map<EquipmentInventorySlot, SlotRule> out = new EnumMap<>(EquipmentInventorySlot.class);
		out.putAll(m);
		return out;
	}

	void setBoss(Boss boss)
	{
		this.boss = boss;
	}

	public Boss getBoss()
	{
		return boss;
	}

	public String getLabel()
	{
		return label;
	}

	public CombatStyle getStyle()
	{
		return style;
	}

	public Map<EquipmentInventorySlot, SlotRule> getPreferred()
	{
		return preferred;
	}

	public Map<EquipmentInventorySlot, SlotRule> getRequired()
	{
		return required;
	}

	public String getNotes()
	{
		return notes;
	}

	@Override
	public String toString()
	{
		return boss == null ? label : boss.getName() + " - " + label;
	}
}
