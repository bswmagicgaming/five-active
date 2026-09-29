package com.bswmagicgaming.fiveactive;

import com.google.common.collect.ImmutableMap;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.ui.ColorScheme;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Colours the skills tab: active skills outlined orange, finished ones (waiting for a roll) green, permanently
 * unlocked ones (Hitpoints and 99s) gold with a trim line on 99s, and everything else darkened.
 */
class SkillsTabOverlay extends Overlay
{
	private static final Color INACTIVE_FILTER = new Color(0, 0, 0, 180);
	/** Same orange as "active" in the side panel. */
	private static final Color ACTIVE_OUTLINE = new Color(220, 138, 0);
	/** Finished and waiting for a replacement to be rolled: the side panel's green for done. */
	private static final Color DONE_OUTLINE = ColorScheme.PROGRESS_COMPLETE_COLOR;
	/** Permanently unlocked (Hitpoints, and every skill at 99): gold, like a skillcape. */
	private static final Color PERMANENT_OUTLINE = new Color(255, 200, 40);
	/** The inner trim line on a 99, like a trimmed skillcape. */
	private static final Color TRIM = new Color(255, 236, 150);
	/** Black line around the orange, so it stays crisp against the stone texture. */
	private static final Color ACTIVE_OUTLINE_EDGE = Color.BLACK;

	/** The skills tab lays skills out column by column, so map each widget explicitly. */
	private static final Map<Skill, Integer> SKILL_WIDGETS = ImmutableMap.<Skill, Integer>builder()
		.put(Skill.ATTACK, InterfaceID.Stats.ATTACK)
		.put(Skill.STRENGTH, InterfaceID.Stats.STRENGTH)
		.put(Skill.DEFENCE, InterfaceID.Stats.DEFENCE)
		.put(Skill.RANGED, InterfaceID.Stats.RANGED)
		.put(Skill.PRAYER, InterfaceID.Stats.PRAYER)
		.put(Skill.MAGIC, InterfaceID.Stats.MAGIC)
		.put(Skill.RUNECRAFT, InterfaceID.Stats.RUNECRAFT)
		.put(Skill.CONSTRUCTION, InterfaceID.Stats.CONSTRUCTION)
		.put(Skill.HITPOINTS, InterfaceID.Stats.HITPOINTS)
		.put(Skill.AGILITY, InterfaceID.Stats.AGILITY)
		.put(Skill.HERBLORE, InterfaceID.Stats.HERBLORE)
		.put(Skill.THIEVING, InterfaceID.Stats.THIEVING)
		.put(Skill.CRAFTING, InterfaceID.Stats.CRAFTING)
		.put(Skill.FLETCHING, InterfaceID.Stats.FLETCHING)
		.put(Skill.SLAYER, InterfaceID.Stats.SLAYER)
		.put(Skill.HUNTER, InterfaceID.Stats.HUNTER)
		.put(Skill.MINING, InterfaceID.Stats.MINING)
		.put(Skill.SMITHING, InterfaceID.Stats.SMITHING)
		.put(Skill.FISHING, InterfaceID.Stats.FISHING)
		.put(Skill.COOKING, InterfaceID.Stats.COOKING)
		.put(Skill.FIREMAKING, InterfaceID.Stats.FIREMAKING)
		.put(Skill.WOODCUTTING, InterfaceID.Stats.WOODCUTTING)
		.put(Skill.FARMING, InterfaceID.Stats.FARMING)
		.put(Skill.SAILING, InterfaceID.Stats.SAILING)
		.build();

	private final Client client;
	private final FiveActivePlugin plugin;

	@Inject
	SkillsTabOverlay(Client client, FiveActivePlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		// Draw inside the skills tab, right after its last skill box, instead of on top of every widget.
		// Everything the tab draws later, like the XP hover tooltip, then appears above the dimming.
		setLayer(OverlayLayer.MANUAL);
		drawAfterLayer(InterfaceID.Stats.SAILING);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (plugin.getState() == null || plugin.getState().getSkills().isEmpty() || !plugin.getConfig().dimSkillsTab())
		{
			return null;
		}

		Widget container = client.getWidget(InterfaceID.Stats.UNIVERSE);
		if (container == null || container.isHidden())
		{
			return null;
		}

		// The Total level bar is drawn after this overlay and overlaps the bottom row of skill boxes by a few
		// pixels, so stop each box at the top of the bar to keep the whole outline visible
		Widget total = client.getWidget(InterfaceID.Stats.TOTAL);
		int visibleBottom = total != null && !total.isHidden() ? total.getBounds().y : Integer.MAX_VALUE;

		graphics.setStroke(new BasicStroke(1));
		for (Map.Entry<Skill, Integer> entry : SKILL_WIDGETS.entrySet())
		{
			Widget widget = client.getWidget(entry.getValue());
			if (widget == null || widget.isHidden())
			{
				continue;
			}

			Rectangle bounds = new Rectangle(widget.getBounds());
			if (bounds.y < visibleBottom && bounds.y + bounds.height > visibleBottom)
			{
				bounds.height = visibleBottom - bounds.y;
			}
			Skill skill = entry.getKey();
			float pop = plugin.skillRevealPop(skill);
			if (pop >= 0f)
			{
				drawPop(graphics, bounds, pop, ACTIVE_OUTLINE);
			}
			float done = plugin.completionPop(Category.SKILLS, skill.getName());
			if (done >= 0f)
			{
				drawPop(graphics, bounds, done, DONE_OUTLINE);
			}
			boolean maxed = plugin.isSkillMaxed(skill);
			if (skill == Skill.HITPOINTS || maxed)
			{
				outline(graphics, bounds, PERMANENT_OUTLINE);
				if (maxed)
				{
					// A second, lighter line just inside the gold, like the trim on a trimmed skillcape
					graphics.setColor(TRIM);
					graphics.drawRect(bounds.x + 3, bounds.y + 3, bounds.width - 7, bounds.height - 7);
				}
			}
			else if (plugin.isSkillShownActive(skill))
			{
				outline(graphics, bounds, ACTIVE_OUTLINE);
			}
			else if (plugin.isSkillCompleted(skill))
			{
				outline(graphics, bounds, DONE_OUTLINE);
			}
			else
			{
				graphics.setColor(INACTIVE_FILTER);
				graphics.fill(bounds);
			}
		}
		return null;
	}

	/** Black on the edge, the colour just inside it; both stay within the skill's box. */
	private static void outline(Graphics2D graphics, Rectangle bounds, Color color)
	{
		graphics.setColor(ACTIVE_OUTLINE_EDGE);
		graphics.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
		graphics.setColor(color);
		graphics.drawRect(bounds.x + 1, bounds.y + 1, bounds.width - 3, bounds.height - 3);
	}

	/**
	 * The pop, same as the overlay, panel and boss pool: a box springs open past the skill's box and settles
	 * back, fading out as it goes. Orange when the skill's reel lands, green when it's finished.
	 */
	private static void drawPop(Graphics2D graphics, Rectangle bounds, float t, Color color)
	{
		float u = t - 1f;
		float spring = 1f + 2.70158f * u * u * u + 1.70158f * u * u; // easeOutBack
		float scale = 0.5f + 0.5f * spring;
		int w = Math.round(bounds.width * scale);
		int h = Math.round(bounds.height * scale);
		int x = (int) Math.round(bounds.getCenterX() - w / 2.0);
		int y = (int) Math.round(bounds.getCenterY() - h / 2.0);
		graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(170 * (1f - t))));
		graphics.fillRect(x, y, w, h);
	}
}
