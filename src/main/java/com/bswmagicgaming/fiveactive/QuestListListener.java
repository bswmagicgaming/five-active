package com.bswmagicgaming.fiveactive;

import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Greys out inactive quests in the quest list whenever the game redraws it.
 */
class QuestListListener
{
	private static final int INACTIVE_COLOR = 0x555555;
	/** Script that redraws the quest list (e.g. after filtering/sorting). */
	private static final int QUESTLIST_REDRAW_SCRIPT = 1353;
	private static final String RFD_PREFIX = "Recipe for Disaster";

	private final Client client;
	private final FiveActivePlugin plugin;

	@Inject
	QuestListListener(Client client, FiveActivePlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != ScriptID.QUESTLIST_INIT && event.getScriptId() != QUESTLIST_REDRAW_SCRIPT)
		{
			return;
		}
		if (plugin.getState() == null || !plugin.getConfig().dimQuestList())
		{
			return;
		}

		Widget container = client.getWidget(InterfaceID.Questlist.CONTAINER);
		if (container == null || container.getDynamicChildren() == null)
		{
			return;
		}

		for (Widget widget : container.getDynamicChildren())
		{
			String name = Text.removeTags(widget.getText());
			if (!name.isEmpty() && !name.endsWith("Quests") && !isActive(name))
			{
				widget.setTextColor(INACTIVE_COLOR);
			}
		}
	}

	private boolean isActive(String listName)
	{
		for (Slot<Quest> slot : plugin.getState().getQuests())
		{
			if (slot.isDone())
			{
				continue;
			}
			String questName = slot.getValue().getName();
			// Recipe for Disaster subquests all live under one entry in the quest list
			if (questName.equals(listName) || (questName.startsWith(RFD_PREFIX) && listName.equals(RFD_PREFIX)))
			{
				return true;
			}
		}
		return false;
	}
}
