package com.bswmagicgaming.fiveactive;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.SoundEffectID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.Item;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Tracks progress on active goals: level-ups, boss kills, quest completion and collection log slots.
 * All handlers run on the client thread.
 */
class ProgressionListener
{
	/** Same shape as the Chat Commands plugin's pattern, applied to tag-stripped text. */
	static final Pattern KILLCOUNT_PATTERN = Pattern.compile(
		"Your (?:completion count for |subdued |completed )?(?<boss>.+?) (?:(?:kill|harvest|lap|completion|success) )?(?:count )?is: ?(?<kc>[0-9,]+)");
	private static final Pattern NEW_CLOG_ITEM = Pattern.compile("New item added to your collection log: ?(?<item>.*)");
	/** In-game counter names that the Chat Commands plugin stores under a different name. */
	private static final Map<String, String> KC_RENAMES = ImmutableMap.of("barrows chest", "barrows chests");
	private static final int QUEST_CHECK_INTERVAL_TICKS = 5;

	private final Client client;
	private final FiveActivePlugin plugin;
	private final RunHistory history;
	private final ItemManager itemManager;

	private final Map<Skill, Integer> lastLevels = new EnumMap<>(Skill.class);
	private final Map<Skill, Integer> lastXp = new EnumMap<>(Skill.class);
	private final Map<Quest, QuestState> lastQuestStates = new EnumMap<>(Quest.class);
	/** Doom of Mokhaiotl has no kill count chat message; its KC lives in a varp. Null until the baseline is known. */
	private Integer lastDoomKc;
	private int tickCounter;
	private boolean promptedClogSync;
	/**
	 * Collection log items named in chat whose slot count increase hasn't come through yet. The count and the
	 * chat message arrive in either order, so new slots wait a couple of ticks for their names before the log
	 * records them as unnamed ("3 new collection log slots").
	 */
	private int namedClogItems;
	private int unnamedClogSlots;
	private int clogSlotsSeenTick;

	@Inject
	ProgressionListener(Client client, FiveActivePlugin plugin, RunHistory history, ItemManager itemManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.history = history;
		this.itemManager = itemManager;
	}

	/** Forget per-login tracking (called on logout so a different account doesn't look like a level-up). */
	void reset()
	{
		lastLevels.clear();
		lastXp.clear();
		lastQuestStates.clear();
		lastDoomKc = null;
		promptedClogSync = false;
		namedClogItems = 0;
		unnamedClogSlots = 0;
	}

	/**
	 * Takes current levels as the baseline when the plugin starts mid-session. On a normal login the
	 * game sends a StatChanged for every skill, which becomes the baseline instead.
	 */
	void seedFromClient()
	{
		for (Skill skill : Skill.values())
		{
			lastLevels.put(skill, client.getRealSkillLevel(skill));
			lastXp.put(skill, client.getSkillExperience(skill));
		}
		lastDoomKc = client.getVarpValue(VarPlayerID.DOM_LEVEL_HIGHSCORES);
	}

	// ---------------------------------------------------------------- Skills

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		FiveActiveState state = plugin.getState();
		Skill skill = event.getSkill();
		Integer previousLevel = lastLevels.put(skill, event.getLevel());
		Integer previousXp = lastXp.put(skill, event.getXp());
		// The first event per skill after login is the baseline, not a gain
		if (state == null || previousLevel == null || previousXp == null)
		{
			return;
		}

		if (event.getXp() > previousXp && !plugin.isSkillActive(skill))
		{
			plugin.chat("<col=ff0000>You gained XP in " + skill.getName() + ", which is not active!</col>");
			plugin.playSound(SoundEffectID.UI_BOOP);
		}

		int gained = event.getLevel() - previousLevel;
		Slot<Skill> slot = FiveActivePlugin.findActive(state.getSkills(), skill);
		if (gained > 0 && (slot == null || slot.isDone()))
		{
			// Not a slot in progress (e.g. Hitpoints): still part of the story
			history.add(HistoryEntry.of(HistoryEntry.Kind.LEVEL_UP).name(skill.getName()).value(event.getLevel()).progress(-1));
			if (event.getLevel() >= Experience.MAX_REAL_LEVEL)
			{
				history.add(HistoryEntry.of(HistoryEntry.Kind.MAXED).category(Category.SKILLS).name(skill.getName()));
			}
		}
		if (gained <= 0 || slot == null || slot.isDone())
		{
			return;
		}

		slot.setProgress(Math.min(Rules.LEVELS_TO_COMPLETE_SKILL, slot.getProgress() + gained));
		history.add(HistoryEntry.of(HistoryEntry.Kind.LEVEL_UP).name(skill.getName()).value(event.getLevel())
			.progress(event.getLevel() >= Experience.MAX_REAL_LEVEL ? Rules.LEVELS_TO_COMPLETE_SKILL : slot.getProgress()));
		slot.setFresh(false);
		if (event.getLevel() >= Experience.MAX_REAL_LEVEL)
		{
			// Can't level any further: the slot is finished and the skill is permanently unlocked
			slot.setProgress(Rules.LEVELS_TO_COMPLETE_SKILL);
			slot.setDone(true);
			history.add(HistoryEntry.of(HistoryEntry.Kind.COMPLETED).category(Category.SKILLS).name(skill.getName()));
			history.add(HistoryEntry.of(HistoryEntry.Kind.MAXED).category(Category.SKILLS).name(skill.getName()));
			plugin.chat("<col=00ff00>" + skill.getName() + " maxed!</col> It is now permanently unlocked. Roll a replacement skill in the side panel.");
			plugin.playSound(SoundEffectID.GE_COIN_TINKLE);
		}
		else if (slot.getProgress() >= Rules.LEVELS_TO_COMPLETE_SKILL)
		{
			slot.setDone(true);
			history.add(HistoryEntry.of(HistoryEntry.Kind.COMPLETED).category(Category.SKILLS).name(skill.getName()));
			plugin.chat("<col=00ff00>" + skill.getName() + " complete!</col> Roll a replacement skill in the side panel.");
			plugin.playSound(SoundEffectID.GE_COIN_TINKLE);
		}
		else
		{
			plugin.chat(skill.getName() + " level-ups: " + slot.getProgress() + "/" + Rules.LEVELS_TO_COMPLETE_SKILL);
		}
		plugin.saveState();
		plugin.refreshPanel();
	}

	// ---------------------------------------------------------------- Bosses & collection log

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		FiveActiveState state = plugin.getState();
		if (state == null || (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM))
		{
			return;
		}
		String message = Text.removeTags(event.getMessage());

		Matcher kc = KILLCOUNT_PATTERN.matcher(message);
		if (kc.find())
		{
			onKillCount(state, kc.group("boss"), Integer.parseInt(kc.group("kc").replace(",", "")));
		}

		Matcher clog = NEW_CLOG_ITEM.matcher(message);
		if (clog.find())
		{
			String item = clog.group("item").trim();
			// The item's ID too (value), so the share card can show its picture
			history.add(HistoryEntry.of(HistoryEntry.Kind.CLOG_ITEM).name(item.isEmpty() ? "an item" : item)
				.value(item.isEmpty() ? -1 : plugin.findItemId(item)));
			namedClogItems++;
			plugin.refreshPanel();
			// Fallback for when the collection log varp hasn't been populated this session
			if (client.getVarpValue(VarPlayerID.COLLECTION_COUNT) == 0)
			{
				setCollectionLogSlots(state, state.getCollectionLogSlots() + 1);
			}
		}
	}

	private void onKillCount(FiveActiveState state, String counterName, int killCount)
	{
		String key = Boss.normalizeKcName(counterName);
		key = KC_RENAMES.getOrDefault(key, key);
		Boss boss = Boss.fromKcName(key);
		if (boss == null)
		{
			return;
		}

		int totalBefore = plugin.getTotalBossKc();
		state.getLifetimeKc().put(key, killCount);

		Slot<Boss> slot = FiveActivePlugin.findActive(state.getBosses(), boss);
		if (slot == null)
		{
			plugin.chat("<col=ff0000>" + boss.getDisplayName() + " is not an active boss!</col> This kill doesn't count.");
			announceTierUnlocks(totalBefore);
			plugin.saveState();
			plugin.refreshPanel();
			return;
		}

		state.getModeKills().merge(boss, 1, Integer::sum);
		slot.setProgress(slot.getProgress() + 1);
		slot.setFresh(false);
		if (slot.getProgress() >= boss.getKillsToComplete())
		{
			slot.setDone(true);
			history.add(HistoryEntry.of(HistoryEntry.Kind.COMPLETED).category(Category.BOSSES).name(boss.getDisplayName()));
			plugin.chat("<col=00ff00>" + boss.getDisplayName() + " complete!</col> Roll a replacement boss in the side panel.");
			plugin.playSound(SoundEffectID.GE_COIN_TINKLE);
		}
		else
		{
			plugin.chat(boss.getDisplayName() + " kills: " + slot.getProgress() + "/" + boss.getKillsToComplete());
		}
		announceTierUnlocks(totalBefore);
		plugin.saveState();
		plugin.refreshPanel();
	}

	/** Announces any tier milestone crossed since totalBefore. */
	private void announceTierUnlocks(int totalBefore)
	{
		announceTierReady(totalBefore, plugin.getTotalBossKc());
	}

	/**
	 * Announces each not-yet-unlocked tier whose KC was crossed going from totalBefore to total: it's now ready
	 * to unlock, and boss rolls wait for it.
	 */
	void announceTierReady(int totalBefore, int total)
	{
		for (int i = 0; i < BossTiers.TIERS.size(); i++)
		{
			int required = BossTiers.TIERS.get(i).getKillsRequired();
			if (i >= plugin.getUnlockedTiers() && totalBefore < required && total >= required)
			{
				history.add(HistoryEntry.of(HistoryEntry.Kind.TIER_READY).value(i + 1).progress(required));
				plugin.chat("<col=ffd700>" + String.format("%,d", required) + " total KC! Tier " + (i + 1) + " is ready to unlock.</col>"
					+ " Claim it in the side panel!");
				plugin.playSound(SoundEffectID.GE_COIN_TINKLE);
			}
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		FiveActiveState state = plugin.getState();
		if (state != null && event.getVarpId() == VarPlayerID.COLLECTION_COUNT && event.getValue() > state.getCollectionLogSlots())
		{
			setCollectionLogSlots(state, event.getValue());
		}

		if (event.getVarpId() == VarPlayerID.DOM_LEVEL_HIGHSCORES)
		{
			// The first value after login is the baseline, not a kill
			Integer previous = lastDoomKc;
			lastDoomKc = event.getValue();
			if (state != null && previous != null && event.getValue() > previous)
			{
				onKillCount(state, Boss.DOOM_OF_MOKHAIOTL.getKcNames().get(0), event.getValue());
			}
		}
	}

	private void setCollectionLogSlots(FiveActiveState state, int slots)
	{
		int previousSlots = state.getCollectionLogSlots();
		int previousShuffles = state.getCollectionLogSlots() / Rules.CLOG_SLOTS_PER_SHUFFLE;
		boolean firstSync = state.getCollectionLogSlots() == 0;
		state.setCollectionLogSlots(slots);

		int earned = slots / Rules.CLOG_SLOTS_PER_SHUFFLE - previousShuffles;
		if (firstSync)
		{
			history.add(HistoryEntry.of(HistoryEntry.Kind.CLOG_SLOTS).value(slots).progress(1));
			namedClogItems = 0;
		}
		else
		{
			unnamedClogSlots += slots - previousSlots;
			clogSlotsSeenTick = tickCounter;
		}
		if (earned > 0 && !firstSync)
		{
			history.add(HistoryEntry.of(HistoryEntry.Kind.SHUFFLE_EARNED).value(earned));
		}
		if (firstSync)
		{
			plugin.chat("Collection log synced: " + slots + " slots, " + plugin.getAvailableShuffles() + " shuffles available.");
		}
		else if (earned > 0)
		{
			plugin.chat("<col=00ff00>You earned " + (earned == 1 ? "a Shuffle" : earned + " Shuffles") + "!</col>");
			plugin.playSound(SoundEffectID.GE_COIN_TINKLE);
		}
		plugin.saveState();
		plugin.refreshPanel();
	}

	/** New collection log slots that no chat message named (a couple of ticks on), logged as a count. */
	private void logUnnamedClogSlots()
	{
		if (unnamedClogSlots <= 0 || tickCounter - clogSlotsSeenTick < 2)
		{
			return;
		}
		int named = Math.min(namedClogItems, unnamedClogSlots);
		namedClogItems -= named;
		int unnamed = unnamedClogSlots - named;
		unnamedClogSlots = 0;
		if (unnamed > 0)
		{
			history.add(HistoryEntry.of(HistoryEntry.Kind.CLOG_SLOTS).value(unnamed));
			plugin.refreshPanel();
		}
	}

	// ---------------------------------------------------------------- Bank value

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		FiveActiveState state = plugin.getState();
		if (state == null || event.getContainerId() != InventoryID.BANK)
		{
			return;
		}
		long value = 0;
		for (Item item : event.getItemContainer().getItems())
		{
			if (item.getId() > 0 && item.getQuantity() > 0)
			{
				value += (long) itemManager.getItemPrice(itemManager.canonicalize(item.getId())) * item.getQuantity();
			}
		}
		long reached = 0;
		for (long milestone : Rules.BANK_MILESTONES)
		{
			if (value >= milestone)
			{
				reached = milestone;
			}
		}
		Long previous = state.getBankMilestone();
		if (previous == null && !plugin.isLogFromStart())
		{
			// First look at the bank of a run from before the log: whatever it's already worth isn't news
			state.setBankMilestone(reached);
			plugin.saveState();
			return;
		}
		long before = previous == null ? 0 : previous;
		if (reached <= before)
		{
			if (previous == null)
			{
				state.setBankMilestone(before);
				plugin.saveState();
			}
			return;
		}
		for (long milestone : Rules.BANK_MILESTONES)
		{
			if (milestone > before && milestone <= reached)
			{
				history.add(HistoryEntry.of(HistoryEntry.Kind.BANK_VALUE).value(milestone));
			}
		}
		state.setBankMilestone(reached);
		plugin.saveState();
		plugin.refreshPanel();
	}

	// ---------------------------------------------------------------- Quests

	/** Clears NEW badges once they are older than {@link Rules#NEW_BADGE_SECONDS}. */
	private void expireNewBadges(FiveActiveState state)
	{
		long cutoff = System.currentTimeMillis() - Rules.NEW_BADGE_SECONDS * 1000L;
		boolean changed = false;
		for (List<? extends Slot<?>> slots : Arrays.asList(state.getSkills(), state.getBosses(), state.getQuests()))
		{
			for (Slot<?> slot : slots)
			{
				if (slot.isFresh() && slot.getRolledAt() < cutoff)
				{
					slot.setFresh(false);
					changed = true;
				}
			}
		}
		if (changed)
		{
			plugin.saveState();
			plugin.refreshPanel();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		FiveActiveState state = plugin.getState();
		if (state == null)
		{
			return;
		}

		expireNewBadges(state);
		++tickCounter;
		logUnnamedClogSlots();

		if (tickCounter % QUEST_CHECK_INTERVAL_TICKS != 0)
		{
			return;
		}

		if (!promptedClogSync && state.getCollectionLogSlots() == 0)
		{
			promptedClogSync = true;
			plugin.chat("Open your Collection Log once to sync your starting Shuffles.");
		}

		boolean changed = false;
		for (Slot<Quest> slot : state.getQuests())
		{
			if (slot.isDone())
			{
				continue;
			}
			QuestState questState = slot.getValue().getState(client);
			if (questState == QuestState.FINISHED)
			{
				slot.setDone(true);
				slot.setFresh(false);
				history.add(HistoryEntry.of(HistoryEntry.Kind.COMPLETED).category(Category.QUESTS).name(slot.getValue().getName()));
				plugin.chat("<col=00ff00>Quest complete: " + slot.getValue().getName() + "!</col> Roll a new quest in the side panel.");
				changed = true;
			}
			else if (questState == QuestState.IN_PROGRESS)
			{
				slot.setFresh(false);
			}
			// Refresh the panel when a quest goes from "Not started" to "In progress"
			changed |= lastQuestStates.put(slot.getValue(), questState) != questState;
		}
		if (changed)
		{
			plugin.saveState();
			plugin.refreshPanel();
		}
	}
}
