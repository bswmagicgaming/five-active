package com.bswmagicgaming.fiveactive;

import java.util.Arrays;
import java.util.Map;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class NameLookupTest
{
	@Test
	public void everyShorthandPointsAtARealName()
	{
		for (Map.Entry<String, String> e : NameLookup.SKILL_SHORTHAND.entrySet())
		{
			assertNotNull("skill shorthand " + e.getKey(), NameLookup.skill(e.getKey()).getMatch());
		}
		for (Map.Entry<String, String> e : NameLookup.BOSS_SHORTHAND.entrySet())
		{
			assertNotNull("boss shorthand " + e.getKey(), NameLookup.boss(e.getKey()).getMatch());
		}
		for (Map.Entry<String, String> e : NameLookup.QUEST_SHORTHAND.entrySet())
		{
			NameLookup.Result<Quest> found = NameLookup.quest(e.getKey());
			assertNotNull("quest shorthand " + e.getKey(), found.getMatch());
			assertEquals("quest shorthand " + e.getKey(), e.getValue(), found.getMatch().getName());
		}
	}

	@Test
	public void skillsAndBossesNeedTheExactNameInAnyCase()
	{
		assertEquals(Skill.FIREMAKING, NameLookup.skill("firemaking").getMatch());
		assertEquals(Skill.FIREMAKING, NameLookup.skill("FireMaking").getMatch());
		assertEquals(Skill.FIREMAKING, NameLookup.skill("fm").getMatch());
		assertNull(NameLookup.skill("fire").getMatch());
		assertEquals(Boss.BRUTUS, NameLookup.boss("brutus").getMatch());
		assertEquals(Boss.BARROWS, NameLookup.boss("BARROWS").getMatch());
		assertEquals(Boss.KREEARRA, NameLookup.boss("kree'arra").getMatch());
		assertEquals(Boss.THE_GAUNTLET, NameLookup.boss("  the   gauntlet ").getMatch());
		assertNull(NameLookup.boss("vork kath").getMatch());
	}

	@Test
	public void questsIgnoreCaseAndPunctuation()
	{
		assertEquals(Quest.COOKS_ASSISTANT, NameLookup.quest("cooks assistant").getMatch());
		assertEquals(Quest.ROMEO__JULIET, NameLookup.quest("romeo and juliet").getMatch());
		assertEquals(Quest.DRAGON_SLAYER_I, NameLookup.quest("dragon slayer i").getMatch());
		assertEquals(Quest.DRAGON_SLAYER_II, NameLookup.quest("Dragon Slayer 2").getMatch());
		assertEquals(Quest.DRAGON_SLAYER_II, NameLookup.quest("ds2").getMatch());
		assertEquals(Quest.ANOTHER_SLICE_OF_HAM, NameLookup.quest("another slice of ham").getMatch());
		// A unique start is enough
		assertEquals(Quest.UNDERGROUND_PASS, NameLookup.quest("underground").getMatch());
	}

	@Test
	public void ambiguousQuestsListTheChoices()
	{
		NameLookup.Result<Quest> found = NameLookup.quest("dragon slayer");
		assertNull(found.getMatch());
		assertEquals(Arrays.asList("Dragon Slayer I", "Dragon Slayer II"), found.getOptions());

		NameLookup.Result<Quest> many = NameLookup.quest("the");
		assertNull(many.getMatch());
		assertTrue(many.getMore() > 0);

		assertNull(NameLookup.quest("not a quest").getMatch());
		assertTrue(NameLookup.quest("not a quest").getOptions().isEmpty());
	}
}
