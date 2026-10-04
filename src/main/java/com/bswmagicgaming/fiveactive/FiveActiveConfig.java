package com.bswmagicgaming.fiveactive;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(FiveActiveConfig.GROUP)
public interface FiveActiveConfig extends Config
{
	String GROUP = "fiveactive";

	@Getter
	@RequiredArgsConstructor
	enum KcMode
	{
		SINCE_START("Since mode start"),
		LIFETIME("Lifetime");

		private final String label;

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigItem(
		keyName = "kcMode",
		name = "Total KC counts",
		description = "What counts towards the boss tier unlocks.<br>"
			+ "Lifetime: your real kill counts (open the Boss Kill Log in your POH with Chat Commands enabled to sync).<br>"
			+ "Since mode start: only kills of active bosses during this run (for starting the mode on an existing account).",
		position = 1
	)
	default KcMode kcMode()
	{
		// Most runs start on a fresh account, where lifetime and mode KC are the same thing
		return KcMode.LIFETIME;
	}

	@ConfigItem(
		keyName = "dimSkillsTab",
		name = "Dim inactive skills",
		description = "Darken inactive skills in the skills tab.",
		position = 4
	)
	default boolean dimSkillsTab()
	{
		return true;
	}

	@ConfigItem(
		keyName = "dimQuestList",
		name = "Dim inactive quests",
		description = "Grey out inactive quests in the quest list.",
		position = 5
	)
	default boolean dimQuestList()
	{
		return true;
	}

	@ConfigItem(
		keyName = "slotMachine",
		name = "Roll animation",
		description = "Spin newly rolled skills, bosses and quests in like a slot machine.<br>Turn off to show results instantly.",
		position = 6
	)
	default boolean slotMachine()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sounds",
		name = "Sounds",
		description = "Play the plugin's sounds: the roll animation, the panel's login and logout animations,<br>"
			+ "rule-break warnings, and earning Shuffles or unlocking boss tiers.",
		position = 7
	)
	default boolean sounds()
	{
		return true;
	}

	@Getter
	@RequiredArgsConstructor
	enum OverlayMode
	{
		PANEL_CLOSED("When panel is closed"),
		ALWAYS("Always"),
		OFF("Never");

		private final String label;

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigSection(name = "Overlay", description = "The at-a-glance overlay beside the inventory", position = 50)
	String OVERLAY_SECTION = "overlay";

	@ConfigItem(
		keyName = "overlayMode",
		name = "Show overlay",
		description = "An at-a-glance overlay of your active quests, skills and bosses, beside the inventory.<br>"
			+ "Alt-drag it to move it.",
		position = 1,
		section = OVERLAY_SECTION
	)
	default OverlayMode overlayMode()
	{
		return OverlayMode.PANEL_CLOSED;
	}

	@Getter
	@RequiredArgsConstructor
	enum OverlayStyle
	{
		DETAILED("Detailed"),
		COMPACT("Compact");

		private final String label;

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigItem(
		keyName = "overlayStyle",
		name = "Style",
		description = "Detailed: names with progress beside them.<br>"
			+ "Compact: just icons with a progress bar under each, for small spaces. Hover an icon for its name.",
		position = 2,
		section = OVERLAY_SECTION
	)
	default OverlayStyle overlayStyle()
	{
		return OverlayStyle.DETAILED;
	}

	@ConfigItem(
		keyName = "overlayQuests",
		name = "Quests",
		description = "Show your active quests in the overlay.",
		position = 3,
		section = OVERLAY_SECTION
	)
	default boolean overlayQuests()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overlaySkills",
		name = "Skills",
		description = "Show your active skills in the overlay.",
		position = 4,
		section = OVERLAY_SECTION
	)
	default boolean overlaySkills()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overlayBosses",
		name = "Bosses",
		description = "Show your active bosses in the overlay.",
		position = 5,
		section = OVERLAY_SECTION
	)
	default boolean overlayBosses()
	{
		return true;
	}

	@ConfigItem(
		keyName = "tierFireworks",
		name = "Tier unlock fireworks",
		description = "Set off level-up fireworks on your character when you unlock a boss tier (only you see them).",
		position = 8
	)
	default boolean tierFireworks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "tierBanner",
		name = "Tier unlock banner",
		description = "Show a big \"Tier unlocked!\" banner in the middle of the game view when you unlock a boss tier.",
		position = 9
	)
	default boolean tierBanner()
	{
		return true;
	}

	@Getter
	@RequiredArgsConstructor
	enum BossPoolStyle
	{
		ICONS("Icons"),
		LIST("List");

		private final String label;

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigItem(
		keyName = "bossPoolStyle",
		name = "Boss pool style",
		description = "Icons: a grid of boss icons per tier (hover for names).<br>List: every boss by name.",
		position = 8
	)
	default BossPoolStyle bossPoolStyle()
	{
		return BossPoolStyle.ICONS;
	}

	@Getter
	@RequiredArgsConstructor
	enum HistoryGrouping
	{
		DAY("Day"),
		WEEK("Week"),
		MONTH("Month"),
		YEAR("Year");

		private final String label;
	}

	String HISTORY_GROUPING_KEY = "historyGrouping";

	/** How the History view groups entries (a view preference, set in the panel). */
	@ConfigItem(keyName = HISTORY_GROUPING_KEY, name = "", description = "", hidden = true)
	default HistoryGrouping historyGrouping()
	{
		return HistoryGrouping.DAY;
	}

	String HISTORY_OLDEST_FIRST_KEY = "historyOldestFirst";

	/** Whether the History view reads from the start of the run instead of from the latest. */
	@ConfigItem(keyName = HISTORY_OLDEST_FIRST_KEY, name = "", description = "", hidden = true)
	default boolean historyOldestFirst()
	{
		return false;
	}

	String SHORTCUTS_KEY = "shortcutsEnabled";

	/** The undocumented shortcuts (see HiddenKeys): off until switched on with the ::fiveactive keys chat command. */
	@ConfigItem(keyName = SHORTCUTS_KEY, name = "", description = "", hidden = true)
	default boolean shortcutsEnabled()
	{
		return false;
	}

	String COLLAPSED_TIERS_KEY = "collapsedTiers";

	/** Comma-separated boss pool tiers (by KC milestone) the user has collapsed. */
	@ConfigItem(keyName = COLLAPSED_TIERS_KEY, name = "", description = "", hidden = true)
	default String collapsedTiers()
	{
		return "";
	}

	String COLLAPSED_SECTIONS_KEY = "collapsedSections";

	/**
	 * Comma-separated side panel sections the user has collapsed. The boss pool starts collapsed (it's extra
	 * detail); after that the panel remembers what the user left open. "NONE" means nothing is collapsed:
	 * RuneLite treats an empty value as unset, which would bring this default back.
	 */
	@ConfigItem(keyName = COLLAPSED_SECTIONS_KEY, name = "", description = "", hidden = true)
	default String collapsedSections()
	{
		return "POOL";
	}

	@ConfigSection(name = "Backups", description = "Save codes and backups of your run", position = 90, closedByDefault = true)
	String BACKUP_SECTION = "backups";

	String COPY_SAVE_CODE_KEY = "copySaveCode";
	String SAVE_FILE_KEY = "saveRunToFile";
	String RESTORE_RUN_KEY = "restoreRun";
	String OPEN_BACKUPS_KEY = "openBackupFolder";

	/** Acts as a button. */
	@ConfigItem(
		keyName = COPY_SAVE_CODE_KEY,
		name = "Copy save code (tick)",
		description = "Copies your whole run and its History to the clipboard as one line of text.<br>"
			+ "You must be logged in to the account whose run you want to copy.<br>"
			+ "Keep it somewhere safe: Restore a backup can bring the run back from it, even on another computer.",
		section = BACKUP_SECTION,
		position = 1
	)
	default boolean copySaveCode()
	{
		return false;
	}

	/** Acts as a button. */
	@ConfigItem(
		keyName = SAVE_FILE_KEY,
		name = "Save run to file (tick)",
		description = "Saves your whole run and its History as a small text file, the same as a save code.<br>"
			+ "Handy for sharing (e.g. on Discord, where a save code is too long to paste) or keeping a copy.<br>"
			+ "You must be logged in to the account whose run you want to save.",
		section = BACKUP_SECTION,
		position = 2
	)
	default boolean saveRunToFile()
	{
		return false;
	}

	/** Acts as a button. */
	@ConfigItem(
		keyName = RESTORE_RUN_KEY,
		name = "Restore a backup (tick)",
		description = "Brings back an earlier copy of an account's run. A copy is saved each time you log in,<br>"
			+ "or you can load a save file or paste a save code. Your run as it is now is backed up first.<br>"
			+ "You must be logged in to the account you want to restore.",
		section = BACKUP_SECTION,
		position = 3
	)
	default boolean restoreRun()
	{
		return false;
	}

	/** Acts as a button. */
	@ConfigItem(
		keyName = OPEN_BACKUPS_KEY,
		name = "Copy backup folder location (tick)",
		description = "Copies where this account's backups and History log are kept.<br>"
			+ "Paste it into File Explorer's address bar to open the folder.",
		section = BACKUP_SECTION,
		position = 4
	)
	default boolean openBackupFolder()
	{
		return false;
	}

}
