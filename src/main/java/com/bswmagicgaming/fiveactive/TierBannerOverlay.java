package com.bswmagicgaming.fiveactive;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * "TIER X UNLOCKED!" across the middle of the game view when a boss tier is claimed, in the panel's pixel
 * letters: it springs in, holds for a moment with how many bosses joined, then shrinks away.
 */
class TierBannerOverlay extends Overlay
{
	private static final int POP_IN_MS = 450;
	private static final int POP_OUT_MS = 300;
	/** Space between the big title and the line under it. */
	private static final int SUBTITLE_GAP = 14;
	private static final Color GOLD = new Color(255, 200, 40);
	private static final Color GOLD_SHADOW = new Color(140, 90, 0);

	private final Client client;
	private volatile long shownAt;
	private volatile BufferedImage title;
	private volatile String subtitle;
	private volatile int tier;
	/** When the banner starts popping out (it's on screen for as long as the tier's fireworks). */
	private volatile int holdUntilMs;

	@Inject
	TierBannerOverlay(Client client)
	{
		this.client = client;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGHEST);
	}

	/** Shows the banner for tier (an index into BossTiers.TIERS). Any thread. */
	void show(int tier, int bossCount)
	{
		this.tier = tier;
		title = titleImage(tier, NavIcon.WHITE, NavIcon.WHITE_SHADOW);
		String joined = bossCount == 0 ? "Its bosses need a quest or Slayer level first"
			: bossCount + " new " + (bossCount == 1 ? "boss joins" : "bosses join") + " the pool";
		subtitle = TierFanfare.isLast(tier) ? "Every tier unlocked!  " + joined : joined;
		holdUntilMs = TierFanfare.celebrateMs(tier) - POP_OUT_MS;
		shownAt = System.currentTimeMillis();
	}

	/** "TIER X " in gold, then "UNLOCKED!" in the given colour, at the tier's size. */
	private static BufferedImage titleImage(int tier, Color unlocked, Color unlockedShadow)
	{
		return NavIcon.outline(PixelFont.render(TierFanfare.bannerScale(tier),
			new PixelFont.Run("TIER " + (tier + 1) + " ", GOLD, GOLD_SHADOW),
			new PixelFont.Run("UNLOCKED!", unlocked, unlockedShadow)));
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		long start = shownAt;
		BufferedImage image = title;
		if (start == 0 || image == null)
		{
			return null;
		}
		long t = System.currentTimeMillis() - start;
		int holdUntil = holdUntilMs;
		int showTier = tier;
		if (TierFanfare.isLast(showTier))
		{
			// The last tier: "UNLOCKED!" shimmers through warm colours, gold to orange to red and back (no blue:
			// that's the credit's colour)
			float wave = 0.5f + 0.5f * (float) Math.sin(t / 300.0);
			Color shimmer = Color.getHSBColor(0.15f * wave, 0.7f, 1f);
			image = titleImage(showTier, shimmer, shimmer.darker().darker());
		}
		if (t > holdUntil + POP_OUT_MS)
		{
			shownAt = 0;
			return null;
		}

		float scale;
		if (t < POP_IN_MS)
		{
			// Springs in past full size and settles (easeOutBack)
			float u = t / (float) POP_IN_MS - 1f;
			scale = 0.2f + 0.8f * (1f + 3.5f * u * u * u + 2.5f * u * u);
		}
		else if (t < holdUntil)
		{
			scale = 1f;
		}
		else
		{
			float out = (t - holdUntil) / (float) POP_OUT_MS;
			scale = 1f - out * out;
		}
		if (scale <= 0f)
		{
			return null;
		}

		int cx = client.getViewportXOffset() + client.getViewportWidth() / 2;
		// A quarter of the way down the game view: up out of the way of your character
		int cy = client.getViewportYOffset() + client.getViewportHeight() / 4;
		Graphics2D g2 = (Graphics2D) g.create();
		// Nearest neighbour: the pixel letters stay crisp at any size
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		int w = Math.round(image.getWidth() * scale);
		int h = Math.round(image.getHeight() * scale);
		if (TierFanfare.glows(showTier) && t >= POP_IN_MS && t < holdUntil)
		{
			// Tier 4 and up: a soft gold glow pulses behind the letters
			float pulse = 0.5f + 0.5f * (float) Math.sin(t / 220.0);
			g2.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), Math.round(40 + 60 * pulse)));
			int grow = Math.round(6 + 6 * pulse);
			g2.fillRoundRect(cx - w / 2 - grow, cy - h / 2 - grow, w + grow * 2, h + grow * 2, 18, 18);
		}
		g2.drawImage(image, cx - w / 2, cy - h / 2, w, h, null);

		if (t >= POP_IN_MS && t < holdUntil)
		{
			Font font = FontManager.getRunescapeBoldFont();
			g2.setFont(font);
			FontMetrics fm = g2.getFontMetrics();
			String text = subtitle;
			int x = cx - fm.stringWidth(text) / 2;
			// A clear gap under the big letters, so the two lines don't crowd each other
			int y = cy + image.getHeight() / 2 + SUBTITLE_GAP + fm.getAscent();
			g2.setColor(Color.BLACK);
			for (int dx = -1; dx <= 1; dx++)
			{
				for (int dy = -1; dy <= 1; dy++)
				{
					if (dx != 0 || dy != 0)
					{
						g2.drawString(text, x + dx, y + dy);
					}
				}
			}
			g2.setColor(Color.WHITE);
			g2.drawString(text, x, y);
		}
		g2.dispose();
		return null;
	}
}
