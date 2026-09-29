package com.bswmagicgaming.fiveactive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * Decides what can be rolled and performs the rolls. Must be used on the client thread,
 * because reading quest state runs a client script.
 */
@Singleton
class RollingEngine
{
	private static final int MAX_LEVEL = 99;

	private final Client client;
	private final Random random = new Random();

	@Inject
	RollingEngine(Client client)
	{
		this.client = client;
	}

	/** Reads every quest's state once. Pass the result around instead of calling getState repeatedly. */
	Map<Quest, QuestState> questStates()
	{
		Map<Quest, QuestState> states = new EnumMap<>(Quest.class);
		for (Quest quest : Quest.values())
		{
			states.put(quest, quest.getState(client));
		}
		return states;
	}

	private static boolean finished(Map<Quest, QuestState> states, Quest quest)
	{
		return states.get(quest) == QuestState.FINISHED;
	}

	// ---------------------------------------------------------------- Skills

	boolean isSkillUnlocked(Skill skill, Map<Quest, QuestState> states)
	{
		Quest unlock = Rules.SKILL_UNLOCK_QUESTS.get(skill);
		return unlock == null || finished(states, unlock);
	}

	/** A skill at 99 can't be levelled further, so it is permanently unlocked instead of rollable. */
	boolean isMaxed(Skill skill)
	{
		return client.getRealSkillLevel(skill) >= MAX_LEVEL;
	}

	/** Skills that could be rolled right now (ignoring which are already active). */
	List<Skill> rollableSkills(Map<Quest, QuestState> states)
	{
		List<Skill> skills = new ArrayList<>();
		for (Skill skill : Skill.values())
		{
			if (!Rules.NEVER_ROLLED.contains(skill)
				&& isSkillUnlocked(skill, states)
				&& !isMaxed(skill))
			{
				skills.add(skill);
			}
		}
		return skills;
	}

	/**
	 * Replaces completed skill slots (or every slot when replaceAll), keeping untouched slots in place.
	 * One active skill must always be a combat skill below 99 (maxed skills aren't rollable). Only once every
	 * combat skill is 99 are there none left to require, and all five can be non-combat.
	 * Fills fewer than five slots once there aren't enough rollable skills left.
	 */
	List<Slot<Skill>> rollSkills(List<Slot<Skill>> current, boolean replaceAll, Map<Quest, QuestState> states)
	{
		return replaceAll
			? shuffle(current, rollableSkills(states), Rules.COMBAT_SKILLS::contains)
			: refill(current, false, rollableSkills(states), Rules.COMBAT_SKILLS::contains);
	}

	// ---------------------------------------------------------------- Bosses

	/**
	 * Why a boss can't be rolled right now, or null if it can. Ignores whether it is already active.
	 * unlockedTiers is how many tiers have been claimed; totalKc only decides how a locked tier is described.
	 */
	String bossLockReason(Boss boss, int unlockedTiers, int totalKc, Map<Quest, QuestState> states)
	{
		BossTiers.Tier tier = BossTiers.tierOf(boss);
		if (tier == null)
		{
			return "Not in the boss pool";
		}
		int index = BossTiers.TIERS.indexOf(tier);
		if (index >= unlockedTiers)
		{
			return totalKc >= tier.getKillsRequired()
				? "Unlock Tier " + (index + 1) + " in the Bosses section"
				: "Unlocks at " + tier.getKillsRequired() + " total KC";
		}
		for (Quest quest : boss.getRequiredQuests())
		{
			if (!finished(states, quest))
			{
				return "Needs " + quest.getName();
			}
		}
		if (boss.getSlayerLevel() > 0 && client.getRealSkillLevel(Skill.SLAYER) < boss.getSlayerLevel())
		{
			return "Needs " + boss.getSlayerLevel() + " Slayer";
		}
		return null;
	}

	/** Bosses that could be rolled right now (ignoring which are already active). */
	List<Boss> rollableBosses(int unlockedTiers, Map<Quest, QuestState> states)
	{
		List<Boss> bosses = new ArrayList<>();
		for (BossTiers.Tier tier : BossTiers.TIERS)
		{
			for (Boss boss : tier.getBosses())
			{
				if (bossLockReason(boss, unlockedTiers, 0, states) == null)
				{
					bosses.add(boss);
				}
			}
		}
		return bosses;
	}

	List<Slot<Boss>> rollBosses(List<Slot<Boss>> current, boolean replaceAll, int unlockedTiers, Map<Quest, QuestState> states)
	{
		return replaceAll
			? shuffle(current, rollableBosses(unlockedTiers, states), null)
			: refill(current, false, rollableBosses(unlockedTiers, states), null);
	}

	// ---------------------------------------------------------------- Quests

	boolean isQuestRollable(Quest quest, Map<Quest, QuestState> states)
	{
		// Miniquests are rolled like any other quest
		if (QuestRequirements.NEVER_ROLLED.contains(quest) || finished(states, quest))
		{
			return false;
		}
		for (Quest prereq : QuestRequirements.prerequisites(quest))
		{
			if (!finished(states, prereq))
			{
				return false;
			}
		}
		return true;
	}

	List<Quest> rollableQuests(Map<Quest, QuestState> states)
	{
		List<Quest> quests = new ArrayList<>();
		for (Quest quest : Quest.values())
		{
			if (isQuestRollable(quest, states))
			{
				quests.add(quest);
			}
		}
		return quests;
	}

	List<Slot<Quest>> rollQuests(List<Slot<Quest>> current, boolean replaceAll, Map<Quest, QuestState> states)
	{
		return replaceAll
			? shuffle(current, rollableQuests(states), null)
			: refill(current, false, rollableQuests(states), null);
	}

	// ---------------------------------------------------------------- Shuffles

	boolean canShuffleSkills(List<Slot<Skill>> current, Map<Quest, QuestState> states)
	{
		return canShuffle(current, rollableSkills(states));
	}

	boolean canShuffleBosses(List<Slot<Boss>> current, int unlockedTiers, Map<Quest, QuestState> states)
	{
		return canShuffle(current, rollableBosses(unlockedTiers, states));
	}

	boolean canShuffleQuests(List<Slot<Quest>> current, Map<Quest, QuestState> states)
	{
		return canShuffle(current, rollableQuests(states));
	}

	/** A shuffle is only worth spending if there is at least one option that isn't already in the category. */
	private static <T> boolean canShuffle(List<Slot<T>> current, List<T> eligible)
	{
		Set<T> currentValues = current.stream().map(Slot::getValue).collect(Collectors.toSet());
		return !current.isEmpty() && eligible.stream().anyMatch(v -> !currentValues.contains(v));
	}

	/**
	 * Re-rolls every slot, favouring new outcomes as hard as possible: if there are at least five options
	 * not currently in the category, all five results are new. If there are fewer, every one of them is
	 * included and the remaining slots are filled from the current values.
	 *
	 * @param mustHaveOne if non-null and any eligible value satisfies it, one result must satisfy it
	 * (taken from the new options when possible, otherwise kept from the current values)
	 */
	private <T> List<Slot<T>> shuffle(List<Slot<T>> current, List<T> eligible, Predicate<T> mustHaveOne)
	{
		Set<T> currentValues = current.stream().map(Slot::getValue).collect(Collectors.toSet());
		List<T> fresh = eligible.stream().filter(v -> !currentValues.contains(v)).collect(Collectors.toList());
		List<T> old = eligible.stream().filter(currentValues::contains).collect(Collectors.toList());
		Collections.shuffle(fresh, random);
		Collections.shuffle(old, random);

		List<T> picks = new ArrayList<>();
		if (mustHaveOne != null && eligible.stream().anyMatch(mustHaveOne))
		{
			T required = fresh.stream().filter(mustHaveOne).findFirst()
				.orElseGet(() -> old.stream().filter(mustHaveOne).findFirst().orElse(null));
			picks.add(required);
			fresh.remove(required);
			old.remove(required);
		}
		while (picks.size() < Rules.SLOTS && !fresh.isEmpty())
		{
			picks.add(fresh.remove(0));
		}
		while (picks.size() < Rules.SLOTS && !old.isEmpty())
		{
			picks.add(old.remove(0));
		}
		// Don't always put the required pick in the top slot
		Collections.shuffle(picks, random);
		return picks.stream().map(Slot::of).collect(Collectors.toList());
	}

	// ---------------------------------------------------------------- Shared

	/** How many slots a free roll would fill (completed slots plus empty ones). */
	static int openSlots(List<? extends Slot<?>> slots)
	{
		int done = (int) slots.stream().filter(Slot::isDone).count();
		return done + Math.max(0, Rules.SLOTS - slots.size());
	}

	/**
	 * Fills every open position (completed slots, empty slots, or all slots when replaceAll) with a random
	 * eligible value. Kept slots stay in their position so the panel doesn't jump around. Values that were
	 * just in a slot are avoided when possible so a re-roll always feels like a change.
	 *
	 * @param mustHaveOne if non-null, at least one slot must satisfy this (e.g. a combat skill), when possible
	 */
	private <T> List<Slot<T>> refill(List<Slot<T>> current, boolean replaceAll, List<T> eligible, Predicate<T> mustHaveOne)
	{
		List<Slot<T>> result = new ArrayList<>();
		List<Integer> open = new ArrayList<>();
		for (int i = 0; i < Rules.SLOTS; i++)
		{
			Slot<T> slot = i < current.size() ? current.get(i) : null;
			if (slot == null || slot.isDone() || replaceAll)
			{
				open.add(i);
				result.add(null);
			}
			else
			{
				result.add(slot);
			}
		}

		Set<T> taken = result.stream().filter(s -> s != null).map(Slot::getValue).collect(Collectors.toCollection(HashSet::new));
		Set<T> recent = current.stream().map(Slot::getValue).collect(Collectors.toSet());
		boolean needMandatory = mustHaveOne != null && taken.stream().noneMatch(mustHaveOne);

		for (int index : open)
		{
			List<T> candidates = eligible.stream().filter(v -> !taken.contains(v)).collect(Collectors.toList());
			if (needMandatory)
			{
				List<T> mandatory = candidates.stream().filter(mustHaveOne).collect(Collectors.toList());
				// If nothing eligible satisfies the requirement, fill the slot anyway rather than leave it empty
				if (!mandatory.isEmpty())
				{
					candidates = mandatory;
				}
			}

			T pick = pick(candidates, recent);
			if (pick == null)
			{
				continue;
			}
			result.set(index, Slot.of(pick));
			taken.add(pick);
			needMandatory = false;
		}

		result.removeIf(s -> s == null);
		return result;
	}

	/** Random candidate, preferring ones not in avoid. Null if there are no candidates. */
	private <T> T pick(List<T> candidates, Set<T> avoid)
	{
		List<T> preferred = candidates.stream().filter(v -> !avoid.contains(v)).collect(Collectors.toList());
		List<T> from = preferred.isEmpty() ? candidates : preferred;
		return from.isEmpty() ? null : from.get(random.nextInt(from.size()));
	}
}
