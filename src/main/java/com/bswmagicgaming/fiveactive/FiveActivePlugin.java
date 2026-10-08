package com.bswmagicgaming.fiveactive;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.io.IOException;
import java.io.IOException;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.FocusChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.SoundEffectID;
import net.runelite.client.input.KeyManager;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.config.RuneScapeProfile;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.util.Filepath;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Five Active",
	internalName = "five-active",
	// Where the History and backups lived before the Plugin Hub version; moved into plugin-data on first use
	legacyDataDirectory = "five-active",
	description = "Only five skills, five bosses and five quests are active at a time",
	tags = {"ironman", "snowflake", "restricted", "gamemode"}
)
public class FiveActivePlugin extends Plugin
{
	private static final String STATE_KEY = "state";
	/** Where the Chat Commands plugin stores lifetime kill counts (per RS profile). */
	private static final String CHAT_COMMANDS_KC_GROUP = "killcount";

	@Inject @Getter private Client client;
	@Inject @Getter private FiveActiveConfig config;
	@Inject private ConfigManager configManager;
	@Inject private ClientThread clientThread;
	@Inject private Gson gson;
	@Inject private ClientToolbar clientToolbar;
	@Inject private OverlayManager overlayManager;
	@Inject private EventBus eventBus;
	@Inject private SkillIconManager skillIconManager;
	@Inject private SpriteManager spriteManager;
	@Inject private RollingEngine rollingEngine;
	@Inject private SkillsTabOverlay skillsTabOverlay;
	@Inject private FiveActiveOverlay fiveActiveOverlay;
	@Inject private TierBannerOverlay tierBannerOverlay;
	@Inject private ProgressionListener progressionListener;
	@Inject private QuestListListener questListListener;
	@Inject private KeyManager keyManager;
	@Inject private HiddenKeys hiddenKeys;
	@Inject @Getter private RunHistory history;
	@Inject @Getter private ItemManager itemManager;

	/** Said once the run shows after login: a welcome to a new run, or that the saved run was missing but a backup exists. */
	private String loginNotice;

	/** Newly rolled skills whose reel hasn't landed yet. Written from Swing, read by the overlay. */
	private final Set<Skill> unrevealedSkills = ConcurrentHashMap.newKeySet();

	/** The loaded run for the logged-in account, or null when logged out. Only touch on the client thread. */
	@Getter private FiveActiveState state;
	/**
	 * Game ticks to wait after logging in before the panel shows the run. Right after login the client is still
	 * receiving quest progress and stats, so the panel would briefly show wrong numbers (e.g. a Roll button for
	 * quests that are actually done) and then correct itself mid login animation. -1 once the run is showing.
	 */
	private int ticksUntilPanelReady = -1;
	/** Ticks to wait; the second tick leaves plenty of margin for everything to have arrived. */
	private static final int PANEL_READY_TICKS = 2;

	private FiveActivePanel panel;
	private NavigationButton navButton;

	@Provides
	FiveActiveConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FiveActiveConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = new FiveActivePanel(this, skillIconManager, spriteManager);
		navButton = NavigationButton.builder()
			.tooltip("Five Active")
			.icon(NavIcon.create())
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		try
		{
			history.start(getPluginDirectory());
		}
		catch (IOException e)
		{
			log.warn("Five Active: no data folder, so no History or backups this session", e);
		}

		overlayManager.add(skillsTabOverlay);
		overlayManager.add(fiveActiveOverlay);
		overlayManager.add(tierBannerOverlay);
		eventBus.register(progressionListener);
		eventBus.register(questListListener);
		keyManager.registerKeyListener(hiddenKeys);

		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				loadState();
				progressionListener.seedFromClient();
			}
			// Turned on while already logged in, everything has long since loaded. 
			// Otherwise (e.g. the client just started) wait for the next login to finish loading, like any other login.
			ticksUntilPanelReady = client.getGameState() == GameState.LOGGED_IN ? -1 : PANEL_READY_TICKS;
			refreshPanel();
		});
	}

	@Override
	protected void shutDown()
	{
		eventBus.unregister(progressionListener);
		eventBus.unregister(questListListener);
		keyManager.unregisterKeyListener(hiddenKeys);
		overlayManager.remove(skillsTabOverlay);
		overlayManager.remove(fiveActiveOverlay);
		overlayManager.remove(tierBannerOverlay);
		overlayData = PanelData.LOGGED_OUT;
		history.stop();
		clientToolbar.removeNavigation(navButton);
		panel.cancelAnimation();
		unrevealedSkills.clear();
		// State is saved on every change, so there is nothing to flush here. Just drop the in-memory copy.
		state = null;
		panel = null;
	}

	// ---------------------------------------------------------------- Persistence

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		loadState();
		refreshPanel();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			state = null;
			history.unload();
			loginNotice = null;
			progressionListener.reset();
			ticksUntilPanelReady = PANEL_READY_TICKS;
			refreshPanel();
		}
		else if (event.getGameState() == GameState.LOGGED_IN)
		{
			if (state == null)
			{
				loadState();
			}
			refreshPanel();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (ticksUntilPanelReady > 0 && --ticksUntilPanelReady == 0)
		{
			ticksUntilPanelReady = -1;
			refreshPanel();
		}
		if (ticksUntilPanelReady < 0 && loginNotice != null)
		{
			chat(loginNotice);
			loginNotice = null;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (FiveActiveConfig.GROUP.equals(event.getGroup()) && Boolean.parseBoolean(event.getNewValue())
			&& (FiveActiveConfig.COPY_SAVE_CODE_KEY.equals(event.getKey())
				|| FiveActiveConfig.SAVE_FILE_KEY.equals(event.getKey())
				|| FiveActiveConfig.RESTORE_RUN_KEY.equals(event.getKey())
				|| FiveActiveConfig.OPEN_BACKUPS_KEY.equals(event.getKey())))
		{
			// Buttons: untick straight away, then act
			configManager.setConfiguration(FiveActiveConfig.GROUP, event.getKey(), false);
			switch (event.getKey())
			{
				case FiveActiveConfig.COPY_SAVE_CODE_KEY:
					clientThread.invoke(this::copySaveCode);
					break;
				case FiveActiveConfig.SAVE_FILE_KEY:
					clientThread.invoke(this::saveRunToFile);
					break;
				case FiveActiveConfig.RESTORE_RUN_KEY:
					clientThread.invoke(this::chooseRestore);
					break;
				default:
					copyBackupFolder();
					break;
			}
			return;
		}

		// The panel already redraws itself when sections are collapsed, so skip that key
		if (FiveActiveConfig.GROUP.equals(event.getGroup())
			&& !STATE_KEY.equals(event.getKey())
			&& !FiveActiveConfig.COLLAPSED_SECTIONS_KEY.equals(event.getKey())
			&& !FiveActiveConfig.SHORTCUTS_KEY.equals(event.getKey())
			&& !FiveActiveConfig.HISTORY_GROUPING_KEY.equals(event.getKey())
			&& !FiveActiveConfig.HISTORY_OLDEST_FIRST_KEY.equals(event.getKey())
			&& !FiveActiveConfig.COLLAPSED_TIERS_KEY.equals(event.getKey()))
		{
			refreshPanel();
		}
	}

	private void loadState()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}

		String json = configManager.getRSProfileConfiguration(FiveActiveConfig.GROUP, STATE_KEY);
		// Loads the log in the background, backing up the run as it was at login (before this session changes anything)
		history.load(configManager.getRSProfileKey(), accountName(), json, backups -> onHistoryLoaded(json == null, backups));
		FiveActiveState loaded = null;
		if (json != null)
		{
			try
			{
				loaded = gson.fromJson(json, FiveActiveState.class);
			}
			catch (JsonParseException e)
			{
				// Never overwrite a save we can't read; keep a backup so the run can be recovered by hand
				log.warn("Five Active: could not read saved state, backing it up and starting fresh", e);
				configManager.setRSProfileConfiguration(FiveActiveConfig.GROUP, STATE_KEY + ".backup." + System.currentTimeMillis(), json);
			}
		}
		if (loaded != null)
		{
			state = loaded;
		}
		else
		{
			state = new FiveActiveState();
			state.setVersion(FiveActiveState.CURRENT_VERSION);
		}
		state.fillDefaults();
		migrate();
	}

	/**
	 * The account's History has loaded (client thread). A new log starts here, and if there was no saved run the
	 * player hears about it: a welcome to a brand new run, or, if there are backups, that the run may be lost.
	 */
	private void onHistoryLoaded(boolean noSavedRun, List<RunHistory.Backup> backups)
	{
		if (state == null)
		{
			return;
		}
		if (history.isEmpty())
		{
			startLog();
		}
		if (noSavedRun && !backups.isEmpty())
		{
			// Settings lost or reset? Don't restore on its own (it may have been on purpose), but say so
			loginNotice = "<col=ff0000>No saved run was found for this account</col>, but there is a backup from "
				+ new SimpleDateFormat("d MMM yyyy, h:mm a").format(new Date(backups.get(0).getTime()))
				+ ". To bring it back, use Restore a backup in the plugin settings.";
		}
		else if (noSavedRun)
		{
			// A brand new run
			loginNotice = "<col=ff9800>Welcome to Five Active!</col> Only 5 skills, 5 bosses and 5 quests are active at a time. "
				+ "Roll your first ones in the Five Active side panel, and press ? there for the rules.";
		}
		refreshPanel();
	}

	/**
	 * Brings a save written by an older version of the plugin up to date, one step at a time. Whenever an update
	 * changes what the save means, bump {@link FiveActiveState#CURRENT_VERSION} and add a step here.
	 */
	private void migrate()
	{
		if (state.getVersion() > FiveActiveState.CURRENT_VERSION)
		{
			// Written by a newer version (e.g. after rolling the plugin back): leave it as it is
			log.warn("Five Active: save is version {}, newer than this plugin's {}", state.getVersion(), FiveActiveState.CURRENT_VERSION);
			return;
		}
		int before = state.getVersion();
		if (state.getVersion() < 1)
		{
			// 1: boss tiers must be claimed. Saves from before that count every tier already reached as unlocked
			if (state.getUnlockedTiers() == null)
			{
				state.setUnlockedTiers(tiersReached(getTotalBossKc()));
			}
			state.setVersion(1);
		}
		if (state.getVersion() != before)
		{
			saveState();
		}
	}

	/** How many tiers a total KC reaches (tier 1 is always reached). */
	private static int tiersReached(int totalKc)
	{
		int reached = 0;
		for (BossTiers.Tier tier : BossTiers.TIERS)
		{
			if (totalKc >= tier.getKillsRequired())
			{
				reached++;
			}
		}
		return Math.max(1, reached);
	}

	/** How many boss tiers are unlocked (claimed), counting tier 1. */
	int getUnlockedTiers()
	{
		Integer unlocked = state == null ? null : state.getUnlockedTiers();
		return unlocked == null ? 1 : Math.max(1, Math.min(unlocked, BossTiers.TIERS.size()));
	}

	/**
	 * The tier whose KC has been reached but that hasn't been unlocked yet (an index into BossTiers.TIERS), or
	 * -1. New boss rolls wait for it. Tiers are claimed one at a time, lowest first.
	 */
	int getPendingTier()
	{
		int next = getUnlockedTiers();
		return next < BossTiers.TIERS.size() && getTotalBossKc() >= BossTiers.TIERS.get(next).getKillsRequired() ? next : -1;
	}

	/** Called from the panel's Unlock button. Claims the pending tier and starts its celebration. */
	void requestUnlockTier()
	{
		clientThread.invoke(() ->
		{
			int tier = getPendingTier();
			if (tier < 0 || state == null)
			{
				return;
			}
			Map<Quest, QuestState> questStates = rollingEngine.questStates();
			state.setUnlockedTiers(tier + 1);
			saveState();
			// The bosses that become rollable right now (some may still need a quest or Slayer level)
			List<String> opened = new ArrayList<>();
			int tierSize = BossTiers.TIERS.get(tier).getBosses().size();
			for (Boss boss : BossTiers.TIERS.get(tier).getBosses())
			{
				if (rollingEngine.bossLockReason(boss, tier + 1, getTotalBossKc(), questStates) == null)
				{
					opened.add(boss.getDisplayName());
				}
			}
			history.add(HistoryEntry.of(HistoryEntry.Kind.TIER_UNLOCKED).value(tier + 1).progress(opened.size()));
			FiveActivePanel target = panel;
			if (target != null)
			{
				// Queued before the refreshed data, so the panel holds the tier locked until the ceremony opens it
				SwingUtilities.invokeLater(() -> target.animateTierUnlock(tier, opened, () -> celebrateTier(tier, opened.size(), tierSize)));
			}
			else
			{
				celebrateTier(tier, opened.size(), tierSize);
			}
			refreshPanel();
		});
	}

	/**
	 * The ceremony's finale: chat, fireworks on the player, and the banner across the game view. opened is how
	 * many of the tier's bosses can actually be rolled now; the rest still need a quest or Slayer level. Any thread.
	 */
	void celebrateTier(int tier, int opened, int tierSize)
	{
		clientThread.invoke(() ->
		{
			int waiting = tierSize - opened;
			chat("<col=ffd700>Tier " + (tier + 1) + " unlocked!</col> " + opened + " new " + Category.BOSSES.noun(opened) + (opened == 1 ? " joins" : " join") + " the pool."
				+ (waiting > 0 ? " " + waiting + " more need a quest or Slayer level first." : ""));
			if (config.tierFireworks() && client.getLocalPlayer() != null)
			{
				// Client-side only, seen just on this screen. Each tier's fireworks are bigger than the last (see
				// TierFanfare), several can play at once, and each is relit as it ends (see onClientTick) so they
				// keep going as long as the banner is up.
				fireworks = tierEffects(tier);
				for (int i = 0; i < fireworks.size(); i++)
				{
					client.getLocalPlayer().createSpotAnim(TIER_FIREWORKS_KEY + i, fireworks.get(i), 0, 0);
				}
				fireworksUntil = System.currentTimeMillis() + TierFanfare.celebrateMs(tier);
			}
		});
		if (config.tierBanner())
		{
			tierBannerOverlay.show(tier, opened);
		}
	}

	/** The fireworks for a tier (see {@link TierFanfare#effects}), bigger with each tier. */
	private List<Integer> tierEffects(int tier)
	{
		return TierFanfare.effects(tier);
	}

	/** Until when the tier unlock fireworks keep going (0 when they're not), and which effects they are. */
	private long fireworksUntil;
	private List<Integer> fireworks = new ArrayList<>();

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (fireworksUntil == 0)
		{
			return;
		}
		Player player = client.getLocalPlayer();
		if (System.currentTimeMillis() >= fireworksUntil || player == null)
		{
			fireworksUntil = 0;
			return;
		}
		for (int i = 0; i < fireworks.size(); i++)
		{
			if (!player.hasSpotAnim(fireworks.get(i)))
			{
				player.createSpotAnim(TIER_FIREWORKS_KEY + i, fireworks.get(i), 0, 0);
			}
		}
	}

	/** Our slot for the fireworks spot animation on the player, so it doesn't replace anything the game shows. */
	private static final int TIER_FIREWORKS_KEY = 0x5A_7153;

	/**
	 * Newly rolled skills start part of the way through their level-ups, so they always complete on a multiple of 5
	 * (level 5, 10, 15...): rolled at level 1 a skill is already 1/5, finishing at 5; at level 5 it's 0/5, finishing
	 * at 10. The last stretch stops at 99: rolled at 95 it's 0/4, at 97 it's 2/4.
	 */
	private void startFromLevel(List<Slot<Skill>> before, List<Slot<Skill>> after)
	{
		for (Slot<Skill> slot : after)
		{
			if (before.stream().noneMatch(b -> b == slot))
			{
				int level = client.getRealSkillLevel(slot.getValue());
				slot.setGoal(Rules.skillGoalFrom(level));
				slot.setProgress(Rules.skillProgressFrom(level));
			}
		}
	}

	// ---------------------------------------------------------------- Hidden shortcuts (see HiddenKeys)

	/**
	 * ::fiveactive keys switches the hidden shortcuts on or off (deliberately not a setting anyone would find), and
	 * ::fiveactive help lists them. Commands starting :: stay in the client: nothing is sent to the game.
	 */
	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		String[] args = event.getArguments();
		if (!"fiveactive".equalsIgnoreCase(event.getCommand()) || args.length == 0)
		{
			return;
		}
		if ("keys".equalsIgnoreCase(args[0]))
		{
			boolean enabled = !config.shortcutsEnabled();
			configManager.setConfiguration(FiveActiveConfig.GROUP, FiveActiveConfig.SHORTCUTS_KEY, enabled);
			hiddenKeys.clear();
			chat("Shortcuts " + (enabled ? "<col=00ff00>on</col>." : "<col=ff0000>off</col>."));
			if (enabled)
			{
				shortcutHelp();
			}
		}
		else if ("help".equalsIgnoreCase(args[0]))
		{
			if (!config.shortcutsEnabled())
			{
				chat("Shortcuts are <col=ff0000>off</col>. Type ::fiveactive keys to switch them on.");
			}
			shortcutHelp();
		}
		else if ("set".equalsIgnoreCase(args[0]))
		{
			if (!config.shortcutsEnabled())
			{
				chat("Shortcuts are <col=ff0000>off</col>. Type ::fiveactive keys to switch them on.");
				return;
			}
			setSlot(args);
		}
	}

	/** The hidden shortcuts, in chat. */
	private void shortcutHelp()
	{
		chat("<col=ffd700>Ctrl+Shift+F11</col>: reset the run (all slots emptied, Shuffles returned).");
		chat("Hold <col=ffd700>R + B / O / L</col>, press <col=ffd700>1-5</col>: empty that boss / quest / skill slot.");
		chat("Hold <col=ffd700>P + B / L</col>, press <col=ffd700>1-5</col>: +1 kill / level on that boss / skill slot.");
		chat("Hold <col=ffd700>[ + B / L</col>, press <col=ffd700>1-5</col>: -1 kill / level on that boss / skill slot.");
		chat("<col=ffd700>::fiveactive set boss / skill / quest 1-5 name</col>: put that in the slot, e.g. ::fiveactive set boss 1 brutus.");
		chat("Each one backs your run up first: Restore a backup in the settings undoes it. ::fiveactive keys switches them off.");
	}

	/**
	 * ::fiveactive set boss|skill|quest 1-5 name. Puts that boss, skill or quest in the slot, as long as it could
	 * be rolled (completed quests are allowed too), without spending a Shuffle. Past the last filled slot it goes in
	 * the next free one; if it's already in another slot, the two swap places.
	 */
	private void setSlot(String[] args)
	{
		if (state == null)
		{
			chat("Log in first.");
			return;
		}
		Category category = args.length < 4 ? null : categoryNamed(args[1]);
		int slot = args.length < 4 ? -1 : slotNumber(args[2]);
		if (category == null || slot < 0)
		{
			chat("Try <col=ffd700>::fiveactive set boss 1 brutus</col> (boss, skill or quest; slot 1 to 5; then the name).");
			return;
		}
		String name = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
		Map<Quest, QuestState> questStates = rollingEngine.questStates();
		switch (category)
		{
			case SKILLS:
			{
				NameLookup.Result<Skill> found = NameLookup.skill(name);
				if (found.getMatch() == null)
				{
					chat("No skill is called \"" + name + "\".");
					return;
				}
				Skill skill = found.getMatch();
				String refused = skillRefusal(skill, slot, questStates);
				if (refused != null)
				{
					chat("<col=ff0000>Can't set " + skill.getName() + ":</col> " + refused);
					return;
				}
				placeInSlot(state.getSkills(), slot, skill, skill.getName(), category, s ->
				{
					int level = client.getRealSkillLevel(skill);
					s.setGoal(Rules.skillGoalFrom(level));
					s.setProgress(Rules.skillProgressFrom(level));
				});
				break;
			}
			case BOSSES:
			{
				NameLookup.Result<Boss> found = NameLookup.boss(name);
				if (found.getMatch() == null)
				{
					chat("No boss is called \"" + name + "\".");
					return;
				}
				Boss boss = found.getMatch();
				String refused = rollingEngine.bossLockReason(boss, getUnlockedTiers(), getTotalBossKc(), questStates);
				if (refused != null)
				{
					chat("<col=ff0000>Can't set " + boss.getDisplayName() + ":</col> " + refused + ".");
					return;
				}
				placeInSlot(state.getBosses(), slot, boss, boss.getDisplayName(), category, s -> { });
				break;
			}
			case QUESTS:
			default:
			{
				NameLookup.Result<Quest> found = NameLookup.quest(name);
				if (found.getMatch() == null)
				{
					if (found.getOptions().isEmpty())
					{
						chat("No quest matches \"" + name + "\".");
					}
					else
					{
						chat("Which quest? " + String.join(", ", found.getOptions())
							+ (found.getMore() > 0 ? " and " + found.getMore() + " more." : "."));
					}
					return;
				}
				Quest quest = found.getMatch();
				boolean finished = questStates.get(quest) == QuestState.FINISHED;
				if (!finished && !rollingEngine.isQuestRollable(quest, questStates))
				{
					chat("<col=ff0000>Can't set " + quest.getName() + ":</col> "
						+ (QuestRequirements.NEVER_ROLLED.contains(quest) ? "it's rolled one subquest at a time." : "its required quests aren't done yet."));
					return;
				}
				placeInSlot(state.getQuests(), slot, quest, quest.getName(), category, s ->
				{
					if (finished)
					{
						// Already done in-game: it shows as completed straight away
						s.setDone(true);
						s.setFresh(false);
						s.setDoneAt(System.currentTimeMillis());
					}
				});
				break;
			}
		}
	}

	/** Why this skill can't go in the slot (the same rules as rolling), or null if it can. */
	private String skillRefusal(Skill skill, int slot, Map<Quest, QuestState> questStates)
	{
		if (Rules.NEVER_ROLLED.contains(skill))
		{
			return skill.getName() + " is always active.";
		}
		if (rollingEngine.isMaxed(skill))
		{
			return "it's already 99.";
		}
		if (!rollingEngine.rollableSkills(questStates).contains(skill))
		{
			Quest unlock = Rules.SKILL_UNLOCK_QUESTS.get(skill);
			return unlock != null ? "it needs " + unlock.getName() + " first." : "it can't be rolled right now.";
		}
		// One active skill must be a combat skill, while there's a combat skill left below 99
		boolean anyCombatLeft = rollingEngine.rollableSkills(questStates).stream().anyMatch(Rules.COMBAT_SKILLS::contains);
		if (anyCombatLeft && !Rules.COMBAT_SKILLS.contains(skill))
		{
			List<Slot<Skill>> skills = state.getSkills();
			boolean otherCombat = false;
			for (int i = 0; i < skills.size(); i++)
			{
				Slot<Skill> other = skills.get(i);
				if (i != slot && other.getValue() != skill && !other.isDone() && Rules.COMBAT_SKILLS.contains(other.getValue()))
				{
					otherCombat = true;
				}
			}
			if (!otherCombat)
			{
				return "at least one active skill must be a combat skill (Attack, Strength, Defence, Ranged or Magic).";
			}
		}
		return null;
	}

	/**
	 * Puts value in slot (0-based): replacing what's there, or in the next free slot if that's past the end. If
	 * it's already in another slot the two swap places instead. setUp prepares a new slot. Backs the run up first.
	 */
	private <T> void placeInSlot(List<Slot<T>> slots, int slot, T value, String name, Category category, Consumer<Slot<T>> setUp)
	{
		String what = category.getSingular() + " slot ";
		int existing = -1;
		for (int i = 0; i < slots.size(); i++)
		{
			if (slots.get(i).getValue() == value)
			{
				existing = i;
			}
		}
		if (existing == slot)
		{
			chat(name + " is already in " + what + (slot + 1) + ".");
			return;
		}
		if (existing >= 0 && slot >= slots.size())
		{
			chat(name + " is already in " + what + (existing + 1) + ".");
			return;
		}
		history.backup(gson.toJson(state));
		if (existing >= 0)
		{
			Collections.swap(slots, existing, slot);
			chat(name + " moved to " + what + (slot + 1) + " (swapped with slot " + (existing + 1) + ").");
		}
		else
		{
			Slot<T> fresh = Slot.of(value);
			setUp.accept(fresh);
			if (slot < slots.size())
			{
				slots.set(slot, fresh);
				chat(what.substring(0, 1).toUpperCase() + what.substring(1) + (slot + 1) + " is now " + name + ".");
			}
			else
			{
				slots.add(fresh);
				chat(name + " added as " + what + slots.size() + ".");
			}
		}
		saveState();
		refreshPanel();
	}

	private static Category categoryNamed(String word)
	{
		switch (word.toLowerCase())
		{
			case "boss":
			case "bosses":
				return Category.BOSSES;
			case "skill":
			case "skills":
				return Category.SKILLS;
			case "quest":
			case "quests":
				return Category.QUESTS;
			default:
				return null;
		}
	}

	/** "1" to "5" as a slot index 0 to 4, or -1. */
	private static int slotNumber(String word)
	{
		try
		{
			int number = Integer.parseInt(word);
			return number >= 1 && number <= Rules.SLOTS ? number - 1 : -1;
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}

	@Subscribe
	public void onFocusChanged(FocusChanged event)
	{
		if (!event.isFocused())
		{
			hiddenKeys.clear();
		}
	}

	/** Ctrl+Shift+F11: every slot emptied and every Shuffle returned. Kill counts, tiers and the History stay. */
	void hiddenResetRun()
	{
		clientThread.invoke(() ->
		{
			if (state == null)
			{
				return;
			}
			history.backup(gson.toJson(state));
			state.setSkills(new ArrayList<>());
			state.setBosses(new ArrayList<>());
			state.setQuests(new ArrayList<>());
			state.setShufflesSpent(0);
			saveState();
			refreshPanel();
		});
	}

	/** R + B/O/L + 1-5: empties that boss, quest or skill slot, so it can be rolled again. */
	void hiddenEmptySlot(Category category, int index)
	{
		clientThread.invoke(() ->
		{
			List<? extends Slot<?>> slots = state == null ? null : slotsOf(category);
			if (slots == null || index >= slots.size())
			{
				return;
			}
			history.backup(gson.toJson(state));
			slots.remove(index);
			saveState();
			refreshPanel();
		});
	}

	/** P + B/L + 1-5: one kill or level-up of progress on that boss or skill slot, completing it at its goal. */
	void hiddenProgressSlot(Category category, int index)
	{
		clientThread.invoke(() ->
		{
			List<? extends Slot<?>> slots = state == null ? null : slotsOf(category);
			if (slots == null || index >= slots.size() || slots.get(index).isDone())
			{
				return;
			}
			history.backup(gson.toJson(state));
			Slot<?> slot = slots.get(index);
			int goal = category == Category.SKILLS ? Rules.skillGoal(slot) : ((Boss) slot.getValue()).getKillsToComplete();
			slot.setProgress(slot.getProgress() + 1);
			slot.setFresh(false);
			if (slot.getProgress() >= goal)
			{
				slot.setDone(true);
				slot.setDoneAt(System.currentTimeMillis());
				String name = category == Category.SKILLS ? ((Skill) slot.getValue()).getName() : ((Boss) slot.getValue()).getDisplayName();
				history.add(HistoryEntry.of(HistoryEntry.Kind.COMPLETED).category(category).name(name));
				playSound(SoundEffectID.GE_COIN_TINKLE);
			}
			saveState();
			refreshPanel();
		});
	}

	/**
	 * [ + B/L + 1-5: takes one kill or level-up off that boss or skill slot (not below 0). A completed slot goes back
	 * to in progress; its "Completed" History entry stays.
	 */
	void hiddenRegressSlot(Category category, int index)
	{
		clientThread.invoke(() ->
		{
			List<? extends Slot<?>> slots = state == null ? null : slotsOf(category);
			if (slots == null || index >= slots.size() || slots.get(index).getProgress() <= 0)
			{
				return;
			}
			history.backup(gson.toJson(state));
			Slot<?> slot = slots.get(index);
			slot.setProgress(slot.getProgress() - 1);
			slot.setDone(false);
			slot.setDoneAt(0);
			saveState();
			refreshPanel();
		});
	}

	private List<? extends Slot<?>> slotsOf(Category category)
	{
		return category == Category.SKILLS ? state.getSkills() : category == Category.BOSSES ? state.getBosses() : state.getQuests();
	}

	void saveState()
	{
		if (state != null && configManager.getRSProfileKey() != null)
		{
			configManager.setRSProfileConfiguration(FiveActiveConfig.GROUP, STATE_KEY, gson.toJson(state));
		}
	}

	// ---------------------------------------------------------------- History & backups

	/** The logged-in account's name, if RuneLite knows it (for labelling its folder, save files and share card). */
	private String accountName()
	{
		String key = configManager.getRSProfileKey();
		if (key == null)
		{
			return null;
		}
		for (RuneScapeProfile profile : configManager.getRSProfiles())
		{
			if (key.equals(profile.getKey()))
			{
				return profile.getDisplayName();
			}
		}
		return null;
	}

	/** Quests completed, as the game's quest list counts them (or counted from every quest's state until it says). */
	private int questsCompleted(Map<Quest, QuestState> questStates)
	{
		int counted = client.getVarbitValue(VarbitID.QUESTS_COMPLETED_COUNT);
		return counted > 0 ? counted : (int) questStates.values().stream().filter(s -> s == QuestState.FINISHED).count();
	}

	/** How many quests there are, as the game's quest list counts them (or every quest the plugin knows). */
	private int questsTotal()
	{
		int total = client.getVarbitValue(VarbitID.QUESTS_TOTAL_COUNT);
		return total > 0 ? total : Quest.values().length;
	}

	/**
	 * An item's ID from its name, for collection log items (the chat message only names them): the unnoted,
	 * non-placeholder item with that name, or -1. Tradeables are found quickly by search; anything else by
	 * looking through every item once. Client thread.
	 */
	int findItemId(String name)
	{
		Integer cached = itemIds.get(name);
		if (cached != null)
		{
			return cached;
		}
		int found = -1;
		for (net.runelite.http.api.item.ItemPrice price : itemManager.search(name))
		{
			if (price.getName().equalsIgnoreCase(name))
			{
				found = price.getId();
				break;
			}
		}
		for (int id = 0; found < 0 && id < client.getItemCount(); id++)
		{
			net.runelite.api.ItemComposition item = client.getItemDefinition(id);
			if (item.getName().equalsIgnoreCase(name) && item.getNote() == -1 && item.getPlaceholderTemplateId() == -1)
			{
				found = id;
			}
		}
		itemIds.put(name, found);
		return found;
	}

	private final Map<String, Integer> itemIds = new HashMap<>();

	/** For the share card. Any thread. */
	String getAccountName()
	{
		return accountName();
	}

	/** Says where the share card went (file is null if it couldn't be saved). Any thread. */
	void shareSaved(Filepath file)
	{
		clientThread.invoke(() -> chat(file != null
			? "A picture of your run is on your clipboard, ready to paste. Also saved to " + file + "."
			: "A picture of your run is on your clipboard, ready to paste (it couldn't be saved to a file)."));
	}

	/** The first entry of a new log: a brand new run, or (for a run from before the log) what's active now. */
	private void startLog()
	{
		boolean fresh = state.getSkills().isEmpty() && state.getBosses().isEmpty() && state.getQuests().isEmpty();
		if (fresh)
		{
			history.add(HistoryEntry.of(HistoryEntry.Kind.RUN_STARTED));
			return;
		}
		Map<Category, List<String>> active = new LinkedHashMap<>();
		active.put(Category.QUESTS, activeNames(state.getQuests(), Quest::getName));
		active.put(Category.SKILLS, activeNames(state.getSkills(), Skill::getName));
		active.put(Category.BOSSES, activeNames(state.getBosses(), Boss::getDisplayName));
		history.add(HistoryEntry.of(HistoryEntry.Kind.LOG_STARTED).active(active));
	}

	private static <T> List<String> activeNames(List<Slot<T>> slots, Function<T, String> name)
	{
		return slots.stream().filter(s -> !s.isDone()).map(s -> name.apply(s.getValue())).collect(Collectors.toList());
	}

	/** Whether the log began with a brand new run (so everything since, like the first bank milestones, counts). */
	boolean isLogFromStart()
	{
		List<HistoryEntry> entries = history.getEntries();
		return !entries.isEmpty() && entries.get(0).getKind() == HistoryEntry.Kind.RUN_STARTED;
	}

	/** Copies where the backups are kept (plugins can't open folders themselves), to paste into a file manager. */
	private void copyBackupFolder()
	{
		if (history.folder() == null)
		{
			return;
		}
		String location = history.folder().toString();
		SwingUtilities.invokeLater(() ->
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(location), null));
		clientThread.invoke(() -> chat("Backup folder location copied: paste it into File Explorer's address bar to open it."));
	}

	private void copySaveCode()
	{
		if (state == null)
		{
			chat("Log in to copy your save code.");
			return;
		}
		String code = history.saveCode(gson.toJson(state));
		SwingUtilities.invokeLater(() ->
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code), null));
		chat("Save code copied to your clipboard. Paste it somewhere safe; Restore a backup in the settings brings it back.");
	}

	/** Save files are the save code in a text file, named after the account and the day. */
	private static final String SAVE_FILE_EXTENSION = "txt";
	private static final String LOAD_FILE_OPTION = "Load a save file...";
	private static final String PASTE_CODE_OPTION = "Paste a save code...";

	/** Asks where to save the run as a file, then writes its save code there. Starts on the client thread. */
	private void saveRunToFile()
	{
		if (state == null)
		{
			chat("Log in to save your run to a file.");
			return;
		}
		String code = history.saveCode(gson.toJson(state));
		String account = accountName();
		String name = "Five Active - " + (account != null ? account.replaceAll("[\\\\/:*?\"<>|]", "_") + " - " : "")
			+ new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + "." + SAVE_FILE_EXTENSION;
		SwingUtilities.invokeLater(() ->
		{
			List<Filepath> chosen = new Filepath.Chooser()
				.setIsSave()
				.setAcceptsFiles()
				.setDialogTitle("Five Active: save run to file")
				.addExtensionFilter("Five Active save files (*." + SAVE_FILE_EXTENSION + ")", SAVE_FILE_EXTENSION)
				.setDefaultExtension(SAVE_FILE_EXTENSION)
				.setFileName(name)
				.showDialog(panel);
			if (chosen == null || chosen.isEmpty())
			{
				return;
			}
			Filepath file = chosen.get(0);
			if (file.exists() && JOptionPane.showConfirmDialog(panel, file.getFileName() + " already exists. Replace it?",
				"Five Active", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
			{
				return;
			}
			history.writeFile(file, code, error ->
			{
				if (error == null)
				{
					clientThread.invoke(() -> chat("Run saved to " + file + ". Restore a backup in the settings can load it."));
				}
				else
				{
					log.warn("Five Active: could not save the run to {}", file, error);
					SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(panel, "Couldn't save the file: " + error.getMessage(),
						"Five Active", JOptionPane.WARNING_MESSAGE));
				}
			});
		});
	}

	/** Asks which backup to restore (or for a save file or code), then restores it. Starts on the client thread. */
	private void chooseRestore()
	{
		if (state == null)
		{
			chat("Log in to the account you want to restore first.");
			return;
		}
		String current = gson.toJson(state);
		history.listBackups(backups -> SwingUtilities.invokeLater(() -> showRestoreDialog(backups, current)));
	}

	private void showRestoreDialog(List<RunHistory.Backup> backups, String current)
	{
		SimpleDateFormat when = new SimpleDateFormat("EEE d MMM yyyy, h:mm a");
		List<Object> options = new ArrayList<>();
		options.add(LOAD_FILE_OPTION);
		options.add(PASTE_CODE_OPTION);
		options.addAll(backups);
		JList<Object> list = new JList<>(options.toArray());
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setSelectedIndex(backups.isEmpty() ? 0 : 2);
		list.setVisibleRowCount(Math.min(12, options.size()));
		list.setCellRenderer(new DefaultListCellRenderer()
		{
			@Override
			public java.awt.Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected, boolean focused)
			{
				Object text = value;
				if (value instanceof RunHistory.Backup)
				{
					RunHistory.Backup backup = (RunHistory.Backup) value;
					text = when.format(new Date(backup.getTime())) + "   " + describe(backup.getStateJson())
						+ (backup.getStateJson().equals(current) ? "   (same as now)" : "");
				}
				return super.getListCellRendererComponent(l, text, index, selected, focused);
			}
		});
		int choice = JOptionPane.showConfirmDialog(panel,
			new Object[]{"Restore which copy of this account's run? Your run as it is now is backed up first.", new JScrollPane(list)},
			"Five Active: restore a backup", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		Object picked = list.getSelectedValue();
		if (choice != JOptionPane.OK_OPTION || picked == null)
		{
			return;
		}

		if (picked instanceof RunHistory.Backup)
		{
			RunHistory.Backup backup = (RunHistory.Backup) picked;
			if (confirmRestore(when.format(new Date(backup.getTime())), false))
			{
				clientThread.invoke(() -> restore(backup.getStateJson(), null, backup.getTime()));
			}
			return;
		}

		if (LOAD_FILE_OPTION.equals(picked))
		{
			List<Filepath> chosen = new Filepath.Chooser()
				.setIsOpen()
				.setAcceptsFiles()
				.setDialogTitle("Five Active: load a save file")
				.addExtensionFilter("Five Active save files (*." + SAVE_FILE_EXTENSION + ")", SAVE_FILE_EXTENSION)
				.showDialog(panel);
			if (chosen == null || chosen.isEmpty())
			{
				return;
			}
			Filepath file = chosen.get(0);
			history.readFile(file, text -> SwingUtilities.invokeLater(() ->
			{
				if (text == null)
				{
					JOptionPane.showMessageDialog(panel, "Couldn't read that file.", "Five Active", JOptionPane.WARNING_MESSAGE);
				}
				else
				{
					restoreFromCode(text, "the save file " + file.getFileName());
				}
			}));
			return;
		}

		String pasted = "";
		try
		{
			pasted = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
		}
		catch (Exception ignored)
		{
			// Nothing text-like on the clipboard; start empty
		}
		javax.swing.JTextArea field = new javax.swing.JTextArea(pasted != null && pasted.trim().startsWith("FA1:") ? pasted.trim() : "", 6, 40);
		field.setLineWrap(true);
		int ok = JOptionPane.showConfirmDialog(panel,
			new Object[]{"Paste your save code:", new JScrollPane(field)},
			"Five Active: restore a save code", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (ok != JOptionPane.OK_OPTION)
		{
			return;
		}
		restoreFromCode(field.getText(), "the save code");
	}

	/** Checks a save code (pasted, or read from a save file), confirms, then restores it. Swing thread. */
	private void restoreFromCode(String text, String what)
	{
		RunHistory.SaveCode code = history.readSaveCode(text);
		if (code == null || readState(code.getStateJson()) == null)
		{
			JOptionPane.showMessageDialog(panel, "That isn't a Five Active save code or file. Nothing was changed.",
				"Five Active", JOptionPane.WARNING_MESSAGE);
			return;
		}
		if (confirmRestore(what + " (" + describe(code.getStateJson()) + ")", code.getHistory() != null))
		{
			clientThread.invoke(() -> restore(code.getStateJson(), code.getHistory(), 0));
		}
	}

	private boolean confirmRestore(String what, boolean withHistory)
	{
		return JOptionPane.showConfirmDialog(panel,
			"Replace this account's run with " + what + "?\n\n"
				+ "Your run as it is now is backed up first, so this can be undone the same way."
				+ (withHistory ? "\nThe History log is replaced with the one in the code too (the old one is kept on disk)." : ""),
			"Five Active: restore", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
	}

	/** A one-line summary of a saved run, to tell backups apart. */
	private String describe(String stateJson)
	{
		FiveActiveState saved = readState(stateJson);
		if (saved == null)
		{
			return "(unreadable)";
		}
		long skills = saved.getSkills().stream().filter(s -> !s.isDone()).count();
		long bosses = saved.getBosses().stream().filter(s -> !s.isDone()).count();
		long quests = saved.getQuests().stream().filter(s -> !s.isDone()).count();
		return skills + " skills, " + bosses + " bosses, " + quests + " quests active, "
			+ saved.getShufflesSpent() + " Shuffles used";
	}

	private FiveActiveState readState(String json)
	{
		try
		{
			FiveActiveState read = gson.fromJson(json, FiveActiveState.class);
			if (read != null)
			{
				read.fillDefaults();
			}
			return read;
		}
		catch (JsonParseException e)
		{
			return null;
		}
	}

	/** Swaps the run (and, from a save code, its log) for a restored copy. backupTime is 0 for a save code. */
	private void restore(String stateJson, List<HistoryEntry> log, long backupTime)
	{
		FiveActiveState restored = readState(stateJson);
		if (state == null || restored == null)
		{
			chat("Log in to restore your run.");
			return;
		}
		// Backed up first, so a restore can itself be undone
		history.backup(gson.toJson(state));
		state = restored;
		migrate();
		saveState();
		if (log != null)
		{
			history.replace(log);
		}
		history.add(HistoryEntry.of(HistoryEntry.Kind.RESTORED).value(backupTime));
		loginNotice = null;
		chat("<col=00ff00>Run restored</col>" + (backupTime > 0
			? " from the backup of " + new SimpleDateFormat("d MMM yyyy, h:mm a").format(new Date(backupTime)) : " from your save code") + ".");
		refreshPanel();
	}

	// ---------------------------------------------------------------- Queries

	boolean isSkillActive(Skill skill)
	{
		// Maxed skills can't be rolled any more, so they are permanently unlocked
		return skill == Skill.HITPOINTS
			|| rollingEngine.isMaxed(skill)
			|| (state != null && findActive(state.getSkills(), skill) != null);
	}

	/**
	 * Whether the skills tab should show this skill as active. Same as {@link #isSkillActive}, except newly
	 * rolled skills stay dimmed until their reel lands, so the tab doesn't spoil the reveal.
	 * Rule enforcement always uses {@link #isSkillActive}.
	 */
	boolean isSkillShownActive(Skill skill)
	{
		return isSkillActive(skill) && !unrevealedSkills.contains(skill);
	}

	/** Whether the skill's slot is finished and waiting to be rolled away (it no longer counts as active). */
	boolean isSkillCompleted(Skill skill)
	{
		return state != null && !isSkillActive(skill)
			&& state.getSkills().stream().anyMatch(s -> s.getValue() == skill && s.isDone());
	}

	/** When each skill's reel landed, so the skills tab can pop its box. */
	private final Map<Skill, Long> skillRevealedAt = new ConcurrentHashMap<>();

	/** Called as each reel lands. */
	void revealSkill(Skill skill)
	{
		unrevealedSkills.remove(skill);
		skillRevealedAt.put(skill, System.currentTimeMillis());
	}

	/** How far through its reveal pop the skill is (0 to 1), or -1 if it isn't popping. */
	float skillRevealPop(Skill skill)
	{
		Long at = skillRevealedAt.get(skill);
		if (at == null)
		{
			return -1f;
		}
		float t = (System.currentTimeMillis() - at) / (float) FiveActiveOverlay.POP_MS;
		if (t >= 1f)
		{
			skillRevealedAt.remove(skill, at);
			return -1f;
		}
		return t;
	}

	/**
	 * Which new slots the overlay should still hide while the panel reveals a roll, and when each landed (so the
	 * overlay can pop it in at the same moment). Null when no roll is being revealed.
	 */
	@Getter
	private volatile OverlayReveal overlayReveal;

	static class OverlayReveal
	{
		final Category category;
		/** Slots whose reel is still spinning. */
		final Set<Integer> spinning = ConcurrentHashMap.newKeySet();
		/** When each slot's reel landed. */
		final Map<Integer, Long> landedAt = new ConcurrentHashMap<>();

		OverlayReveal(Category category, Set<Integer> slots)
		{
			this.category = category;
			spinning.addAll(slots);
		}

		/** -1 if the slot isn't part of this reveal, 0 while it spins, otherwise when it landed. */
		long revealTime(Category category, int slot)
		{
			if (category != this.category)
			{
				return -1;
			}
			if (spinning.contains(slot))
			{
				return 0;
			}
			return landedAt.getOrDefault(slot, -1L);
		}

		boolean isSpinning(Category category)
		{
			return category == this.category && !spinning.isEmpty();
		}
	}

	/** Called as each reel lands: the overlay pops that slot in. */
	void revealSlot(Category category, int slot)
	{
		OverlayReveal reveal = overlayReveal;
		if (reveal != null && reveal.category == category && reveal.spinning.remove(slot))
		{
			reveal.landedAt.put(slot, System.currentTimeMillis());
		}
	}

	/** The reveal is over (or was skipped): nothing is hidden any more. */
	private void endReveal()
	{
		OverlayReveal reveal = overlayReveal;
		if (reveal != null)
		{
			reveal.spinning.clear();
		}
	}

	boolean isQuestActive(Quest quest)
	{
		return state != null && findActive(state.getQuests(), quest) != null;
	}

	/** The slot holding value, if it is active (not completed). */
	static <T> Slot<T> findActive(List<Slot<T>> slots, T value)
	{
		for (Slot<T> slot : slots)
		{
			if (!slot.isDone() && slot.getValue() == value)
			{
				return slot;
			}
		}
		return null;
	}

	int getAvailableShuffles()
	{
		return state == null ? 0 : Math.max(0, state.getCollectionLogSlots() / Rules.CLOG_SLOTS_PER_SHUFFLE - state.getShufflesSpent());
	}

	int getTotalBossKc()
	{
		if (state == null)
		{
			return 0;
		}
		if (config.kcMode() == FiveActiveConfig.KcMode.LIFETIME)
		{
			int total = 0;
			for (Boss boss : Boss.values())
			{
				total += getLifetimeKc(boss);
			}
			return total;
		}
		return state.getModeTotalKc();
	}

	/** Best known lifetime KC: what we've seen in chat, or what the Chat Commands plugin has recorded, whichever is higher. */
	private int getLifetimeKc(Boss boss)
	{
		int total = 0;
		for (String kcName : boss.getKcNames())
		{
			String key = Boss.normalizeKcName(kcName);
			int seen = state.getLifetimeKc().getOrDefault(key, 0);
			Integer chatCommands = configManager.getRSProfileConfiguration(CHAT_COMMANDS_KC_GROUP, key, int.class);
			total += Math.max(seen, chatCommands == null ? 0 : chatCommands);
		}
		return total;
	}

	// ---------------------------------------------------------------- Actions (client thread)

	/** Rolls replacements for completed/empty slots, or re-rolls everything when shuffling (costs one shuffle). */
	void roll(Category category, boolean shuffle)
	{
		if (state == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		if (category == Category.BOSSES && getPendingTier() >= 0)
		{
			// A tier is waiting to be claimed; nothing is spent
			chat("Unlock Tier " + (getPendingTier() + 1) + " in the side panel before rolling new bosses.");
			return;
		}
		if (shuffle)
		{
			if (getAvailableShuffles() <= 0)
			{
				return;
			}
			if (!canShuffle(category, rollingEngine.questStates()))
			{
				chat("There are no new " + category.getPlural() + " to shuffle into. Your Shuffle was not used.");
				return;
			}
			state.setShufflesSpent(state.getShufflesSpent() + 1);
		}

		Map<Quest, QuestState> questStates = rollingEngine.questStates();
		List<Slot<Skill>> skillsBefore = state.getSkills();
		List<Slot<Boss>> bossesBefore = state.getBosses();
		List<Slot<Quest>> questsBefore = state.getQuests();
		List<String> added;
		switch (category)
		{
			case SKILLS:
			{
				List<Slot<Skill>> rolled = rollingEngine.rollSkills(state.getSkills(), shuffle, questStates);
				added = newNames(state.getSkills(), rolled, Skill::getName);
				startFromLevel(state.getSkills(), rolled);
				state.setSkills(rolled);
				break;
			}
			case BOSSES:
			{
				List<Slot<Boss>> rolled = rollingEngine.rollBosses(state.getBosses(), shuffle, getUnlockedTiers(), questStates);
				added = newNames(state.getBosses(), rolled, Boss::getDisplayName);
				state.setBosses(rolled);
				break;
			}
			case QUESTS:
			default:
			{
				List<Slot<Quest>> rolled = rollingEngine.rollQuests(state.getQuests(), shuffle, questStates);
				added = newNames(state.getQuests(), rolled, Quest::getName);
				state.setQuests(rolled);
				break;
			}
		}

		if (!added.isEmpty())
		{
			HistoryEntry entry = HistoryEntry.of(shuffle ? HistoryEntry.Kind.SHUFFLED : HistoryEntry.Kind.ROLLED)
				.category(category).names(added);
			if (shuffle)
			{
				entry.replaced(category == Category.SKILLS ? oldNames(skillsBefore, state.getSkills(), Skill::getName)
					: category == Category.BOSSES ? oldNames(bossesBefore, state.getBosses(), Boss::getDisplayName)
					: oldNames(questsBefore, state.getQuests(), Quest::getName));
			}
			history.add(entry);
		}
		if (added.isEmpty())
		{
			chat("No " + category.getPlural() + " are available to roll right now.");
		}
		else
		{
			String announcement = (shuffle ? "Shuffled! " : "") + "New " + category.noun(added.size()) + ": <col=00ff00>" + String.join(", ", added) + "</col>";
			if (panel != null && config.slotMachine())
			{
				// Hold the chat message until the reels land so it doesn't spoil the reveal
				Runnable announce = () -> clientThread.invoke(() -> chat(announcement));
				animateRoll(category, skillsBefore, bossesBefore, questsBefore, questStates, announce);
			}
			else
			{
				chat(announcement);
			}
		}
		saveState();
		refreshPanel();
	}

	private boolean canShuffle(Category category, Map<Quest, QuestState> questStates)
	{
		switch (category)
		{
			case SKILLS:
				return rollingEngine.canShuffleSkills(state.getSkills(), questStates);
			case BOSSES:
				// No boss shuffles while a tier is waiting to be claimed (a shuffle is a new roll too)
				return getPendingTier() < 0 && rollingEngine.canShuffleBosses(state.getBosses(), getUnlockedTiers(), questStates);
			case QUESTS:
			default:
				return rollingEngine.canShuffleQuests(state.getQuests(), questStates);
		}
	}

	/**
	 * Starts the slot machine reveal for whatever was just rolled. The reels spin through what could
	 * actually have been rolled (the current pool), then land on the real results.
	 */
	private void animateRoll(Category category, List<Slot<Skill>> skillsBefore, List<Slot<Boss>> bossesBefore,
		List<Slot<Quest>> questsBefore, Map<Quest, QuestState> questStates, Runnable announce)
	{
		FiveActivePanel target = panel;
		// Once the reveal ends, anything the overlay still hides shows, then the result is announced
		Runnable done = () ->
		{
			endReveal();
			announce.run();
		};
		switch (category)
		{
			case SKILLS:
			{
				Map<Integer, Skill> results = newSlots(skillsBefore, state.getSkills());
				List<Skill> pool = rollingEngine.rollableSkills(questStates);
				// Keep new skills dimmed in the skills tab (and hidden in the overlay) until their reel lands
				unrevealedSkills.addAll(results.values());
				overlayReveal = new OverlayReveal(category, results.keySet());
				SwingUtilities.invokeLater(() -> target.animateSkillRoll(results, pool, () ->
				{
					// Anything not revealed by its reel yet (e.g. the reveal was skipped) shows now
					unrevealedSkills.removeAll(results.values());
					done.run();
				}));
				break;
			}
			case BOSSES:
			{
				Map<Integer, Boss> results = newSlots(bossesBefore, state.getBosses());
				List<Boss> pool = rollingEngine.rollableBosses(getUnlockedTiers(), questStates);
				overlayReveal = new OverlayReveal(category, results.keySet());
				SwingUtilities.invokeLater(() -> target.animateBossRoll(results, pool, done));
				break;
			}
			case QUESTS:
			default:
			{
				Map<Integer, Quest> results = newSlots(questsBefore, state.getQuests());
				List<Quest> pool = rollingEngine.rollableQuests(questStates);
				overlayReveal = new OverlayReveal(category, results.keySet());
				SwingUtilities.invokeLater(() -> target.animateQuestRoll(results, pool, done));
				break;
			}
		}
	}

	/** Slot index to value for each slot in after that was newly rolled. */
	private static <T> Map<Integer, T> newSlots(List<Slot<T>> before, List<Slot<T>> after)
	{
		Map<Integer, T> added = new HashMap<>();
		for (int i = 0; i < after.size(); i++)
		{
			Slot<T> slot = after.get(i);
			if (before.stream().noneMatch(b -> b == slot))
			{
				added.put(i, slot.getValue());
			}
		}
		return added;
	}

	void playSound(int soundId)
	{
		if (!config.sounds())
		{
			return;
		}
		clientThread.invoke(() -> client.playSoundEffect(soundId));
	}

	/** Names of the unfinished slots in before that are gone from after (what a shuffle swapped out). */
	private static <T> List<String> oldNames(List<Slot<T>> before, List<Slot<T>> after, Function<T, String> name)
	{
		List<String> removed = new ArrayList<>();
		for (Slot<T> slot : before)
		{
			if (!slot.isDone() && after.stream().noneMatch(a -> a == slot))
			{
				removed.add(name.apply(slot.getValue()));
			}
		}
		return removed;
	}

	/** Names of the slots in after that were newly rolled (kept slots are the same instances as before). */
	private static <T> List<String> newNames(List<Slot<T>> before, List<Slot<T>> after, Function<T, String> name)
	{
		List<String> added = new ArrayList<>();
		for (Slot<T> slot : after)
		{
			if (before.stream().noneMatch(b -> b == slot))
			{
				added.add(name.apply(slot.getValue()));
			}
		}
		return added;
	}

	/** Called from Swing; hops to the client thread. */
	/** Remembers which panel sections are collapsed (a layout preference, shared across accounts). */
	void saveCollapsedSections(String sections)
	{
		configManager.setConfiguration(FiveActiveConfig.GROUP, FiveActiveConfig.COLLAPSED_SECTIONS_KEY, sections);
	}

	/** Remembers how the History view is grouped and ordered (a view preference, shared across accounts). */
	void saveHistoryView(FiveActiveConfig.HistoryGrouping grouping, boolean oldestFirst)
	{
		configManager.setConfiguration(FiveActiveConfig.GROUP, FiveActiveConfig.HISTORY_GROUPING_KEY, grouping);
		configManager.setConfiguration(FiveActiveConfig.GROUP, FiveActiveConfig.HISTORY_OLDEST_FIRST_KEY, oldestFirst);
	}

	/** Remembers which boss pool tiers are collapsed (a layout preference, shared across accounts). */
	void saveCollapsedTiers(String tiers)
	{
		configManager.setConfiguration(FiveActiveConfig.GROUP, FiveActiveConfig.COLLAPSED_TIERS_KEY, tiers);
	}

	/** Clears the NEW badge on one slot. Called from Swing; hops to the client thread. */
	void requestDismissNew(Category category, int index)
	{
		clientThread.invoke(() ->
		{
			if (state == null)
			{
				return;
			}
			List<? extends Slot<?>> slots = category == Category.SKILLS ? state.getSkills()
				: category == Category.BOSSES ? state.getBosses()
				: state.getQuests();
			if (index < slots.size() && slots.get(index).isFresh())
			{
				slots.get(index).setFresh(false);
				saveState();
				refreshPanel();
			}
		});
	}

	void requestRoll(Category category, boolean shuffle)
	{
		clientThread.invoke(() -> roll(category, shuffle));
	}

	void chat(String message)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=ff9800>Five Active:</col> " + message, null);
	}

	// ---------------------------------------------------------------- Panel

	/** The boss's quest and Slayer requirements, each marked met or not, for the boss pool tooltips. */
	private List<PanelData.Requirement> requirements(Boss boss, Map<Quest, QuestState> questStates)
	{
		List<PanelData.Requirement> requirements = new ArrayList<>();
		for (Quest quest : boss.getRequiredQuests())
		{
			requirements.add(new PanelData.Requirement(quest.getName(), questStates.get(quest) == QuestState.FINISHED));
		}
		if (boss.getSlayerLevel() > 0)
		{
			requirements.add(new PanelData.Requirement(boss.getSlayerLevel() + " Slayer",
				client.getRealSkillLevel(Skill.SLAYER) >= boss.getSlayerLevel()));
		}
		return requirements;
	}

	/** When each slot was finished (keyed by category and name), for the green completion flash. */
	private final Map<String, Long> completedAt = new ConcurrentHashMap<>();

	/**
	 * Notes every slot that has just been finished, by comparing the last snapshot with the new one. Whatever
	 * finished it (a level-up, a kill, a quest, the debug tools), the flash plays everywhere the slot shows.
	 */
	private void recordCompletions(PanelData before, PanelData after)
	{
		if (!before.isLoggedIn() || !after.isLoggedIn())
		{
			return;
		}
		long now = System.currentTimeMillis();
		recordCompletions(Category.SKILLS, before.getSkills(), after.getSkills(), now);
		recordCompletions(Category.BOSSES, before.getBosses(), after.getBosses(), now);
		recordCompletions(Category.QUESTS, before.getQuests(), after.getQuests(), now);
	}

	private void recordCompletions(Category category, List<PanelData.SlotView> before, List<PanelData.SlotView> after, long now)
	{
		for (int i = 0; i < Math.min(before.size(), after.size()); i++)
		{
			PanelData.SlotView was = before.get(i);
			PanelData.SlotView is = after.get(i);
			if (was.getName().equals(is.getName()) && !was.isDone() && is.isDone())
			{
				completedAt.put(category + ":" + is.getName(), now);
			}
		}
	}

	/** How far through its green completion flash the slot is (0 to 1), or -1 if it isn't flashing. Any thread. */
	float completionPop(Category category, String name)
	{
		String key = category + ":" + name;
		Long at = completedAt.get(key);
		if (at == null)
		{
			return -1f;
		}
		float t = (System.currentTimeMillis() - at) / (float) FiveActiveOverlay.COMPLETE_POP_MS;
		if (t >= 1f)
		{
			completedAt.remove(key, at);
			return -1f;
		}
		return t;
	}

	boolean isSkillMaxed(Skill skill)
	{
		return rollingEngine.isMaxed(skill);
	}

	/** What the at-a-glance overlay shows: the same snapshot as the panel, updated alongside it. */
	@Getter
	private volatile PanelData overlayData = PanelData.LOGGED_OUT;

	/** Whether the side panel is open on screen (the overlay can hide itself then). */
	boolean isPanelOpen()
	{
		FiveActivePanel target = panel;
		return target != null && target.isShowing();
	}

	/** Rebuilds the side panel from current state. Safe to call from any thread. */
	void refreshPanel()
	{
		clientThread.invokeLater(() ->
		{
			if (panel == null)
			{
				return;
			}
			// Loading screens (moving between areas, hopping) aren't a logout: keep showing the run as it is.
			// Showing logged-out here would pack the whole panel away and pop it back in. LOGGED_IN refreshes it.
			if (state != null && ticksUntilPanelReady < 0 && client.getGameState() != GameState.LOGGED_IN
				&& client.getGameState() != GameState.LOGIN_SCREEN)
			{
				return;
			}
			PanelData data = buildPanelData();
			recordCompletions(overlayData, data);
			overlayData = data;
			FiveActivePanel target = panel;
			SwingUtilities.invokeLater(() -> target.update(data));
		});
	}

	private PanelData buildPanelData()
	{
		if (state == null || client.getGameState() != GameState.LOGGED_IN || ticksUntilPanelReady >= 0)
		{
			return PanelData.LOGGED_OUT;
		}

		Map<Quest, QuestState> questStates = rollingEngine.questStates();
		int totalKc = getTotalBossKc();

		List<PanelData.SlotView> skills = state.getSkills().stream()
			.map(s -> new PanelData.SlotView(s.getValue().getName(), s.getValue(), s.getProgress(), Rules.skillGoal(s), s.isDone(), s.isFresh(), null, -1))
			.collect(Collectors.toList());
		List<PanelData.SlotView> bosses = state.getBosses().stream()
			.map(s -> new PanelData.SlotView(s.getValue().getDisplayName(), null, s.getProgress(), s.getValue().getKillsToComplete(), s.isDone(), s.isFresh(), null, iconSprite(s.getValue())))
			.collect(Collectors.toList());
		List<PanelData.SlotView> quests = state.getQuests().stream()
			.map(s -> new PanelData.SlotView(s.getValue().getName(), null, 0, 0, s.isDone(), s.isFresh(), questStates.get(s.getValue()), -1))
			.collect(Collectors.toList());

		// How many slots a roll would actually fill, given what's left in each pool
		List<Skill> skillPool = rollingEngine.rollableSkills(questStates);
		skillPool.removeIf(s -> findActive(state.getSkills(), s) != null);
		int skillsCanRoll = Math.min(RollingEngine.openSlots(state.getSkills()), skillPool.size());
		boolean allSkillsDone = skillPool.isEmpty() && state.getSkills().isEmpty();
		List<Skill> maxedSkills = new ArrayList<>();
		for (Skill skill : Skill.values())
		{
			if (!Rules.NEVER_ROLLED.contains(skill) && rollingEngine.isMaxed(skill))
			{
				maxedSkills.add(skill);
			}
		}
		int unlockedTiers = getUnlockedTiers();
		int pendingTier = getPendingTier();
		// New boss rolls wait while a tier is ready to be claimed
		int bossesCanRoll = pendingTier >= 0 ? 0 : Math.min(RollingEngine.openSlots(state.getBosses()),
			countRollableNotActive(state.getBosses(), totalKc, questStates));
		List<Quest> questPool = rollingEngine.rollableQuests(questStates);
		questPool.removeIf(q -> findActive(state.getQuests(), q) != null);
		int questsCanRoll = Math.min(RollingEngine.openSlots(state.getQuests()), questPool.size());
		boolean allQuestsDone = questPool.isEmpty() && state.getQuests().isEmpty();

		List<PanelData.TierView> tiers = new ArrayList<>();
		for (BossTiers.Tier tier : BossTiers.TIERS)
		{
			List<PanelData.BossView> views = new ArrayList<>();
			for (Boss boss : tier.getBosses())
			{
				String reason = rollingEngine.bossLockReason(boss, unlockedTiers, totalKc, questStates);
				PanelData.BossStatus status = findActive(state.getBosses(), boss) != null ? PanelData.BossStatus.ACTIVE
					: reason == null ? PanelData.BossStatus.ROLLABLE
					: PanelData.BossStatus.LOCKED;
				views.add(new PanelData.BossView(boss.getDisplayName(), status, status == PanelData.BossStatus.LOCKED ? reason : null,
					iconSprite(boss), requirements(boss, questStates)));
			}
			tiers.add(new PanelData.TierView(tier.getKillsRequired(), BossTiers.TIERS.indexOf(tier) < unlockedTiers, views));
		}

		return PanelData.builder()
			.loggedIn(true)
			.shuffles(getAvailableShuffles())
			.collectionLogSlots(state.getCollectionLogSlots())
			.skills(skills)
			.skillsCanRoll(skillsCanRoll)
			.maxedSkills(maxedSkills)
			.allSkillsDone(allSkillsDone)
			.bosses(bosses)
			.bossesCanRoll(bossesCanRoll)
			.quests(quests)
			.questsCanRoll(questsCanRoll)
			.allQuestsDone(allQuestsDone)
			.totalKc(totalKc)
			.questsCompleted(questsCompleted(questStates))
			.questsTotal(questsTotal())
			.totalLevel(client.getTotalLevel())
			.combatLevel(client.getLocalPlayer() != null ? client.getLocalPlayer().getCombatLevel() : 0)
			.accountType(client.getVarbitValue(VarbitID.IRONMAN))
			.unlockedTiers(unlockedTiers)
			.pendingTier(pendingTier)
			.kcMode(config.kcMode())
			.tiers(tiers)
			.shufflable(shufflable(questStates))
			.build();
	}

	private Set<Category> shufflable(Map<Quest, QuestState> questStates)
	{
		Set<Category> categories = EnumSet.noneOf(Category.class);
		for (Category category : Category.values())
		{
			if (canShuffle(category, questStates))
			{
				categories.add(category);
			}
		}
		return categories;
	}

	private static int iconSprite(Boss boss)
	{
		return boss.getHiscore() == null ? -1 : boss.getHiscore().getSpriteId();
	}

	private int countRollableNotActive(List<Slot<Boss>> slots, int totalKc, Map<Quest, QuestState> questStates)
	{
		int count = 0;
		for (BossTiers.Tier tier : BossTiers.TIERS)
		{
			for (Boss boss : tier.getBosses())
			{
				if (findActive(slots, boss) == null && rollingEngine.bossLockReason(boss, getUnlockedTiers(), totalKc, questStates) == null)
				{
					count++;
				}
			}
		}
		return count;
	}
}
