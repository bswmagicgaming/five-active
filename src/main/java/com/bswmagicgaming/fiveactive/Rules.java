package com.bswmagicgaming.fiveactive;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.Skill;

/**
 * Core tuning knobs for the Five Active game mode. Change these to rebalance the mode.
 * Boss tiers and milestones live in {@link BossTiers}; quest prerequisites live in {@link QuestRequirements}.
 */
final class Rules
{
	/** How many skills / bosses / quests are active at once. */
	static final int SLOTS = 5;

	/** Level-ups needed in an active skill before it is completed and can be replaced. */
	static final int LEVELS_TO_COMPLETE_SKILL = 5;

	/** Kills needed on an active boss before it is completed and can be replaced. */
	static final int KILLS_TO_COMPLETE_BOSS = 5;

	/** How long the "NEW" badge shows on a freshly rolled slot. */
	static final int NEW_BADGE_SECONDS = 7;

	/** Collection log slots needed per Shuffle earned. */
	static final int CLOG_SLOTS_PER_SHUFFLE = 50;

	/** At least one active skill must always come from this pool so the player can fight. */
	static final Set<Skill> COMBAT_SKILLS = ImmutableSet.of(
		Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC);

	/** Skills that can never be rolled. Hitpoints is always considered active. */
	static final Set<Skill> NEVER_ROLLED = ImmutableSet.of(Skill.HITPOINTS);

	/** Skills that cannot be rolled until the listed quest is complete. */
	static final Map<Skill, Quest> SKILL_UNLOCK_QUESTS = ImmutableMap.of(
		Skill.HERBLORE, Quest.DRUIDIC_RITUAL,
		Skill.RUNECRAFT, Quest.RUNE_MYSTERIES,
		Skill.SAILING, Quest.PANDEMONIUM);

	/** Bank values the History view marks the first time the bank is worth them. */
	static final long[] BANK_MILESTONES = {100_000L, 1_000_000L, 10_000_000L, 100_000_000L, 1_000_000_000L};

	private Rules()
	{
	}
}
