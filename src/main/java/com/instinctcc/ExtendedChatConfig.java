package com.instinctcc;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.ModifierlessKeybind;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

@ConfigGroup("instinctcc")
public interface ExtendedChatConfig extends Config
{
	@ConfigItem(
			position = 0,
			keyName = "togglerun",
			name = "Enabled?",
			description = "Enables the chat bot responder."
	)
	default boolean enablePasting()
	{
		return false;
	}




}