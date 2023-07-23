package com.instinctcc;

import com.google.common.base.CharMatcher;
import com.google.inject.Provides;
import joptsimple.internal.Strings;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.VarClientStrChanged;
import net.runelite.api.vars.InputType;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;
import org.pf4j.Extension;
import javax.inject.Inject;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

@Extension
@PluginDescriptor(
		name = "Chat Extended",
		description = "Chat filtering, Ctrl+V pasting, and retype previous message",
		tags = {"sundar", "pajeet"},
		enabledByDefault = false
)

@Slf4j
public class ExtendedChatPlugin extends Plugin
{
	@Inject
	private Client client;
	private Map<String, String> commandResponseMap = new HashMap<>();
	@Inject
	private KeyManager keyManager;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ExtendedChatConfig config;

	@Inject
	private ConfigManager configManager;

	@Provides
	ExtendedChatConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ExtendedChatConfig.class);
	}

	private boolean ctrlPressed = false;
	private String lastMessage;
	private String oldChat = "";
	private final CharMatcher jagexPrintableCharMatcher = Text.JAGEX_PRINTABLE_CHAR_MATCHER;
	private HashMap<String,String> incomingWordFilter = new HashMap<>();
	private HashMap<String,String> outgoingWordFilter = new HashMap<>();

	@Override
	protected void startUp()
	{
		{
			commandResponseMap.put("!discord", "//Join our discord: discord.gg/instinct");
			commandResponseMap.put("!loot", "//Coming soon...");
			// ...
		}
		client.refreshChat();
	}
	@Subscribe
	private void onVarClientStrChanged(VarClientStrChanged varClient)
	{
		String newChat = client.getVarcStrValue(VarClientStr.CHATBOX_TYPED_TEXT);
		if (varClient.getIndex() == VarClientStr.CHATBOX_TYPED_TEXT && !newChat.equals(oldChat))
		{
			oldChat = newChat;
		}
	}
	@Override
	protected void shutDown()
	{
		client.refreshChat();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals("extendedchat"))
		{
		}

	}
	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		// Check if the message is from the clan chat
		if (chatMessage.getType() != ChatMessageType.CLAN_CHAT)
		{
			return;
		}

		// Get the message content
		String messageContent = chatMessage.getMessage();

		// If the message content is a command, respond accordingly
		if (commandResponseMap.containsKey(messageContent))
		{
			// Add the / prefix to send the message to the clan chat
			String response = "/" + commandResponseMap.get(messageContent);
			sendMessage(response);
		}
	}


	private void sendMessage(String text)
	{
		int mode = 0;
		if (inClanChat() && text.startsWith("/"))
		{
			mode = 2;
		}
		int finalMode = mode;
		Runnable r = () ->
		{
			String cached = oldChat;
			client.setVarcStrValue(VarClientStr.CHATBOX_TYPED_TEXT, text);
			client.runScript(ScriptID.CHAT_SEND, text, finalMode, 0, 0, -1);
			oldChat = cached;
			client.setVarcStrValue(VarClientStr.CHATBOX_TYPED_TEXT, oldChat);
		};
		clientThread.invoke(r);
	}

	private boolean inClanChat()
	{
		return client.getWidget(WidgetID.CLAN_GROUP_ID, 1) != null;
	}
	@Subscribe
	public void onOverheadTextChanged(final OverheadTextChanged event)
	{

		if (!(event.getActor() instanceof Player))
		{
			return;
		}
		final String output = censorMessage(event.getOverheadText(), incomingWordFilter);

		if (output != null)
		{
			event.getActor().setOverheadText(output);
		}
	}

	private void toggleKeyListener(Boolean enabled,KeyListener keylistener)
	{
		ctrlPressed = false;
		if (enabled)
		{
			keyManager.registerKeyListener(keylistener);
		}
		else
		{
			keyManager.unregisterKeyListener(keylistener);
		}
	}

	//checks if chat is disabled by the runelite key remaping plugin
	private boolean isChatEnabled()
	{
		Widget chatboxInput = client.getWidget(WidgetInfo.CHATBOX_INPUT);
		if (chatboxInput != null && client.getGameState() == GameState.LOGGED_IN)
		{
			String text = chatboxInput.getText();
			int idx = text.indexOf(':');
			if (idx != -1)
			{
				return !text.substring(idx + 2).equals("Press Enter to Chat...");
			}
		}
		//default, should probably throw an error or something
		return false;
	}

	private HashMap<String,String> parseConfig(String input)
	{
		HashMap<String,String> newFilterMap = new HashMap<>();
		String[] filterArguments = input.split("\\r?\\n");//split on newline

		for (String filterArg : filterArguments)
		{
			//ignores empty inputs, comments that start with //, and inputs that dont follow the format of thing:thing
			if (filterArg.matches("^\\s*$") || filterArg.startsWith("//") || !filterArg.contains(":"))
			{
				continue;
			}
			String[] pair = filterArg.split(":", 2);
			String key = pair[0].trim().toLowerCase();
			String value = pair[1].trim();

			//ensures key is one word
			if (key.contains(" "))
			{
				key = key.substring(key.lastIndexOf(" ") + 1);
			}

			if (!key.isBlank() && !value.isBlank())
			{
				newFilterMap.put(key, value);
			}
		}

		return newFilterMap;

	}

	private String censorMessage(String message, Map<String,String> wordFilter)
	{
		List<String> words = Arrays.asList(jagexPrintableCharMatcher
				.retainFrom(message)
				.replace(' ', ' ')
				.split(" "));

		boolean filtered = false;

		ListIterator<String> iterator = words.listIterator();
		while(iterator.hasNext())
		{
			String word = iterator.next().toLowerCase();
			if(wordFilter.containsKey(word))
			{
				iterator.set(wordFilter.get(word));
				filtered = true;
			}
		}

		String output = Strings.join(words, " ");
		if (output.length()>80)
		{
			output = output.substring(0, 80);
		}

		if (output.length() > 0)
		{
			output = output.substring(0, 1).toUpperCase() + output.substring(1);
		}

		return filtered ? output : message;
	}
}