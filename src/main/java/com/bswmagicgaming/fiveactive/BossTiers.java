package com.bswmagicgaming.fiveactive;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.Value;
import static com.bswmagicgaming.fiveactive.Boss.*;

/**
 * The boss pool, split into tiers that unlock as the player's total boss KC grows.
 * Edit the milestones and boss lists here to rebalance progression.
 * A boss that is not in any tier can never be rolled.
 */
final class BossTiers
{
	@Value
	static class Tier
	{
		/** Total boss KC needed before this tier's bosses join the pool (0 = available from the start). */
		int killsRequired;
		List<Boss> bosses;
	}

	static final List<Tier> TIERS = ImmutableList.of(
		new Tier(0, ImmutableList.of(
			BARROWS,
			BRUTUS,
			OBOR,
			BRYOPHYTA,
			SCURRIUS,
			GIANT_MOLE,
			AMOXLIATL,
			CRAZY_ARCHAEOLOGIST,
			DERANGED_ARCHAEOLOGIST,
			CHAOS_FANATIC,
			TEMPOROSS,
			WINTERTODT,
			HESPORI,
			DAGANNOTH_KINGS
		)),
		new Tier(100, ImmutableList.of(
			SARACHNIS,
			SCORPIA,
			PERILOUS_MOONS,
			CHAOS_ELEMENTAL,
			FIGHT_CAVES,
			SPINDEL,
			ARTIO,
			KING_BLACK_DRAGON,
			ROYAL_TITANS,
			THE_HUEYCOATL,
			CALVARION,
			SKOTIZO,
			KALPHITE_QUEEN,
			MIMIC,
			MAD_ANGEL,
			ZALCANO,
			SHELLBANE_GRYPHON
		)),
		new Tier(500, ImmutableList.of(
			THE_GAUNTLET,
			VORKATH,
			ZULRAH,
			VETION,
			TOMBS_OF_AMASCUT,
			DOOM_OF_MOKHAIOTL,
			PHANTOM_MUSPAH,
			GROTESQUE_GUARDIANS,
			ABYSSAL_SIRE,
			KRAKEN,
			CERBERUS,
			ARAXXOR,
			THERMONUCLEAR_SMOKE_DEVIL,
			ALCHEMICAL_HYDRA
		)),
		new Tier(1000, ImmutableList.of(
			GENERAL_GRAARDOR,
			KREEARRA,
			KRIL_TSUTSAROTH,
			COMMANDER_ZILYANA,
			DUKE_SUCELLUS,
			MAGGOT_KING,
			CHAMBERS_OF_XERIC,
			YAMA,
			VENENATIS,
			CALLISTO
		)),
		new Tier(2500, ImmutableList.of(
			THEATRE_OF_BLOOD,
			INFERNO,
			FORTIS_COLOSSEUM,
			THE_WHISPERER,
			THE_LEVIATHAN,
			VARDORVIS,
			THE_NIGHTMARE,
			CORPOREAL_BEAST,
			NEX
		))
	);

	/** The first tier the player has not reached yet, or null once everything is unlocked. */
	static Tier nextLockedTier(int totalKc)
	{
		for (Tier tier : TIERS)
		{
			if (totalKc < tier.getKillsRequired())
			{
				return tier;
			}
		}
		return null;
	}

	/** The tier a boss belongs to, or null if it is not in the pool at all. */
	static Tier tierOf(Boss boss)
	{
		for (Tier tier : TIERS)
		{
			if (tier.getBosses().contains(boss))
			{
				return tier;
			}
		}
		return null;
	}

	private BossTiers()
	{
	}
}
