package com.bswmagicgaming.fiveactive;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * The 16x16 sidebar icon: "5A" in the plugin's pixel font (see {@link PixelFont}), an orange 5 and a white A,
 * each with a one-pixel drop shadow. The panel title uses the same letters and colours.
 */
final class NavIcon
{
	static final Color ORANGE = new Color(255, 152, 0);
	static final Color ORANGE_SHADOW = new Color(150, 80, 0);
	static final Color WHITE = new Color(235, 235, 235);
	static final Color WHITE_SHADOW = new Color(90, 90, 90);

	static BufferedImage create()
	{
		BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		PixelFont.draw(icon, 1, 3, 1,
			new PixelFont.Run("5", ORANGE, ORANGE_SHADOW),
			new PixelFont.Run("A", WHITE, WHITE_SHADOW));
		return icon;
	}

	/** "FIVE ACTIVE" in the same style, scaled up for the panel header. */
	static BufferedImage title(int scale)
	{
		return outline(PixelFont.render(scale,
			new PixelFont.Run("FIVE ", ORANGE, ORANGE_SHADOW),
			new PixelFont.Run("ACTIVE", WHITE, WHITE_SHADOW)));
	}

	/**
	 * Adds a 1px black outline around every drawn pixel (letters and shadow together), like the panel's other
	 * titles, so the logo reads clearly. The image grows by 1px on each side.
	 */
	static BufferedImage outline(BufferedImage image)
	{
		int w = image.getWidth();
		int h = image.getHeight();
		BufferedImage out = new BufferedImage(w + 2, h + 2, BufferedImage.TYPE_INT_ARGB);
		int black = Color.BLACK.getRGB();
		for (int y = 0; y < h; y++)
		{
			for (int x = 0; x < w; x++)
			{
				if ((image.getRGB(x, y) >>> 24) == 0)
				{
					continue;
				}
				// Paint black around the pixel; the real pixels are drawn over it afterwards
				for (int dy = 0; dy <= 2; dy++)
				{
					for (int dx = 0; dx <= 2; dx++)
					{
						out.setRGB(x + dx, y + dy, black);
					}
				}
			}
		}
		for (int y = 0; y < h; y++)
		{
			for (int x = 0; x < w; x++)
			{
				int rgb = image.getRGB(x, y);
				if ((rgb >>> 24) != 0)
				{
					out.setRGB(x + 1, y + 1, rgb);
				}
			}
		}
		return out;
	}

	private NavIcon()
	{
	}
}
