package com.bswmagicgaming.fiveactive;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.BackgroundComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;

/**
 * The run at a glance, for when the side panel is closed: every active quest, skill and boss with its progress.
 * Sits in the bottom-right corner of the game view (beside the inventory in resizable mode); Alt-drag to move it.
 *
 * Progress is shown, not spelled out: skills and bosses fill a row of green squares, and quests are coloured like
 * the in-game quest list (red not started, yellow in progress, green done), which users already know.
 */
class FiveActiveOverlay extends Overlay
{
	private static final int WIDTH = 200;
	private static final int PAD = 6;
	private static final int ROW_HEIGHT = 18;
	private static final int ICON = 16;
	private static final int PIP = 6;
	private static final int PIP_GAP = 2;
	private static final int SECTION_GAP = 5;
	/** How long a newly revealed row takes to pop in (matching the panel's reel landing). */
	static final int POP_MS = 450;
	/** The green flash when a slot is finished: a little longer than a reveal, it's a celebration. */
	static final int COMPLETE_POP_MS = 700;

	// Compact style: a row of icon cells per section, each with a progress bar underneath
	private static final int CELL = 25;
	private static final int CELL_GAP = 3;
	private static final int BAR_HEIGHT = 4;
	private static final int CELL_BLOCK = CELL + 2 + BAR_HEIGHT;
	private static final int COMPACT_WIDTH = 2 * PAD + Rules.SLOTS * CELL + (Rules.SLOTS - 1) * CELL_GAP;
	/** A step lighter than the overlay background, so dark sprites (spiders, demons) stand out from it. */
	private static final Color CELL_BACK = new Color(125, 110, 90, 110);
	/** Green tick on a finished cell. 'o' green, 'k' black edge. */
	private static final String[] TICK = {
		"......kk",
		".....kok",
		"k...kok.",
		"kok.kok.",
		".kokok..",
		"..kok...",
		"...k....",
	};

	private static final Color GREEN = ColorScheme.PROGRESS_COMPLETE_COLOR;
	private static final Color ORANGE = ColorScheme.BRAND_ORANGE;
	private static final Color GREY = new Color(170, 170, 170);
	private static final Color TIER_GOLD = new Color(255, 200, 40);
	/** Empty progress: a mid brown-grey, clearly apart from both the black gaps and the green fill. */
	private static final Color PIP_EMPTY = new Color(100, 90, 76);
	/** The quest list's own colours. */
	private static final Color QUEST_NOT_STARTED = new Color(255, 0, 0);
	private static final Color QUEST_IN_PROGRESS = new Color(255, 255, 0);
	private static final Color QUEST_DONE = new Color(0, 255, 0);

	private final FiveActivePlugin plugin;
	private final FiveActiveConfig config;
	private final SkillIconManager skillIconManager;
	private final SpriteManager spriteManager;
	private final BackgroundComponent background = new BackgroundComponent();
	private final BufferedImage logo = NavIcon.title(1);
	private final Map<Skill, BufferedImage> skillIcons = new EnumMap<>(Skill.class);
	private final Map<Integer, BufferedImage> bossIcons = new HashMap<>();
	private final Map<Skill, BufferedImage> bigSkillIcons = new EnumMap<>(Skill.class);
	private final Map<Integer, BufferedImage> bigBossIcons = new HashMap<>();
	private BufferedImage questIcon;
	/** The boss tier waiting to be unlocked this frame, or -1. */
	private int pendingTier = -1;
	/** Compact cells drawn this frame and what hovering each one says. */
	private final Map<Rectangle, String> cellTips = new java.util.LinkedHashMap<>();

	@Inject
	private Client client;
	@Inject
	private TooltipManager tooltipManager;

	@Inject
	FiveActiveOverlay(FiveActivePlugin plugin, FiveActiveConfig config, SkillIconManager skillIconManager, SpriteManager spriteManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.skillIconManager = skillIconManager;
		this.spriteManager = spriteManager;
		setPosition(OverlayPosition.BOTTOM_RIGHT);
		setPriority(PRIORITY_LOW);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		PanelData data = plugin.getOverlayData();
		FiveActiveConfig.OverlayMode mode = config.overlayMode();
		if (data == null || !data.isLoggedIn() || mode == FiveActiveConfig.OverlayMode.OFF
			|| (mode == FiveActiveConfig.OverlayMode.PANEL_CLOSED && plugin.isPanelOpen()))
		{
			return null;
		}

		// Only the sections picked in the settings, in the panel's order
		List<Category> sections = new ArrayList<>();
		if (config.overlayQuests())
		{
			sections.add(Category.QUESTS);
		}
		if (config.overlaySkills())
		{
			sections.add(Category.SKILLS);
		}
		if (config.overlayBosses())
		{
			sections.add(Category.BOSSES);
		}
		if (sections.isEmpty())
		{
			return null;
		}

		if (config.overlayStyle() == FiveActiveConfig.OverlayStyle.COMPACT)
		{
			return renderCompact(g, data, sections);
		}

		int height = PAD + logo.getHeight() + 3 + PAD - 2;
		for (Category category : sections)
		{
			height += sectionHeight(slots(data, category));
		}
		height += SECTION_GAP * (sections.size() - 1);

		background.setRectangle(new Rectangle(0, 0, WIDTH, height));
		background.render(g);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

		int y = PAD;
		g.drawImage(logo, (WIDTH - logo.getWidth()) / 2, y, null);
		y += logo.getHeight() + 3;

		FiveActivePlugin.OverlayReveal reveal = plugin.getOverlayReveal();
		pendingTier = data.getPendingTier();
		for (int i = 0; i < sections.size(); i++)
		{
			Category category = sections.get(i);
			y = drawSection(g, y + (i == 0 ? 0 : SECTION_GAP), category, slots(data, category), canRoll(data, category), reveal);
		}
		return new Dimension(WIDTH, height);
	}

	// ---------------------------------------------------------------- Compact style

	/**
	 * The logo, then one row of five icon cells per section: skill and boss icons, and the quest tab's icon for
	 * quests. Under each cell a bar shows progress: five segments filling green for skills and bosses (one for
	 * one-kill bosses), and for quests one bar in the quest list's colour. Finished cells get a green tick.
	 * Hovering a cell shows its name.
	 */
	private Dimension renderCompact(Graphics2D g, PanelData data, List<Category> sections)
	{
		int height = PAD + logo.getHeight() + 4 + sections.size() * CELL_BLOCK + (sections.size() - 1) * SECTION_GAP + PAD;
		background.setRectangle(new Rectangle(0, 0, COMPACT_WIDTH, height));
		background.render(g);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

		int y = PAD;
		g.drawImage(logo, (COMPACT_WIDTH - logo.getWidth()) / 2, y, null);
		y += logo.getHeight() + 4;

		cellTips.clear();
		FiveActivePlugin.OverlayReveal reveal = plugin.getOverlayReveal();
		long now = System.currentTimeMillis();
		for (Category category : sections)
		{
			List<PanelData.SlotView> slots = slots(data, category);
			for (int i = 0; i < Rules.SLOTS; i++)
			{
				int x = PAD + i * (CELL + CELL_GAP);
				if (i >= slots.size())
				{
					// Empty slot (not rolled, or fewer than five available)
					drawEmptyCell(g, x, y);
					continue;
				}
				long revealed = reveal == null ? -1 : reveal.revealTime(category, i);
				PanelData.SlotView slot = slots.get(i);
				if (revealed == 0)
				{
					drawMysteryCell(g, x, y);
					cellTips.put(new Rectangle(x, y, CELL, CELL_BLOCK), "Rolling...");
					continue;
				}
				float done = plugin.completionPop(category, slot.getName());
				if (revealed > 0 && now - revealed < POP_MS)
				{
					drawPoppingCell(g, x, y, category, slot, (now - revealed) / (float) POP_MS, ORANGE);
				}
				else if (done >= 0f)
				{
					drawPoppingCell(g, x, y, category, slot, done, GREEN);
				}
				else
				{
					drawCell(g, x, y, category, slot);
				}
				cellTips.put(new Rectangle(x, y, CELL, CELL_BLOCK), tip(category, slot));
			}
			y += CELL_BLOCK + SECTION_GAP;
		}
		addHoverTooltip();
		return new Dimension(COMPACT_WIDTH, height);
	}

	private void drawCell(Graphics2D g, int x, int y, Category category, PanelData.SlotView slot)
	{
		g.setColor(CELL_BACK);
		g.fillRect(x, y, CELL, CELL);
		BufferedImage icon = category == Category.QUESTS ? questIcon()
			: category == Category.SKILLS ? bigSkillIcon(slot.getSkill())
			: bigBossIcon(slot.getIconSpriteId());
		if (icon != null)
		{
			g.drawImage(icon, x + (CELL - icon.getWidth()) / 2, y + (CELL - icon.getHeight()) / 2, null);
		}

		int barY = y + CELL + 2;
		// Black edge around the whole bar, then the segments inside it
		g.setColor(Color.BLACK);
		g.fillRect(x, barY, CELL, BAR_HEIGHT);
		if (category == Category.QUESTS)
		{
			g.setColor(questColor(slot));
			g.fillRect(x + 1, barY + 1, CELL - 2, BAR_HEIGHT - 2);
		}
		else
		{
			int goal = Math.max(1, slot.getGoal());
			int inner = CELL - 2;
			for (int i = 0; i < goal; i++)
			{
				// Segments share the width exactly, with a 1px black gap between them
				int sx = x + 1 + i * (inner + 1) / goal;
				int ex = x + 1 + (i + 1) * (inner + 1) / goal - 1;
				g.setColor(i < slot.getProgress() || slot.isDone() ? GREEN : PIP_EMPTY);
				g.fillRect(sx, barY + 1, ex - sx, BAR_HEIGHT - 2);
			}
		}

		if (slot.isDone())
		{
			drawPattern(g, TICK, x + CELL - TICK[0].length(), y, GREEN);
		}
	}

	private void drawEmptyCell(Graphics2D g, int x, int y)
	{
		g.setColor(new Color(0, 0, 0, 40));
		g.fillRect(x, y, CELL, CELL);
		g.setColor(new Color(0, 0, 0, 90));
		g.drawRect(x, y, CELL - 1, CELL - 1);
	}

	private void drawMysteryCell(Graphics2D g, int x, int y)
	{
		g.setColor(CELL_BACK);
		g.fillRect(x, y, CELL, CELL);
		Font font = FontManager.getRunescapeBoldFont();
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		int tx = x + (CELL - fm.stringWidth("?")) / 2;
		int ty = y + (CELL - fm.getHeight()) / 2 + fm.getAscent();
		g.setColor(Color.BLACK);
		g.drawString("?", tx + 1, ty + 1);
		g.setColor(GREY);
		g.drawString("?", tx, ty);
		g.setColor(Color.BLACK);
		g.fillRect(x, y + CELL + 2, CELL, BAR_HEIGHT);
	}

	/** A cell that just landed: an orange box springs open past full size and fades, revealing the cell. */
	private void drawPoppingCell(Graphics2D g, int x, int y, Category category, PanelData.SlotView slot, float t, Color color)
	{
		float u = t - 1f;
		float spring = 1f + 2.70158f * u * u * u + 1.70158f * u * u; // easeOutBack
		float scale = 0.5f + 0.5f * spring;
		int w = Math.round((CELL + 4) * scale);
		int h = Math.round((CELL_BLOCK + 4) * scale);
		int bx = x + CELL / 2 - w / 2;
		int by = y + CELL_BLOCK / 2 - h / 2;
		Graphics2D box = (Graphics2D) g.create();
		box.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(190 * (1f - t))));
		box.fillRect(bx, by, w, h);
		box.clipRect(bx, by, w, h);
		drawCell(box, x, y, category, slot);
		box.dispose();
	}

	private static String tip(Category category, PanelData.SlotView slot)
	{
		// Tooltips take the game's <col=rrggbb> colour tags
		if (category == Category.QUESTS)
		{
			String state = slot.isDone() || slot.getQuestState() == QuestState.FINISHED ? "Complete"
				: slot.getQuestState() == QuestState.IN_PROGRESS ? "In progress"
				: "Not started";
			return slot.getName() + "</br>" + ColorUtil.wrapWithColorTag(state, questColor(slot));
		}
		return slot.getName() + "</br>" + (slot.isDone()
			? ColorUtil.wrapWithColorTag("Done!", GREEN)
			: slot.getProgress() + "/" + slot.getGoal());
	}

	/** Shows the name of the compact cell under the mouse. */
	private void addHoverTooltip()
	{
		if (client == null || tooltipManager == null)
		{
			return;
		}
		Point mouse = client.getMouseCanvasPosition();
		Rectangle bounds = getBounds();
		if (mouse == null || bounds == null)
		{
			return;
		}
		int mx = mouse.getX() - bounds.x;
		int my = mouse.getY() - bounds.y;
		for (Map.Entry<Rectangle, String> cell : cellTips.entrySet())
		{
			if (cell.getKey().contains(mx, my))
			{
				tooltipManager.add(new Tooltip(cell.getValue()));
				return;
			}
		}
	}

	private static Color questColor(PanelData.SlotView slot)
	{
		return slot.isDone() || slot.getQuestState() == QuestState.FINISHED ? QUEST_DONE
			: slot.getQuestState() == QuestState.IN_PROGRESS ? QUEST_IN_PROGRESS
			: QUEST_NOT_STARTED;
	}

	private static void drawPattern(Graphics2D g, String[] pattern, int x, int y, Color color)
	{
		for (int row = 0; row < pattern.length; row++)
		{
			for (int col = 0; col < pattern[row].length(); col++)
			{
				char ch = pattern[row].charAt(col);
				if (ch == 'o' || ch == 'k')
				{
					g.setColor(ch == 'o' ? color : Color.BLACK);
					g.fillRect(x + col, y + row, 1, 1);
				}
			}
		}
	}

	private BufferedImage bigSkillIcon(Skill skill)
	{
		return skill == null ? null : bigSkillIcons.computeIfAbsent(skill, s -> fit(skillIconManager.getSkillImage(s, false)));
	}

	private BufferedImage bigBossIcon(int spriteId)
	{
		if (spriteId < 0)
		{
			return null;
		}
		BufferedImage cached = bigBossIcons.get(spriteId);
		if (cached == null)
		{
			BufferedImage sprite = spriteManager.getSprite(spriteId, 0);
			if (sprite == null)
			{
				return null;
			}
			cached = fit(sprite);
			bigBossIcons.put(spriteId, cached);
		}
		return cached;
	}

	private BufferedImage questIcon()
	{
		if (questIcon == null)
		{
			BufferedImage sprite = spriteManager.getSprite(SpriteID.SideIcons.QUEST, 0);
			questIcon = sprite == null ? null : fit(sprite);
		}
		return questIcon;
	}

	/** Native size (crisp pixel art), only shrunk if it's bigger than a cell. */
	private static BufferedImage fit(BufferedImage image)
	{
		if (image.getWidth() > CELL - 2 || image.getHeight() > CELL - 2)
		{
			return ImageUtil.resizeImage(image, CELL - 2, CELL - 2, true);
		}
		return image;
	}

	private static List<PanelData.SlotView> slots(PanelData data, Category category)
	{
		return category == Category.QUESTS ? data.getQuests() : category == Category.SKILLS ? data.getSkills() : data.getBosses();
	}

	private static int canRoll(PanelData data, Category category)
	{
		return category == Category.QUESTS ? data.getQuestsCanRoll()
			: category == Category.SKILLS ? data.getSkillsCanRoll()
			: data.getBossesCanRoll();
	}

	/** A header plus one row per slot (a single "none" row when nothing's rolled). */
	private static int sectionHeight(List<PanelData.SlotView> slots)
	{
		return ROW_HEIGHT + ROW_HEIGHT * Math.max(1, slots.size());
	}

	private int drawSection(Graphics2D g, int y, Category category, List<PanelData.SlotView> slots, int canRoll,
		FiveActivePlugin.OverlayReveal reveal)
	{
		// Header: title on the left, "N ready" on the right when there's something to roll
		Font bold = FontManager.getRunescapeBoldFont();
		text(g, category.getTitle(), PAD, y, bold, ORANGE);
		if (reveal != null && reveal.isSpinning(category))
		{
			rightText(g, "Rolling...", y, FontManager.getRunescapeSmallFont(), GREY);
		}
		else if (category == Category.BOSSES && pendingTier >= 0)
		{
			rightText(g, "Tier " + (pendingTier + 1) + " ready!", y, FontManager.getRunescapeSmallFont(), TIER_GOLD);
		}
		else if (canRoll > 0)
		{
			rightText(g, canRoll + " ready to roll", y, FontManager.getRunescapeSmallFont(), GREEN);
		}
		y += ROW_HEIGHT;

		if (slots.isEmpty())
		{
			text(g, "None rolled yet", PAD + 2, y, FontManager.getRunescapeFont(), GREY);
			return y + ROW_HEIGHT;
		}
		long now = System.currentTimeMillis();
		for (int i = 0; i < slots.size(); i++)
		{
			long revealed = reveal == null ? -1 : reveal.revealTime(category, i);
			if (revealed == 0)
			{
				// Its reel is still spinning in the panel: keep the result hidden until it lands
				drawMystery(g, y, category);
			}
			else if (revealed > 0 && now - revealed < POP_MS)
			{
				drawPopping(g, y, category, slots.get(i), (now - revealed) / (float) POP_MS, ORANGE);
			}
			else if (plugin.completionPop(category, slots.get(i).getName()) >= 0f)
			{
				drawPopping(g, y, category, slots.get(i), plugin.completionPop(category, slots.get(i).getName()), GREEN);
			}
			else
			{
				drawRow(g, y, category, slots.get(i));
			}
			y += ROW_HEIGHT;
		}
		return y;
	}

	private void drawRow(Graphics2D g, int y, Category category, PanelData.SlotView slot)
	{
		if (category == Category.QUESTS)
		{
			drawQuest(g, y, slot);
		}
		else
		{
			drawProgressRow(g, y, slot, category == Category.SKILLS ? skillIcon(slot.getSkill()) : bossIcon(slot.getIconSpriteId()));
		}
	}

	/** A slot still being rolled: "???" where its name will be. */
	private void drawMystery(Graphics2D g, int y, Category category)
	{
		text(g, "???", category == Category.QUESTS ? PAD + 2 : PAD + ICON + 4, y, FontManager.getRunescapeFont(), GREY);
	}

	/**
	 * A row that just landed, popping in like the panel's cards: an orange box springs open past full size and
	 * settles, fading as it goes, with the row revealed inside it at its real size (so the text stays sharp).
	 */
	private void drawPopping(Graphics2D g, int y, Category category, PanelData.SlotView slot, float t, Color color)
	{
		float u = t - 1f;
		float spring = 1f + 2.70158f * u * u * u + 1.70158f * u * u; // easeOutBack
		float scale = 0.6f + 0.4f * spring;
		int fullW = WIDTH - 2 * PAD + 4;
		int w = Math.round(fullW * scale);
		int h = Math.round(ROW_HEIGHT * scale);
		int x = (WIDTH - w) / 2;
		int top = y + (ROW_HEIGHT - h) / 2;

		Graphics2D box = (Graphics2D) g.create();
		box.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(170 * (1f - t))));
		box.fillRect(x, top, w, h);
		box.clipRect(x, top, w, h);
		drawRow(box, y, category, slot);
		box.dispose();
	}

	/** [icon] name ........ progress squares. Finished rows go green. */
	private void drawProgressRow(Graphics2D g, int y, PanelData.SlotView slot, BufferedImage icon)
	{
		int x = PAD;
		if (icon != null)
		{
			g.drawImage(icon, x + (ICON - icon.getWidth()) / 2, y + (ROW_HEIGHT - icon.getHeight()) / 2, null);
		}
		x += ICON + 4;

		int pipsWidth = slot.getGoal() * PIP + (slot.getGoal() - 1) * PIP_GAP;
		int pipsX = WIDTH - PAD - pipsWidth;
		Font font = FontManager.getRunescapeFont();
		text(g, fit(g, slot.getName(), font, pipsX - 4 - x), x, y, font, slot.isDone() ? GREEN : Color.WHITE);

		int pipY = y + (ROW_HEIGHT - PIP) / 2;
		for (int i = 0; i < slot.getGoal(); i++)
		{
			int px = pipsX + i * (PIP + PIP_GAP);
			g.setColor(Color.BLACK);
			g.fillRect(px - 1, pipY - 1, PIP + 2, PIP + 2);
			g.setColor(i < slot.getProgress() || slot.isDone() ? GREEN : PIP_EMPTY);
			g.fillRect(px, pipY, PIP, PIP);
		}
	}

	/** The quest's name in its quest list colour. */
	private void drawQuest(Graphics2D g, int y, PanelData.SlotView slot)
	{
		Color color = questColor(slot);
		Font font = FontManager.getRunescapeFont();
		text(g, fit(g, slot.getName(), font, WIDTH - 2 * PAD - 2), PAD + 2, y, font, color);
	}

	/** Text with the game's drop shadow, vertically centred in a row starting at y. */
	private static void text(Graphics2D g, String text, int x, int y, Font font, Color color)
	{
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		int baseline = y + (ROW_HEIGHT - fm.getHeight()) / 2 + fm.getAscent();
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, baseline + 1);
		g.setColor(color);
		g.drawString(text, x, baseline);
	}

	private static void rightText(Graphics2D g, String text, int y, Font font, Color color)
	{
		g.setFont(font);
		text(g, text, WIDTH - PAD - g.getFontMetrics().stringWidth(text), y, font, color);
	}

	/** The text, cut short with "..." if it's wider than maxWidth. */
	private static String fit(Graphics2D g, String text, Font font, int maxWidth)
	{
		FontMetrics fm = g.getFontMetrics(font);
		if (fm.stringWidth(text) <= maxWidth)
		{
			return text;
		}
		String cut = text;
		while (cut.length() > 1 && fm.stringWidth(cut + "...") > maxWidth)
		{
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut.trim() + "...";
	}

	private BufferedImage skillIcon(Skill skill)
	{
		return skill == null ? null : skillIcons.computeIfAbsent(skill, s -> skillIconManager.getSkillImage(s, true));
	}

	/** The boss's hiscores icon shrunk to the row, or null until the sprite is available. */
	private BufferedImage bossIcon(int spriteId)
	{
		if (spriteId < 0)
		{
			return null;
		}
		BufferedImage cached = bossIcons.get(spriteId);
		if (cached != null)
		{
			return cached;
		}
		// Overlays render on the client thread, where sprites can be read directly
		BufferedImage sprite = spriteManager.getSprite(spriteId, 0);
		if (sprite == null)
		{
			return null;
		}
		BufferedImage icon = ImageUtil.resizeImage(sprite, ICON, ICON, true);
		bossIcons.put(spriteId, icon);
		return icon;
	}
}
