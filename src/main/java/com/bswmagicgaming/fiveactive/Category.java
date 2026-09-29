package com.bswmagicgaming.fiveactive;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
enum Category
{
	SKILLS("Skills", "skill", "skills"),
	BOSSES("Bosses", "boss", "bosses"),
	QUESTS("Quests", "quest", "quests");

	private final String title;
	private final String singular;
	private final String plural;

	String noun(int count)
	{
		return count == 1 ? singular : plural;
	}
}
