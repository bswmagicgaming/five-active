package com.bswmagicgaming.fiveactive;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One active goal (a skill, boss or quest) and how far the player has got with it.
 * A completed slot stays visible (marked done) until the player rolls its replacement.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
class Slot<T>
{
	private T value;
	/** Level-ups for skills, kills for bosses. Unused for quests. */
	private int progress;
	private boolean done;
	/** Shows a "NEW" badge until it expires (see {@link Rules#NEW_BADGE_SECONDS}) or the player makes progress. */
	private boolean fresh;
	/** When this slot was rolled (epoch millis), for expiring the NEW badge. */
	private long rolledAt;
	/** When this slot was completed (epoch millis), or 0 if it isn't or it was before this was kept. */
	private long doneAt;

	static <T> Slot<T> of(T value)
	{
		return new Slot<>(value, 0, false, true, System.currentTimeMillis(), 0);
	}
}
