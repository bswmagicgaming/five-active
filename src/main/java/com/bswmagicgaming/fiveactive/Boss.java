package com.bswmagicgaming.fiveactive;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import net.runelite.api.Quest;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;
import static net.runelite.api.Quest.*;

/**
 * Every boss the mode knows about, with its unlock requirements.
 * Which bosses are actually rollable (and at what total KC) is decided by {@link BossTiers}.
 *
 * Each boss is declared as {@code NAME("Display name", options...)}. Options, all optional:
 * <ul>
 * <li>{@code quest(...)} quests that must be complete before the boss can be rolled</li>
 * <li>{@code slayer(n)} Slayer level needed to fight it</li>
 * <li>{@code kc(...)} in-game kill count names, if not the same as the display name. These are the names from the
 * "Your ___ kill count is: N" message. A boss with several modes/counters lists each one, and kills on any of them count</li>
 * <li>{@code oneKill()} completed after a single kill instead of {@link Rules#KILLS_TO_COMPLETE_BOSS} (wave bosses, and bosses that are slow to get another attempt at)</li>
 * <li>{@code hiscore(...)} the hiscores entry to take the panel icon from, only needed when it can't be matched by name</li>
 * </ul>
 *
 * Search for "FA-VERIFY" to find entries that need double-checking.
 */
@Getter
enum Boss
{
	// --- No requirements ---
	BRYOPHYTA("Bryophyta"),
	CHAOS_ELEMENTAL("Chaos Elemental"),
	CHAOS_FANATIC("Chaos Fanatic"),
	CHAMBERS_OF_XERIC("Chambers of Xeric", kc("Chambers of Xeric", "Chambers of Xeric Challenge Mode")),
	CORPOREAL_BEAST("Corporeal Beast"),
	CRAZY_ARCHAEOLOGIST("Crazy Archaeologist"),
	DAGANNOTH_KINGS("Dagannoth Kings", kc("Dagannoth Rex", "Dagannoth Prime", "Dagannoth Supreme")),
	GIANT_MOLE("Giant Mole"),
	KALPHITE_QUEEN("Kalphite Queen"),
	KING_BLACK_DRAGON("King Black Dragon"),
	OBOR("Obor"),
	ROYAL_TITANS("Royal Titans"),
	SARACHNIS("Sarachnis"),
	SCORPIA("Scorpia"),
	SCURRIUS("Scurrius"),
	TEMPOROSS("Tempoross"),
	WINTERTODT("Wintertodt"),

	// --- Wilderness bosses ---
	ARTIO("Artio"),
	CALLISTO("Callisto"),
	CALVARION("Calvar'ion"),
	SPINDEL("Spindel"),
	VENENATIS("Venenatis"),
	VETION("Vet'ion"),

	// --- One-kill bosses (done after a single kill) ---
	// Wave bosses: one completion is a whole run of waves
	FIGHT_CAVES("Fight Caves", kc("TzTok-Jad"), oneKill()),
	INFERNO("Inferno", kc("TzKal-Zuk"), oneKill()),
	FORTIS_COLOSSEUM("Fortis Colosseum", kc("Sol Heredit"), oneKill(), quest(CHILDREN_OF_THE_SUN)),
	// Slow to get another attempt at (a farming patch to grow, a rare casket, a totem to piece together)
	HESPORI("Hespori", oneKill()),
	MIMIC("Mimic", oneKill()),
	SKOTIZO("Skotizo", oneKill()),

	// --- God Wars Dungeon ---
	COMMANDER_ZILYANA("Commander Zilyana", quest(DEATH_PLATEAU)),
	GENERAL_GRAARDOR("General Graardor", quest(DEATH_PLATEAU)),
	KREEARRA("Kree'arra", quest(DEATH_PLATEAU)),
	KRIL_TSUTSAROTH("K'ril Tsutsaroth", quest(DEATH_PLATEAU)),
	NEX("Nex", quest(THE_FROZEN_DOOR)),

	// --- Slayer bosses ---
	SHELLBANE_GRYPHON("Shellbane Gryphon", slayer(51), quest(TROUBLED_TORTUGANS)),
	GROTESQUE_GUARDIANS("Grotesque Guardians", slayer(75)),
	ABYSSAL_SIRE("Abyssal Sire", slayer(85), quest(ENTER_THE_ABYSS)),
	KRAKEN("Kraken", slayer(87)),
	CERBERUS("Cerberus", slayer(91)),
	ARAXXOR("Araxxor", slayer(92)),
	THERMONUCLEAR_SMOKE_DEVIL("Thermonuclear Smoke Devil", slayer(93)),
	ALCHEMICAL_HYDRA("Alchemical Hydra", slayer(95)),

	// --- Quest-locked bosses ---
	AMOXLIATL("Amoxliatl", quest(THE_HEART_OF_DARKNESS)),
	BARROWS("Barrows", kc("Barrows Chests"), quest(PRIEST_IN_PERIL)),
	BRUTUS("Brutus", quest(THE_IDES_OF_MILK)),
	DERANGED_ARCHAEOLOGIST("Deranged Archaeologist", quest(BONE_VOYAGE)),
	DOOM_OF_MOKHAIOTL("Doom of Mokhaiotl", quest(THE_FINAL_DAWN)), // FA-VERIFY: kills are read from a varp, not chat (see ProgressionListener)
	DUKE_SUCELLUS("Duke Sucellus", quest(DESERT_TREASURE_II__THE_FALLEN_EMPIRE)),
	PERILOUS_MOONS("Perilous Moons", kc("Lunar Chest"), quest(Quest.PERILOUS_MOONS), hiscore(HiscoreSkill.LUNAR_CHESTS)),
	PHANTOM_MUSPAH("Phantom Muspah", quest(SECRETS_OF_THE_NORTH)),
	THE_GAUNTLET("The Gauntlet", kc("Gauntlet", "Corrupted Gauntlet"), quest(SONG_OF_THE_ELVES)),
	THE_HUEYCOATL("The Hueycoatl", kc("Hueycoatl"), quest(CHILDREN_OF_THE_SUN)),
	THE_LEVIATHAN("The Leviathan", quest(DESERT_TREASURE_II__THE_FALLEN_EMPIRE)),
	THE_NIGHTMARE("The Nightmare", kc("Nightmare", "Phosani's Nightmare"), quest(PRIEST_IN_PERIL)),
	THE_WHISPERER("The Whisperer", quest(DESERT_TREASURE_II__THE_FALLEN_EMPIRE)),
	THEATRE_OF_BLOOD("Theatre of Blood", kc("Theatre of Blood", "Theatre of Blood Hard Mode", "Theatre of Blood Entry Mode"), quest(PRIEST_IN_PERIL)),
	TOMBS_OF_AMASCUT("Tombs of Amascut", kc("Tombs of Amascut", "Tombs of Amascut Entry Mode", "Tombs of Amascut Expert Mode"), quest(BENEATH_CURSED_SANDS)),
	VARDORVIS("Vardorvis", quest(DESERT_TREASURE_II__THE_FALLEN_EMPIRE)),
	VORKATH("Vorkath", quest(DRAGON_SLAYER_II)),
	YAMA("Yama", quest(A_KINGDOM_DIVIDED)),
	ZALCANO("Zalcano", quest(SONG_OF_THE_ELVES)),
	ZULRAH("Zulrah", quest(REGICIDE)),

	// --- Requirements unknown ---
	MAD_ANGEL("Mad Angel", quest(FALLEN_FROM_GRACE)),
	MAGGOT_KING("Maggot King", quest(THE_BLOOD_MOON_RISES)), // FA-VERIFY: check whether it has a kill count chat message
	;

	/** Name shown in the side panel. */
	private final String displayName;
	/** In-game kill count counter names for this boss (see class docs). */
	private final List<String> kcNames;
	/** Slayer level needed to fight this boss, or 0. */
	private final int slayerLevel;
	/** Quests that must be complete before this boss can be rolled. */
	private final List<Quest> requiredQuests;
	/** Kills needed to complete this boss once it is active. */
	private final int killsToComplete;
	/** Hiscores entry whose 25x25 sprite is used as this boss's icon, or null if there isn't one. */
	private final HiscoreSkill hiscore;

	Boss(String displayName, Option... options)
	{
		String[] kcNames = {displayName};
		int slayerLevel = 0;
		Quest[] quests = {};
		int killsToComplete = Rules.KILLS_TO_COMPLETE_BOSS;
		HiscoreSkill hiscore = null;
		for (Option option : options)
		{
			if (option.hiscore != null)
			{
				hiscore = option.hiscore;
			}
			if (option.kcNames != null)
			{
				kcNames = option.kcNames;
			}
			if (option.quests != null)
			{
				quests = option.quests;
			}
			slayerLevel = Math.max(slayerLevel, option.slayerLevel);
			if (option.killsToComplete > 0)
			{
				killsToComplete = option.killsToComplete;
			}
		}
		this.displayName = displayName;
		this.kcNames = Collections.unmodifiableList(Arrays.asList(kcNames));
		this.slayerLevel = slayerLevel;
		this.requiredQuests = Collections.unmodifiableList(Arrays.asList(quests));
		this.killsToComplete = killsToComplete;
		this.hiscore = hiscore != null ? hiscore : findHiscore(displayName, kcNames);
	}

	/** Matches a hiscores boss entry by display name or any kill count name, ignoring case, punctuation and "The". */
	private static HiscoreSkill findHiscore(String displayName, String[] kcNames)
	{
		for (HiscoreSkill skill : HiscoreSkill.values())
		{
			if (skill.getType() != HiscoreSkillType.BOSS)
			{
				continue;
			}
			String hiscoreName = matchKey(skill.getName());
			if (hiscoreName.equals(matchKey(displayName)))
			{
				return skill;
			}
			for (String kcName : kcNames)
			{
				if (hiscoreName.equals(matchKey(kcName)))
				{
					return skill;
				}
			}
		}
		return null;
	}

	private static String matchKey(String name)
	{
		return name.toLowerCase().replaceFirst("^the ", "").replaceAll("[^a-z0-9]", "");
	}

	/** One declaration option; see the class docs. */
	private static final class Option
	{
		private String[] kcNames;
		private Quest[] quests;
		private int slayerLevel;
		private int killsToComplete;
		private HiscoreSkill hiscore;
	}

	private static Option kc(String... names)
	{
		Option option = new Option();
		option.kcNames = names;
		return option;
	}

	private static Option quest(Quest... quests)
	{
		Option option = new Option();
		option.quests = quests;
		return option;
	}

	private static Option slayer(int level)
	{
		Option option = new Option();
		option.slayerLevel = level;
		return option;
	}

	private static Option oneKill()
	{
		Option option = new Option();
		option.killsToComplete = 1;
		return option;
	}

	private static Option hiscore(HiscoreSkill skill)
	{
		Option option = new Option();
		option.hiscore = skill;
		return option;
	}

	/** Normalises a kill count counter name so chat text and our names compare reliably. */
	static String normalizeKcName(String name)
	{
		return name.toLowerCase().replace(":", "").trim();
	}

	/** Finds the boss owning an in-game kill count counter name, or null. */
	static Boss fromKcName(String kcName)
	{
		String normalized = normalizeKcName(kcName);
		for (Boss boss : values())
		{
			for (String name : boss.kcNames)
			{
				if (normalizeKcName(name).equals(normalized))
				{
					return boss;
				}
			}
		}
		return null;
	}
}
