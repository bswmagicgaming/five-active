package com.bswmagicgaming.fiveactive;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * One thing that happened during a run, for the History view. Only the facts are stored (what, when, how much),
 * never display text, so the wording can change without touching old logs. Unused fields stay null / 0.
 */
@Data
class HistoryEntry
{
	enum Kind
	{
		/** A brand new run: nothing had been rolled yet. */
		RUN_STARTED,
		/** The log began part-way through a run (e.g. the run predates it). active holds what was active then. */
		LOG_STARTED,
		/** Slots filled by the Roll button. names are what was rolled. */
		ROLLED,
		/** A Shuffle. names came in, replaced went out. */
		SHUFFLED,
		/** A slot finished: names[0] in category. */
		COMPLETED,
		/** A skill reached 99. */
		MAXED,
		/** names[0] reached level value; progress is the active slot's level-ups so far, or -1 if it wasn't active. */
		LEVEL_UP,
		/** names[0] went into the collection log. value is its item ID (0 in logs from before IDs were kept, -1 if unknown). */
		CLOG_ITEM,
		/** value slots were added to the collection log without their names (or synced for the first time: progress 1). */
		CLOG_SLOTS,
		/** value Shuffles earned from the collection log. */
		SHUFFLE_EARNED,
		/** Tier value reached its KC (progress) and is ready to unlock. */
		TIER_READY,
		/** Tier value was unlocked, opening progress bosses. */
		TIER_UNLOCKED,
		/** The bank was first seen worth at least value coins. */
		BANK_VALUE,
		/** The run was restored from a backup. */
		RESTORED,
	}

	private long time;
	private Kind kind;
	private Category category;
	private List<String> names;
	private List<String> replaced;
	private long value;
	private int progress;
	/** LOG_STARTED only: the names active in each category. */
	private Map<Category, List<String>> active;

	static HistoryEntry of(Kind kind)
	{
		HistoryEntry entry = new HistoryEntry();
		entry.time = System.currentTimeMillis();
		entry.kind = kind;
		return entry;
	}

	HistoryEntry category(Category category)
	{
		this.category = category;
		return this;
	}

	HistoryEntry names(List<String> names)
	{
		this.names = names;
		return this;
	}

	HistoryEntry name(String name)
	{
		return names(Collections.singletonList(name));
	}

	HistoryEntry replaced(List<String> replaced)
	{
		this.replaced = replaced;
		return this;
	}

	HistoryEntry value(long value)
	{
		this.value = value;
		return this;
	}

	HistoryEntry progress(int progress)
	{
		this.progress = progress;
		return this;
	}

	HistoryEntry active(Map<Category, List<String>> active)
	{
		this.active = active;
		return this;
	}

	/** The first name, or "" (entries read from an old or hand-edited file may have none). */
	String name()
	{
		return names == null || names.isEmpty() ? "" : names.get(0);
	}

	List<String> nameList()
	{
		return names == null ? Collections.emptyList() : names;
	}

	List<String> replacedList()
	{
		return replaced == null ? Collections.emptyList() : replaced;
	}
}
