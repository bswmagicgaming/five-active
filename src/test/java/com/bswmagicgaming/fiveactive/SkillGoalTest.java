package com.bswmagicgaming.fiveactive;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class SkillGoalTest
{
	/** level rolled at, level-ups already counted, level-ups needed */
	private static void check(int level, int progress, int goal)
	{
		assertEquals("progress at " + level, progress, Rules.skillProgressFrom(level));
		assertEquals("goal at " + level, goal, Rules.skillGoalFrom(level));
	}

	@Test
	public void completesOnMultiplesOfFive()
	{
		check(1, 1, 5);
		check(4, 4, 5);
		check(5, 0, 5);
		check(12, 2, 5);
		check(90, 0, 5);
		check(94, 4, 5);
	}

	@Test
	public void lastStretchStopsAt99()
	{
		check(95, 0, 4);
		check(96, 1, 4);
		check(97, 2, 4);
		check(98, 3, 4);
	}

	@Test
	public void loggedLevelUpsShowTheRightGoal()
	{
		assertEquals(5, Rules.skillGoalAtLevel(50));
		assertEquals(5, Rules.skillGoalAtLevel(95));
		assertEquals(4, Rules.skillGoalAtLevel(96));
		assertEquals(4, Rules.skillGoalAtLevel(99));
	}
}
