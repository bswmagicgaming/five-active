package com.bswmagicgaming.fiveactive;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import net.runelite.api.Quest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

/**
 * Sanity checks for the hand-edited boss / quest data, so typos in the tables are caught early.
 */
public class FiveActiveDataTest
{
	@Test
	public void questPrerequisitesHaveNoCycles()
	{
		for (Quest quest : Quest.values())
		{
			assertNoCycle(quest, EnumSet.noneOf(Quest.class));
		}
	}

	private static void assertNoCycle(Quest quest, Set<Quest> path)
	{
		if (!path.add(quest))
		{
			fail("Quest prerequisite cycle through " + quest + ": " + path);
		}
		for (Quest prereq : QuestRequirements.prerequisites(quest))
		{
			assertNoCycle(prereq, EnumSet.copyOf(path));
		}
	}

	@Test
	public void biohazardNeedsPlagueCity()
	{
		assertTrue(QuestRequirements.prerequisites(Quest.BIOHAZARD).contains(Quest.PLAGUE_CITY));
		assertTrue(QuestRequirements.prerequisites(Quest.PLAGUE_CITY).isEmpty());
	}

	@Test
	public void everyBossIsInAtMostOneTier()
	{
		Set<Boss> seen = new HashSet<>();
		for (BossTiers.Tier tier : BossTiers.TIERS)
		{
			for (Boss boss : tier.getBosses())
			{
				assertTrue(boss + " is in more than one tier", seen.add(boss));
			}
		}
	}

	@Test
	public void tiersAreInAscendingOrderStartingAtZero()
	{
		assertEquals(0, BossTiers.TIERS.get(0).getKillsRequired());
		for (int i = 1; i < BossTiers.TIERS.size(); i++)
		{
			assertTrue(BossTiers.TIERS.get(i).getKillsRequired() > BossTiers.TIERS.get(i - 1).getKillsRequired());
		}
	}

	@Test
	public void killCountNamesAreUnique()
	{
		Map<String, Boss> owners = new HashMap<>();
		for (Boss boss : Boss.values())
		{
			for (String name : boss.getKcNames())
			{
				Boss previous = owners.put(Boss.normalizeKcName(name), boss);
				assertNull("KC name '" + name + "' used by both " + previous + " and " + boss, previous);
			}
		}
	}

	@Test
	public void parsesKillCountMessages()
	{
		assertKc("Your Vorkath kill count is: 12.", Boss.VORKATH, 12);
		assertKc("Your Barrows chest count is: 1,234.", Boss.BARROWS, 1234);
		assertKc("Your completion count for Tombs of Amascut: Expert Mode is: 7.", Boss.TOMBS_OF_AMASCUT, 7);
		assertKc("Your completed Chambers of Xeric count is: 3.", Boss.CHAMBERS_OF_XERIC, 3);
		assertKc("Your Gauntlet completion count is: 40.", Boss.THE_GAUNTLET, 40);
		assertKc("Your subdued Wintertodt count is: 500.", Boss.WINTERTODT, 500);
		assertKc("Your Kree'arra kill count is: 9.", Boss.KREEARRA, 9);
		assertKc("Your TzTok-Jad kill count is: 1.", Boss.FIGHT_CAVES, 1);
		assertKc("Your Dagannoth Prime kill count is: 20.", Boss.DAGANNOTH_KINGS, 20);
		assertKc("Your Lunar Chest count is: 4.", Boss.PERILOUS_MOONS, 4);
		assertKc("Your Phosani's Nightmare kill count is: 2.", Boss.THE_NIGHTMARE, 2);
		assertKc("Your Corrupted Gauntlet completion count is: 6.", Boss.THE_GAUNTLET, 6);
	}

	@Test
	public void everyTieredBossHasAnIcon()
	{
		for (BossTiers.Tier tier : BossTiers.TIERS)
		{
			for (Boss boss : tier.getBosses())
			{
				assertNotNull(boss + " has no hiscores icon; add hiscore(...) to its declaration", boss.getHiscore());
			}
		}
		assertEquals(net.runelite.client.hiscore.HiscoreSkill.LUNAR_CHESTS, Boss.PERILOUS_MOONS.getHiscore());
		assertEquals(net.runelite.client.hiscore.HiscoreSkill.THE_ROYAL_TITANS, Boss.ROYAL_TITANS.getHiscore());
		assertEquals(net.runelite.client.hiscore.HiscoreSkill.TZTOK_JAD, Boss.FIGHT_CAVES.getHiscore());
	}

	@Test
	public void waveBossesCompleteAfterOneKill()
	{
		assertEquals(1, Boss.FIGHT_CAVES.getKillsToComplete());
		assertEquals(1, Boss.INFERNO.getKillsToComplete());
		assertEquals(1, Boss.FORTIS_COLOSSEUM.getKillsToComplete());
		assertEquals(Rules.KILLS_TO_COMPLETE_BOSS, Boss.VORKATH.getKillsToComplete());
		assertEquals(95, Boss.ALCHEMICAL_HYDRA.getSlayerLevel());
		assertEquals(0, Boss.VORKATH.getSlayerLevel());
	}

	@Test
	public void ignoresNonBossMessages()
	{
		Matcher matcher = ProgressionListener.KILLCOUNT_PATTERN.matcher("Your reward is: 5 coins.");
		assertFalse(matcher.find() && Boss.fromKcName(matcher.group("boss")) != null);
	}

	private static void assertKc(String message, Boss expected, int kc)
	{
		Matcher matcher = ProgressionListener.KILLCOUNT_PATTERN.matcher(message);
		assertTrue("No match: " + message, matcher.find());
		String name = Boss.normalizeKcName(matcher.group("boss"));
		// Mirror ProgressionListener's rename of the Barrows counter
		name = name.equals("barrows chest") ? "barrows chests" : name;
		Boss boss = Boss.fromKcName(name);
		assertNotNull("No boss for '" + name + "'", boss);
		assertEquals(expected, boss);
		assertEquals(kc, Integer.parseInt(matcher.group("kc").replace(",", "")));
	}
}
