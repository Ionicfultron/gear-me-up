package com.gearmeup;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.EquipmentInventorySlot;

/**
 * The recommended setup: one owned item per slot (or none), plus warnings such as missing
 * required items.
 */
public final class GearResult
{
	private final GearSetup setup;
	private final Map<EquipmentInventorySlot, Candidate> picks;
	private final List<String> warnings;

	GearResult(GearSetup setup, Map<EquipmentInventorySlot, Candidate> picks, List<String> warnings)
	{
		this.setup = setup;
		this.picks = Collections.unmodifiableMap(new EnumMap<>(picks));
		this.warnings = Collections.unmodifiableList(warnings);
	}

	public GearSetup getSetup()
	{
		return setup;
	}

	public Candidate get(EquipmentInventorySlot slot)
	{
		return picks.get(slot);
	}

	public Map<EquipmentInventorySlot, Candidate> getPicks()
	{
		return picks;
	}

	public List<String> getWarnings()
	{
		return warnings;
	}
}
