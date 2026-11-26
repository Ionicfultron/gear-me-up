package com.gearmeup;

import java.util.Collections;
import java.util.List;

/**
 * An ordered list of item name fragments (best first) for one slot. If {@code required}, the slot
 * must hold one of them and the result warns when the player owns none.
 */
public final class SlotRule
{
	private final List<String> names;
	private final boolean required;

	SlotRule(List<String> names, boolean required)
	{
		this.names = Collections.unmodifiableList(names);
		this.required = required;
	}

	public List<String> getNames()
	{
		return names;
	}

	public boolean isRequired()
	{
		return required;
	}
}
