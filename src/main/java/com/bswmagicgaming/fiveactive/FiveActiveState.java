package com.bswmagicgaming.fiveactive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import net.runelite.api.Quest;
import net.runelite.api.Skill;

/**
 * Everything about a Five Active run, saved per RuneScape account as a single JSON blob.
 * It is written on every change and never cleared by the plugin, so toggling the plugin
 * or restarting the client picks up exactly where the run left off.
 */
@Data
class FiveActiveState
{
	/** The save format this version of the plugin writes. See FiveActivePlugin#migrate. */
	static final int CURRENT_VERSION = 1;

	/**
	 * Which save format this is. Saves from before versioning have 0 (deliberately no initialiser: the field
	 * missing from an old save must read as 0, not as the current version). New runs are given CURRENT_VERSION.
	 */
	private int version;

	private List<Slot<Skill>> skills = new ArrayList<>();
	private List<Slot<Boss>> bosses = new ArrayList<>();
	private List<Slot<Quest>> quests = new ArrayList<>();

	/** Kills of each boss while it was active during this run. */
	private Map<Boss, Integer> modeKills = new HashMap<>();

	/** Latest lifetime kill count seen in chat, keyed by the in-game counter name (see {@link Boss#getKcNames()}). */
	private Map<String, Integer> lifetimeKc = new HashMap<>();

	/**
	 * How many boss tiers have been unlocked (claimed with the Unlock button), counting tier 1. Reaching a tier's
	 * KC only makes it ready to unlock. Null in saves from before tiers had to be claimed: the plugin then counts
	 * every tier already reached as unlocked.
	 */
	private Integer unlockedTiers;

	private int collectionLogSlots;
	private int shufflesSpent;

	/**
	 * The highest bank value milestone (see {@link Rules#BANK_MILESTONES}) the bank has been seen worth, for the
	 * History view. Null until the bank is first opened, which sets it without logging anything already passed.
	 */
	private Long bankMilestone;

	/** Gson bypasses field initialisers when the saved JSON is missing a field, so patch up nulls after loading. */
	void fillDefaults()
	{
		if (skills == null)
		{
			skills = new ArrayList<>();
		}
		if (bosses == null)
		{
			bosses = new ArrayList<>();
		}
		if (quests == null)
		{
			quests = new ArrayList<>();
		}
		if (modeKills == null)
		{
			modeKills = new HashMap<>();
		}
		if (lifetimeKc == null)
		{
			lifetimeKc = new HashMap<>();
		}
		// Drop entries for enum constants that no longer exist (Gson deserialises unknown names as null)
		skills.removeIf(s -> s == null || s.getValue() == null);
		bosses.removeIf(s -> s == null || s.getValue() == null);
		quests.removeIf(s -> s == null || s.getValue() == null);
		modeKills.remove(null);
	}

	int getModeTotalKc()
	{
		return modeKills.values().stream().mapToInt(Integer::intValue).sum();
	}
}
