package com.bswmagicgaming.fiveactive;

import com.google.gson.Gson;
import java.util.Collections;
import net.runelite.api.Skill;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class RunHistoryTest
{
	private final Gson gson = new Gson();

	@Test
	public void saveCodeRoundTrips()
	{
		FiveActiveState state = new FiveActiveState();
		state.getSkills().add(Slot.of(Skill.FISHING));
		state.setShufflesSpent(3);
		state.setUnlockedTiers(2);
		String json = gson.toJson(state);

		RunHistory history = new RunHistory(gson, null);
		String code = history.saveCode(json);
		// Survives being wrapped across lines when pasted
		RunHistory.SaveCode read = history.readSaveCode("  " + code.substring(0, 10) + "\n" + code.substring(10) + " ");

		assertNotNull(read);
		FiveActiveState restored = gson.fromJson(read.getStateJson(), FiveActiveState.class);
		assertEquals(Skill.FISHING, restored.getSkills().get(0).getValue());
		assertEquals(3, restored.getShufflesSpent());
		assertEquals(Integer.valueOf(2), restored.getUnlockedTiers());
		assertEquals(Collections.emptyList(), read.getHistory());
	}

	@Test
	public void rejectsAnythingElse()
	{
		RunHistory history = new RunHistory(gson, null);
		assertNull(history.readSaveCode(""));
		assertNull(history.readSaveCode("hello"));
		assertNull(history.readSaveCode("FA1:not base64!"));
		assertNull(history.readSaveCode("FA1:aGVsbG8="));
	}
}
