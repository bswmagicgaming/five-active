package com.bswmagicgaming.fiveactive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SkillRollingTest
{
	private static final int ROLLS = 300;

	@Test
	public void alwaysRollsACombatSkillBelow99()
	{
		// Attack and Strength are maxed, so the required combat skill must be Defence, Ranged or Magic
		RollingEngine engine = engineWithMaxed(Skill.ATTACK, Skill.STRENGTH);
		for (int i = 0; i < ROLLS; i++)
		{
			List<Skill> skills = values(engine.rollSkills(new ArrayList<>(), false, allQuestsDone()));
			assertEquals(Rules.SLOTS, skills.size());
			assertTrue("No combat skill below 99 in " + skills,
				skills.stream().anyMatch(s -> s == Skill.DEFENCE || s == Skill.RANGED || s == Skill.MAGIC));
			assertFalse("Rolled a maxed skill in " + skills, skills.contains(Skill.ATTACK) || skills.contains(Skill.STRENGTH));
		}
	}

	@Test
	public void combatStillRequiredWithOnlyOneCombatSkillLeft()
	{
		RollingEngine engine = engineWithMaxed(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED);
		for (int i = 0; i < ROLLS; i++)
		{
			List<Skill> skills = values(engine.rollSkills(new ArrayList<>(), false, allQuestsDone()));
			assertTrue("Magic should always be rolled, got " + skills, skills.contains(Skill.MAGIC));
		}
	}

	@Test
	public void allNonCombatOnceEveryCombatSkillIs99()
	{
		RollingEngine engine = engineWithMaxed(Rules.COMBAT_SKILLS.toArray(new Skill[0]));
		for (int i = 0; i < ROLLS; i++)
		{
			List<Skill> skills = values(engine.rollSkills(new ArrayList<>(), false, allQuestsDone()));
			assertEquals(Rules.SLOTS, skills.size());
			assertTrue("Rolled a maxed combat skill in " + skills, skills.stream().noneMatch(Rules.COMBAT_SKILLS::contains));
		}
	}

	@Test
	public void replacingTheOnlyCombatSlotRollsAnotherCombatSkill()
	{
		RollingEngine engine = engineWithMaxed(Skill.ATTACK);
		List<Slot<Skill>> current = new ArrayList<>();
		current.add(new Slot<>(Skill.STRENGTH, 5, true, false, 0, 0)); // completed combat slot
		for (Skill skill : Arrays.asList(Skill.COOKING, Skill.FISHING, Skill.MINING, Skill.AGILITY))
		{
			current.add(Slot.of(skill));
		}
		for (int i = 0; i < ROLLS; i++)
		{
			List<Skill> skills = values(engine.rollSkills(current, false, allQuestsDone()));
			assertTrue("Replacement for the combat slot must be combat, got " + skills.get(0),
				Rules.COMBAT_SKILLS.contains(skills.get(0)) && skills.get(0) != Skill.ATTACK);
		}
	}

	@Test
	public void rollsFewerThanFiveWhenFewSkillsAreLeft()
	{
		// Everything maxed except three skills
		Set<Skill> maxed = EnumSet.allOf(Skill.class);
		maxed.removeAll(Arrays.asList(Skill.COOKING, Skill.FISHING, Skill.SAILING));
		RollingEngine engine = engineWithMaxed(maxed.toArray(new Skill[0]));
		List<Skill> skills = values(engine.rollSkills(new ArrayList<>(), false, allQuestsDone()));
		assertEquals(EnumSet.of(Skill.COOKING, Skill.FISHING, Skill.SAILING), EnumSet.copyOf(skills));
	}

	@Test
	public void shuffleGivesAllNewSkillsWhenEnoughAreLeft()
	{
		RollingEngine engine = engineWithMaxed();
		List<Slot<Skill>> current = slots(Skill.ATTACK, Skill.MAGIC, Skill.RANGED, Skill.MINING, Skill.SMITHING);
		for (int i = 0; i < ROLLS; i++)
		{
			List<Skill> skills = values(engine.rollSkills(current, true, allQuestsDone()));
			assertEquals(Rules.SLOTS, skills.size());
			for (Slot<Skill> slot : current)
			{
				assertFalse("Shuffle kept " + slot.getValue() + ": " + skills, skills.contains(slot.getValue()));
			}
			// Attack, Magic and Ranged were current, so the combat pick has to be Strength or Defence
			assertTrue("No new combat skill in " + skills, skills.contains(Skill.STRENGTH) || skills.contains(Skill.DEFENCE));
		}
	}

	@Test
	public void shuffleIncludesEveryNewOptionWhenFewAreLeft()
	{
		// Only seven skills left to level: the five current ones plus Cooking and Fishing
		Set<Skill> maxed = EnumSet.allOf(Skill.class);
		maxed.removeAll(Arrays.asList(Skill.ATTACK, Skill.MINING, Skill.SMITHING, Skill.AGILITY, Skill.THIEVING, Skill.COOKING, Skill.FISHING));
		RollingEngine engine = engineWithMaxed(maxed.toArray(new Skill[0]));
		List<Slot<Skill>> current = slots(Skill.ATTACK, Skill.MINING, Skill.SMITHING, Skill.AGILITY, Skill.THIEVING);
		assertTrue(engine.canShuffleSkills(current, allQuestsDone()));
		for (int i = 0; i < ROLLS; i++)
		{
			List<Skill> skills = values(engine.rollSkills(current, true, allQuestsDone()));
			assertEquals(Rules.SLOTS, skills.size());
			assertTrue("Both new skills must be included: " + skills, skills.contains(Skill.COOKING) && skills.contains(Skill.FISHING));
			// Attack is the only combat skill left, so it has to stay
			assertTrue("Attack must be kept for combat: " + skills, skills.contains(Skill.ATTACK));
		}
	}

	@Test
	public void cannotShuffleWhenNothingNewIsLeft()
	{
		// Only the five current skills are below 99
		Set<Skill> maxed = EnumSet.allOf(Skill.class);
		maxed.removeAll(Arrays.asList(Skill.ATTACK, Skill.MINING, Skill.SMITHING, Skill.AGILITY, Skill.THIEVING));
		RollingEngine engine = engineWithMaxed(maxed.toArray(new Skill[0]));
		assertFalse(engine.canShuffleSkills(slots(Skill.ATTACK, Skill.MINING, Skill.SMITHING, Skill.AGILITY, Skill.THIEVING), allQuestsDone()));
		// Nothing rolled yet: nothing to shuffle either
		assertFalse(engine.canShuffleSkills(new ArrayList<>(), allQuestsDone()));
	}

	@Test
	public void shuffleKeepsACurrentCombatSkillWhenNoNewOneExists()
	{
		// Attack is the only combat skill below 99, and it's already active
		RollingEngine engine = engineWithMaxed(Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC);
		List<Slot<Skill>> current = slots(Skill.ATTACK, Skill.COOKING, Skill.FISHING, Skill.MINING, Skill.SMITHING);
		for (int i = 0; i < ROLLS; i++)
		{
			List<Skill> skills = values(engine.rollSkills(current, true, allQuestsDone()));
			assertTrue("Attack must be kept for combat: " + skills, skills.contains(Skill.ATTACK));
			assertTrue("Everything else must be new: " + skills, skills.stream()
				.filter(s -> s != Skill.ATTACK)
				.noneMatch(s -> s == Skill.COOKING || s == Skill.FISHING || s == Skill.MINING || s == Skill.SMITHING));
		}
	}

	@Test
	public void combatRuleHoldsForRandomRollsAndShuffles()
	{
		// Random maxed skills, random active slots (some finished), random roll or shuffle. Whenever a roll can
		// change anything, an unfinished combat skill must be active afterwards if any is still below 99.
		Random random = new Random(1);
		for (int i = 0; i < 5000; i++)
		{
			Set<Skill> maxed = EnumSet.noneOf(Skill.class);
			for (Skill skill : Skill.values())
			{
				if (random.nextInt(4) == 0)
				{
					maxed.add(skill);
				}
			}
			RollingEngine engine = engineWithMaxed(maxed.toArray(new Skill[0]));
			List<Skill> pool = engine.rollableSkills(allQuestsDone());
			Collections.shuffle(pool, random);
			List<Slot<Skill>> current = new ArrayList<>();
			for (int s = 0; s < random.nextInt(6) && s < pool.size(); s++)
			{
				current.add(new Slot<>(pool.get(s), 0, random.nextBoolean(), false, 0, 0));
			}
			boolean shuffle = random.nextBoolean() && !current.isEmpty();
			if (!shuffle && RollingEngine.openSlots(current) == 0)
			{
				continue; // nothing to roll
			}
			List<Slot<Skill>> rolled = engine.rollSkills(current, shuffle, allQuestsDone());
			if (pool.stream().anyMatch(Rules.COMBAT_SKILLS::contains))
			{
				assertTrue("No active combat skill after rolling " + values(current) + ": " + values(rolled),
					rolled.stream().anyMatch(s -> !s.isDone() && Rules.COMBAT_SKILLS.contains(s.getValue())));
			}
		}
	}

	private static List<Slot<Skill>> slots(Skill... skills)
	{
		List<Slot<Skill>> slots = new ArrayList<>();
		for (Skill skill : skills)
		{
			slots.add(Slot.of(skill));
		}
		return slots;
	}

	private static RollingEngine engineWithMaxed(Skill... maxed)
	{
		Set<Skill> maxedSet = maxed.length == 0 ? EnumSet.noneOf(Skill.class) : EnumSet.copyOf(Arrays.asList(maxed));
		Client client = mock(Client.class);
		when(client.getRealSkillLevel(any(Skill.class)))
			.thenAnswer(invocation -> maxedSet.contains(invocation.<Skill>getArgument(0)) ? 99 : 50);
		return new RollingEngine(client);
	}

	/** Every quest finished, so no skill is quest-locked. */
	private static Map<Quest, QuestState> allQuestsDone()
	{
		Map<Quest, QuestState> states = new EnumMap<>(Quest.class);
		for (Quest quest : Quest.values())
		{
			states.put(quest, QuestState.FINISHED);
		}
		return states;
	}

	private static List<Skill> values(List<Slot<Skill>> slots)
	{
		List<Skill> skills = new ArrayList<>();
		slots.forEach(s -> skills.add(s.getValue()));
		return skills;
	}
}
