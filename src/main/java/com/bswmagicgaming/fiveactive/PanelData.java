package com.bswmagicgaming.fiveactive;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * Immutable snapshot of everything the side panel shows. Built on the client thread
 * (quest states need it) and handed to Swing, so the panel never touches game state directly.
 */
@Value
@Builder
class PanelData
{
	static final PanelData LOGGED_OUT = PanelData.builder().loggedIn(false).build();

	boolean loggedIn;

	int shuffles;
	int collectionLogSlots;

	List<SlotView> skills;
	int skillsCanRoll;
	/** Skills at 99: permanently unlocked and never rolled. */
	@Builder.Default
	List<Skill> maxedSkills = Collections.emptyList();
	/** No active skills and nothing left to roll. */
	boolean allSkillsDone;
	List<SlotView> bosses;
	int bossesCanRoll;
	List<SlotView> quests;
	int questsCanRoll;
	/** No active quests and nothing left to roll. */
	boolean allQuestsDone;

	/** Categories where a shuffle would change something (there is at least one new option). */
	@Builder.Default
	Set<Category> shufflable = Collections.emptySet();

	int totalKc;
	/** Lifetime numbers for the share card: quests completed (of all), and total level. */
	int questsCompleted;
	int questsTotal;
	int totalLevel;
	int combatLevel;
	/** The IRONMAN varbit: 0 normal, 1 ironman, 2 ultimate, 3 hardcore, 4 group, 5 hardcore group, 6 unranked group. */
	int accountType;
	/** Tiers claimed so far (at least 1). */
	int unlockedTiers;
	/** Index of the tier whose KC is reached but that hasn't been unlocked yet, or -1. Boss rolls wait for it. */
	@Builder.Default
	int pendingTier = -1;
	FiveActiveConfig.KcMode kcMode;
	List<TierView> tiers;

	@Value
	static class SlotView
	{
		String name;
		/** Only set for skill slots, for the icon. */
		Skill skill;
		int progress;
		int goal;
		boolean done;
		boolean fresh;
		/** Only set for quest slots. */
		QuestState questState;
		/** Sprite id for boss slots' icon, or -1. */
		int iconSpriteId;
	}

	enum BossStatus
	{
		ACTIVE,
		ROLLABLE,
		LOCKED
	}

	@Value
	@AllArgsConstructor
	static class BossView
	{
		String name;
		BossStatus status;
		/** Why it's locked (null unless LOCKED). */
		String reason;
		/** Hiscores sprite for the icon, or -1. */
		int iconSpriteId;
		/** Quests and Slayer level the boss needs, each marked met or not (empty if it needs neither). */
		List<Requirement> requirements;

		BossView(String name, BossStatus status, String reason, int iconSpriteId)
		{
			this(name, status, reason, iconSpriteId, Collections.emptyList());
		}
	}

	@Value
	static class Requirement
	{
		String text;
		boolean met;
	}

	@Value
	static class TierView
	{
		int killsRequired;
		boolean unlocked;
		List<BossView> bosses;
	}
}
