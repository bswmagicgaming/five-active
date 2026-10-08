package com.bswmagicgaming.fiveactive;

import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.input.KeyListener;

/**
 * Undocumented shortcuts for filming and for fixing a run by hand. Deliberately not in the settings.
 * 
 * Ctrl+Shift+F11: reset the whole run (every slot emptied, Shuffles returned).
 * Hold R and B, O or L, then press 1 to 5: empty that boss, quest or skill slot.
 * Hold P and B or L, then press 1 to 5: one kill / level-up of progress on that boss or skill slot.
 * Hold [ and B or L, then press 1 to 5: one kill / level-up less on that boss or skill slot.
 * ::fiveactive set boss|skill|quest 1-5 name: put that in the slot (see FiveActivePlugin#setSlot, NameLookup).
 * 
 * They're off until switched on by typing ::fiveactive keys in the chatbox (again to switch off), so a random
 * smack of the keyboard can't touch anyone's run. ::fiveactive help lists them.
 * The run is backed up before each one, so Restore a backup can undo it. Nothing happens while typing in chat.
 */
class HiddenKeys implements KeyListener
{
	private final Client client;
	private final FiveActivePlugin plugin;
	/** Letter keys held down right now. */
	private final Set<Integer> held = new HashSet<>();

	@Inject
	HiddenKeys(Client client, FiveActivePlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
		if (!plugin.getConfig().shortcutsEnabled())
		{
			return;
		}
		int key = e.getKeyCode();
		if (key == KeyEvent.VK_F11 && e.isControlDown() && e.isShiftDown())
		{
			e.consume();
			plugin.hiddenResetRun();
			return;
		}
		if (key == KeyEvent.VK_R || key == KeyEvent.VK_P || key == KeyEvent.VK_OPEN_BRACKET || key == KeyEvent.VK_B || key == KeyEvent.VK_O || key == KeyEvent.VK_L)
		{
			held.add(key);
			return;
		}

		int slot = slotKey(key);
		if (slot < 0 || isTyping())
		{
			return;
		}
		Category category = held.contains(KeyEvent.VK_B) ? Category.BOSSES
			: held.contains(KeyEvent.VK_O) ? Category.QUESTS
			: held.contains(KeyEvent.VK_L) ? Category.SKILLS
			: null;
		if (category == null)
		{
			return;
		}
		if (held.contains(KeyEvent.VK_R))
		{
			e.consume();
			plugin.hiddenEmptySlot(category, slot);
		}
		else if (held.contains(KeyEvent.VK_P) && category != Category.QUESTS)
		{
			e.consume();
			plugin.hiddenProgressSlot(category, slot);
		}
		else if (held.contains(KeyEvent.VK_OPEN_BRACKET) && category != Category.QUESTS)
		{
			e.consume();
			plugin.hiddenRegressSlot(category, slot);
		}
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
		held.remove(e.getKeyCode());
	}

	@Override
	public void keyTyped(KeyEvent e)
	{
	}

	/** Forgets held keys (e.g. the window lost focus while one was down, so its release never arrived). */
	void clear()
	{
		held.clear();
	}

	/** 1 to 5 (the top row or the number pad) as a slot index 0 to 4, or -1. */
	private static int slotKey(int key)
	{
		if (key >= KeyEvent.VK_1 && key <= KeyEvent.VK_5)
		{
			return key - KeyEvent.VK_1;
		}
		if (key >= KeyEvent.VK_NUMPAD1 && key <= KeyEvent.VK_NUMPAD5)
		{
			return key - KeyEvent.VK_NUMPAD1;
		}
		return -1;
	}

	/** Whether something's being typed into the chatbox, where these letters are just letters. */
	private boolean isTyping()
	{
		String typed = client.getVarcStrValue(VarClientID.CHATINPUT);
		return typed != null && !typed.isEmpty();
	}
}
