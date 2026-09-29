package com.bswmagicgaming.fiveactive;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.Timer;
import lombok.Getter;
import lombok.Value;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Slot machine reveal for freshly rolled slots. Each new slot becomes a reel that scrolls through
 * random candidates, slows down, and lands on the real result one reel after another, then flashes.
 *
 * The result is already decided and saved before this starts; this is purely presentation.
 * Runs on the Swing thread.
 */
class RollAnimation
{
	/** How long the first reel spins before landing. */
	private static final int SPIN_MS = 2400;
	/** Extra spin time for each following reel, so they land one after another. */
	private static final int STAGGER_MS = 800;
	/** How long a landed reel flashes. */
	private static final int FLASH_MS = 800;
	/** After the flash, how long the reel takes to fade into the normal row. */
	private static final int SETTLE_MS = 450;
	/** How long the "NEW!" pop takes to settle. */
	private static final int POP_MS = 250;
	/** Time between candidate changes: starts fast, eases out to slow. */
	private static final int FASTEST_CHANGE_MS = 45;
	private static final int SLOWEST_CHANGE_MS = 380;
	/** Longest time a candidate takes to slide into place. */
	private static final int MAX_SLIDE_MS = 110;
	/** Minimum gap between tick sounds, so several reels don't turn into noise. */
	private static final int TICK_SOUND_GAP_MS = 70;

	private static final Color ORANGE = ColorScheme.BRAND_ORANGE;
	private static final Item MYSTERY = new Item("", "???", null);

	/**
	 * Something a reel can show: a name and an optional icon. id identifies what it is (e.g. the Skill's name()).
	 * The icon is looked up every frame, so an icon that is still loading appears as soon as it arrives.
	 */
	@Value
	static class Item
	{
		String id;
		String name;
		Supplier<Image> iconSource;

		Image getIcon()
		{
			return iconSource == null ? null : iconSource.get();
		}
	}

	interface Listener
	{
		/** A reel moved to the next candidate. */
		void tick();

		/** A reel landed on its result. */
		void land(Item result);

		/** Every reel has landed and finished flashing. */
		void finished();
	}

	@Getter
	private final Category category;
	private final Map<Integer, Reel> reels = new TreeMap<>();
	private final List<Item> candidates;
	private final Listener listener;
	private final Random random = new Random();
	private final Timer timer;
	/** Row components currently on screen; the panel re-registers them whenever it rebuilds. */
	private final List<JComponent> rows = new ArrayList<>();
	private long lastTickSound;
	private boolean finished;

	/**
	 * @param results slot index to the item it will land on, in top-to-bottom order
	 * @param candidates items to flash past while spinning
	 */
	RollAnimation(Category category, Map<Integer, Item> results, List<Item> candidates, Listener listener)
	{
		this.category = category;
		this.candidates = candidates;
		this.listener = listener;

		long now = System.currentTimeMillis();
		int order = 0;
		for (Map.Entry<Integer, Item> result : new TreeMap<>(results).entrySet())
		{
			Reel reel = new Reel(result.getValue(), now, now + SPIN_MS + (long) order * STAGGER_MS);
			reel.current = randomCandidate(null, reel.target);
			reels.put(result.getKey(), reel);
			order++;
		}
		timer = new Timer(16, e -> step());
	}

	void start()
	{
		timer.start();
	}

	/** Stops immediately without a reveal (e.g. the plugin shut down). */
	void cancel()
	{
		timer.stop();
		finished = true;
	}

	boolean isAnimating(int slotIndex)
	{
		return !finished && reels.containsKey(slotIndex);
	}

	/** Called by the panel at the start of every rebuild, before it creates new rows. */
	void clearRows()
	{
		rows.clear();
	}

	/**
	 * A row that draws the reel for slot index. finalRow is the normal row it will be replaced by: the reel
	 * takes its size, and fades into a picture of it at the end so the swap is seamless.
	 */
	JComponent createRow(int slotIndex, JComponent finalRow)
	{
		ReelRow row = new ReelRow(reels.get(slotIndex), finalRow);
		rows.add(row);
		return row;
	}

	private void step()
	{
		long now = System.currentTimeMillis();
		boolean ticked = false;
		long lastLanding = 0;
		boolean allLanded = true;

		for (Reel reel : reels.values())
		{
			if (!reel.landed)
			{
				if (now >= reel.stopAt)
				{
					reel.show(reel.target, now, MAX_SLIDE_MS + 40);
					reel.landed = true;
					reel.landedAt = now;
					listener.land(reel.target);
				}
				else if (now >= reel.nextChangeAt)
				{
					double progress = (double) (now - reel.startAt) / (reel.stopAt - reel.startAt);
					long interval = FASTEST_CHANGE_MS + Math.round((SLOWEST_CHANGE_MS - FASTEST_CHANGE_MS) * Math.pow(progress, 2.5));
					reel.show(randomCandidate(reel.current, reel.target), now, Math.min(interval, MAX_SLIDE_MS));
					reel.nextChangeAt = now + interval;
					ticked = true;
				}
			}
			allLanded &= reel.landed;
			lastLanding = Math.max(lastLanding, reel.landedAt);
		}

		if (ticked && now - lastTickSound >= TICK_SOUND_GAP_MS)
		{
			lastTickSound = now;
			listener.tick();
		}

		rows.forEach(JComponent::repaint);

		if (allLanded && now - lastLanding >= FLASH_MS + SETTLE_MS)
		{
			timer.stop();
			finished = true;
			listener.finished();
		}
	}

	/**
	 * A random candidate that isn't what's showing now, isn't this reel's result (so the landing is a surprise),
	 * and hasn't already landed on another reel (each result can only appear once).
	 */
	private Item randomCandidate(Item current, Item target)
	{
		List<Item> options = new ArrayList<>();
		for (Item item : candidates)
		{
			String name = item.getName();
			boolean landedElsewhere = reels.values().stream().anyMatch(r -> r.landed && r.target.getName().equals(name));
			if (!name.equals(target.getName())
				&& (current == null || !name.equals(current.getName()))
				&& !landedElsewhere)
			{
				options.add(item);
			}
		}
		// Tiny pool with nothing else to show: spin a mystery card rather than give the result away early
		return options.isEmpty() ? MYSTERY : options.get(random.nextInt(options.size()));
	}

	private static class Reel
	{
		final Item target;
		final long startAt;
		final long stopAt;
		Item previous;
		Item current;
		long changedAt;
		long slideMs = 1;
		long nextChangeAt;
		boolean landed;
		long landedAt;

		Reel(Item target, long startAt, long stopAt)
		{
			this.target = target;
			this.startAt = startAt;
			this.stopAt = stopAt;
			this.changedAt = startAt;
			this.nextChangeAt = startAt;
		}

		void show(Item item, long now, long slide)
		{
			previous = current;
			current = item;
			changedAt = now;
			slideMs = Math.max(1, slide);
		}
	}

	/** Custom-painted row: the reel window, the flash when it lands, and the "NEW!" pop. */
	private static class ReelRow extends JComponent
	{
		private static final int PAD_X = 6;
		private static final int ICON_SIZE = FiveActivePanel.ICON_SIZE;
		private static final int ICON_GAP = 6;
		/** Space kept free on the right for the "NEW!" pop. */
		private static final int NEW_RESERVE = 44;

		private final Reel reel;
		private final JComponent finalRow;
		private BufferedImage finalImage;
		/** The normal row's contents without its background, for drawing the landed result. */
		private BufferedImage contentImage;

		ReelRow(Reel reel, JComponent finalRow)
		{
			this.reel = reel;
			this.finalRow = finalRow;
			int height = finalRow.getPreferredSize().height;
			setPreferredSize(new Dimension(0, height));
			setMinimumSize(new Dimension(0, height));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			int w = getWidth();
			int h = getHeight();
			long now = System.currentTimeMillis();

			g2.setColor(ColorScheme.DARK_GRAY_COLOR);
			g2.fillRect(0, 0, w, h);

			float flash = reel.landed ? 1f - clamp((now - reel.landedAt) / (float) FLASH_MS) : 0f;
			if (flash > 0)
			{
				g2.setColor(withAlpha(ORANGE, (int) (150 * flash)));
				g2.fillRect(0, 0, w, h);
			}

			// Reel window: the previous item slides up and out while the current one slides in from below
			float slide = clamp((now - reel.changedAt) / (float) reel.slideMs);
			slide = 1f - (1f - slide) * (1f - slide); // ease out
			// Clip the scrolling items to the row; remember the original clip so it can be restored exactly
			// (setClip(null) would remove all clipping and let later drawing spill outside the row)
			Shape rowClip = g2.getClip();
			g2.clipRect(1, 1, w - 2, h - 2);
			if (reel.previous != null && slide < 1f)
			{
				drawItem(g2, reel.previous, (int) (-slide * h), w, h, reel.landed ? 0.5f : 1f);
			}
			drawItem(g2, reel.current, (int) ((1f - slide) * h), w, h, 1f);
			g2.setClip(rowClip);

			if (reel.landed)
			{
				drawNewPop(g2, now, w, h);
			}
			else
			{
				// Fade the top and bottom edges so it reads as a spinning drum
				g2.setPaint(new GradientPaint(0, 0, ColorScheme.DARK_GRAY_COLOR, 0, h / 3f, withAlpha(ColorScheme.DARK_GRAY_COLOR, 0)));
				g2.fillRect(0, 0, w, h / 3);
				g2.setPaint(new GradientPaint(0, h * 2 / 3f, withAlpha(ColorScheme.DARK_GRAY_COLOR, 0), 0, h, ColorScheme.DARK_GRAY_COLOR));
				g2.fillRect(0, h * 2 / 3, w, h - h * 2 / 3);
			}

			// Border: steady orange while spinning, bright flash when landed
			float border = reel.landed ? flash : 0.8f;
			if (border > 0)
			{
				g2.setColor(withAlpha(ORANGE, (int) (255 * border)));
				g2.setStroke(new BasicStroke(reel.landed ? 2f : 1f));
				g2.drawRect(0, 0, w - 1, h - 1);
			}

			// Settle: cross-fade into a picture of the normal row, which then takes this row's place
			float settle = reel.landed ? clamp((now - reel.landedAt - FLASH_MS) / (float) SETTLE_MS) : 0f;
			if (settle > 0)
			{
				float eased = settle * settle * (3f - 2f * settle); // smoothstep
				g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, eased));
				g2.drawImage(finalImage(w, h), 0, 0, null);
			}
			g2.dispose();
		}

		/** The normal row rendered off-screen at this size (cached). */
		private BufferedImage finalImage(int w, int h)
		{
			if (finalImage == null || finalImage.getWidth() != w || finalImage.getHeight() != h)
			{
				finalImage = render(w, h);
			}
			return finalImage;
		}

		/** The normal row's contents (icon, name, status) without its background, at this size (cached). */
		private BufferedImage contentImage(int w, int h)
		{
			if (contentImage == null || contentImage.getWidth() != w || contentImage.getHeight() != h)
			{
				boolean opaque = finalRow.isOpaque();
				finalRow.setOpaque(false);
				try
				{
					contentImage = render(w, h);
				}
				finally
				{
					finalRow.setOpaque(opaque);
				}
			}
			return contentImage;
		}

		private BufferedImage render(int w, int h)
		{
			finalRow.setSize(w, h);
			layoutTree(finalRow);
			BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = image.createGraphics();
			finalRow.printAll(g);
			g.dispose();
			return image;
		}

		private static void layoutTree(Component component)
		{
			component.doLayout();
			if (component instanceof Container)
			{
				for (Component child : ((Container) component).getComponents())
				{
					layoutTree(child);
				}
			}
		}

		/** Icon and name, the name wrapped onto as many lines as the row fits (long quest names), centred vertically. */
		private void drawItem(Graphics2D g2, Item item, int offsetY, int w, int h, float alpha)
		{
			if (item == null)
			{
				return;
			}
			Graphics2D g = (Graphics2D) g2.create();
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
			if (item == reel.target)
			{
				// The result is the real row itself (same icon spot, name layout and status line), so when the row
				// takes over at the end nothing moves. Its NEW! is left out; the pop draws that.
				g.clipRect(0, offsetY, w - NEW_RESERVE, h);
				g.drawImage(contentImage(w, h), 0, offsetY, null);
				g.dispose();
				return;
			}
			int x = PAD_X;
			Image icon = item.getIcon();
			if (icon != null)
			{
				g.drawImage(icon, x, offsetY + (h - ICON_SIZE) / 2, ICON_SIZE, ICON_SIZE, null);
				x += ICON_SIZE + ICON_GAP;
			}
			Font font = FontManager.getRunescapeFont();
			g.setFont(font);
			FontMetrics fm = g.getFontMetrics();
			// Keep clear of the right edge, where "NEW!" pops in
			List<String> lines = wrap(item.getName(), fm, w - x - NEW_RESERVE, Math.max(1, (h - 4) / fm.getHeight()));
			int top = offsetY + (h - lines.size() * fm.getHeight()) / 2;
			g.setColor(Color.WHITE);
			for (int i = 0; i < lines.size(); i++)
			{
				g.drawString(lines.get(i), x, top + i * fm.getHeight() + fm.getAscent());
			}
			g.dispose();
		}

		/** Greedy word wrap to maxWidth, at most maxLines lines (the last one ends in "..." if cut short). */
		private static List<String> wrap(String text, FontMetrics fm, int maxWidth, int maxLines)
		{
			List<String> lines = new ArrayList<>();
			StringBuilder line = new StringBuilder();
			for (String word : text.split(" "))
			{
				String attempt = line.length() == 0 ? word : line + " " + word;
				if (fm.stringWidth(attempt) <= maxWidth || line.length() == 0)
				{
					line.setLength(0);
					line.append(attempt);
				}
				else
				{
					lines.add(line.toString());
					line.setLength(0);
					line.append(word);
				}
			}
			lines.add(line.toString());
			if (lines.size() > maxLines)
			{
				List<String> cut = new ArrayList<>(lines.subList(0, maxLines));
				cut.set(maxLines - 1, cut.get(maxLines - 1) + "...");
				return cut;
			}
			return lines;
		}

		/** "NEW!" on the right that pops in large and settles to normal size. */
		private void drawNewPop(Graphics2D g2, long now, int w, int h)
		{
			float t = clamp((now - reel.landedAt) / (float) POP_MS);
			float scale = 1f + 0.8f * (1f - t) * (1f - t);
			Font font = FontManager.getRunescapeBoldFont();
			Graphics2D g = (Graphics2D) g2.create();
			g.setFont(font);
			FontMetrics fm = g.getFontMetrics();
			String text = "NEW!";
			int textW = fm.stringWidth(text);
			// Settles where the row's NEW! sits; while it's popped large, shift left so it stays inside the row
			int cx = Math.min(w - PAD_X - textW / 2, w - 2 - Math.round(textW * scale / 2f));
			int cy = h / 2;
			g.translate(cx, cy);
			g.scale(scale, scale);
			g.setColor(new Color(0, 0, 0, 160));
			g.drawString(text, -textW / 2 + 1, fm.getAscent() / 2 + 1);
			g.setColor(ORANGE); // same orange NEW! as the row it fades into
			g.drawString(text, -textW / 2, fm.getAscent() / 2);
			g.dispose();
		}

		private static float clamp(float value)
		{
			return Math.max(0f, Math.min(1f, value));
		}

		private static Color withAlpha(Color color, int alpha)
		{
			return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
		}
	}
}
