package com.bswmagicgaming.fiveactive;

import com.google.common.collect.ImmutableMap;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * The chunky hand-drawn pixel letters used for the sidebar icon, the panel title and the tier unlock banner.
 * Each glyph is 9 pixels tall with 2-pixel strokes; '#' is a filled pixel.
 * Text is drawn with a one-pixel drop shadow, and can be scaled up by a whole number so it stays crisp.
 */
final class PixelFont
{
	static final int HEIGHT = 9;
	private static final int LETTER_GAP = 1;
	private static final int SPACE_WIDTH = 3;

	private static final Map<Character, String[]> GLYPHS = ImmutableMap.<Character, String[]>builder()
		.put('5', new String[]{
			"######",
			"######",
			"##....",
			"#####.",
			"######",
			"....##",
			"##..##",
			"######",
			".####.",
		})
		.put('A', new String[]{
			".####.",
			"######",
			"##..##",
			"##..##",
			"######",
			"######",
			"##..##",
			"##..##",
			"##..##",
		})
		.put('C', new String[]{
			".#####",
			"######",
			"##....",
			"##....",
			"##....",
			"##....",
			"##....",
			"######",
			".#####",
		})
		.put('E', new String[]{
			"#####",
			"#####",
			"##...",
			"####.",
			"####.",
			"##...",
			"##...",
			"#####",
			"#####",
		})
		.put('F', new String[]{
			"#####",
			"#####",
			"##...",
			"####.",
			"####.",
			"##...",
			"##...",
			"##...",
			"##...",
		})
		.put('I', new String[]{
			"##",
			"##",
			"##",
			"##",
			"##",
			"##",
			"##",
			"##",
			"##",
		})
		.put('T', new String[]{
			"######",
			"######",
			"..##..",
			"..##..",
			"..##..",
			"..##..",
			"..##..",
			"..##..",
			"..##..",
		})
		.put('V', new String[]{
			"##..##",
			"##..##",
			"##..##",
			"##..##",
			"##..##",
			"##..##",
			".####.",
			".####.",
			"..##..",
		})
		.put('1', new String[]{
			"..##",
			".###",
			"####",
			"..##",
			"..##",
			"..##",
			"..##",
			"..##",
			"..##",
		})
		.put('2', new String[]{
			".####.",
			"######",
			"##..##",
			"...###",
			"..###.",
			".###..",
			"###...",
			"######",
			"######",
		})
		.put('3', new String[]{
			"#####.",
			"######",
			"....##",
			"..###.",
			"..####",
			"....##",
			"##..##",
			"######",
			".####.",
		})
		.put('4', new String[]{
			"##..##",
			"##..##",
			"##..##",
			"######",
			"######",
			"....##",
			"....##",
			"....##",
			"....##",
		})
		.put('D', new String[]{
			"#####.",
			"######",
			"##..##",
			"##..##",
			"##..##",
			"##..##",
			"##..##",
			"######",
			"#####.",
		})
		.put('K', new String[]{
			"##..##",
			"##.##.",
			"####..",
			"###...",
			"###...",
			"####..",
			"##.##.",
			"##..##",
			"##..##",
		})
		.put('L', new String[]{
			"##...",
			"##...",
			"##...",
			"##...",
			"##...",
			"##...",
			"##...",
			"#####",
			"#####",
		})
		.put('N', new String[]{
			"##...##",
			"###..##",
			"####.##",
			"##.####",
			"##..###",
			"##...##",
			"##...##",
			"##...##",
			"##...##",
		})
		.put('O', new String[]{
			".####.",
			"######",
			"##..##",
			"##..##",
			"##..##",
			"##..##",
			"##..##",
			"######",
			".####.",
		})
		.put('R', new String[]{
			"#####.",
			"######",
			"##..##",
			"##..##",
			"######",
			"#####.",
			"##.##.",
			"##..##",
			"##..##",
		})
		.put('U', new String[]{
			"##..##",
			"##..##",
			"##..##",
			"##..##",
			"##..##",
			"##..##",
			"##..##",
			"######",
			".####.",
		})
		.put('!', new String[]{
			"##",
			"##",
			"##",
			"##",
			"##",
			"##",
			"..",
			"##",
			"##",
		})
		.build();

	/** A run of text in one colour, with the colour of its drop shadow. */
	static final class Run
	{
		final String text;
		final Color color;
		final Color shadow;

		Run(String text, Color color, Color shadow)
		{
			this.text = text;
			this.color = color;
			this.shadow = shadow;
		}
	}

	/** Width in unscaled pixels, not counting the shadow. */
	static int width(Run... runs)
	{
		int x = 0;
		for (Run run : runs)
		{
			for (char c : run.text.toCharArray())
			{
				x += advance(c);
			}
		}
		return Math.max(0, x - LETTER_GAP);
	}

	/** Renders the runs side by side at the given whole-number scale, shadow included. */
	static BufferedImage render(int scale, Run... runs)
	{
		// +1 for the shadow, which sits one pixel down and right
		BufferedImage image = new BufferedImage((width(runs) + 1) * scale, (HEIGHT + 1) * scale, BufferedImage.TYPE_INT_ARGB);
		draw(image, 0, 0, scale, runs);
		return image;
	}

	/** Draws the runs onto image with their top-left at (left, top), in scaled pixels. */
	static void draw(BufferedImage image, int left, int top, int scale, Run... runs)
	{
		// Every shadow first, so no shadow ever covers a neighbouring letter
		drawLayer(image, left + scale, top + scale, scale, true, runs);
		drawLayer(image, left, top, scale, false, runs);
	}

	private static void drawLayer(BufferedImage image, int left, int top, int scale, boolean shadow, Run... runs)
	{
		int x = left;
		for (Run run : runs)
		{
			int rgb = (shadow ? run.shadow : run.color).getRGB();
			for (char c : run.text.toCharArray())
			{
				String[] glyph = GLYPHS.get(c);
				if (glyph != null)
				{
					for (int gy = 0; gy < glyph.length; gy++)
					{
						for (int gx = 0; gx < glyph[gy].length(); gx++)
						{
							if (glyph[gy].charAt(gx) == '#')
							{
								fill(image, x + gx * scale, top + gy * scale, scale, rgb);
							}
						}
					}
				}
				x += advance(c) * scale;
			}
		}
	}

	private static int advance(char c)
	{
		String[] glyph = GLYPHS.get(c);
		return glyph == null ? SPACE_WIDTH : glyph[0].length() + LETTER_GAP;
	}

	private static void fill(BufferedImage image, int x, int y, int size, int rgb)
	{
		for (int dy = 0; dy < size; dy++)
		{
			for (int dx = 0; dx < size; dx++)
			{
				int px = x + dx;
				int py = y + dy;
				if (px >= 0 && py >= 0 && px < image.getWidth() && py < image.getHeight())
				{
					image.setRGB(px, py, rgb);
				}
			}
		}
	}

	private PixelFont()
	{
	}
}
