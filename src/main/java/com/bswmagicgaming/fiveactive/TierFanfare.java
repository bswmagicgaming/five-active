package com.bswmagicgaming.fiveactive;

import net.runelite.api.gameval.SpotanimID;

/**
 * How big each boss tier unlock's celebration is. Every tier is flashier than the last: longer trembling,
 * harder shaking, bigger fireworks, a bigger banner that stays up longer, more booms. Tier 5 (the last one)
 * gets everything. Indexes are into BossTiers.TIERS (1 = tier 2, the first tier ever unlocked).
 */
final class TierFanfare
{
	/** How long the tier's icons tremble before the first pop. */
	private static final int[] TREMBLE_MS = {1350, 1350, 1650, 1950, 2400};
	/** How far (pixels) the icons shake at full strength. */
	private static final int[] SHAKE_PX = {2, 2, 2, 3, 3};
	/** How long the banner and fireworks stay up. */
	private static final int[] CELEBRATE_MS = {3500, 3500, 5000, 6500, 9000};
	/** Pixel size of the banner's letters. */
	private static final int[] BANNER_SCALE = {3, 3, 4, 4, 5};
	/** How many times the finale sound booms (a little apart). */
	private static final int[] FINALE_BOOMS = {1, 1, 1, 2, 3};
	/** Fireworks (spot animations, played together): normal level-up, level 99, max total level, then max total and 99 layered. */
	private static final int[][] EFFECTS = {
		{SpotanimID.LEVELUP_ANIM},
		{SpotanimID.LEVELUP_ANIM},
		{SpotanimID.LEVELUP_99_ANIM},
		{SpotanimID.LEVELUP_MAX},
		{SpotanimID.LEVELUP_MAX, SpotanimID.LEVELUP_99_ANIM},
	};

	/** Sound effect IDs for the ceremony: the Unlock button's rumble, each boss popping in, and the finale. */
	static final int RUMBLE_SOUND = 1930;
	static final int POP_SOUND = 3929;
	static final int FINALE_SOUND = 3813;

	static java.util.List<Integer> effects(int tier)
	{
		java.util.List<Integer> effects = new java.util.ArrayList<>();
		for (int id : EFFECTS[clamp(tier)])
		{
			effects.add(id);
		}
		return effects;
	}

	static int trembleMs(int tier)
	{
		return TREMBLE_MS[clamp(tier)];
	}

	static int shakePx(int tier)
	{
		return SHAKE_PX[clamp(tier)];
	}

	static int celebrateMs(int tier)
	{
		return CELEBRATE_MS[clamp(tier)];
	}

	static int bannerScale(int tier)
	{
		return BANNER_SCALE[clamp(tier)];
	}

	static int finaleBooms(int tier)
	{
		return FINALE_BOOMS[clamp(tier)];
	}

	/** The last tier: the banner shimmers and says every boss is unlocked. */
	static boolean isLast(int tier)
	{
		return tier >= BossTiers.TIERS.size() - 1;
	}

	/** Tier 4 and up: the banner's letters get a pulsing gold glow. */
	static boolean glows(int tier)
	{
		return tier >= 3;
	}

	private static int clamp(int tier)
	{
		return Math.max(0, Math.min(tier, TREMBLE_MS.length - 1));
	}

	private TierFanfare()
	{
	}
}
