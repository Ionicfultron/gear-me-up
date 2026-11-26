package com.gearmeup;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(GearMeUpConfig.GROUP)
public interface GearMeUpConfig extends Config
{
	String GROUP = "gearmeup";

	@ConfigItem(
		keyName = "includeBank",
		name = "Include bank",
		description = "Use items from your bank. Open your bank once so the plugin can remember what's in it.",
		position = 1
	)
	default boolean includeBank()
	{
		return true;
	}

	@ConfigItem(
		keyName = "includeInventory",
		name = "Include inventory",
		description = "Use items currently in your inventory.",
		position = 2
	)
	default boolean includeInventory()
	{
		return true;
	}

	@ConfigItem(
		keyName = "ignoredItems",
		name = "Ignored items",
		description = "Comma-separated item names (or parts of names) never to recommend, e.g. 'Dharok, Void'.",
		position = 3
	)
	default String ignoredItems()
	{
		return "";
	}
}
