package com.bswmagicgaming.fiveactive;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.GridLayout;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.util.AsyncBufferedImage;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.border.EmptyBorder;
import net.runelite.api.IconID;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.SoundEffectID;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

/**
 * Side panel. Rebuilt from a {@link PanelData} snapshot whenever anything changes.
 * Designed to be readable on stream: big numbers, one colour per meaning, no hidden info.
 */
class FiveActivePanel extends PluginPanel
{
	// One colour per meaning, used consistently everywhere
	private static final Color GREEN = ColorScheme.PROGRESS_COMPLETE_COLOR;
	private static final Color ORANGE = ColorScheme.BRAND_ORANGE;
	private static final Color YELLOW = new Color(240, 200, 60);
	private static final Color ROLLABLE = ColorScheme.LIGHT_GRAY_COLOR;
	private static final Color LOCKED = new Color(95, 95, 95);
	private static final Color WHITE = Color.WHITE;
	/** Active bosses in the pool. Distinct from green (done) and orange (actions / NEW!). */
	private static final Color ACTIVE = ORANGE;
	/** Raised background behind an active boss tile. */
	private static final Color RAISED = new Color(80, 80, 80);
	/** Boss pool tile background: a step lighter than the tier card, so dark boss sprites don't sink into it. */
	private static final Color TILE = new Color(52, 52, 52);
	private static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color ROW = ColorScheme.DARK_GRAY_COLOR;
	private static final Color ROW_HOVER = new Color(60, 60, 60);

	/** Inner width of a rules card in screen pixels (the panel's width less the panel and card padding). */
	private static final int RULES_CARD_INNER_WIDTH = 177;
	/** A rules list's indent, and the gap between each marker and its text. */
	private static final int BULLET_INDENT = 2;
	private static final int BULLET_GAP = 5;
	/** Swing draws HTML widths about this much larger than their number (see BOSS_NAME_WIDTH). */
	private static final float HTML_PX_SCALE = 1.34f;
	/** Wrapping width of the rules view's paragraphs (HTML units: about 177 screen px, a card's inner width). */
	private static final int RULES_TEXT_WIDTH = 132;
	/** Text width for wrapping labels inside a row (panel width minus card and row padding). */
	private static final int TEXT_WIDTH = 135;
	/** Pixel size of the header title's letters (each font pixel becomes a TITLE_SCALE x TITLE_SCALE block). */
	private static final int TITLE_SCALE = 2;
	/** How long NEW! takes to scroll away (revealing the progress squares) once it clears. */
	private static final int BADGE_SWAP_MS = 450;
	/** Welcome intro on login: the logo pops in on its own first, taking this long... */
	private static final int INTRO_LOGO_MS = 500;
	/** ...then the sections start this long after the logo... */
	private static final int INTRO_LOGO_LEAD_MS = 600;
	/** ...each one this long after the one above it... */
	private static final int INTRO_STAGGER_MS = 110;
	/** ...and takes this long to pop in. */
	private static final int INTRO_POP_MS = 420;
	/** Logout outro: each section starts packing away this long after the one below it... */
	private static final int OUTRO_STAGGER_MS = 80;
	/** ...and takes this long to shrink into the logo... */
	private static final int OUTRO_POP_MS = 320;
	/** ...then the logo sits alone this long before the login card pops in behind it. */
	private static final int OUTRO_HOLD_MS = 200;
	/** Tier unlock: gold, like the permanent unlocks in the skills tab. */
	private static final Color GOLD = new Color(255, 200, 40);
	/** Tier unlock ceremony timing: the button shakes and rumbles... */
	private static final int CHARGE_MS = 900;
	/** How long a section (or boss pool tier) takes to slide open or shut during the ceremony. */
	private static final int FOLD_MS = 350;
	/** ...then the tier's icons tremble, harder and harder... */
	/** Gap between the finale's booms on the bigger tiers. */
	private static final int BOOM_GAP_MS = 450;
	/** Extra charge time when other panels had to slide shut first, so the button keeps rumbling while they go. */
	private static final int EXTRA_CHARGE_MS = 250;
	/** ...then pop into the pool one by one, the gaps shrinking from the first to the last... */
	private static final int FIRST_POP_GAP_MS = 280;
	private static final int LAST_POP_GAP_MS = 110;
	/** ...then, a beat after the last one, the finale... */
	private static final int FINALE_DELAY_MS = 400;
	/** ...and "Tier N unlocked!" shows where the button was for this long before the Roll button returns. */
	private static final int BANNER_MS = 2800;
	/** How long the tier header's gold flash lasts at the finale. */
	private static final int HEADER_FLASH_MS = 900;
	/** How long a button takes to slide into or out of its space. */
	private static final int SLIDE_MS = 250;
	/** Vertical gap between the components of a card. */
	private static final int CARD_GAP = 3;
	/** Gap between a skill/boss icon and its name, and between a row's name and its progress. */
	private static final int ICON_GAP = 6;
	private static final int ROW_GAP = 2;
	/**
	 * Wrapping width of boss names (in HTML units, which Swing draws about 4/3 larger: this is ~82 screen px).
	 * That's what's left of a row beside the icon and the progress squares, and just wide enough for the longest
	 * single word ("Thermonuclear"), so every boss name fits on two lines.
	 */
	private static final int BOSS_NAME_WIDTH = 61;
	/** Width of the green bar marking a finished row. */
	private static final int DONE_BAR_WIDTH = 3;
	/** Every skill and boss icon in the plugin is fitted to this square, so they all line up. */
	static final int ICON_SIZE = 25;
	private static final int MAXED_ICONS_PER_ROW = 6;
	/**
	 * Boss pool icon grid: tiles per row, icon size, tile size, gap between tiles. Icons stay at their native
	 * size (so the pixel art stays crisp); five roomy 33px tiles with 3px gaps fill the ~181px inside a tier card.
	 */
	private static final int POOL_ICONS_PER_ROW = 5;
	private static final int POOL_ICON_SIZE = ICON_SIZE;
	private static final int POOL_TILE_SIZE = 33;
	private static final int POOL_TILE_GAP = 3;
	/** Corner rounding of the boss pool tiles. */
	private static final int TILE_ARC = 4;

	private final FiveActivePlugin plugin;
	private final SkillIconManager skillIconManager;
	private final SpriteManager spriteManager;
	/** Boss sprites by sprite id (null while loading), loaded once from the game cache. Swing thread only. */
	private final Map<Integer, BufferedImage> bossSprites = new HashMap<>();
	/** Sized / greyed-out versions of the boss sprites. Swing thread only. */
	private final Map<String, ImageIcon> scaledBossIcons = new HashMap<>();
	private final Map<Skill, ImageIcon> skillIcons = new EnumMap<>(Skill.class);
	private boolean iconRefreshQueued;

	/** Sections the user can collapse by clicking their header. */
	private enum Section
	{
		SKILLS, BOSSES, QUESTS, POOL
	}

	private final Set<Section> collapsed = EnumSet.noneOf(Section.class);
	/** Boss pool tiers (by KC milestone) collapsed to just their header. */
	private final Set<Integer> collapsedTiers = new HashSet<>();
	/** Whether the header's "Use a Shuffle" chooser is open. */
	private boolean shuffleMenuOpen;
	/** The roll reveal currently playing, or null. */
	private RollAnimation animation;
	/** What to run when the current reveal ends (announces the result in chat). */
	private Runnable onAnimationComplete;
	/** The boss pool as it was before a boss roll; bosses still being revealed keep their old look from here. */
	private List<PanelData.TierView> frozenTiers;
	/** Names of newly rolled bosses whose reel hasn't landed yet. Swing thread only. */
	private final Set<String> unrevealedBosses = new HashSet<>();
	/** When each boss's reel landed, so its boss pool tile can pop. Swing thread only. */
	private final Map<String, Long> bossPops = new HashMap<>();
	/** Repaints rows while their completion flash plays; stops once it's over. */
	private final javax.swing.Timer completionTimer = new javax.swing.Timer(16, e ->
	{
		repaint();
		if (!anyCompletionFlashing())
		{
			((javax.swing.Timer) e.getSource()).stop();
			repaint();
		}
	});

	private boolean anyCompletionFlashing()
	{
		for (Category category : Category.values())
		{
			List<PanelData.SlotView> slots = category == Category.SKILLS ? data.getSkills()
				: category == Category.BOSSES ? data.getBosses() : data.getQuests();
			if (slots != null && slots.stream().anyMatch(s -> plugin.completionPop(category, s.getName()) >= 0f))
			{
				return true;
			}
		}
		return false;
	}

	/** The tier unlock ceremony playing now, or null. Swing thread only. */
	private TierCeremony ceremony;
	/** Drives the ceremony (and the Unlock button's pulse while a tier is waiting). */
	private final javax.swing.Timer ceremonyTimer = new javax.swing.Timer(16, e -> stepCeremony());
	/**
	 * Slide state of each section and boss pool tier card, by key ("S:QUESTS", "T:100"). Opening and closing only
	 * slides when the ceremony asks for it (the key is in foldsToAnimate); a user's own click stays instant.
	 */
	private final Map<String, Fold> folds = new HashMap<>();
	private final Set<String> foldsToAnimate = new HashSet<>();
	/**
	 * The fold wrappers on screen. Each is revalidated every frame while sliding: Swing caches a container's
	 * size, so a slide nested inside another card (a boss pool tier) wouldn't grow if only the panel was.
	 */
	private final List<FoldBox> foldBoxes = new ArrayList<>();
	/** Relayouts sliding cards each frame; stops once none are moving. */
	private final javax.swing.Timer foldTimer = new javax.swing.Timer(16, e ->
	{
		relayoutFolds();
		repaint();
		long now = System.currentTimeMillis();
		if (folds.values().stream().noneMatch(f -> f.moving(now)))
		{
			((javax.swing.Timer) e.getSource()).stop();
			// One more layout so each card settles at its final size (and a shut card swaps in)
			relayoutFolds();
		}
	});

	/** The tier card being unlocked, so the ceremony can scroll it into view. */
	private JComponent ceremonyCard;
	private final java.util.Random tremble = new java.util.Random();

	/** Repaints popping boss pool tiles; stops once none are left. */
	private final javax.swing.Timer bossPopTimer = new javax.swing.Timer(16, e -> stepBossPops());
	private PanelData data = PanelData.LOGGED_OUT;
	/** When each row's NEW! started fading out, keyed by badgeKey(). Swing thread only. */
	private final Map<String, Long> badgeFades = new HashMap<>();
	/** Repaints fading badges; stops once none are left. */
	private final javax.swing.Timer badgeFadeTimer = new javax.swing.Timer(16, e -> stepBadgeFades());
	/** Crossfade components currently on screen. */
	private final List<JComponent> badgeFadeRows = new ArrayList<>();
	/**
	 * Height animation of each button area (Roll buttons, the Shuffle button/chooser), by slot key. Kept across
	 * rebuilds so a slide carries on smoothly when the panel is rebuilt mid-way. Swing thread only.
	 */
	private final Map<String, SlideState> slides = new HashMap<>();
	/** Button areas currently on screen. */
	private final List<Slide> slideRows = new ArrayList<>();
	/** Relayouts sliding button areas each frame; stops once none are moving. */
	private final javax.swing.Timer slideTimer = new javax.swing.Timer(16, e -> stepSlides());
	/** The login intro / logout outro currently playing. Swing thread only. */
	private Show show = Show.NONE;
	private long showStart;
	/** Whether the intro pops the logo in first (on login), or the logo is already there (after the outro). */
	private boolean introWithLogo;
	/** How long after the intro starts the first section pops in. */
	private long introLead;
	/** No sounds (switching between the run and the rules view). */
	private boolean quietShow;

	/** Whether the rules view is showing instead of the run. */
	private boolean showingRules;
	/** Whether the rules view is showing the run's History instead (only while logged in). */
	private boolean showingHistory;
	/** History groups that are open, by key (see historyKey). The newest opens by itself the first time. */
	private final Set<String> openHistoryGroups = new HashSet<>();
	private boolean historyOpenedNewest;
	/** The History button popping in (logging in) or out (logging out) in the rules view; 0 when it isn't. */
	private long historyPopStart;
	private boolean historyPopIn;
	private final javax.swing.Timer historyPopTimer = new javax.swing.Timer(16, e -> stepHistoryPop());
	private static final int HISTORY_POP_MS = 320;
	/** Pop the new view's cards in once it's built (logging out of the History). */
	private boolean popInAfterBuild;
	/** How many lines each open History group shows before "Show more". */
	private final Map<String, Integer> historyShown = new HashMap<>();
	/** Item pictures for the History view (collection log, coins), fitted to the icon size. Null while loading. */
	private final Map<String, ImageIcon> itemIcons = new HashMap<>();
	/** Sounds cued so far: the logo's, then one per section. */
	private int showSoundsPlayed;
	/** The logo in the top card, which the intro and outro draw on their own. */
	private TitleLabel headerTitle;
	/** The logged-out data, applied once the outro has finished. */
	private PanelData pendingLogout;
	/** Repaints the panel while the intro or outro plays. */
	private final javax.swing.Timer showTimer = new javax.swing.Timer(16, e -> stepShow());
	/** Height of a quest name wrapped onto two lines (the most any quest needs). */
	private static int questNameHeight;

	FiveActivePanel(FiveActivePlugin plugin, SkillIconManager skillIconManager, SpriteManager spriteManager)
	{
		this.plugin = plugin;
		this.skillIconManager = skillIconManager;
		this.spriteManager = spriteManager;
		loadCollapsed(plugin.getConfig().collapsedSections());
		for (String kc : plugin.getConfig().collapsedTiers().split(","))
		{
			try
			{
				collapsedTiers.add(Integer.parseInt(kc.trim()));
			}
			catch (NumberFormatException ignored)
			{
				// empty or stale entry
			}
		}
		setLayout(new DynamicGridLayout(0, 1, 0, 14));
		setBorder(new EmptyBorder(8, 8, 8, 8));
		update(PanelData.LOGGED_OUT);
	}

	void update(PanelData data)
	{
		if (!data.isLoggedIn() && (this.data.isLoggedIn() || show == Show.OUTRO))
		{
			// Logging out: the run packs itself away into the logo first (if anyone can see it). The rules view
			// doesn't depend on being logged in, so it just stays put.
			if (show != Show.OUTRO && isShowing() && !showingRules)
			{
				startShow(Show.OUTRO);
			}
			if (show == Show.OUTRO)
			{
				pendingLogout = data;
				return;
			}
		}
		else if (show == Show.OUTRO)
		{
			if (data == this.data)
			{
				return; // an internal refresh; keep the run on screen while it packs away
			}
			// Logged straight back in: stop packing away
			stopShow();
		}
		rebuild(data);
	}

	private void rebuild(PanelData data)
	{
		detectClearedBadges(this.data, data);
		// Only worth playing if the panel was already open; opened later, it just shows the run
		if (!this.data.isLoggedIn() && data.isLoggedIn() && isShowing() && !showingRules)
		{
			startIntro(true, INTRO_LOGO_LEAD_MS, false);
		}
		else if (!data.isLoggedIn() && show == Show.INTRO && introWithLogo)
		{
			stopShow();
		}
		if (showingRules && !showingHistory && isShowing() && this.data.isLoggedIn() != data.isLoggedIn())
		{
			// The History button comes and goes with the account
			historyPopStart = System.currentTimeMillis();
			historyPopIn = data.isLoggedIn();
			historyPopTimer.start();
		}
		this.data = data;
		removeAll();
		badgeFadeRows.clear();
		slideRows.clear();
		foldBoxes.clear();
		if (animation != null)
		{
			animation.clearRows();
		}

		if (!data.isLoggedIn())
		{
			// Nothing to slide from after logging back in
			slides.clear();
		}
		if (!data.isLoggedIn() && showingHistory)
		{
			// The History belongs to an account: logging out goes back to the rules, the way its button does
			showingHistory = false;
			popInAfterBuild = isShowing();
		}
		if (showingRules && showingHistory)
		{
			buildHistory();
		}
		else if (showingRules)
		{
			buildRules();
		}
		else if (!data.isLoggedIn())
		{
			JPanel card = card();
			// Same spot as the logged-in header's logo, so the outro can end on it
			card.add(titleRow());
			card.add(wrapped("Log in to load your Five Active run.", FontManager.getRunescapeFont(), ROLLABLE, TEXT_WIDTH + 30));
			add(card);
		}
		else
		{
			preloadBossIcons();
			add(buildHeader());
			add(fold(sectionFoldKey(Section.QUESTS), !collapsed.contains(Section.QUESTS), buildQuests()));
			add(fold(sectionFoldKey(Section.SKILLS), !collapsed.contains(Section.SKILLS), buildSkills()));
			// Bosses sit right above the boss pool they're rolled from
			add(fold(sectionFoldKey(Section.BOSSES), !collapsed.contains(Section.BOSSES), buildBosses()));
			add(fold(sectionFoldKey(Section.POOL), !collapsed.contains(Section.POOL), buildBossPool()));
		}

		if (popInAfterBuild)
		{
			popInAfterBuild = false;
			scrollRectToVisible(new Rectangle(0, 0, 1, 1));
			startIntro(false, 0, true);
		}
		revalidate();
		repaint();
	}

	// ---------------------------------------------------------------- Login intro / logout outro

	private enum Show
	{
		NONE, INTRO, OUTRO
	}

	/**
	 * Pops the sections in from the top. withLogo pops the logo in first (login); otherwise the logo stays put
	 * and the cards pop in behind it. lead is when the first card starts.
	 */
	private void startIntro(boolean withLogo, long lead, boolean quiet)
	{
		introWithLogo = withLogo;
		introLead = lead;
		quietShow = quiet;
		startShow(Show.INTRO);
	}

	private void startShow(Show which)
	{
		if (which == Show.OUTRO)
		{
			quietShow = false;
		}
		show = which;
		showStart = System.currentTimeMillis();
		showSoundsPlayed = 0;
		showTimer.start();
	}

	private void stopShow()
	{
		show = Show.NONE;
		showTimer.stop();
	}

	/** While the intro or outro plays, any repaint inside the panel goes through paintChildren below. */
	@Override
	protected boolean isPaintingOrigin()
	{
		return show != Show.NONE;
	}

	private void stepShow()
	{
		long elapsed = System.currentTimeMillis() - showStart;
		int sections = getComponentCount();
		// Sound cue 0 is the logo's ding (only when it pops in); after that, a plop as each section starts moving
		while (show != Show.NONE && showSoundsPlayed <= sections)
		{
			int order = showSoundsPlayed - 1;
			long cue = order < 0 ? 0 : sectionStart(order);
			if (elapsed < cue)
			{
				break;
			}
			if (quietShow)
			{
				// no sounds
			}
			else if (order >= 0)
			{
				plugin.playSound(SoundEffectID.GE_INCREMENT_PLOP);
			}
			else if (show == Show.INTRO && introWithLogo)
			{
				plugin.playSound(SoundEffectID.GE_ADD_OFFER_DINGALING);
			}
			showSoundsPlayed++;
		}

		if (progress(sections - 1) >= 1f)
		{
			if (show == Show.OUTRO)
			{
				// Everything is packed into the logo: swap to the login card and pop it in behind the logo
				stopShow();
				PanelData loggedOut = pendingLogout;
				pendingLogout = null;
				rebuild(loggedOut);
				startIntro(false, OUTRO_HOLD_MS, false);
			}
			else
			{
				stopShow();
			}
		}
		repaint();
	}

	/**
	 * When the section with this order starts moving, in ms from the start. Intro order counts from the top
	 * (after the logo has popped in); outro order counts from the bottom.
	 */
	private long sectionStart(int order)
	{
		if (show == Show.OUTRO)
		{
			return (long) order * OUTRO_STAGGER_MS;
		}
		return introLead + (long) order * INTRO_STAGGER_MS;
	}

	/** 0 before the section starts moving, 1 once it's done. */
	private float progress(int order)
	{
		if (show == Show.NONE)
		{
			return 1f;
		}
		long elapsed = System.currentTimeMillis() - showStart - sectionStart(order);
		int duration = show == Show.OUTRO ? OUTRO_POP_MS : INTRO_POP_MS;
		return Math.max(0f, Math.min(1f, elapsed / (float) duration));
	}

	/** easeOutBack: rises past 1 by an amount set by overshoot, then settles on it. */
	private static float spring(float t, float overshoot)
	{
		float u = t - 1f;
		return 1f + (overshoot + 1f) * u * u * u + overshoot * u * u;
	}

	/** easeInBack, the reverse of spring: dips slightly below 0 first (a little wind-up), then accelerates to 1. */
	private static float windUp(float t, float overshoot)
	{
		return t * t * ((overshoot + 1f) * t - overshoot);
	}

	/**
	 * Intro: the logo pops in by itself, then the sections pop in one at a time from top to bottom. Each starts
	 * small and a little high, springs slightly past full size and settles (no fading); the header card springs
	 * in behind the logo, which stays put.
	 * Outro: the reverse. From the bottom up, each section winds up slightly and then shrinks away into the logo,
	 * the header card last, leaving the logo on its own until the login card pops in behind it.
	 * Space is laid out as normal throughout, so nothing jumps. Each section is drawn from a picture of it:
	 * painting live Swing components under a scale makes their double buffering smear.
	 */
	@Override
	protected void paintChildren(Graphics g)
	{
		if (show == Show.NONE)
		{
			super.paintChildren(g);
			return;
		}
		Rectangle logo = logoBounds();
		Component[] sections = getComponents();
		for (int i = 0; i < sections.length; i++)
		{
			int order = show == Show.OUTRO ? sections.length - 1 - i : i;
			float t = progress(order);
			if (show == Show.INTRO ? t <= 0f : t >= 1f)
			{
				continue; // not popped in yet / already packed away
			}
			Component section = sections[i];
			Rectangle b = section.getBounds();
			if (b.width <= 0 || b.height <= 0)
			{
				continue;
			}
			double cx = b.x + b.width / 2.0;
			double cy = b.y + b.height / 2.0;
			float scale;
			if (show == Show.INTRO)
			{
				float spring = spring(t, 1.70158f);
				scale = 0.8f + 0.2f * spring;
				cy += (1f - spring) * -16f;
			}
			else
			{
				float e = windUp(t, 1.70158f);
				scale = 1f - 0.95f * e;
				if (logo != null)
				{
					// Heads for the logo as it shrinks
					cx += (logo.getCenterX() - cx) * e;
					cy += (logo.getCenterY() - cy) * e;
				}
			}
			BufferedImage picture = picture(section, true);
			if (show == Show.INTRO)
			{
				// Only the card's box grows and springs (a flat colour, sharp at any size). Its contents stay at
				// their real size on whole pixels, revealed as the box opens, so text never goes blurry.
				int w = Math.round(b.width * scale);
				int h = Math.round(b.height * scale);
				int x = (int) Math.round(cx - w / 2.0);
				int y = (int) Math.round(cy - h / 2.0);
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setColor(section.getBackground());
				g2.fillRect(x, y, w, h);
				g2.clipRect(x, y, w, h);
				g2.drawImage(picture, (int) Math.round(cx - b.width / 2.0), (int) Math.round(cy - b.height / 2.0), null);
				g2.dispose();
				continue;
			}
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.translate(cx, cy);
			g2.scale(scale, scale);
			g2.drawImage(picture, -b.width / 2, -b.height / 2, null);
			g2.dispose();
		}
		paintLogo(g, logo);
	}

	/** The logo, drawn on its own on top of everything (popping in, on the login intro). */
	private void paintLogo(Graphics g, Rectangle b)
	{
		if (b == null || b.width <= 0 || b.height <= 0)
		{
			return;
		}
		float scale = 1f;
		if (show == Show.INTRO && introWithLogo)
		{
			float t = Math.max(0f, Math.min(1f, (System.currentTimeMillis() - showStart) / (float) INTRO_LOGO_MS));
			if (t <= 0f)
			{
				return;
			}
			scale = 0.2f + 0.8f * spring(t, 3f);
		}
		Image letters = ((ImageIcon) headerTitle.getIcon()).getImage();
		Graphics2D g2 = (Graphics2D) g.create();
		// Nearest neighbour keeps the pixel letters crisp while they grow
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g2.translate(b.getCenterX(), b.getCenterY());
		g2.scale(scale, scale);
		g2.drawImage(letters, -b.width / 2, -b.height / 2, null);
		g2.dispose();
	}

	/**
	 * Where the logo's letters sit, in panel coordinates, or null if there isn't one. Just the letters, not the
	 * whole label: the logo grows around (and sections pack into) the middle of the word.
	 */
	private Rectangle logoBounds()
	{
		if (headerTitle == null || headerTitle.getParent() == null || !SwingUtilities.isDescendingFrom(headerTitle, this))
		{
			return null;
		}
		Rectangle label = SwingUtilities.convertRectangle(headerTitle.getParent(), headerTitle.getBounds(), this);
		java.awt.Insets insets = headerTitle.getInsets();
		int w = headerTitle.getIcon().getIconWidth();
		int h = headerTitle.getIcon().getIconHeight();
		int y = label.y + insets.top + (label.height - insets.top - insets.bottom - h) / 2;
		return new Rectangle(label.x + insets.left, y, w, h);
	}

	/** A picture of the component as laid out. hideLogo leaves the logo out (it's drawn separately). */
	private static BufferedImage picture(Component component, boolean hideLogo)
	{
		BufferedImage image = new BufferedImage(Math.max(1, component.getWidth()), Math.max(1, component.getHeight()), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		TitleLabel.hidden = hideLogo;
		try
		{
			// printAll paints straight into the image, skipping Swing's double buffering
			component.printAll(g);
		}
		finally
		{
			TitleLabel.hidden = false;
			g.dispose();
		}
		return image;
	}

	// ---------------------------------------------------------------- Rules view

	/** The logo with the rules button on the right. Every top card starts with this. */
	private JComponent titleRow()
	{
		// Same pixel letters as the sidebar icon: FIVE in orange, ACTIVE in white
		headerTitle = new TitleLabel();
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.add(headerTitle, BorderLayout.WEST);
		JPanel buttons = new JPanel(new BorderLayout(4, 0));
		buttons.setOpaque(false);
		boolean poppingOut = historyPopStart != 0 && !historyPopIn;
		if (showingRules && (data.isLoggedIn() || poppingOut))
		{
			InfoButton history = new InfoButton(InfoButton.HISTORY_GLYPH, showingHistory ? "Back to the rules" : "Your run's History",
				showingHistory, () ->
				{
					if (data.isLoggedIn())
					{
						toggleHistory();
					}
				});
			history.scale = this::historyButtonScale;
			buttons.add(history, BorderLayout.WEST);
		}
		buttons.add(new InfoButton(showingRules ? InfoButton.CLOSE_GLYPH : InfoButton.QUESTION_GLYPH,
			showingRules ? "Back to your run" : "How Five Active works", false, this::toggleRules), BorderLayout.EAST);
		row.add(buttons, BorderLayout.EAST);
		return row;
	}

	/** Grows the History button in, or shrinks it away and then drops it from the row. */
	private void stepHistoryPop()
	{
		if (historyPopStart == 0 || System.currentTimeMillis() - historyPopStart >= HISTORY_POP_MS)
		{
			historyPopTimer.stop();
			boolean wasOut = historyPopStart != 0 && !historyPopIn;
			historyPopStart = 0;
			if (wasOut)
			{
				rebuild(data);
			}
		}
		repaint();
	}

	/** The History button's size (1 is full size) while it pops in or out. */
	private float historyButtonScale()
	{
		if (historyPopStart == 0)
		{
			return 1f;
		}
		float t = Math.min(1f, (System.currentTimeMillis() - historyPopStart) / (float) HISTORY_POP_MS);
		return historyPopIn ? Math.max(0f, spring(t, 1.7f)) : Math.max(0f, 1f - windUp(t, 1.7f));
	}

	/** Switches between the rules and the History, popping the new view's cards in. */
	private void toggleHistory()
	{
		showingHistory = !showingHistory;
		rebuild(data);
		scrollRectToVisible(new Rectangle(0, 0, 1, 1));
		if (isShowing())
		{
			startIntro(false, 0, true);
		}
	}

	/** Switches between the run and the rules view, popping the new view's cards in. */
	private void toggleRules()
	{
		showingRules = !showingRules;
		showingHistory = false;
		rebuild(data);
		scrollRectToVisible(new Rectangle(0, 0, 1, 1));
		if (isShowing())
		{
			startIntro(false, 0, true);
		}
	}

	/** How Five Active works, in plain words, from the same numbers the plugin uses. */
	private void buildRules()
	{
		JPanel header = card();
		header.add(titleRow());
		header.add(small("How the game mode works", ROLLABLE));
		add(header);

		add(rulesCard("The Basics",
			"Only <w>" + Rules.SLOTS + " skills, " + Rules.SLOTS + " bosses, and " + Rules.SLOTS + " quests</w> are active at a time. ",
			"Train only active skills, fight only active bosses, and do only active quests.",
			"Complete one and it can be replaced: press <o>Roll</o> to refill your completed slots. ",
			"What you get is random, from everything you're able to do right now."));

		List<Bullet> unlocks = new ArrayList<>();
		Rules.SKILL_UNLOCK_QUESTS.forEach((skill, quest) -> unlocks.add(new Bullet(
			// Skill on the first line, its quest under it: every entry is laid out the same, and the pair fills the icon's height
			skillIcon(skill), "<w>" + skill.getName() + "</w>\n" + quest.getName())));
		add(rulesCard("Skills",
			"Gain <w>" + Rules.LEVELS_TO_COMPLETE_SKILL + " levels</w> in an active skill to complete it, ending on a multiple of "
				+ Rules.LEVELS_TO_COMPLETE_SKILL + ": a skill rolled at level 1 starts at <w>1/" + Rules.LEVELS_TO_COMPLETE_SKILL
				+ "</w> and completes at level 5, then 10, 15 and so on.",
			"At least one active skill will always be a <w>combat skill</w> (Attack, Strength, Defence, Ranged or Magic), so that you're always ready for a fight.",
			"Skills that reach <w>level 99</w> stay active for good and are never rolled. Hitpoints is always active.",
			"Some skills can only be rolled once you've done their quest:",
			bulletList(unlocks)));

		List<Bullet> oneKill = new ArrayList<>();
		for (Boss boss : Boss.values())
		{
			if (boss.getKillsToComplete() == 1)
			{
				ImageIcon icon = bossIcon(boss.getHiscore() == null ? -1 : boss.getHiscore().getSpriteId());
				oneKill.add(new Bullet(icon, boss.getDisplayName()));
			}
		}
		List<Bullet> tiers = new ArrayList<>();
		for (int i = 0; i < BossTiers.TIERS.size(); i++)
		{
			int kc = BossTiers.TIERS.get(i).getKillsRequired();
			tiers.add(new Bullet(null, "<w>Tier " + (i + 1) + "</w>: " + (kc == 0 ? "from the start" : String.format("%,d", kc) + " total KC")));
		}
		add(rulesCard("Bosses",
			"Kill an active boss <w>" + Rules.KILLS_TO_COMPLETE_BOSS + " times</w> to complete it. "
				+ "Only one kill is needed for:",
			bulletList(oneKill),
			"Bosses unlock in Tiers as your <w>total boss KC</w> grows:",
			bulletList(tiers),
			"Reaching a Tier's milestone KC makes it <w>ready to unlock</w>.",
			"New boss rolls are then locked until you use the <o>Unlock Tier</o> button in the Bosses section.",
			"The <w>Boss Pool</w> shows all bosses and to which tiers they belong.",
			"Some bosses also require a quest or a Slayer level first.",
			"Total KC is your lifetime kill count by default.",
			"The plugin settings can switch it to kills since the mode started, for starting on an existing account."));

		add(rulesCard("Quests",
			"Complete an active quest and it's done: roll a new one in its place.",
			"Quests are able to be rolled as soon as all of their prerequisite quests have been completed.",
			"Skill requirements are not taken into account.",
			"Miniquests are rolled too."));

		add(rulesCard("Shuffles",
			"Earn <w>1 Shuffle per " + Rules.CLOG_SLOTS_PER_SHUFFLE + " collection log slots</w>. "
				+ "Open your Collection Log to sync the count.",
			"Spend one with <o>Use a Shuffle</o> to re-roll an entire category at once. ",
			"You get all-new picks whenever there are enough; progress on the old ones is lost."));

		add(rulesCard("What the plugin does",
			"Tracks your levels and kills and marks your progress as you go.",
			"Dims inactive skills in the skills tab and inactive quests in the quest list, and emits warnings in chat when you train an inactive skill or kill an inactive boss.",
			"Keeps a <w>History</w> of your whole run: open it with the list button at the top of this page.",
			"Backs up your run every time you log in. <w>Backups</w> in the plugin settings can restore one, or copy a save code to keep somewhere safe."));

		add(rulesCard("Feedback",
			"Found a bug or have an idea?",
			"Want to commission your own Plugin?",
			"Get in touch:",
			emailLink(),
			"RSN: <w>BSW Magic</w>",
			"Game mode concept by <o>Act1</o>.",
			new CreditLine("Made by ", "Magic", MAGIC_BLUE, ".")));
	}

	/**
	 * The feedback email, styled like a link: light grey at rest, white and underlined on hover (the only blue in
	 * the plugin is the credit). Clicking copies it and the link itself briefly reads "Copied!" in white, rather
	 * than relying on a tooltip, which Swing hides on every click and only brings back after a delay.
	 */
	private JLabel emailLink()
	{
		JLabel email = new JLabel();
		email.setFont(FontManager.getRunescapeFont());
		email.setBorder(new EmptyBorder(2, 0, 0, 0));
		email.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		email.setToolTipText("Click to copy");
		boolean[] hovered = {false};
		boolean[] copied = {false};
		Runnable restyle = () ->
		{
			String text = copied[0] ? "Copied!" : html(FEEDBACK_EMAIL);
			email.setText("<html>" + (hovered[0] && !copied[0] ? "<u>" + text + "</u>" : text) + "</html>");
			email.setForeground(copied[0] ? WHITE : hovered[0] ? WHITE : LINK);
		};
		javax.swing.Timer revert = new javax.swing.Timer(1200, e ->
		{
			copied[0] = false;
			restyle.run();
		});
		revert.setRepeats(false);
		restyle.run();
		email.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
					.setContents(new java.awt.datatransfer.StringSelection(FEEDBACK_EMAIL), null);
				copied[0] = true;
				restyle.run();
				revert.restart();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				hovered[0] = true;
				restyle.run();
				showTooltipNow(email, e);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hovered[0] = false;
				restyle.run();
			}
		});
		return email;
	}

	/** Shows a component's tooltip straight away instead of after Swing's usual hover delay. */
	private static void showTooltipNow(JComponent component, MouseEvent e)
	{
		// Runs after the tooltip manager has scheduled the delayed tip for this hover
		SwingUtilities.invokeLater(() ->
		{
			ToolTipManager tooltips = ToolTipManager.sharedInstance();
			int delay = tooltips.getInitialDelay();
			tooltips.setInitialDelay(0);
			tooltips.mouseEntered(new MouseEvent(component, MouseEvent.MOUSE_ENTERED, e.getWhen(), 0, e.getX(), e.getY(), 0, false));
			tooltips.mouseMoved(new MouseEvent(component, MouseEvent.MOUSE_MOVED, e.getWhen(), 0, e.getX(), e.getY(), 0, false));
			tooltips.setInitialDelay(delay);
		});
	}

	private static final Color MAGIC_BLUE = new Color(0x4d8dff);

	private static class CreditLine extends JComponent
	{
		private final String before;
		private final String name;
		private final Color color;
		private final String after;

		CreditLine(String before, String name, Color color, String after)
		{
			this.before = before;
			this.name = name;
			this.color = color;
			this.after = after;
			setFont(FontManager.getRunescapeFont());
			setBorder(new EmptyBorder(2, 0, 0, 0));
			FontMetrics fm = getFontMetrics(getFont());
			Dimension size = new Dimension(fm.stringWidth(before + name + after) + 2, fm.getHeight() + 4);
			setPreferredSize(size);
			setMinimumSize(size);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setFont(getFont());
			FontMetrics fm = g2.getFontMetrics();
			int y = getInsets().top + 1 + fm.getAscent();
			g2.setColor(ROLLABLE);
			g2.drawString(before, 0, y);
			int x = fm.stringWidth(before) + 1;
			g2.setColor(Color.BLACK);
			for (int dx = -1; dx <= 1; dx++)
			{
				for (int dy = -1; dy <= 1; dy++)
				{
					if (dx != 0 || dy != 0)
					{
						g2.drawString(name, x + dx, y + dy);
					}
				}
			}
			g2.setColor(color);
			g2.drawString(name, x, y);
			g2.setColor(ROLLABLE);
			g2.drawString(after, x + fm.stringWidth(name) + 1, y);
			g2.dispose();
		}
	}

	/** A link at rest: a step lighter than the body text, so it stands out without shouting. */
	private static final Color LINK = new Color(215, 215, 215);

	/** Where bug reports and feedback go (shown in the rules view). */
	private static final String FEEDBACK_EMAIL = "bswmagic.gaming@gmail.com";

	/**
	 * A rules card: an outlined title, then paragraphs (Strings) and lists (components, see bulletList).
	 * In the text, <w>...</w> is white and <o>...</o> is orange (buttons); everything else is light grey.
	 */
	private static JPanel rulesCard(String title, Object... parts)
	{
		JPanel card = card();
		card.add(outlined(title, FontManager.getRunescapeBoldFont(), WHITE));
		for (Object part : parts)
		{
			if (part instanceof JComponent)
			{
				card.add((JComponent) part);
				continue;
			}
			JLabel label = wrapped(rulesHtml((String) part), FontManager.getRunescapeFont(), ROLLABLE, RULES_TEXT_WIDTH);
			label.setBorder(new EmptyBorder(2, 0, 0, 0));
			card.add(label);
		}
		return card;
	}

	private static String rulesHtml(String text)
	{
		return html(text).replace("\n", "<br>")
			.replace("&lt;w&gt;", "<font color='#ffffff'>").replace("&lt;/w&gt;", "</font>")
			.replace("&lt;o&gt;", "<font color='#ff9800'>").replace("&lt;/o&gt;", "</font>");
	}

	/** One list entry: its marker (an icon, or null for an orange diamond) and its text (same markup as paragraphs). */
	@lombok.Value
	private static class Bullet
	{
		javax.swing.Icon icon;
		String text;
	}

	/**
	 * An indented list, each entry's text wrapping beside its marker rather than under it. The marker column is
	 * as wide as the list's widest marker (a full icon, or just the small diamond), and the text gets the rest.
	 */
	private static JComponent bulletList(List<Bullet> bullets)
	{
		int markerWidth = 0;
		for (Bullet bullet : bullets)
		{
			markerWidth = Math.max(markerWidth, marker(bullet).getIconWidth());
		}
		// Room left for the text, converted to the HTML width units the labels wrap by
		int textPx = RULES_CARD_INNER_WIDTH - BULLET_INDENT - markerWidth - BULLET_GAP;
		int textWidth = Math.round(textPx / HTML_PX_SCALE);

		JPanel list = new JPanel(new DynamicGridLayout(0, 1, 0, 2));
		list.setOpaque(false);
		list.setBorder(new EmptyBorder(3, BULLET_INDENT, 2, 0));
		for (Bullet bullet : bullets)
		{
			JPanel row = new JPanel(new BorderLayout(BULLET_GAP, 0));
			row.setOpaque(false);
			javax.swing.Icon icon = marker(bullet);
			JLabel marker = new JLabel(icon);
			marker.setHorizontalAlignment(SwingConstants.CENTER);
			// Same width for every marker in the list, so the text lines up
			marker.setPreferredSize(new Dimension(markerWidth, icon.getIconHeight()));
			row.add(marker, BorderLayout.WEST);
			row.add(wrapped(rulesHtml(bullet.getText()), FontManager.getRunescapeFont(), ROLLABLE, textWidth), BorderLayout.CENTER);
			list.add(row);
		}
		return list;
	}

	private static javax.swing.Icon marker(Bullet bullet)
	{
		return bullet.getIcon() != null ? bullet.getIcon() : new PixelIcon(DIAMOND, ORANGE);
	}

	/**
	 * Round button in the top card's corner: "?" opens the rules view, "x" goes back to the run, and in the rules
	 * view the History button sits beside it. Orange ring, filled in on hover (and while its view is showing).
	 */
	private static class InfoButton extends JComponent
	{
		private static final int SIZE = 18;
		static final String[] CAMERA_GLYPH = {
			"...####...",
			"##########",
			"###....###",
			"##..##..##",
			"##..##..##",
			"###....###",
			"##########",
		};
		static final String[] HISTORY_GLYPH = {
			"##.#####",
			"##.#####",
			"........",
			"##.#####",
			"##.#####",
			"........",
			"##.#####",
			"##.#####",
		};
		static final String[] QUESTION_GLYPH = {
			".####.",
			"##..##",
			"##..##",
			"....##",
			"...##.",
			"..##..",
			"..##..",
			"......",
			"..##..",
			"..##..",
		};
		static final String[] CLOSE_GLYPH = {
			"##....##",
			"###..###",
			".######.",
			"..####..",
			"..####..",
			".######.",
			"###..###",
			"##....##",
		};
		private final String[] glyph;
		/** Its size while popping in or out (1 is full size). */
		java.util.function.DoubleSupplier scale = () -> 1;
		/** Drawn filled in, like a hover, because its view is the one showing. */
		private final boolean active;
		private boolean hover;

		InfoButton(String[] glyph, String tooltip, boolean active, Runnable action)
		{
			this.glyph = glyph;
			this.active = active;
			Dimension size = new Dimension(SIZE + 2, SIZE + 2);
			setPreferredSize(size);
			setMinimumSize(size);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setToolTipText(tooltip);
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					action.run();
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = false;
					repaint();
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int x = getWidth() - SIZE - 1;
			int y = (getHeight() - SIZE) / 2;
			double s = scale.getAsDouble();
			if (s <= 0.01)
			{
				g2.dispose();
				return;
			}
			if (s != 1)
			{
				g2.translate(x + SIZE / 2.0, y + SIZE / 2.0);
				g2.scale(s, s);
				g2.translate(-(x + SIZE / 2.0), -(y + SIZE / 2.0));
			}
			// Black edge like the rest of the panel's outlined titles, then the orange ring (filled on hover)
			g2.setColor(Color.BLACK);
			g2.fillOval(x - 1, y - 1, SIZE + 2, SIZE + 2);
			g2.setColor(ORANGE);
			g2.fillOval(x, y, SIZE, SIZE);
			boolean filled = hover || active;
			if (!filled)
			{
				g2.setColor(CARD);
				g2.fillOval(x + 2, y + 2, SIZE - 4, SIZE - 4);
			}
			// Pixel glyphs, an even number of pixels across like the ring, so they sit exactly in its middle
			// (font text and antialiased lines land half a pixel off)
			g2.setColor(filled ? new Color(30, 30, 30) : ORANGE);
			int gx = x + (SIZE - glyph[0].length()) / 2;
			int gy = y + (SIZE - glyph.length) / 2;
			for (int row = 0; row < glyph.length; row++)
			{
				for (int col = 0; col < glyph[row].length(); col++)
				{
					if (glyph[row].charAt(col) == '#')
					{
						g2.fillRect(gx + col, gy + row, 1, 1);
					}
				}
			}
			g2.dispose();
		}
	}

	// ---------------------------------------------------------------- History view

	/** Lines an open History group shows at first, and how many more each "Show more" adds. */
	private static final int HISTORY_PAGE = 60;
	/** Wrapping width of a History line's text (HTML units), beside its icon. */
	private static final int HISTORY_TEXT_WIDTH = Math.round((RULES_CARD_INNER_WIDTH - 12 - ICON_SIZE - 6) / HTML_PX_SCALE);
	private static final Color HISTORY_TIME = new Color(150, 150, 150);

	/** Everything that's happened in the run, grouped by calendar day, week, month or year. */
	private void buildHistory()
	{
		FiveActiveConfig.HistoryGrouping grouping = plugin.getConfig().historyGrouping();
		boolean oldestFirst = plugin.getConfig().historyOldestFirst();
		List<HistoryEntry> entries = plugin.getHistory().getEntries();

		JPanel header = card();
		header.add(titleRow());
		// The camera sits on the subtitle's line: the title row has no room for a third button
		JPanel subtitle = new JPanel(new BorderLayout());
		subtitle.setOpaque(false);
		subtitle.add(small("Your run's History", ROLLABLE), BorderLayout.WEST);
		subtitle.add(new InfoButton(InfoButton.CAMERA_GLYPH, "Share your run: copies a picture of it to paste anywhere", false,
			this::shareRun), BorderLayout.EAST);
		header.add(subtitle);
		// Warm the share card's pictures up, so they're ready when it's drawn
		accountBadge(data.getAccountType());
		for (PanelData.SlotView boss : data.getBosses())
		{
			bossIcon(boss.getIconSpriteId());
		}
		bossIcon(SpriteID.SideIcons._0);
		bossIcon(SpriteID.SideIcons._1);
		bossIcon(SpriteID.SideIcons._2);
		itemIcon(ItemID.COLLECTION_LOG, 1);
		int clogs = 0;
		for (int i = entries.size() - 1; i >= 0 && clogs < SHARE_CLOGS; i--)
		{
			if (entries.get(i).getKind() == HistoryEntry.Kind.CLOG_ITEM)
			{
				clogs++;
				if (entries.get(i).getValue() > 0)
				{
					itemIcon((int) entries.get(i).getValue(), 1);
				}
			}
		}
		header.add(historySummary(entries));
		header.add(groupingSwitch(grouping, oldestFirst));
		add(header);

		if (entries.isEmpty())
		{
			JPanel card = card();
			card.add(wrapped("Nothing has happened yet. Everything from your first roll on shows up here.",
				FontManager.getRunescapeFont(), ROLLABLE, RULES_TEXT_WIDTH));
			add(card);
			return;
		}

		// Calendar groups, oldest first
		Map<String, List<HistoryEntry>> groups = new LinkedHashMap<>();
		for (HistoryEntry entry : entries)
		{
			groups.computeIfAbsent(historyKey(grouping, entry.getTime()), k -> new ArrayList<>()).add(entry);
		}
		List<String> keys = new ArrayList<>(groups.keySet());
		if (!historyOpenedNewest)
		{
			historyOpenedNewest = true;
			openHistoryGroups.add(keys.get(keys.size() - 1));
		}
		if (!oldestFirst)
		{
			Collections.reverse(keys);
		}
		for (String key : keys)
		{
			boolean open = openHistoryGroups.contains(key);
			add(fold("H:" + key, open, historyGroup(key, grouping, groups.get(key), open, oldestFirst)));
		}
	}

	// ---------------------------------------------------------------- Share card

	/** The share card's width before it's doubled up (pixel-art style) for sharing. */
	private static final int SHARE_WIDTH = 240;
	private static final int SHARE_PAD = 8;
	private static final int SHARE_ROW = 29;

	/** Draws the run as a picture, copies it to the clipboard and saves it with RuneLite's screenshots. */
	private void shareRun()
	{
		if (shareWait != null)
		{
			return; // already on its way
		}
		// Pictures load in the background the first time they're asked for: give them a moment (at most a
		// couple of seconds) so the card doesn't go out with gaps where icons should be
		long giveUpAt = System.currentTimeMillis() + SHARE_WAIT_MS;
		shareWait = new javax.swing.Timer(50, null);
		shareWait.addActionListener(e ->
		{
			if (shareIconsReady() || System.currentTimeMillis() >= giveUpAt)
			{
				shareWait.stop();
				shareWait = null;
				shareNow();
			}
		});
		shareWait.setInitialDelay(0);
		shareWait.start();
	}

	/** Longest the share button waits for pictures to load. */
	private static final int SHARE_WAIT_MS = 2500;
	private javax.swing.Timer shareWait;

	/** Whether every picture the card uses has loaded (asking for one starts loading it). */
	private boolean shareIconsReady()
	{
		boolean ready = bossIcon(SpriteID.SideIcons._0) != null
			& bossIcon(SpriteID.SideIcons._1) != null
			& bossIcon(SpriteID.SideIcons._2) != null
			& itemIcon(ItemID.COLLECTION_LOG, 1) != null
			& (data.getAccountType() <= 0 || accountBadge(data.getAccountType()) != null);
		for (PanelData.SlotView boss : data.getBosses())
		{
			ready &= boss.getIconSpriteId() < 0 || bossIcon(boss.getIconSpriteId()) != null;
		}
		List<HistoryEntry> entries = plugin.getHistory().getEntries();
		int clogs = 0;
		for (int i = entries.size() - 1; i >= 0 && clogs < SHARE_CLOGS; i--)
		{
			HistoryEntry entry = entries.get(i);
			if (entry.getKind() == HistoryEntry.Kind.CLOG_ITEM)
			{
				clogs++;
				ready &= entry.getValue() <= 0 || itemIcon((int) entry.getValue(), 1) != null;
			}
		}
		return ready;
	}

	private void shareNow()
	{
		BufferedImage card = renderShareCard();
		try
		{
			java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageSelection(card), null);
		}
		catch (IllegalStateException e)
		{
			// Clipboard busy: the saved file still works
		}
		String name = "Five Active " + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + ".png";
		plugin.getHistory().saveShareCard(card, name, plugin::shareSaved);
	}

	/** An image on the clipboard. */
	private static class ImageSelection implements java.awt.datatransfer.Transferable
	{
		private final Image image;

		ImageSelection(Image image)
		{
			this.image = image;
		}

		@Override
		public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors()
		{
			return new java.awt.datatransfer.DataFlavor[]{java.awt.datatransfer.DataFlavor.imageFlavor};
		}

		@Override
		public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor)
		{
			return java.awt.datatransfer.DataFlavor.imageFlavor.equals(flavor);
		}

		@Override
		public Object getTransferData(java.awt.datatransfer.DataFlavor flavor) throws java.awt.datatransfer.UnsupportedFlavorException
		{
			if (!isDataFlavorSupported(flavor))
			{
				throw new java.awt.datatransfer.UnsupportedFlavorException(flavor);
			}
			return image;
		}
	}

	/** How many of the latest collection log items the share card shows. */
	private static final int SHARE_CLOGS = 3;
	/** Width of a tile in the share card's rows of five. */
	private static final int SHARE_TILE = 42;

	/**
	 * The run on one card: the logo and account; lifetime numbers (quests, total level, boss KC, collection log);
	 * the current objectives (skills and bosses as icon tiles with their progress, quests by name); the latest
	 * collection log items; and the run's totals. Drawn at 1x with the game fonts, then doubled pixel for pixel
	 * so it stays crisp wherever it's pasted.
	 */
	BufferedImage renderShareCard()
	{
		BufferedImage image = new BufferedImage(SHARE_WIDTH, 1200, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(CARD);
		g.fillRect(0, 0, SHARE_WIDTH, image.getHeight());
		int right = SHARE_WIDTH - SHARE_PAD;
		Font small = FontManager.getRunescapeSmallFont();
		Font bold = FontManager.getRunescapeBoldFont();
		List<HistoryEntry> entries = plugin.getHistory().getEntries();

		// Logo, with the account and the date on the right
		BufferedImage logo = NavIcon.title(TITLE_SCALE);
		g.drawImage(logo, SHARE_PAD, SHARE_PAD, null);
		String account = plugin.getAccountName();
		if (account != null)
		{
			shareText(g, account, bold, WHITE, right, SHARE_PAD + 11, true);
			// Ironman helm (or the other account types' badge) in front of the name, like in chat
			BufferedImage badge = accountBadge(data.getAccountType());
			if (badge != null)
			{
				int nameWidth = g.getFontMetrics(bold).stringWidth(account);
				g.drawImage(badge, right - nameWidth - badge.getWidth() - 3, SHARE_PAD + 11 - badge.getHeight() + 1, null);
			}
		}
		shareText(g, new SimpleDateFormat("d MMM yyyy").format(new Date()), small, ROLLABLE, right, SHARE_PAD + 27, true);
		int y = SHARE_PAD + logo.getHeight() + 9;

		// The run: which day of it, its tier and Shuffles
		StringBuilder run = new StringBuilder();
		if (!entries.isEmpty() && entries.get(0).getKind() == HistoryEntry.Kind.RUN_STARTED)
		{
			long days = (System.currentTimeMillis() - entries.get(0).getTime()) / (24L * 3600 * 1000) + 1;
			run.append("Day ").append(days).append("     ");
		}
		run.append("Boss Tier ").append(Math.max(1, data.getUnlockedTiers())).append(" of ").append(BossTiers.TIERS.size());
		shareText(g, run.toString(), small, ORANGE, SHARE_PAD, y + 9, false);
		shareText(g, data.getShuffles() + (data.getShuffles() == 1 ? " Shuffle" : " Shuffles"), small, ORANGE, right, y + 9, true);
		y += 14;

		// Completed this run: the accomplishments, in green
		if (!entries.isEmpty())
		{
			String since = "since " + new SimpleDateFormat("d MMM yyyy").format(new Date(entries.get(0).getTime()));
			y = shareTitle(g, "Completed", since, y);
			int third = (SHARE_WIDTH - 2 * SHARE_PAD - 8) / 3;
			int x = SHARE_PAD;
			for (Category category : new Category[]{Category.SKILLS, Category.BOSSES, Category.QUESTS})
			{
				long count = entries.stream()
					.filter(e -> e.getKind() == HistoryEntry.Kind.COMPLETED && e.getCategory() == category).count();
				javax.swing.Icon icon = category == Category.SKILLS ? bossIcon(SpriteID.SideIcons._1)
					: category == Category.BOSSES ? skillIcon(Skill.SLAYER)
					: bossIcon(SpriteID.SideIcons._2);
				shareStat(g, icon, String.valueOf(count), category.getTitle(), GREEN, x, y, third);
				x += third + 4;
			}
			y += 34;
		}

		// Lifetime: big numbers, two to a row
		y = shareTitle(g, "Lifetime", null, y);
		int half = (SHARE_WIDTH - 2 * SHARE_PAD - 4) / 2;
		shareStat(g, bossIcon(SpriteID.SideIcons._2), data.getQuestsCompleted() + "/" + data.getQuestsTotal(), "Quests", WHITE, SHARE_PAD, y, half);
		shareStat(g, bossIcon(SpriteID.SideIcons._1), String.format("%,d", data.getTotalLevel()), "Total level", WHITE, SHARE_PAD + half + 4, y, half);
		y += 34;
		shareStat(g, bossIcon(SpriteID.SideIcons._0), String.valueOf(data.getCombatLevel()), "Combat level", WHITE, SHARE_PAD, y, half);
		shareStat(g, skillIcon(Skill.SLAYER), String.format("%,d", data.getTotalKc()), "Boss KC", WHITE, SHARE_PAD + half + 4, y, half);
		y += 34;
		shareStat(g, itemIcon(ItemID.COLLECTION_LOG, 1), String.format("%,d", data.getCollectionLogSlots()), "Collection log slots", WHITE,
			SHARE_PAD, y, SHARE_WIDTH - 2 * SHARE_PAD);
		y += 34;

		// Current objectives
		y = shareTitle(g, "Objectives", null, y + 2);
		y = shareTiles(g, data.getSkills(), Category.SKILLS, y);
		y = shareTiles(g, data.getBosses(), Category.BOSSES, y);
		y = shareQuests(g, data.getQuests(), y);

		// The latest collection log items: their pictures, or names for items logged before IDs were kept
		List<HistoryEntry> clogs = new ArrayList<>();
		for (int i = entries.size() - 1; i >= 0 && clogs.size() < SHARE_CLOGS; i--)
		{
			if (entries.get(i).getKind() == HistoryEntry.Kind.CLOG_ITEM)
			{
				clogs.add(entries.get(i));
			}
		}
		if (!clogs.isEmpty())
		{
			y = shareTitle(g, "Latest collection log", null, y + 2);
			List<String> unnamed = new ArrayList<>();
			int x = SHARE_PAD;
			boolean anyPictures = false;
			for (HistoryEntry clog : clogs)
			{
				ImageIcon icon = clog.getValue() > 0 ? itemIcon((int) clog.getValue(), 1) : null;
				if (icon == null)
				{
					unnamed.add(clog.name());
					continue;
				}
				g.setColor(ROW);
				g.fillRect(x, y, SHARE_TILE, 30);
				icon.paintIcon(this, g, x + (SHARE_TILE - icon.getIconWidth()) / 2, y + (30 - icon.getIconHeight()) / 2);
				x += SHARE_TILE + 3;
				anyPictures = true;
			}
			if (anyPictures)
			{
				y += 33;
			}
			for (String name : unnamed)
			{
				shareText(g, fit(name, g.getFontMetrics(small), SHARE_WIDTH - 2 * SHARE_PAD), small, WHITE, SHARE_PAD, y + 9, false);
				y += 13;
			}
		}

		y += SHARE_PAD - 2;

		// Frame
		g.setColor(ORANGE);
		g.drawRect(0, 0, SHARE_WIDTH - 1, y - 1);
		g.dispose();

		BufferedImage cropped = image.getSubimage(0, 0, SHARE_WIDTH, y);
		BufferedImage doubled = new BufferedImage(SHARE_WIDTH * 2, y * 2, BufferedImage.TYPE_INT_ARGB);
		Graphics2D d = doubled.createGraphics();
		d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		d.drawImage(cropped, 0, 0, SHARE_WIDTH * 2, y * 2, null);
		d.dispose();
		return doubled;
	}

	/** The chat badge for an account type (ironman helm and so on), or null for a normal account or while it loads. */
	private BufferedImage accountBadge(int accountType)
	{
		int[] icons = {-1, IconID.IRONMAN.getIndex(), IconID.ULTIMATE_IRONMAN.getIndex(), IconID.HARDCORE_IRONMAN.getIndex(),
			IconID.GROUP_IRONMAN.getIndex(), IconID.HARDCORE_GROUP_IRONMAN.getIndex(), IconID.UNRANKED_GROUP_IRONMAN.getIndex()};
		int frame = accountType > 0 && accountType < icons.length ? icons[accountType] : -1;
		if (frame < 0 || spriteManager == null)
		{
			return null;
		}
		if (!badges.containsKey(frame))
		{
			badges.put(frame, null);
			spriteManager.getSpriteAsync(SpriteID.MOD_ICONS, frame, sprite -> SwingUtilities.invokeLater(() -> badges.put(frame, sprite)));
		}
		return badges.get(frame);
	}

	private final Map<Integer, BufferedImage> badges = new HashMap<>();

	/** A section title on the share card, with an optional note on the right. Returns the y below it. */
	private static int shareTitle(Graphics2D g, String title, String note, int y)
	{
		y += 4;
		shareText(g, title, FontManager.getRunescapeBoldFont(), WHITE, SHARE_PAD, y + 11, false);
		if (note != null)
		{
			shareText(g, note, FontManager.getRunescapeSmallFont(), ROLLABLE, SHARE_WIDTH - SHARE_PAD, y + 11, true);
		}
		return y + 16;
	}

	/** A big number: icon, the number, and what it is under it. */
	private void shareStat(Graphics2D g, javax.swing.Icon icon, String number, String label, Color color, int x, int y, int width)
	{
		g.setColor(ROW);
		g.fillRect(x, y, width, 31);
		if (icon != null)
		{
			icon.paintIcon(this, g, x + 3, y + 3);
		}
		// The number and its label centred together in the tile, by their letters' cap heights
		Font bold = FontManager.getRunescapeBoldFont();
		Font small = FontManager.getRunescapeSmallFont();
		int numberCap = capHeight(g, bold);
		int labelCap = capHeight(g, small);
		int gap = 4;
		// (a pixel lower than dead centre looks centred, with the bold numbers' weight on top)
		int top = y + (31 - (numberCap + gap + labelCap) + 1) / 2 + 1;
		shareText(g, number, bold, color, x + ICON_SIZE + 8, top + numberCap, false);
		shareText(g, label, small, ROLLABLE, x + ICON_SIZE + 8, top + numberCap + gap + labelCap, false);
	}

	/** Skills or bosses as a row of icon tiles, each with its progress under it (green when done). */
	private int shareTiles(Graphics2D g, List<PanelData.SlotView> slots, Category category, int y)
	{
		if (slots == null || slots.isEmpty())
		{
			return y;
		}
		int x = SHARE_PAD;
		Font small = FontManager.getRunescapeSmallFont();
		for (PanelData.SlotView slot : slots)
		{
			g.setColor(ROW);
			g.fillRect(x, y, SHARE_TILE, 46);
			javax.swing.Icon icon = category == Category.SKILLS && slot.getSkill() != null ? skillIcon(slot.getSkill())
				: bossIcon(slot.getIconSpriteId());
			if (icon != null)
			{
				icon.paintIcon(this, g, x + (SHARE_TILE - icon.getIconWidth()) / 2, y + 3);
			}
			String progress = slot.isDone() ? "Done" : slot.getProgress() + "/" + slot.getGoal();
			int w = g.getFontMetrics(small).stringWidth(progress);
			// A clear gap under the icon (boss pictures fill theirs right to the bottom)
			shareText(g, progress, small, slot.isDone() ? GREEN : ORANGE, x + (SHARE_TILE - w) / 2, y + 42, false);
			x += SHARE_TILE + 3;
		}
		return y + 49;
	}

	/** Quests by name (their icons are all the same), each with its state on the right. */
	private int shareQuests(Graphics2D g, List<PanelData.SlotView> quests, int y)
	{
		if (quests == null)
		{
			return y;
		}
		Font font = FontManager.getRunescapeFont();
		for (PanelData.SlotView quest : quests)
		{
			int w = SHARE_WIDTH - 2 * SHARE_PAD;
			g.setColor(ROW);
			g.fillRect(SHARE_PAD, y, w, 18);
			String status;
			Color color;
			if (quest.isDone())
			{
				status = "Done";
				color = GREEN;
			}
			else if (quest.getQuestState() == net.runelite.api.QuestState.IN_PROGRESS)
			{
				status = "In progress";
				color = YELLOW;
			}
			else
			{
				status = "Not started";
				color = ROLLABLE;
			}
			int statusWidth = g.getFontMetrics(font).stringWidth(status);
			// Centred on the letters' cap height, so the row's gap above and below them matches
			int baseline = y + (18 + capHeight(g, font)) / 2;
			shareText(g, status, font, color, SHARE_PAD + w - 5, baseline, true);
			shareText(g, fit(quest.getName(), g.getFontMetrics(font), w - statusWidth - 18), font, quest.isDone() ? GREEN : WHITE,
				SHARE_PAD + 5, baseline, false);
			y += 21;
		}
		return y;
	}

	/** How tall a font's capital letters are, for centring text by what's actually drawn. */
	private static int capHeight(Graphics2D g, Font font)
	{
		return (int) Math.round(-font.createGlyphVector(g.getFontRenderContext(), "H").getVisualBounds().getY());
	}

	/** Text with the panel's 1px black outline, left- or right-aligned at x. */
	private static void shareText(Graphics2D g, String text, Font font, Color color, int x, int baseline, boolean alignRight)
	{
		g.setFont(font);
		int left = alignRight ? x - g.getFontMetrics().stringWidth(text) : x;
		g.setColor(Color.BLACK);
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				if (dx != 0 || dy != 0)
				{
					g.drawString(text, left + dx, baseline + dy);
				}
			}
		}
		g.setColor(color);
		g.drawString(text, left, baseline);
	}

	/** The text, cut short with "..." if it's wider than width. */
	private static String fit(String text, FontMetrics fm, int width)
	{
		if (fm.stringWidth(text) <= width)
		{
			return text;
		}
		String cut = text;
		while (cut.length() > 1 && fm.stringWidth(cut + "...") > width)
		{
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut.trim() + "...";
	}

	/** A line of totals under the History title: since when, and the highlights. */
	private JComponent historySummary(List<HistoryEntry> entries)
	{
		if (entries.isEmpty())
		{
			return small(" ", ROLLABLE);
		}
		int finished = 0;
		int levels = 0;
		int rolls = 0;
		for (HistoryEntry entry : entries)
		{
			switch (entry.getKind())
			{
				case COMPLETED:
					finished++;
					break;
				case LEVEL_UP:
					levels++;
					break;
				case ROLLED:
				case SHUFFLED:
					rolls += entry.nameList().size();
					break;
				default:
					break;
			}
		}
		String since = new SimpleDateFormat("d MMM yyyy").format(new Date(entries.get(0).getTime()));
		return wrapped("Since " + since + ": <font color='#ffffff'>" + finished + "</font> completed, <font color='#ffffff'>"
			+ rolls + "</font> rolled, <font color='#ffffff'>" + String.format("%,d", levels) + "</font> level-ups",
			FontManager.getRunescapeSmallFont(), ROLLABLE, RULES_TEXT_WIDTH);
	}

	/** Day / Week / Month / Year tabs, and which end of the run the list starts from. */
	private JComponent groupingSwitch(FiveActiveConfig.HistoryGrouping grouping, boolean oldestFirst)
	{
		JPanel box = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
		box.setOpaque(false);
		box.setBorder(new EmptyBorder(4, 0, 0, 0));

		JPanel tabs = new JPanel(new GridLayout(1, 0, 2, 0));
		tabs.setOpaque(false);
		for (FiveActiveConfig.HistoryGrouping option : FiveActiveConfig.HistoryGrouping.values())
		{
			boolean selected = option == grouping;
			tabs.add(historyTab(option.getLabel(), selected, selected ? null : () ->
			{
				plugin.saveHistoryView(option, oldestFirst);
				// Different groups now: open the newest again
				openHistoryGroups.clear();
				historyShown.clear();
				historyOpenedNewest = false;
				SwingUtilities.invokeLater(() -> update(data));
			}));
		}
		box.add(tabs);

		JPanel order = new JPanel(new GridLayout(1, 0, 2, 0));
		order.setOpaque(false);
		order.add(historyTab("Latest first", !oldestFirst, oldestFirst ? () ->
		{
			plugin.saveHistoryView(grouping, false);
			SwingUtilities.invokeLater(() -> update(data));
		} : null));
		order.add(historyTab("From the start", oldestFirst, oldestFirst ? null : () ->
		{
			plugin.saveHistoryView(grouping, true);
			SwingUtilities.invokeLater(() -> update(data));
		}));
		box.add(order);
		return box;
	}

	/** One tab: orange on a raised background when selected, grey (white on hover) otherwise. */
	private static JComponent historyTab(String text, boolean selected, Runnable select)
	{
		JLabel tab = label(text, FontManager.getRunescapeSmallFont(), selected ? ORANGE : ROLLABLE);
		tab.setHorizontalAlignment(SwingConstants.CENTER);
		tab.setOpaque(true);
		tab.setBackground(selected ? ROW_HOVER : ROW);
		tab.setBorder(new EmptyBorder(3, 0, 3, 0));
		if (select != null)
		{
			tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			tab.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					select.run();
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					tab.setForeground(WHITE);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					tab.setForeground(ROLLABLE);
				}
			});
		}
		return tab;
	}

	/** Which group a moment falls in: its day, the Monday its week starts on, its month or its year. */
	private static String historyKey(FiveActiveConfig.HistoryGrouping grouping, long time)
	{
		Calendar c = Calendar.getInstance();
		c.setTimeInMillis(time);
		switch (grouping)
		{
			case WEEK:
				c.setFirstDayOfWeek(Calendar.MONDAY);
				int back = (c.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7;
				c.add(Calendar.DAY_OF_MONTH, -back);
				return String.format("W%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
			case MONTH:
				return String.format("M%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
			case YEAR:
				return String.format("Y%04d", c.get(Calendar.YEAR));
			case DAY:
			default:
				return String.format("D%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
		}
	}

	/** A group's title: "Today", "Mon 28 Sep", "Week of 21 Sep", "September 2026", "2026". */
	private static String historyTitle(String key, long anyTime)
	{
		Calendar c = Calendar.getInstance();
		c.setTimeInMillis(anyTime);
		boolean thisYear = c.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR);
		switch (key.charAt(0))
		{
			case 'W':
			{
				String[] ymd = key.substring(1).split("-");
				c.set(Integer.parseInt(ymd[0]), Integer.parseInt(ymd[1]) - 1, Integer.parseInt(ymd[2]));
				if (key.equals(historyKey(FiveActiveConfig.HistoryGrouping.WEEK, System.currentTimeMillis())))
				{
					return "This week";
				}
				return "Week of " + new SimpleDateFormat(thisYear ? "d MMM" : "d MMM yyyy").format(c.getTime());
			}
			case 'M':
				return new SimpleDateFormat("MMMM yyyy").format(c.getTime());
			case 'Y':
				return String.valueOf(c.get(Calendar.YEAR));
			default:
				long day = 24L * 60 * 60 * 1000;
				if (key.equals(historyKey(FiveActiveConfig.HistoryGrouping.DAY, System.currentTimeMillis())))
				{
					return "Today";
				}
				if (key.equals(historyKey(FiveActiveConfig.HistoryGrouping.DAY, System.currentTimeMillis() - day)))
				{
					return "Yesterday";
				}
				return new SimpleDateFormat(thisYear ? "EEE d MMM" : "EEE d MMM yyyy").format(c.getTime());
		}
	}

	/** One calendar group: a header that opens and closes it, then its lines. */
	private JComponent historyGroup(String key, FiveActiveConfig.HistoryGrouping grouping, List<HistoryEntry> entries,
		boolean open, boolean oldestFirst)
	{
		JPanel card = card();
		List<HistoryLine> lines = historyLines(entries);

		JPanel left = new JPanel(new BorderLayout(5, 0));
		left.setOpaque(false);
		left.add(new Arrow(!open), BorderLayout.WEST);
		left.add(outlined(historyTitle(key, entries.get(0).getTime()), FontManager.getRunescapeBoldFont(), WHITE), BorderLayout.CENTER);
		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.setBorder(new EmptyBorder(0, 0, open ? 2 : 0, 0));
		header.add(left, BorderLayout.WEST);
		header.add(small(historyGroupSummary(entries), ROLLABLE), BorderLayout.EAST);
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (!openHistoryGroups.remove(key))
				{
					openHistoryGroups.add(key);
				}
				foldsToAnimate.add("H:" + key);
				update(data);
			}
		});
		card.add(header);
		if (!open)
		{
			return card;
		}

		if (!oldestFirst)
		{
			Collections.reverse(lines);
		}
		int shown = Math.min(lines.size(), historyShown.getOrDefault(key, HISTORY_PAGE));
		boolean withDate = grouping != FiveActiveConfig.HistoryGrouping.DAY;
		for (int i = 0; i < shown; i++)
		{
			card.add(historyRow(lines.get(i), withDate));
		}
		if (shown < lines.size())
		{
			int more = Math.min(HISTORY_PAGE, lines.size() - shown);
			JLabel showMore = label("Show " + more + " more", FontManager.getRunescapeSmallFont(), ORANGE);
			showMore.setHorizontalAlignment(SwingConstants.CENTER);
			showMore.setBorder(new EmptyBorder(3, 0, 1, 0));
			showMore.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			showMore.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					historyShown.put(key, shown + HISTORY_PAGE);
					update(data);
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					showMore.setForeground(WHITE);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					showMore.setForeground(ORANGE);
				}
			});
			card.add(showMore);
		}
		return card;
	}

	/** The group header's right-hand note: what was finished, else how much happened. */
	private static String historyGroupSummary(List<HistoryEntry> entries)
	{
		long finished = entries.stream().filter(e -> e.getKind() == HistoryEntry.Kind.COMPLETED).count();
		if (finished > 0)
		{
			return finished + " completed";
		}
		long levels = entries.stream().filter(e -> e.getKind() == HistoryEntry.Kind.LEVEL_UP).count();
		if (levels > 0)
		{
			return levels + (levels == 1 ? " level" : " levels");
		}
		return entries.size() + (entries.size() == 1 ? " entry" : " entries");
	}

	/** One line in the History: an icon, what happened (HTML), and when. */
	@lombok.Value
	private static class HistoryLine
	{
		javax.swing.Icon icon;
		String text;
		long time;
	}

	/**
	 * Turns a group's entries (oldest first) into lines. A roll becomes a line per pick, and runs of level-ups in
	 * the same skill with nothing else happening in between become one line ("Fishing levels 41-45").
	 */
	private List<HistoryLine> historyLines(List<HistoryEntry> entries)
	{
		List<HistoryLine> lines = new ArrayList<>();
		// Level-up runs still open, by skill: the line's index and its first entry
		Map<String, int[]> runs = new HashMap<>();
		Map<String, HistoryEntry> runStarts = new HashMap<>();
		for (HistoryEntry entry : entries)
		{
			if (entry.getKind() == HistoryEntry.Kind.LEVEL_UP)
			{
				String skill = entry.name();
				int[] run = runs.get(skill);
				if (run == null)
				{
					runs.put(skill, new int[]{lines.size()});
					runStarts.put(skill, entry);
					lines.add(historyLine(entry, null));
				}
				else
				{
					lines.set(run[0], historyLine(entry, runStarts.get(skill)));
				}
				continue;
			}
			runs.clear();
			runStarts.clear();
			if ((entry.getKind() == HistoryEntry.Kind.ROLLED) && entry.nameList().size() > 1)
			{
				for (String name : entry.nameList())
				{
					lines.add(new HistoryLine(entryIcon(entry.getCategory(), name),
						"Rolled " + w(name), entry.getTime()));
				}
				continue;
			}
			lines.add(historyLine(entry, null));
		}
		return lines;
	}

	/** The line for one entry. runStart is the first of a run of level-ups ending with entry, or null. */
	private HistoryLine historyLine(HistoryEntry e, HistoryEntry runStart)
	{
		String name = e.name();
		switch (e.getKind())
		{
			case RUN_STARTED:
				return new HistoryLine(logoIcon(), w("Run started!") + " Good luck.", e.getTime());
			case LOG_STARTED:
			{
				StringBuilder text = new StringBuilder(w("History started.") + " Active at the time:");
				if (e.getActive() != null)
				{
					e.getActive().forEach((category, names) -> text.append("<br>").append(category.getTitle()).append(": ")
						.append(names.isEmpty() ? "none" : w(String.join(", ", names))));
				}
				return new HistoryLine(logoIcon(), text.toString(), e.getTime());
			}
			case ROLLED:
				return new HistoryLine(entryIcon(e.getCategory(), name), "Rolled " + w(name), e.getTime());
			case SHUFFLED:
				return new HistoryLine(new PixelIcon(SHUFFLE_ICON, ORANGE),
					"Used a " + color("Shuffle", ORANGE) + " on " + e.getCategory().getPlural()
						+ "<br>In: " + w(String.join(", ", e.nameList()))
						+ (e.replacedList().isEmpty() ? "" : "<br>Out: " + html(String.join(", ", e.replacedList()))), e.getTime());
			case COMPLETED:
				return new HistoryLine(entryIcon(e.getCategory(), name),
					color("Completed " + name, GREEN), e.getTime());
			case MAXED:
				return new HistoryLine(entryIcon(Category.SKILLS, name), color(name + " reached 99!", GOLD), e.getTime());
			case LEVEL_UP:
			{
				String levels = runStart == null ? "level " + w(String.valueOf(e.getValue()))
					: "levels " + w(runStart.getValue() + "-" + e.getValue());
				String progress = e.getProgress() >= 0 ? " " + color("(" + e.getProgress() + "/" + Rules.LEVELS_TO_COMPLETE_SKILL + ")", HISTORY_TIME) : "";
				return new HistoryLine(entryIcon(Category.SKILLS, name), html(name) + " " + levels + progress, e.getTime());
			}
			case CLOG_ITEM:
				return new HistoryLine(itemIcon(e.getValue() > 0 ? (int) e.getValue() : ItemID.COLLECTION_LOG, 1),
					"Collection log: " + w(name), e.getTime());
			case CLOG_SLOTS:
				return new HistoryLine(itemIcon(ItemID.COLLECTION_LOG, 1), e.getProgress() == 1
					? "Collection log synced: " + w(String.format("%,d", e.getValue())) + " slots"
					: w(String.valueOf(e.getValue())) + " new collection log " + (e.getValue() == 1 ? "slot" : "slots"), e.getTime());
			case SHUFFLE_EARNED:
				return new HistoryLine(new PixelIcon(SHUFFLE_ICON, ORANGE),
					"Earned " + color(e.getValue() == 1 ? "a Shuffle" : e.getValue() + " Shuffles", ORANGE) + "!", e.getTime());
			case TIER_READY:
				return new HistoryLine(new PixelIcon(PADLOCK, GOLD), color("Tier " + e.getValue(), GOLD) + " ready to unlock ("
					+ String.format("%,d", e.getProgress()) + " total KC)", e.getTime());
			case TIER_UNLOCKED:
				return new HistoryLine(new PixelIcon(unlockPixels(), GOLD), "Unlocked " + color("Tier " + e.getValue(), GOLD)
					+ (e.getProgress() > 0 ? ": " + e.getProgress() + " new " + (e.getProgress() == 1 ? "boss" : "bosses") : ""), e.getTime());
			case BANK_VALUE:
				return new HistoryLine(itemIcon(ItemID.COINS, e.getValue() >= 1_000_000 ? 10000 : 1000),
					"Bank worth over " + color(shortValue(e.getValue()), GOLD) + "!", e.getTime());
			case RESTORED:
			default:
				return new HistoryLine(logoIcon(), e.getValue() > 0
					? "Run restored from the backup of " + new SimpleDateFormat("d MMM, h:mm a").format(new Date(e.getValue()))
					: "Run restored from a save code", e.getTime());
		}
	}

	/** 100K, 1M, 10M, 1B. */
	private static String shortValue(long value)
	{
		if (value >= 1_000_000_000L)
		{
			return value / 1_000_000_000L + "B";
		}
		return value >= 1_000_000L ? value / 1_000_000L + "M" : value / 1_000L + "K";
	}

	private static String w(String text)
	{
		return color(text, WHITE);
	}

	private static String color(String text, Color color)
	{
		return String.format("<font color='#%06x'>", color.getRGB() & 0xffffff) + html(text) + "</font>";
	}

	private JComponent historyRow(HistoryLine line, boolean withDate)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ROW);
		row.setBorder(new EmptyBorder(4, 6, 4, 6));
		JLabel icon = new JLabel(line.getIcon() != null ? line.getIcon() : blankIcon());
		icon.setVerticalAlignment(SwingConstants.TOP);
		icon.setPreferredSize(new Dimension(ICON_SIZE, ICON_SIZE));
		row.add(icon, BorderLayout.WEST);

		JPanel text = new JPanel(new DynamicGridLayout(0, 1, 0, 1));
		text.setOpaque(false);
		text.add(wrapped(line.getText(), FontManager.getRunescapeFont(), ROLLABLE, HISTORY_TEXT_WIDTH));
		String when = new SimpleDateFormat(withDate ? "EEE d MMM, h:mm a" : "h:mm a").format(new Date(line.getTime()));
		text.add(small(when, HISTORY_TIME));
		row.add(text, BorderLayout.CENTER);
		return row;
	}

	/** The icon for a skill, boss or quest by name (null while a sprite loads). */
	private javax.swing.Icon entryIcon(Category category, String name)
	{
		if (category == Category.SKILLS)
		{
			for (Skill skill : Skill.values())
			{
				if (skill.getName().equals(name))
				{
					return skillIcon(skill);
				}
			}
			return null;
		}
		if (category == Category.BOSSES)
		{
			for (Boss boss : Boss.values())
			{
				if (boss.getDisplayName().equals(name))
				{
					return bossIcon(boss.getHiscore() == null ? -1 : boss.getHiscore().getSpriteId());
				}
			}
			return null;
		}
		return bossIcon(SpriteID.SideIcons._2);
	}

	private javax.swing.Icon logoIcon()
	{
		return itemIcons.computeIfAbsent("logo", k -> new ImageIcon(fitIcon(NavIcon.create(), ICON_SIZE)));
	}

	/** An item's picture fitted to the icon size, or null while it loads (the panel redraws once it has). */
	private ImageIcon itemIcon(int itemId, int quantity)
	{
		String key = itemId + "x" + quantity;
		if (!itemIcons.containsKey(key))
		{
			itemIcons.put(key, null);
			AsyncBufferedImage image = plugin.getItemManager().getImage(itemId, quantity, quantity > 1);
			if (image == null)
			{
				return null;
			}
			image.onLoaded(() -> SwingUtilities.invokeLater(() ->
			{
				itemIcons.put(key, new ImageIcon(fitIcon(crop(image), ICON_SIZE)));
				if (!iconRefreshQueued)
				{
					iconRefreshQueued = true;
					SwingUtilities.invokeLater(() ->
					{
						iconRefreshQueued = false;
						update(data);
					});
				}
			}));
		}
		return itemIcons.get(key);
	}

	/** Two arrows passing each other, for Shuffles in the History. */
	private static final String[] SHUFFLE_ICON = {
		"......k.....",
		"......kk....",
		"......kok...",
		"kkkkkkkook..",
		"koooooooook.",
		"kkkkkkkook..",
		"......kok...",
		"......kk....",
		"......k.....",
		".....k......",
		"....kk......",
		"...kok......",
		"..kookkkkkkk",
		".koooooooook",
		"..kookkkkkkk",
		"...kok......",
		"....kk......",
		".....k......",
	};

	/** The Unlock button's open padlock, in PixelIcon's pattern letters. */
	private static String[] unlockPixels()
	{
		return java.util.Arrays.stream(UNLOCK_ICON).map(r -> r.replace('#', 'o')).toArray(String[]::new);
	}

	/** The top card's logo. The intro and outro leave it out of the card's picture and draw it separately. */
	private static class TitleLabel extends JLabel
	{
		/** Swing thread only. */
		static boolean hidden;

		TitleLabel()
		{
			super(new ImageIcon(NavIcon.title(TITLE_SCALE)));
			setHorizontalAlignment(SwingConstants.LEFT);
			setBorder(new EmptyBorder(2, 0, 4, 0));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			if (!hidden)
			{
				super.paintComponent(g);
			}
		}
	}

	// ---------------------------------------------------------------- Header

	private JComponent buildHeader()
	{
		JPanel card = card();
		card.add(titleRow());

		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.add(outlined("Shuffles", FontManager.getRunescapeBoldFont(), WHITE), BorderLayout.WEST);
		row.add(outlined(String.valueOf(data.getShuffles()), bigFont(), data.getShuffles() > 0 ? ORANGE : LOCKED), BorderLayout.EAST);
		card.add(row);

		int perShuffle = Rules.CLOG_SLOTS_PER_SHUFFLE;
		int slots = data.getCollectionLogSlots();
		if (slots == 0)
		{
			card.add(small("Open your Collection Log to sync.", YELLOW));
		}
		else
		{
			int toNext = perShuffle - slots % perShuffle;
			card.add(progressBar(slots % perShuffle, perShuffle, (slots % perShuffle) + " / " + perShuffle, ORANGE));
			card.add(small(toNext + " more log slots for the next Shuffle", ROLLABLE));
		}

		if (data.getShuffles() <= 0 || animation != null)
		{
			shuffleMenuOpen = false;
		}
		// The button stays put; the chooser slides open underneath it and slides shut again
		addSlot(card, "shuffle", data.getShuffles() > 0 ? buildShuffleButton() : null);
		addSlot(card, "shuffleMenu", shuffleMenuOpen ? buildShuffleMenu() : null);
		return card;
	}

	private JComponent buildShuffleButton()
	{
		JButton open = button("Use a Shuffle", ORANGE);
		open.setToolTipText("Spend 1 Shuffle to re-roll all of your skills, bosses or quests");
		// Stays in place (so the layout doesn't jump) but can't be used while a roll is being revealed,
		// or when no category has anything new to shuffle into
		boolean anyShufflable = !data.getShufflable().isEmpty();
		open.setEnabled(animation == null && anyShufflable);
		if (!anyShufflable)
		{
			open.setToolTipText("Nothing new left to shuffle into");
		}
		else if (shuffleMenuOpen)
		{
			open.setToolTipText("Close the Shuffle chooser");
		}
		open.addActionListener(e ->
		{
			shuffleMenuOpen = !shuffleMenuOpen;
			update(data);
		});
		return withTopGap(open);
	}

	/** Inline chooser: one button per category that has something to shuffle, plus Cancel. */
	private JComponent buildShuffleMenu()
	{
		JPanel menu = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
		menu.setOpaque(false);
		menu.add(small("Shuffle which category?", ROLLABLE));

		addShuffleChoice(menu, Category.SKILLS, data.getSkills());
		addShuffleChoice(menu, Category.BOSSES, data.getBosses());
		addShuffleChoice(menu, Category.QUESTS, data.getQuests());

		JButton cancel = button("Cancel", ROLLABLE);
		cancel.addActionListener(e ->
		{
			shuffleMenuOpen = false;
			update(data);
		});
		menu.add(cancel);
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(false);
		wrapper.setBorder(new EmptyBorder(4, 0, 0, 0));
		wrapper.add(menu, BorderLayout.CENTER);
		return wrapper;
	}

	private void addShuffleChoice(JPanel choices, Category category, List<PanelData.SlotView> slots)
	{
		JButton choice = button("Shuffle " + category.getPlural(), WHITE);
		boolean possible = data.getShufflable().contains(category);
		choice.setEnabled(possible);
		choice.setToolTipText(slots.isEmpty() ? "Roll your " + category.getPlural() + " first"
			: !possible ? "No new " + category.getPlural() + " left to shuffle into"
			: "Re-roll all of your " + category.getPlural() + " into new ones");
		choice.addActionListener(e ->
		{
			if (confirmShuffle(category))
			{
				shuffleMenuOpen = false;
				update(data);
			}
		});
		choices.add(choice);
	}

	private static JComponent withTopGap(JComponent component)
	{
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(false);
		wrapper.setBorder(new EmptyBorder(8, 0, 0, 0));
		wrapper.add(component, BorderLayout.CENTER);
		return wrapper;
	}

	// ---------------------------------------------------------------- Roll animation

	private boolean isAnimating(Category category, int slotIndex)
	{
		return animation != null && animation.getCategory() == category && animation.isAnimating(slotIndex);
	}

	/**
	 * Slot machine reveal for newly rolled skills. results are slot index to skill; 
	 * pool is what the reels spin through.
	 * onComplete runs once every reel has landed (it announces the result in chat).
	 */
	void animateSkillRoll(Map<Integer, Skill> results, List<Skill> pool, Runnable onComplete)
	{
		Map<Integer, RollAnimation.Item> targets = new HashMap<>();
		results.forEach((index, skill) -> targets.put(index, skillItem(skill)));
		List<RollAnimation.Item> candidates = pool.stream().map(this::skillItem).collect(Collectors.toList());
		// Each skill lights up in the skills tab as its reel lands
		startAnimation(Category.SKILLS, targets, candidates, result -> plugin.revealSkill(Skill.valueOf(result.getId())), onComplete);
	}

	void animateBossRoll(Map<Integer, Boss> results, List<Boss> pool, Runnable onComplete)
	{
		Map<Integer, RollAnimation.Item> targets = new HashMap<>();
		results.forEach((index, boss) -> targets.put(index, bossItem(boss)));
		List<RollAnimation.Item> candidates = pool.stream().map(this::bossItem).collect(Collectors.toList());
		startAnimation(Category.BOSSES, targets, candidates, result -> { }, onComplete);
	}

	void animateQuestRoll(Map<Integer, Quest> results, List<Quest> pool, Runnable onComplete)
	{
		Map<Integer, RollAnimation.Item> targets = new HashMap<>();
		results.forEach((index, quest) -> targets.put(index, questItem(quest)));
		List<RollAnimation.Item> candidates = pool.stream().map(this::questItem).collect(Collectors.toList());
		startAnimation(Category.QUESTS, targets, candidates, result -> { }, onComplete);
	}

	private void startAnimation(Category category, Map<Integer, RollAnimation.Item> targets, List<RollAnimation.Item> candidates,
		Consumer<RollAnimation.Item> onLand, Runnable onComplete)
	{
		finishAnimationNow();
		// The panel still holds the pre-roll data here (the rolled data arrives just after)
		frozenTiers = category == Category.BOSSES ? data.getTiers() : null;
		unrevealedBosses.clear();
		if (category == Category.BOSSES)
		{
			targets.values().forEach(item -> unrevealedBosses.add(item.getName()));
		}
		// Which slot each result belongs to, so the overlay can pop that slot in as its reel lands
		Map<String, Integer> slotOf = new HashMap<>();
		targets.forEach((index, item) -> slotOf.put(item.getId(), index));
		animation = new RollAnimation(category, targets, candidates, new RollAnimation.Listener()
		{
			@Override
			public void tick()
			{
				plugin.playSound(SoundEffectID.GE_INCREMENT_PLOP);
			}

			@Override
			public void land(RollAnimation.Item result)
			{
				onLand.accept(result);
				Integer slot = slotOf.get(result.getId());
				if (slot != null)
				{
					plugin.revealSlot(category, slot);
				}
				// Like the skills tab: the boss lights up in the pool (with a pop) the moment its reel lands
				if (unrevealedBosses.remove(result.getName()))
				{
					bossPops.put(result.getName(), System.currentTimeMillis());
					bossPopTimer.start();
					update(data);
				}
				plugin.playSound(SoundEffectID.GE_ADD_OFFER_DINGALING);
			}

			@Override
			public void finished()
			{
				animation = null;
				onAnimationComplete = null;
				frozenTiers = null;
				unrevealedBosses.clear();
				update(data);
				onComplete.run();
			}
		});
		onAnimationComplete = onComplete;
		update(data);
		animation.start();
	}

	private void stepBossPops()
	{
		long now = System.currentTimeMillis();
		bossPops.values().removeIf(at -> now - at >= FiveActiveOverlay.POP_MS);
		repaint();
		if (bossPops.isEmpty())
		{
			bossPopTimer.stop();
		}
	}

	// ---------------------------------------------------------------- Tier unlock ceremony

	/** One tier unlock: which tier, the order its bosses pop in, and when each step happens. */
	private static class TierCeremony
	{
		final int tier;
		final long start;
		/** How long the button shakes before Bosses slides shut. */
		final int chargeMs;
		/** Bosses that become rollable, in the (random) order they pop. */
		final List<String> order;
		/** Every boss in the tier (they all tremble; the ones still missing an unlock requirement just stay grey). */
		final Set<String> tierBosses;
		final Map<String, Long> popped = new HashMap<>();
		final Runnable onFinale;
		int nextPop;
		int boomsLeft;
		long nextBoomAt;
		boolean scrolled;
		boolean poolOpened;
		boolean charged;
		long finaleAt;
		boolean finaleDone;

		TierCeremony(int tier, List<String> order, Set<String> tierBosses, Runnable onFinale, int chargeMs)
		{
			this.tier = tier;
			this.chargeMs = chargeMs;
			this.start = System.currentTimeMillis();
			this.order = order;
			this.tierBosses = tierBosses;
			this.onFinale = onFinale;
			finaleAt = popAt(order.size() - 1) + FINALE_DELAY_MS;
		}

		/** The tier's icons start to tremble once the stage is cleared and the pool has slid open. */
		long trembleStart()
		{
			return start + chargeMs + FOLD_MS;
		}

		/** When the i-th boss pops (the first right after the trembling; -1 means "no pops"). */
		long popAt(int i)
		{
			long at = trembleStart() + TierFanfare.trembleMs(tier);
			for (int k = 0; k < i; k++)
			{
				float f = order.size() <= 2 ? 0f : k / (float) (order.size() - 2);
				at += Math.round(FIRST_POP_GAP_MS + (LAST_POP_GAP_MS - FIRST_POP_GAP_MS) * f);
			}
			return i < 0 ? trembleStart() + TierFanfare.trembleMs(tier) : at;
		}

		/** 0 to 1: how hard the tier's icons shake right now. */
		float trembleStrength(long now)
		{
			if (finaleDone || now < trembleStart())
			{
				return 0f;
			}
			return Math.min(1f, (now - trembleStart()) / (float) TierFanfare.trembleMs(tier));
		}
	}

	/**
	 * Plays the tier unlock: the Unlock button shakes and rumbles; 
	 * the Boss Pool opens on the tier; 
	 * its icons tremble harder and harder, then pop one by one, in random order, from grey into full colour with the green flash; 
	 * a beat after the last, the tier's header flashes gold and onFinale runs (chat, fireworks, banner).
	 * opened is the bosses that become rollable; the rest of the tier still needs a quest or Slayer level.
	 */
	void animateTierUnlock(int tier, List<String> opened, Runnable onFinale)
	{
		finishAnimationNow();
		List<String> order = new ArrayList<>(opened);
		java.util.Collections.shuffle(order);
		Set<String> tierBosses = new HashSet<>();
		BossTiers.TIERS.get(tier).getBosses().forEach(b -> tierBosses.add(b.getDisplayName()));
		// Clear the stage while the button charges: Quests, Skills and every other boss pool tier slide shut
		boolean closedOthers = foldShut(Section.QUESTS);
		closedOthers |= foldShut(Section.SKILLS);
		for (BossTiers.Tier other : BossTiers.TIERS)
		{
			if (BossTiers.TIERS.indexOf(other) != tier && collapsedTiers.add(other.getKillsRequired()))
			{
				foldsToAnimate.add(tierFoldKey(other.getKillsRequired()));
				closedOthers = true;
			}
		}
		saveCollapsed();
		// If anything had to slide shut, the button keeps rumbling a little longer while it goes
		ceremony = new TierCeremony(tier, order, tierBosses, onFinale, CHARGE_MS + (closedOthers ? EXTRA_CHARGE_MS : 0));
		plugin.playSound(TierFanfare.RUMBLE_SOUND);
		update(data);
		ceremonyTimer.start();
	}

	private void stepCeremony()
	{
		TierCeremony c = ceremony;
		long now = System.currentTimeMillis();
		if (c == null)
		{
			if (data.getPendingTier() < 0)
			{
				ceremonyTimer.stop();
			}
			repaint();
			return;
		}

		boolean changed = false;
		if (!c.scrolled && now >= c.start + c.chargeMs)
		{
			// The button has had its moment: Bosses slides shut while the Boss Pool and the tier being unlocked slide open, and all eyes go to it
			c.scrolled = true;
			c.poolOpened = true;
			foldShut(Section.BOSSES);
			scrollRectToVisible(new Rectangle(0, 0, 1, 1));
			foldOpen(Section.POOL);
			int milestone = BossTiers.TIERS.get(c.tier).getKillsRequired();
			if (collapsedTiers.remove(milestone))
			{
				foldsToAnimate.add(tierFoldKey(milestone));
			}
			saveCollapsed();
			changed = true;
		}
		c.charged |= c.scrolled;
		while (c.nextPop < c.order.size() && now >= c.popAt(c.nextPop))
		{
			c.popped.put(c.order.get(c.nextPop), now);
			c.nextPop++;
			plugin.playSound(TierFanfare.POP_SOUND);
			changed = true;
		}

		if (!c.finaleDone && now >= c.finaleAt)
		{
			c.finaleDone = true;
			plugin.playSound(TierFanfare.FINALE_SOUND);
			c.boomsLeft = TierFanfare.finaleBooms(c.tier) - 1;
			c.nextBoomAt = now + BOOM_GAP_MS;
			c.onFinale.run();
			// Bosses slides back open, with "Tier N unlocked!" where the button was, then the Roll button
			foldOpen(Section.BOSSES);
			saveCollapsed();
			changed = true;
		}

		if (c.boomsLeft > 0 && now >= c.nextBoomAt)
		{
			// Bigger tiers boom more than once
			plugin.playSound(TierFanfare.FINALE_SOUND);
			c.boomsLeft--;
			c.nextBoomAt = now + BOOM_GAP_MS;
		}

		if (c.finaleDone && c.boomsLeft == 0 && now >= c.finaleAt + BANNER_MS)
		{
			ceremony = null;
			changed = true;
		}
		if (changed)
		{
			update(data);
		}
		// Sliding sections change height every frame
		relayoutFolds();
		repaint();
	}

	/** Slides a section shut. False if it was already shut. */
	private boolean foldShut(Section section)
	{
		if (collapsed.add(section))
		{
			foldsToAnimate.add(sectionFoldKey(section));
			return true;
		}
		return false;
	}

	private void foldOpen(Section section)
	{
		if (collapsed.remove(section))
		{
			foldsToAnimate.add(sectionFoldKey(section));
		}
	}

	private static String sectionFoldKey(Section section)
	{
		return "S:" + section;
	}

	private static String tierFoldKey(int milestone)
	{
		return "T:" + milestone;
	}

	/** Where a section or tier card is in its slide. */
	private static class Fold
	{
		boolean open;
		float from;
		int to;
		long start;
		/** Picture of the card as it was before sliding shut, shown while it closes. */
		BufferedImage closing;
		/** The card from the last build (for its height and picture). */
		JComponent last;

		float height(long now)
		{
			float t = Math.max(0f, Math.min(1f, (now - start) / (float) FOLD_MS));
			float eased = t * t * (3f - 2f * t); // smoothstep
			return from + (to - from) * eased;
		}

		boolean moving(long now)
		{
			return from != to && now - start < FOLD_MS;
		}
	}

	/**
	 * Wraps a section or tier card so it can slide open and shut: the card is shown from the top, clipped to a height that eases between closed and open. 
	 * Only animates when the ceremony asked for it (see foldsToAnimate); 
	 * anything else (a user's click, new data) just shows the card at its size.
	 */
	private JComponent fold(String key, boolean open, JComponent card)
	{
		long now = System.currentTimeMillis();
		int target = card.getPreferredSize().height;
		Fold f = folds.get(key);
		if (f == null)
		{
			f = new Fold();
			f.open = open;
			f.from = target;
			f.to = target;
			folds.put(key, f);
		}
		else if (f.open != open && foldsToAnimate.remove(key))
		{
			f.from = f.moving(now) ? f.height(now) : (f.last != null && f.last.getHeight() > 0 ? f.last.getHeight() : target);
			f.closing = open ? null : snapshot(f.last);
			f.to = target;
			f.start = now;
			f.open = open;
			if (!foldTimer.isRunning())
			{
				foldTimer.start();
			}
		}
		else if (f.open != open || !f.moving(now))
		{
			f.open = open;
			f.from = target;
			f.to = target;
			f.closing = null;
		}
		else
		{
			f.to = target;
		}
		f.last = card;
		FoldBox box = new FoldBox(f, card);
		foldBoxes.add(box);
		return box;
	}

	/** Re-measures every fold wrapper (and the cards around it), so slides show at every depth. */
	private void relayoutFolds()
	{
		foldBoxes.forEach(JComponent::revalidate);
		revalidate();
	}

	private static class FoldBox extends JPanel
	{
		private final Fold fold;
		private final JComponent card;
		private final JLabel closing;

		FoldBox(Fold fold, JComponent card)
		{
			super(null);
			this.fold = fold;
			this.card = card;
			setOpaque(false);
			add(card);
			closing = fold.closing == null ? null : new JLabel(new ImageIcon(fold.closing));
			if (closing != null)
			{
				closing.setVerticalAlignment(SwingConstants.TOP);
				add(closing);
			}
		}

		@Override
		public Dimension getPreferredSize()
		{
			long now = System.currentTimeMillis();
			int height = fold.moving(now) ? Math.round(fold.height(now)) : card.getPreferredSize().height;
			return new Dimension(card.getPreferredSize().width, height);
		}

		@Override
		public Dimension getMinimumSize()
		{
			return getPreferredSize();
		}

		@Override
		public void doLayout()
		{
			// While shutting, the old (open) card slides away; afterwards the real, closed card shows
			boolean showClosing = closing != null && fold.moving(System.currentTimeMillis());
			card.setVisible(!showClosing);
			card.setBounds(0, 0, getWidth(), card.getPreferredSize().height);
			if (closing != null)
			{
				closing.setVisible(showClosing);
				closing.setBounds(0, 0, getWidth(), fold.closing.getHeight());
			}
		}
	}

	/** Remembers which sections and boss pool tiers are collapsed, as if the user had clicked them. */
	private void saveCollapsed()
	{
		plugin.saveCollapsedSections(collapsed.isEmpty() ? "NONE" : collapsed.stream().map(Enum::name).collect(Collectors.joining(",")));
		plugin.saveCollapsedTiers(collapsedTiers.stream().sorted().map(String::valueOf).collect(Collectors.joining(",")));
	}

	/** 0 to 1 through the tier header's gold finale flash, or -1. */
	private float tierHeaderFlash(int tierIndex)
	{
		TierCeremony c = ceremony;
		if (c == null || c.tier != tierIndex || !c.finaleDone)
		{
			return -1f;
		}
		float t = (System.currentTimeMillis() - c.finaleAt) / (float) HEADER_FLASH_MS;
		return t >= 1f ? -1f : t;
	}

	/** Skips to the end of any running reveal (still announcing it), e.g. when another roll starts. */
	private void finishAnimationNow()
	{
		if (animation != null)
		{
			animation.cancel();
			animation = null;
			frozenTiers = null;
			unrevealedBosses.clear();
			Runnable complete = onAnimationComplete;
			onAnimationComplete = null;
			if (complete != null)
			{
				complete.run();
			}
		}
	}

	/** Stops any running reveal silently (plugin shutting down). */
	void cancelAnimation()
	{
		if (animation != null)
		{
			animation.cancel();
			animation = null;
			onAnimationComplete = null;
			frozenTiers = null;
			unrevealedBosses.clear();
		}
	}

	/** The boss pool to show: frozen at its pre-roll state while a boss roll is being revealed. */
	private List<PanelData.TierView> poolTiers()
	{
		if (ceremony != null && !ceremony.finaleDone)
		{
			return ceremonyTiers(ceremony);
		}
		if (animation == null || frozenTiers == null || unrevealedBosses.isEmpty())
		{
			return data.getTiers();
		}
		// Current pool, except bosses still spinning keep their pre-roll look. 
		// Replaced bosses switch off straight away; 
		// new ones light up as their reel lands.
		Map<String, PanelData.BossView> before = new HashMap<>();
		frozenTiers.forEach(t -> t.getBosses().forEach(b -> before.put(b.getName(), b)));
		List<PanelData.TierView> tiers = new ArrayList<>();
		for (PanelData.TierView tier : data.getTiers())
		{
			List<PanelData.BossView> bosses = new ArrayList<>();
			for (PanelData.BossView boss : tier.getBosses())
			{
				bosses.add(unrevealedBosses.contains(boss.getName()) && before.containsKey(boss.getName())
					? before.get(boss.getName())
					: boss);
			}
			tiers.add(new PanelData.TierView(tier.getKillsRequired(), tier.isUnlocked(), bosses));
		}
		return tiers;
	}

	/** Mid-ceremony: the tier stays locked, and its bosses stay grey until each one pops. */
	private List<PanelData.TierView> ceremonyTiers(TierCeremony c)
	{
		List<PanelData.TierView> tiers = new ArrayList<>();
		List<PanelData.TierView> real = data.getTiers();
		for (int i = 0; i < real.size(); i++)
		{
			PanelData.TierView tier = real.get(i);
			if (i != c.tier)
			{
				tiers.add(tier);
				continue;
			}
			List<PanelData.BossView> bosses = new ArrayList<>();
			for (PanelData.BossView boss : tier.getBosses())
			{
				bosses.add(c.popped.containsKey(boss.getName()) ? boss
					: new PanelData.BossView(boss.getName(), PanelData.BossStatus.LOCKED, null, boss.getIconSpriteId(), boss.getRequirements()));
			}
			tiers.add(new PanelData.TierView(tier.getKillsRequired(), false, bosses));
		}
		return tiers;
	}

	private RollAnimation.Item skillItem(Skill skill)
	{
		Image icon = skillIcon(skill).getImage();
		return new RollAnimation.Item(skill.name(), skill.getName(), () -> icon);
	}

	private RollAnimation.Item bossItem(Boss boss)
	{
		int spriteId = boss.getHiscore() == null ? -1 : boss.getHiscore().getSpriteId();
		return new RollAnimation.Item(boss.name(), boss.getDisplayName(), () ->
		{
			ImageIcon icon = bossIcon(spriteId);
			return icon == null ? null : icon.getImage();
		});
	}

	private RollAnimation.Item questItem(Quest quest)
	{
		return new RollAnimation.Item(quest.name(), quest.getName(), null);
	}

	// ---------------------------------------------------------------- Skills

	private JComponent buildSkills()
	{
		JPanel card = card();
		// Every rollable skill (every skill except Hitpoints) at 99
		boolean allMaxed = data.getMaxedSkills().size() == Skill.values().length - Rules.NEVER_ROLLED.size();
		String doneText = allMaxed ? "All skills maxed!" : "No skills left to roll";
		JLabel summary = data.isAllSkillsDone()
			? small(allMaxed ? "All maxed!" : "None left", GREEN)
			: slotSummary(data.getSkills(), data.getSkillsCanRoll());
		card.add(sectionHeader(Section.SKILLS, Category.SKILLS.getTitle(),
			data.isAllSkillsDone() ? "" : "Level each " + Rules.LEVELS_TO_COMPLETE_SKILL + " times", summary));
		if (collapsed.contains(Section.SKILLS))
		{
			forgetSlides(Category.SKILLS);
			return card;
		}
		if (data.isAllSkillsDone())
		{
			forgetSlides(Category.SKILLS);
			card.add(label(doneText, FontManager.getRunescapeBoldFont(), GREEN));
			addMaxedSkills(card);
			return card;
		}
		List<PanelData.SlotView> skills = data.getSkills();
		card.add(slotRows(Category.SKILLS, skills,
			i -> progressRow(skills.get(i), skillIcon(skills.get(i).getSkill()), Category.SKILLS, i)));
		addButtons(card, Category.SKILLS, data.getSkills(), data.getSkillsCanRoll());
		addMaxedSkills(card);
		return card;
	}

	/** Icons of skills at 99, which are permanently unlocked. Hover an icon for its name. */
	private void addMaxedSkills(JPanel card)
	{
		if (data.getMaxedSkills().isEmpty())
		{
			return;
		}
		JPanel box = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
		box.setOpaque(false);
		box.setBorder(new EmptyBorder(8, 0, 0, 0));
		box.add(small("Maxed, always active (" + data.getMaxedSkills().size() + ")", ROLLABLE));

		JPanel icons = new JPanel(new GridLayout(0, MAXED_ICONS_PER_ROW, 4, 4));
		icons.setOpaque(false);
		for (Skill skill : data.getMaxedSkills())
		{
			JLabel icon = new JLabel(skillIcon(skill));
			icon.setToolTipText(skill.getName() + " (99)");
			icons.add(icon);
		}
		// Pad the last row so icons stay left-aligned in their grid cells
		for (int i = data.getMaxedSkills().size(); i % MAXED_ICONS_PER_ROW != 0; i++)
		{
			icons.add(new JLabel());
		}
		box.add(icons);
		card.add(box);
	}

	// ---------------------------------------------------------------- Bosses

	private JComponent buildBosses()
	{
		JPanel card = card();
		JLabel summary = data.getPendingTier() >= 0 ? small("Tier " + (data.getPendingTier() + 1) + " ready!", GOLD)
			: data.getBossesCanRoll() > 0 ? readySummary(data.getBossesCanRoll())
			: small(String.format("%,d", data.getTotalKc()) + " total KC", ROLLABLE);
		card.add(sectionHeader(Section.BOSSES, Category.BOSSES.getTitle(), "Kill each " + Rules.KILLS_TO_COMPLETE_BOSS + " times", summary));
		if (collapsed.contains(Section.BOSSES))
		{
			forgetSlides(Category.BOSSES);
			return card;
		}
		List<PanelData.SlotView> bosses = data.getBosses();
		card.add(slotRows(Category.BOSSES, bosses,
			i -> progressRow(bosses.get(i), bossIcon(bosses.get(i).getIconSpriteId()), Category.BOSSES, i)));
		addButtons(card, Category.BOSSES, data.getBosses(), data.getBossesCanRoll());
		// Separated from the boss rows so it reads as its own block
		JPanel kcTracker = new JPanel(new BorderLayout());
		kcTracker.setOpaque(false);
		kcTracker.setBorder(new EmptyBorder(10, 0, 0, 0));
		kcTracker.add(buildKcTracker(), BorderLayout.CENTER);
		card.add(kcTracker);
		return card;
	}

	private JComponent buildKcTracker()
	{
		JPanel box = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
		box.setBackground(ROW);
		box.setBorder(new EmptyBorder(8, 8, 8, 8));

		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		JPanel titles = new JPanel(new DynamicGridLayout(0, 1, 0, 0));
		titles.setOpaque(false);
		titles.add(outlined("Total Boss KC", FontManager.getRunescapeBoldFont(), WHITE));
		titles.add(small(data.getKcMode() == FiveActiveConfig.KcMode.LIFETIME ? "lifetime" : "since mode start", ROLLABLE));
		row.add(titles, BorderLayout.WEST);
		row.add(outlined(String.format("%,d", data.getTotalKc()), bigFont(), ORANGE), BorderLayout.EAST);
		box.add(row);

		BossTiers.Tier next = data.getUnlockedTiers() < BossTiers.TIERS.size() ? BossTiers.TIERS.get(data.getUnlockedTiers()) : null;
		if (data.getPendingTier() >= 0)
		{
			// KC reached: a full gold bar until the tier is claimed
			box.add(progressBar(1, 1, "Tier " + (data.getPendingTier() + 1) + " ready to unlock!", GOLD));
			box.add(small("Unlock it above to roll new bosses", GOLD));
		}
		else if (next == null)
		{
			box.add(progressBar(1, 1, "All tiers unlocked!", GREEN));
		}
		else
		{
			int previous = previousMilestone(next.getKillsRequired());
			int into = data.getTotalKc() - previous;
			int span = next.getKillsRequired() - previous;
			box.add(progressBar(into, span, String.format("%,d / %,d", data.getTotalKc(), next.getKillsRequired()), ORANGE));
			int toGo = next.getKillsRequired() - data.getTotalKc();
			// Only the bosses whose quests and Slayer level are already done: those are the ones that will open up
			int count = (int) data.getTiers().get(data.getUnlockedTiers()).getBosses().stream()
				.filter(b -> b.getRequirements().stream().allMatch(PanelData.Requirement::isMet))
				.count();
			box.add(small(String.format("%,d", toGo) + " KC until " + count + " new " + Category.BOSSES.noun(count) + " unlock", ROLLABLE));
		}
		return box;
	}

	private static int previousMilestone(int milestone)
	{
		int previous = 0;
		for (BossTiers.Tier tier : BossTiers.TIERS)
		{
			if (tier.getKillsRequired() < milestone)
			{
				previous = Math.max(previous, tier.getKillsRequired());
			}
		}
		return previous;
	}

	/** Skill icon at the standard size (the large icons vary slightly, e.g. Sailing is 23px). Cached. */
	private ImageIcon skillIcon(Skill skill)
	{
		return skillIcons.computeIfAbsent(skill, s -> new ImageIcon(fitIcon(skillIconManager.getSkillImage(s, false), ICON_SIZE)));
	}

	/** Boss icon at the standard size, or null while it loads. */
	private ImageIcon bossIcon(int spriteId)
	{
		return bossIcon(spriteId, ICON_SIZE, false);
	}

	/**
	 * Boss icon fitted to size x size, optionally greyed out (locked). Null while the sprite is still loading;
	 * the panel redraws once it arrives.
	 */
	private ImageIcon bossIcon(int spriteId, int size, boolean faded)
	{
		BufferedImage sprite = bossSprite(spriteId);
		if (sprite == null)
		{
			return null;
		}
		String key = spriteId + ":" + size + ":" + faded;
		return scaledBossIcons.computeIfAbsent(key, k ->
		{
			BufferedImage image = fitIcon(sprite, size);
			if (faded)
			{
				image = ImageUtil.alphaOffset(ImageUtil.grayscaleImage(image), 0.45f);
			}
			return new ImageIcon(image);
		});
	}

	/**
	 * The boss's hiscores sprite (padding trimmed), or null. The first request loads it in the background;
	 * sprites that arrive together trigger a single redraw.
	 */
	private BufferedImage bossSprite(int spriteId)
	{
		if (spriteId < 0 || spriteManager == null)
		{
			return null;
		}
		if (!bossSprites.containsKey(spriteId))
		{
			bossSprites.put(spriteId, null); // mark as loading so it's only requested once
			spriteManager.getSpriteAsync(spriteId, 0, sprite -> SwingUtilities.invokeLater(() ->
			{
				bossSprites.put(spriteId, crop(sprite));
				if (!iconRefreshQueued)
				{
					iconRefreshQueued = true;
					SwingUtilities.invokeLater(() ->
					{
						iconRefreshQueued = false;
						update(data);
					});
				}
			}));
		}
		return bossSprites.get(spriteId);
	}

	/** Starts loading every pool boss icon, so they are ready before any roll or pool view needs them. */
	private void preloadBossIcons()
	{
		for (PanelData.TierView tier : data.getTiers())
		{
			for (PanelData.BossView boss : tier.getBosses())
			{
				bossSprite(boss.getIconSpriteId());
			}
		}
	}

	/** Crops transparent padding so every boss fills its icon square the same way. */
	private static BufferedImage crop(BufferedImage image)
	{
		int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				if ((image.getRGB(x, y) >>> 24) != 0)
				{
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}
		return maxX < 0 ? image : image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
	}

	/** Centres the image in a size x size square at its native resolution. */
	private static BufferedImage fitIcon(BufferedImage image, int size)
	{
		// Never scale pixel art up (that blurs it): centre it at its native size, only shrinking oversized images
		if (image.getWidth() > size || image.getHeight() > size)
		{
			image = ImageUtil.resizeImage(image, size, size, true);
		}
		return ImageUtil.resizeCanvas(image, size, size);
	}

	// ---------------------------------------------------------------- Quests

	private JComponent buildQuests()
	{
		JPanel card = card();
		JLabel summary = data.isAllQuestsDone()
			? small("All complete!", GREEN)
			: slotSummary(data.getQuests(), data.getQuestsCanRoll());
		card.add(sectionHeader(Section.QUESTS, Category.QUESTS.getTitle(), data.isAllQuestsDone() ? "" : "Complete to replace", summary));
		if (collapsed.contains(Section.QUESTS))
		{
			forgetSlides(Category.QUESTS);
			return card;
		}
		if (data.isAllQuestsDone())
		{
			forgetSlides(Category.QUESTS);
			card.add(label("All quests complete!", FontManager.getRunescapeBoldFont(), GREEN));
			return card;
		}
		List<PanelData.SlotView> quests = data.getQuests();
		card.add(slotRows(Category.QUESTS, quests, i -> questRow(quests.get(i), i)));
		addButtons(card, Category.QUESTS, data.getQuests(), data.getQuestsCanRoll());
		return card;
	}

	private JComponent questRow(PanelData.SlotView slot, int index)
	{
		FlashRow row = row();
		flashOnCompletion(row, Category.QUESTS, slot);
		row.setLayout(new BorderLayout(6, 0));
		Long fadeStart = badgeFades.get(badgeKey(Category.QUESTS, index, slot));

		// The NEW! spot on the right is always reserved (empty when there's no badge
		// the name always wraps to the width that leaves room for it, so rows never resize when NEW! comes or goes
		JPanel text = new JPanel(new DynamicGridLayout(0, 1, 0, 1));
		text.setOpaque(false);
		text.add(wrapped(html(slot.getName()), FontManager.getRunescapeFont(), slot.isDone() ? GREEN : WHITE, TEXT_WIDTH - 12));
		if (fadeStart != null && !slot.isDone())
		{
			row.add(badgeFade(newBadgeSpace(), fadeStart), BorderLayout.EAST);
		}
		else if (slot.isFresh() && !slot.isDone())
		{
			row.add(newBadge(), BorderLayout.EAST);
		}
		else
		{
			row.add(newBadgeSpace(), BorderLayout.EAST);
		}

		String status;
		Color color;
		if (slot.isDone())
		{
			status = "Complete!";
			color = GREEN;
		}
		else if (slot.getQuestState() == QuestState.IN_PROGRESS)
		{
			status = "In progress";
			color = YELLOW;
		}
		else
		{
			status = "Not started";
			color = ROLLABLE;
		}
		JLabel statusLabel = small(status, color);
		text.add(statusLabel);

		// Quest names take one or two lines. Every row is as tall as a two-line one (text centred), 
		// so rows don't change height as quests are rolled and replaced.
		JPanel centred = new JPanel(new GridBagLayout());
		centred.setOpaque(false);
		GridBagConstraints c = new GridBagConstraints();
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		centred.add(text, c);
		Dimension natural = text.getPreferredSize();
		centred.setPreferredSize(new Dimension(natural.width, Math.max(natural.height, questTextHeight())));
		row.add(centred, BorderLayout.CENTER);
		if (slot.isDone())
		{
			markDone(row);
		}
		return row;
	}

	/** Height of a quest row's text: a two-line name plus the status line. */
	private static int questTextHeight()
	{
		return questNameHeight() + 1 + small("A", WHITE).getPreferredSize().height;
	}

	private static int questNameHeight()
	{
		if (questNameHeight == 0)
		{
			questNameHeight = wrapped("A<br>A", FontManager.getRunescapeFont(), WHITE, TEXT_WIDTH - 12).getPreferredSize().height;
		}
		return questNameHeight;
	}

	// ---------------------------------------------------------------- Boss pool

	private JComponent buildBossPool()
	{
		JPanel card = card();

		long rollable = poolTiers().stream()
			.flatMap(t -> t.getBosses().stream())
			.filter(b -> b.getStatus() == PanelData.BossStatus.ROLLABLE)
			.count();
		JComponent header = sectionHeader(Section.POOL, "Boss Pool", rollable + " rollable", small(rollable + " rollable", ROLLABLE));
		header.setToolTipText("Every boss that can ever be rolled, grouped by when it unlocks");
		card.add(header);
		if (collapsed.contains(Section.POOL))
		{
			return card;
		}

		boolean icons = plugin.getConfig().bossPoolStyle() == FiveActiveConfig.BossPoolStyle.ICONS;
		// The icon grid explains itself (outlined = active, greyed = locked); only the list view needs a legend
		if (!icons)
		{
			JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
			legend.setOpaque(false);
			legend.add(small("Active", ACTIVE));
			legend.add(small("Rollable", ROLLABLE));
			legend.add(small("Locked", LOCKED));
			card.add(legend);
		}

		List<PanelData.TierView> tiers = poolTiers();
		for (int i = 0; i < tiers.size(); i++)
		{
			PanelData.TierView tier = tiers.get(i);
			if (icons)
			{
				int milestone = tier.getKillsRequired();
				card.add(fold(tierFoldKey(milestone), !collapsedTiers.contains(milestone), tierCard(tier, i + 1)));
				continue;
			}
			card.add(tierHeader(tier));
			for (PanelData.BossView boss : tier.getBosses())
			{
				card.add(poolRow(boss, tier.isUnlocked()));
			}
		}
		return card;
	}

	// Compact milestone for tier headers: 100, 500, 1k, 2.5k. Just a space issue, maybe a revisit in the future?
	private static String shortKc(int kc)
	{
		if (kc < 1000)
		{
			return String.valueOf(kc);
		}
		String thousands = String.format("%.1f", kc / 1000.0);
		return (thousands.endsWith(".0") ? thousands.substring(0, thousands.length() - 2) : thousands) + "k";
	}

	/** Icons style: one card per tier with a header and a grid of boss icons. Hover an icon for its name. */
	private JComponent tierCard(PanelData.TierView tier, int number)
	{
		int milestone = tier.getKillsRequired();
		boolean isCollapsed = collapsedTiers.contains(milestone);

		// Padding lives on the header and grid (not the card) so the header's hover band spans the full width
		JPanel tierCard = new JPanel(new DynamicGridLayout(0, 1, 0, 0));
		tierCard.setBackground(ROW);
		tierCard.setBorder(new EmptyBorder(0, 0, isCollapsed ? 0 : 7, 0));

		int tierIndex = number - 1;
		if (ceremony != null && ceremony.tier == tierIndex)
		{
			ceremonyCard = tierCard;
		}
		JPanel header = new JPanel(new BorderLayout())
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				super.paintComponent(g);
				float t = tierHeaderFlash(tierIndex);
				if (t >= 0f)
				{
					// The finale: a gold band springs open across the header and fades
					float spring = spring(t, 1.70158f);
					int fw = Math.round(getWidth() * (0.4f + 0.6f * spring));
					int fh = Math.round(getHeight() * (0.4f + 0.6f * spring));
					g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), Math.round(200 * (1f - t))));
					g.fillRect((getWidth() - fw) / 2, (getHeight() - fh) / 2, fw, fh);
				}
			}
		};
		header.setOpaque(false);
		header.setBackground(ROW_HOVER);
		header.setBorder(new EmptyBorder(6, 6, 6, 6));
		String when = tier.getKillsRequired() == 0 ? "Start" : shortKc(tier.getKillsRequired()) + " KC";
		JLabel title = label("<html>TIER " + number + " <font color='#a5a5a5'>&middot; " + when + "</font></html>",
			FontManager.getRunescapeBoldFont(), tier.isUnlocked() ? ORANGE : LOCKED);
		if (!tier.isUnlocked())
		{
			// Drawn 2px high: the label centres it on the font's full height, which includes room below the letters
			title.setIcon(new PixelIcon(PADLOCK, ROLLABLE, 2));
			title.setIconTextGap(5);
		}
		header.add(title, BorderLayout.WEST);
		String status;
		Color statusColor;
		if (tier.isUnlocked())
		{
			long available = tier.getBosses().stream().filter(b -> b.getStatus() != PanelData.BossStatus.LOCKED).count();
			status = available + "/" + tier.getBosses().size() + " available";
			statusColor = ROLLABLE;
		}
		else if (data.getTotalKc() >= tier.getKillsRequired())
		{
			// KC reached, waiting to be claimed
			status = "Ready!";
			statusColor = GOLD;
		}
		else
		{
			status = String.format("%,d", tier.getKillsRequired() - data.getTotalKc()) + " to go";
			statusColor = LOCKED;
		}
		header.add(small(status, statusColor), BorderLayout.EAST);
		tierCard.add(header);

		// The whole header toggles the tier; no extra icon, just a hover highlight and hand cursor.
		// Only the header lights up, so the boss icons keep their contrast.
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		header.setToolTipText(isCollapsed ? "Click to expand" : "Click to collapse");
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				header.setOpaque(true);
				header.repaint();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				header.setOpaque(false);
				tierCard.repaint();
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				if (!collapsedTiers.remove(milestone))
				{
					collapsedTiers.add(milestone);
				}
				plugin.saveCollapsedTiers(collapsedTiers.stream().sorted().map(String::valueOf).collect(Collectors.joining(",")));
				// Tiers slide open and shut, like the sections
				foldsToAnimate.add(tierFoldKey(milestone));
				update(data);
			}
		});
		if (isCollapsed)
		{
			return tierCard;
		}

		JPanel grid = new JPanel(new GridLayout(0, POOL_ICONS_PER_ROW, POOL_TILE_GAP, POOL_TILE_GAP));
		grid.setOpaque(false);
		for (PanelData.BossView boss : tier.getBosses())
		{
			grid.add(new BossTile(boss, tier.isUnlocked()));
		}
		for (int i = tier.getBosses().size(); i % POOL_ICONS_PER_ROW != 0; i++)
		{
			grid.add(new JLabel()); // keep the last row left-aligned
		}
		// Left-align the grid instead of stretching it across the card
		JPanel gridWrap = new JPanel(new BorderLayout());
		gridWrap.setOpaque(false);
		gridWrap.setBorder(new EmptyBorder(0, 6, 0, 6));
		gridWrap.add(grid, BorderLayout.WEST);
		tierCard.add(gridWrap);
		return tierCard;
	}

	/**
	 * One boss in the icon grid. Active: raised tile with an orange outline. 
	 * Rollable: full colour. Locked: greyed out.
	 * The tooltip gives the name and, when locked, why.
	 */
	private class BossTile extends JComponent
	{
		private final PanelData.BossView boss;
		private final ImageIcon icon;

		BossTile(PanelData.BossView boss, boolean tierUnlocked)
		{
			this.boss = boss;
			boolean locked = boss.getStatus() == PanelData.BossStatus.LOCKED;
			this.icon = bossIcon(boss.getIconSpriteId(), POOL_ICON_SIZE, locked);
			Dimension size = new Dimension(POOL_TILE_SIZE, POOL_TILE_SIZE);
			setPreferredSize(size);
			setMinimumSize(size);
			// Every quest / Slayer requirement, ticked green when met and crossed red when not, so it's clear
			// what's left (and, for rollable bosses, what it took). The tier's KC is on the tier header.
			StringBuilder requirements = new StringBuilder();
			for (PanelData.Requirement requirement : boss.getRequirements())
			{
				requirements.append("<br>")
					.append(requirement.isMet() ? "<font color='#37f046'>✔</font> " : "<font color='#e61e1e'>✘</font> ")
					.append(html(requirement.getText()));
			}
			String state = boss.getStatus() == PanelData.BossStatus.ACTIVE ? " <font color='#dc8a00'>(Active)</font>" : "";
			setToolTipText("<html>" + html(boss.getName()) + state + requirements + "</html>");
			keepTooltipOnClick(this);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int w = getWidth();
			int h = getHeight();
			boolean active = boss.getStatus() == PanelData.BossStatus.ACTIVE;
			// Active bosses sit on a raised tile with a thin orange outline
			g2.setColor(active ? RAISED : TILE);
			g2.fillRoundRect(0, 0, w, h, TILE_ARC, TILE_ARC);
			TierCeremony c = ceremony;
			Long poppedAt = c == null ? null : c.popped.get(boss.getName());
			if (poppedAt != null)
			{
				// Just unlocked: the green completion flash springs open behind the icon
				float t = (System.currentTimeMillis() - poppedAt) / (float) FiveActiveOverlay.COMPLETE_POP_MS;
				if (t < 1f)
				{
					float spring = spring(t, 1.70158f);
					int pw = Math.round(w * (0.3f + 0.7f * spring));
					int ph = Math.round(h * (0.3f + 0.7f * spring));
					g2.setColor(new Color(GREEN.getRed(), GREEN.getGreen(), GREEN.getBlue(), Math.round(210 * (1f - t))));
					g2.fillRoundRect((w - pw) / 2, (h - ph) / 2, pw, ph, TILE_ARC, TILE_ARC);
				}
			}
			int shakeX = 0;
			int shakeY = 0;
			if (c != null && poppedAt == null && c.tierBosses.contains(boss.getName()))
			{
				// Waiting to unlock: trembles harder and harder until it pops
				int amount = Math.round(TierFanfare.shakePx(c.tier) * c.trembleStrength(System.currentTimeMillis()));
				if (amount > 0)
				{
					shakeX = tremble.nextInt(amount * 2 + 1) - amount;
					shakeY = tremble.nextInt(amount * 2 + 1) - amount;
				}
			}
			Long popAt = bossPops.get(boss.getName());
			if (popAt != null)
			{
				// The reveal pop, as in the overlay: an orange box springs open behind the icon and fades out
				float t = Math.min(1f, (System.currentTimeMillis() - popAt) / (float) FiveActiveOverlay.POP_MS);
				float spring = spring(t, 1.70158f);
				int pw = Math.round(w * (0.3f + 0.7f * spring));
				int ph = Math.round(h * (0.3f + 0.7f * spring));
				g2.setColor(new Color(ACTIVE.getRed(), ACTIVE.getGreen(), ACTIVE.getBlue(), Math.round(200 * (1f - t))));
				g2.fillRoundRect((w - pw) / 2, (h - ph) / 2, pw, ph, TILE_ARC, TILE_ARC);
			}
			if (icon != null)
			{
				icon.paintIcon(this, g2, (w - icon.getIconWidth()) / 2 + shakeX, (h - icon.getIconHeight()) / 2 + shakeY);
			}
			if (active)
			{
				g2.setColor(ACTIVE);
				g2.setStroke(new BasicStroke(1f));
				g2.drawRoundRect(0, 0, w - 1, h - 1, TILE_ARC, TILE_ARC);
			}
			g2.dispose();
		}
	}

	/**
	 * Swing hides a tooltip whenever the mouse is pressed. 
	 * Bring it straight back afterwards, so clicking a tile you're hovering doesn't make its name disappear.
	 */
	private static void keepTooltipOnClick(JComponent component)
	{
		component.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				// Runs after the tooltip manager has hidden the tip for this press
				SwingUtilities.invokeLater(() ->
				{
					ToolTipManager tooltips = ToolTipManager.sharedInstance();
					int delay = tooltips.getInitialDelay();
					tooltips.setInitialDelay(0);
					tooltips.mouseEntered(new MouseEvent(component, MouseEvent.MOUSE_ENTERED, e.getWhen(), 0, e.getX(), e.getY(), 0, false));
					tooltips.mouseMoved(new MouseEvent(component, MouseEvent.MOUSE_MOVED, e.getWhen(), 0, e.getX(), e.getY(), 0, false));
					tooltips.setInitialDelay(delay);
				});
			}
		});
	}

	private JComponent tierHeader(PanelData.TierView tier)
	{
		String text = tier.getKillsRequired() == 0
			? "Starting bosses"
			: "Unlocks at " + String.format("%,d", tier.getKillsRequired()) + " KC";
		JLabel label = label("-- " + text + " --", FontManager.getRunescapeSmallFont(), tier.isUnlocked() ? ORANGE : LOCKED);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setBorder(new EmptyBorder(6, 0, 1, 0));
		return label;
	}

	private JComponent poolRow(PanelData.BossView boss, boolean tierUnlocked)
	{
		Color color;
		switch (boss.getStatus())
		{
			case ACTIVE:
				color = ACTIVE;
				break;
			case ROLLABLE:
				color = ROLLABLE;
				break;
			default:
				color = LOCKED;
		}

		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(0, 6, 0, 0));
		boolean active = boss.getStatus() == PanelData.BossStatus.ACTIVE;
		row.add(label(boss.getName(), active ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeFont(), color), BorderLayout.WEST);
		// Tier-locked bosses are explained by the tier header; only show requirement locks inline
		if (boss.getReason() != null && tierUnlocked)
		{
			row.add(wrapped(html(boss.getReason()), FontManager.getRunescapeSmallFont(), LOCKED), BorderLayout.SOUTH);
		}
		if (boss.getReason() != null)
		{
			row.setToolTipText(boss.getReason());
		}
		return row;
	}

	// ---------------------------------------------------------------- Shared pieces

	/**
	 * Clickable section header: [arrow] Title ... right text.
	 * The right text is the subtitle while expanded, and a short summary while collapsed so the key info stays visible.
	 */
	private JComponent sectionHeader(Section section, String title, String subtitle, JLabel collapsedSummary)
	{
		boolean isCollapsed = collapsed.contains(section);

		JPanel left = new JPanel(new BorderLayout(5, 0));
		left.setOpaque(false);
		left.add(new Arrow(isCollapsed), BorderLayout.WEST);
		left.add(outlined(title, FontManager.getRunescapeBoldFont(), WHITE), BorderLayout.CENTER);

		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.setBorder(new EmptyBorder(0, 0, isCollapsed ? 0 : 2, 0));
		header.add(left, BorderLayout.WEST);
		header.add(isCollapsed ? collapsedSummary : small(subtitle, ROLLABLE), BorderLayout.EAST);
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (!collapsed.remove(section))
				{
					collapsed.add(section);
				}
				String saved = collapsed.stream().map(Enum::name).collect(Collectors.joining(","));
				// Saved as NONE rather than empty, which RuneLite would treat as unset (bringing back the default)
				plugin.saveCollapsedSections(saved.isEmpty() ? "NONE" : saved);
				foldsToAnimate.add(sectionFoldKey(section));
				update(data);
			}
		});
		return header;
	}

	/** Restores collapsed sections from config, ignoring names that no longer exist. */
	private void loadCollapsed(String saved)
	{
		for (String name : saved.split(","))
		{
			for (Section section : Section.values())
			{
				if (section.name().equals(name.trim()))
				{
					collapsed.add(section);
				}
			}
		}
	}

	/** Collapsed summary for a slot section: how many are ready to roll, otherwise how many are active. */
	private static JLabel slotSummary(List<PanelData.SlotView> slots, int canRoll)
	{
		if (canRoll > 0)
		{
			return readySummary(canRoll);
		}
		long active = slots.stream().filter(s -> !s.isDone()).count();
		return small(slots.isEmpty() ? "Not rolled" : active + " active", ROLLABLE);
	}

	private static JLabel readySummary(int canRoll)
	{
		return small(canRoll + " ready to roll", GREEN);
	}

	/** If the slot shows a NEW badge, clicking anywhere on its row clears it. */
	private JComponent dismissNewOnClick(JComponent row, PanelData.SlotView slot, Category category, int index)
	{
		if (!slot.isFresh() || slot.isDone())
		{
			return row;
		}
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.setToolTipText("Click to clear NEW!");
		MouseAdapter listener = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				plugin.requestDismissNew(category, index);
			}
		};
		// Child labels would otherwise swallow the click, so listen on the whole row
		addMouseListenerDeep(row, listener);
		return row;
	}

	private static void addMouseListenerDeep(Component component, MouseAdapter listener)
	{
		component.addMouseListener(listener);
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				addMouseListenerDeep(child, listener);
			}
		}
	}

	/**
	 * A skill/boss row: [icon] name ... progress pips (or DONE). 
	 * Boss names wrap to at most two lines, and every boss row is as tall as a two-line one,
	 * so rows don't change height as bosses are rolled and replaced.
	 */
	private JComponent progressRow(PanelData.SlotView slot, ImageIcon icon, Category category, int index)
	{
		FlashRow row = row();
		flashOnCompletion(row, category, slot);
		row.setLayout(new BorderLayout(ROW_GAP, 0));

		Color color = slot.isDone() ? GREEN : WHITE;
		JLabel nameLabel = category == Category.BOSSES
			? wrapped(html(slot.getName()), FontManager.getRunescapeFont(), color, BOSS_NAME_WIDTH)
			: label("<html>" + html(slot.getName()) + "</html>", FontManager.getRunescapeFont(), color);
		if (icon != null)
		{
			nameLabel.setIcon(icon);
		}
		nameLabel.setIconTextGap(ICON_GAP);
		if (category == Category.BOSSES)
		{
			// Blank icon space while a boss icon is still loading, so the name doesn't shift when it arrives
			if (icon == null)
			{
				nameLabel.setIcon(blankIcon());
			}
			Dimension size = nameLabel.getPreferredSize();
			nameLabel.setPreferredSize(new Dimension(size.width, Math.max(size.height, questNameHeight())));
		}
		row.add(nameLabel, BorderLayout.CENTER);

		if (slot.isDone())
		{
			row.add(label("DONE", FontManager.getRunescapeBoldFont(), GREEN), BorderLayout.EAST);
			markDone(row);
		}
		else if (slot.isFresh())
		{
			// A new slot has no progress yet, so NEW! takes the progress spot (where the roll reveal's NEW! lands)
			row.add(newBadge(), BorderLayout.EAST);
		}
		else
		{
			JPanel right = new JPanel(new BorderLayout(3, 0));
			right.setOpaque(false);
			right.add(new Pips(slot.getProgress(), slot.getGoal()), BorderLayout.CENTER);
			right.add(label(slot.getProgress() + "/" + slot.getGoal(), FontManager.getRunescapeFont(), WHITE), BorderLayout.EAST);
			Long fadeStart = badgeFades.get(badgeKey(category, index, slot));
			row.add(fadeStart != null ? badgeFade(right, fadeStart) : right, BorderLayout.EAST);
		}
		return row;
	}

	// ---------------------------------------------------------------- NEW! badge swap-out

	private static String badgeKey(Category category, int index, PanelData.SlotView slot)
	{
		return category + ":" + index + ":" + slot.getName();
	}

	/** Starts a fade for every row that had NEW! in the old data and doesn't any more (dismissed or timed out). */
	private void detectClearedBadges(PanelData before, PanelData after)
	{
		if (!before.isLoggedIn() || !after.isLoggedIn())
		{
			return;
		}
		long now = System.currentTimeMillis();
		detectClearedBadges(Category.SKILLS, before.getSkills(), after.getSkills(), now);
		detectClearedBadges(Category.BOSSES, before.getBosses(), after.getBosses(), now);
		detectClearedBadges(Category.QUESTS, before.getQuests(), after.getQuests(), now);
		if (!badgeFades.isEmpty() && !badgeFadeTimer.isRunning())
		{
			badgeFadeTimer.start();
		}
	}

	private void detectClearedBadges(Category category, List<PanelData.SlotView> before, List<PanelData.SlotView> after, long now)
	{
		for (int i = 0; i < Math.min(before.size(), after.size()); i++)
		{
			PanelData.SlotView was = before.get(i);
			PanelData.SlotView is = after.get(i);
			if (was.getName().equals(is.getName()) && was.isFresh() && !is.isFresh() && !is.isDone())
			{
				badgeFades.putIfAbsent(badgeKey(category, i, is), now);
			}
		}
	}

	private JComponent badgeFade(JComponent to, long start)
	{
		BadgeSwap swap = new BadgeSwap(newBadge(), to, start);
		badgeFadeRows.add(swap);
		return swap;
	}

	private void stepBadgeFades()
	{
		long now = System.currentTimeMillis();
		if (badgeFades.values().removeIf(start -> now - start >= BADGE_SWAP_MS))
		{
			// A fade just finished: rebuild so the real components (progress squares) replace the picture
			update(data);
		}
		badgeFadeRows.forEach(JComponent::repaint);
		if (badgeFades.isEmpty())
		{
			badgeFadeTimer.stop();
		}
	}

	/**
	 * Swaps the NEW! badge for the row's normal right-hand side with a reel-style scroll: NEW! scrolls up and out
	 * while the progress squares scroll up in from below, a full row apart so they never overlap. Nothing is
	 * faded. Both are drawn right-aligned, so the end state lines up exactly with the real component.
	 */
	private static class BadgeSwap extends JComponent
	{
		private final JComponent from;
		private final JComponent to;
		private final long start;
		private BufferedImage fromImage;
		private BufferedImage toImage;

		BadgeSwap(JComponent from, JComponent to, long start)
		{
			this.from = from;
			this.to = to;
			this.start = start;
			Dimension a = from.getPreferredSize();
			Dimension b = to.getPreferredSize();
			Dimension size = new Dimension(Math.max(a.width, b.width), Math.max(a.height, b.height));
			setPreferredSize(size);
			setMinimumSize(size);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			if (fromImage == null)
			{
				fromImage = snapshot(from);
				toImage = snapshot(to);
			}
			float t = Math.max(0f, Math.min(1f, (System.currentTimeMillis() - start) / (float) BADGE_SWAP_MS));
			float eased = t * t * (3f - 2f * t); // smoothstep: eases in and out
			Graphics2D g2 = (Graphics2D) g.create();
			int w = getWidth();
			int h = getHeight();
			int offset = Math.round(eased * h);
			g2.clipRect(0, 0, w, h);
			g2.drawImage(fromImage, w - fromImage.getWidth(), (h - fromImage.getHeight()) / 2 - offset, null);
			g2.drawImage(toImage, w - toImage.getWidth(), (h - toImage.getHeight()) / 2 + h - offset, null);
			g2.dispose();
		}

		private static BufferedImage snapshot(JComponent component)
		{
			Dimension size = component.getPreferredSize();
			int w = Math.max(1, size.width);
			int h = Math.max(1, size.height);
			component.setSize(w, h);
			layoutTree(component);
			BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = image.createGraphics();
			component.printAll(g);
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
	}

	/** Roll button: free, fills completed/empty slots. Shuffles live in the header card. */
	private void addButtons(JPanel card, Category category, List<PanelData.SlotView> slots, int canRoll)
	{
		if (category == Category.BOSSES && (ceremony != null || data.getPendingTier() >= 0))
		{
			addSlot(card, rollSlot(category), tierButtonArea());
			return;
		}
		JButton button = null;
		if (animation != null && animation.getCategory() == category)
		{
			button = new PrimaryButton("Rolling...");
			button.setEnabled(false);
		}
		else if (canRoll > 0)
		{
			String text = slots.isEmpty()
				? "Roll your " + category.getPlural() + "!"
				: "Roll " + canRoll + " new " + category.noun(canRoll);
			button = new PrimaryButton(text);
			button.addActionListener(e -> plugin.requestRoll(category, false));
		}

		JComponent content = null;
		if (button != null)
		{
			JPanel buttons = new JPanel(new BorderLayout());
			buttons.setOpaque(false);
			buttons.setBorder(new EmptyBorder(4, 0, 0, 0));
			buttons.add(button, BorderLayout.CENTER);
			content = buttons;
		}
		addSlot(card, rollSlot(category), content);
	}

	/**
	 * The Bosses button area around a tier unlock: the pulsing gold Unlock button while a tier waits; the same
	 * button shaking as it charges once clicked; "Unlocking..." while the pool opens up; then the "Tier N
	 * unlocked!" banner for a moment before the Roll button returns.
	 */
	private JComponent tierButtonArea()
	{
		TierCeremony c = ceremony;
		JComponent content;
		if (c != null && c.finaleDone)
		{
			JLabel banner = outlined("Tier " + (c.tier + 1) + " unlocked!", FontManager.getRunescapeBoldFont().deriveFont(20f), GOLD);
			banner.setHorizontalAlignment(SwingConstants.CENTER);
			content = banner;
		}
		else
		{
			int tier = c != null ? c.tier : data.getPendingTier();
			PrimaryButton button = new PrimaryButton(c != null && System.currentTimeMillis() >= c.start + c.chargeMs
				? "Unlocking..." : "Unlock Tier " + (tier + 1) + "!", GOLD, UNLOCK_ICON);
			if (c == null)
			{
				button.pulse = true;
				button.addActionListener(e ->
				{
					if (ceremony == null)
					{
						plugin.requestUnlockTier();
					}
				});
				// Keep the pulse moving while the button waits
				ceremonyTimer.start();
			}
			else
			{
				long start = c.start;
				int charge = c.chargeMs;
				button.shake = () ->
				{
					long t = System.currentTimeMillis() - start;
					if (t >= charge)
					{
						return 0;
					}
					// A fast rattle that builds up as it charges
					float amount = 1f + 3f * t / charge;
					return Math.round((float) Math.sin(t / 18.0) * amount);
				};
			}
			content = button;
		}
		JPanel area = new JPanel(new BorderLayout());
		area.setOpaque(false);
		area.setBorder(new EmptyBorder(4, 0, 0, 0));
		area.add(content, BorderLayout.CENTER);
		return area;
	}

	/** Open padlock on the Unlock button. */
	private static final String[] UNLOCK_ICON = {
		"..######..",
		".##....##.",
		".##....##.",
		".##.......",
		".##.......",
		"##########",
		"##########",
		"####..####",
		"####..####",
		"##########",
		"##########",
	};

	private static String rollSlot(Category category)
	{
		return category + ":roll";
	}

	/** Forgets a category's row and button slides, so the section shows up in place next time (no slide). */
	private void forgetSlides(Category category)
	{
		slides.keySet().removeIf(key -> key.startsWith(category + ":"));
	}

	/**
	 * The category's five row positions. Before the first roll each shows an empty placeholder the size of a real
	 * row, so the section already has its final shape and the roll fills it in place. Positions left without a
	 * slot (fewer than five were available) close up smoothly.
	 */
	private JComponent slotRows(Category category, List<PanelData.SlotView> slots, IntFunction<JComponent> rowFor)
	{
		JPanel rows = new JPanel(new DynamicGridLayout(0, 1, 0, 0));
		rows.setOpaque(false);
		for (int i = 0; i < Rules.SLOTS; i++)
		{
			JComponent row = null;
			if (i < slots.size())
			{
				JComponent real = rowFor.apply(i);
				// The reel takes the real row's size and fades into it, so nothing jumps when it's swapped out
				row = isAnimating(category, i)
					? animation.createRow(i, real)
					: dismissNewOnClick(real, slots.get(i), category, i);
			}
			else if (slots.isEmpty())
			{
				row = placeholderRow(category);
			}
			// The gap between rows lives inside each slide, so a closed position takes no space
			rows.add(slide(category + ":row" + i, row, i == 0 ? 0 : CARD_GAP));
		}
		return rows;
	}

	/** An empty row exactly as tall as a real one of the category. */
	private static JComponent placeholderRow(Category category)
	{
		JPanel row = row();
		row.setLayout(new BorderLayout());
		JLabel text = label("Empty slot", FontManager.getRunescapeFont(), LOCKED);
		if (category == Category.QUESTS)
		{
			text.setPreferredSize(new Dimension(text.getPreferredSize().width, questTextHeight()));
		}
		else
		{
			// Blank icon-sized space, so it lines up with (and is as tall as) the skill and boss rows
			text.setIcon(blankIcon());
			text.setIconTextGap(ICON_GAP);
			if (category == Category.BOSSES)
			{
				text.setPreferredSize(new Dimension(text.getPreferredSize().width, questNameHeight()));
			}
		}
		row.add(text, BorderLayout.CENTER);
		return row;
	}

	private static ImageIcon blankIcon()
	{
		return new ImageIcon(new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB));
	}

	// ---------------------------------------------------------------- Sliding button areas

	/**
	 * Adds a button area under everything already in the card that slides open when its content appears, closes
	 * when it goes away, and eases between heights when it changes (e.g. the Shuffle chooser opening). content is
	 * null when there's nothing to show. The card's gap above the area is inside the slide, so a closed area takes
	 * no space at all.
	 */
	private void addSlot(JPanel card, String key, JComponent content)
	{
		JPanel body = new JPanel(new DynamicGridLayout(0, 1, 0, CARD_GAP));
		body.setOpaque(false);
		for (Component child : card.getComponents())
		{
			body.add(child);
		}
		JPanel stack = new JPanel(new BorderLayout());
		stack.setOpaque(false);
		stack.add(body, BorderLayout.CENTER);
		stack.add(slide(key, content), BorderLayout.SOUTH);
		card.add(stack);
	}

	private Slide slide(String key, JComponent content)
	{
		return slide(key, content, CARD_GAP);
	}

	private Slide slide(String key, JComponent content, int gapAbove)
	{
		long now = System.currentTimeMillis();
		JComponent padded = null;
		int target = 0;
		if (content != null)
		{
			padded = new JPanel(new BorderLayout());
			padded.setOpaque(false);
			padded.setBorder(new EmptyBorder(gapAbove, 0, 0, 0));
			padded.add(content, BorderLayout.CENTER);
			target = padded.getPreferredSize().height;
		}

		SlideState state = slides.get(key);
		if (state == null || show == Show.INTRO)
		{
			// First time on screen (or just expanded), or the intro is revealing everything anyway: no slide
			state = new SlideState();
			state.from = target;
			state.to = target;
			slides.put(key, state);
		}
		else if (state.to != target)
		{
			if (target == 0)
			{
				// The old buttons are gone from the data, so slide a picture of them out
				state.exitImage = snapshot(state.lastContent);
			}
			state.from = state.height(now);
			state.to = target;
			state.start = now;
		}

		JComponent shown;
		if (padded != null)
		{
			state.lastContent = padded;
			state.exitImage = null;
			shown = padded;
		}
		else
		{
			state.lastContent = null;
			shown = state.exitImage == null ? null : new JLabel(new ImageIcon(state.exitImage));
		}

		Slide slide = new Slide(state, shown);
		slideRows.add(slide);
		if (state.isMoving(now) && !slideTimer.isRunning())
		{
			slideTimer.start();
		}
		return slide;
	}

	private void stepSlides()
	{
		long now = System.currentTimeMillis();
		boolean moving = false;
		for (Slide slide : slideRows)
		{
			slide.revalidate();
			slide.repaint();
			moving |= slide.state.isMoving(now);
		}
		// The last tick above already laid everything out at its final height
		if (!moving)
		{
			slideTimer.stop();
		}
	}

	/** Picture of a laid-out component, or null if it was never on screen. */
	private static BufferedImage snapshot(JComponent component)
	{
		if (component == null || component.getWidth() <= 0 || component.getHeight() <= 0)
		{
			return null;
		}
		BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		component.printAll(g);
		g.dispose();
		return image;
	}

	/** Where a button area's height is heading, and from where. */
	private static class SlideState
	{
		float from;
		int to;
		long start;
		/** The content shown on the last build, to take a picture of if it goes away. */
		JComponent lastContent;
		/** Picture of the content that went away, shown while the area closes. */
		BufferedImage exitImage;

		float height(long now)
		{
			float t = Math.max(0f, Math.min(1f, (now - start) / (float) SLIDE_MS));
			float eased = t * t * (3f - 2f * t); // smoothstep
			return from + (to - from) * eased;
		}

		boolean isMoving(long now)
		{
			return from != to && now - start < SLIDE_MS;
		}
	}

	/**
	 * Shows its content clipped to the current animated height. While the area is shorter than the content, the
	 * content sits against the bottom edge, so it slides down into view (and back up out of it). When the area is
	 * taller (a bigger chooser just swapped for a small button), the content stays at the top and the spare space
	 * below closes up.
	 */
	private static class Slide extends JPanel
	{
		private final SlideState state;
		private final JComponent content;

		Slide(SlideState state, JComponent content)
		{
			super(null);
			this.state = state;
			this.content = content;
			setOpaque(false);
			if (content != null)
			{
				add(content);
			}
		}

		@Override
		public Dimension getPreferredSize()
		{
			// At least 1 wide: the grid layouts scale widths against the widest child, and 0 would collapse them
			int width = content == null ? 1 : Math.max(1, content.getPreferredSize().width);
			return new Dimension(width, Math.round(state.height(System.currentTimeMillis())));
		}

		@Override
		public Dimension getMinimumSize()
		{
			return getPreferredSize();
		}

		@Override
		public void doLayout()
		{
			if (content != null)
			{
				int full = content.getPreferredSize().height;
				content.setBounds(0, Math.min(0, getHeight() - full), getWidth(), full);
			}
		}
	}

	private boolean confirmShuffle(Category category)
	{
		int choice = JOptionPane.showConfirmDialog(this,
			"Spend 1 Shuffle to re-roll all of your " + category.getPlural() + "?\n"
				+ "Progress on your current " + category.getPlural() + " will be lost.\n\n"
				+ "Shuffles left after this: " + (data.getShuffles() - 1),
			"Shuffle " + category.getPlural(),
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE);
		if (choice != JOptionPane.YES_OPTION)
		{
			return false;
		}
		plugin.requestRoll(category, true);
		return true;
	}

	private static JPanel card()
	{
		JPanel card = new JPanel(new DynamicGridLayout(0, 1, 0, 3));
		card.setBackground(CARD);
		card.setBorder(new EmptyBorder(8, 8, 8, 8));
		return card;
	}

	private static FlashRow row()
	{
		FlashRow row = new FlashRow();
		row.setBackground(ROW);
		row.setBorder(new EmptyBorder(5, 6, 5, 6));
		return row;
	}

	/**
	 * A row that can play the green completion flash behind its contents: a box springs open past the row and
	 * settles, fading out, like the overlay and skills tab.
	 */
	private static class FlashRow extends JPanel
	{
		/** How far through the flash the row is (0 to 1), or below 0 when it isn't flashing. */
		private java.util.function.Supplier<Float> flash;

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			float t = flash == null ? -1f : flash.get();
			if (t < 0f)
			{
				return;
			}
			float spring = spring(t, 1.70158f);
			int w = Math.round(getWidth() * (0.5f + 0.5f * spring));
			int h = Math.round(getHeight() * (0.5f + 0.5f * spring));
			g.setColor(new Color(GREEN.getRed(), GREEN.getGreen(), GREEN.getBlue(), Math.round(150 * (1f - t))));
			g.fillRect((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
		}
	}

	/** Makes the row flash green when its slot is finished, and keeps the panel repainting while it does. */
	private void flashOnCompletion(FlashRow row, Category category, PanelData.SlotView slot)
	{
		row.flash = () -> plugin.completionPop(category, slot.getName());
		if (plugin.completionPop(category, slot.getName()) >= 0f && !completionTimer.isRunning())
		{
			completionTimer.start();
		}
	}

	/** Marks a finished row with a green bar down its left edge (same overall padding as a normal row). */
	private static void markDone(JPanel row)
	{
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, DONE_BAR_WIDTH, 0, 0, GREEN),
			new EmptyBorder(5, 6 - DONE_BAR_WIDTH + 2, 5, 6)));
	}

	/**
	 * The main call to action (Roll): a solid orange button with dark bold text, so it stands out from
	 * the dark panel. Brightens on hover, darkens while pressed, greys out when disabled.
	 */
	private static class PrimaryButton extends JButton
	{
		private static final Color TEXT = new Color(30, 30, 30);
		private static final Color HOVER = new Color(245, 160, 20);
		private static final Color PRESSED = new Color(185, 115, 0);
		private static final Color DISABLED = new Color(110, 75, 20);
		private static final Color OUTLINE = new Color(12, 12, 12);
		private static final int ARC = 6;
		/** Offset of the hard shadow block under the button. */
		private static final int SHADOW = 2;
		/** Pixel die (five face): '#' is the die body, '.' inside are the pips. */
		private static final String[] DIE = {
			".##########.",
			"############",
			"##..####..##",
			"##..####..##",
			"############",
			"#####..#####",
			"#####..#####",
			"############",
			"##..####..##",
			"##..####..##",
			"############",
			".##########.",
		};
		private static final int DIE_GAP = 6;

		private final Color face;
		private final String[] icon;
		/** Sideways shake in pixels (the tier unlock's charge), or null. */
		java.util.function.IntSupplier shake;
		/** Gently brightens and dims, to draw the eye (a tier waiting to be unlocked). */
		boolean pulse;

		PrimaryButton(String text)
		{
			this(text, ORANGE, DIE);
		}

		PrimaryButton(String text, Color face, String[] icon)
		{
			super(text);
			this.face = face;
			this.icon = icon;
			setFont(FontManager.getRunescapeBoldFont());
			setForeground(TEXT);
			setFocusable(false);
			setContentAreaFilled(false);
			setBorderPainted(false);
			setOpaque(false);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		}

		private static Color mix(Color a, Color b, float t)
		{
			return new Color(
				Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
				Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
				Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
		}

		private static Color scale(Color c, float f)
		{
			return new Color(Math.round(c.getRed() * f), Math.round(c.getGreen() * f), Math.round(c.getBlue() * f));
		}

		@Override
		public Dimension getPreferredSize()
		{
			// Natural width (the layout stretches it across the card), a little taller than a normal button
			return new Dimension(super.getPreferredSize().width, 26 + SHADOW);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int w = getWidth();
			int h = getHeight();
			boolean pressed = isEnabled() && getModel().isPressed();
			Color base = !isEnabled() ? scale(face, 0.43f)
				: pressed ? scale(face, 0.73f)
				: getModel().isRollover() ? mix(face, Color.WHITE, 0.12f)
				: face;
			if (pulse && isEnabled() && !pressed)
			{
				float glow = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 260.0);
				base = mix(base, Color.WHITE, 0.25f * glow);
			}
			if (shake != null)
			{
				g2.translate(shake.getAsInt(), 0);
			}

			int bw = w - SHADOW;
			int bh = h - SHADOW;
			// Pressing slides the button onto its shadow, so the click is visible
			int off = pressed ? SHADOW : 0;
			if (isEnabled())
			{
				g2.setColor(OUTLINE);
				g2.fillRoundRect(SHADOW, SHADOW, bw, bh, ARC + 2, ARC + 2);
			}

			// Flat face with a crisp dark outline, so it reads as a button rather than a label
			g2.setColor(OUTLINE);
			g2.fillRoundRect(off, off, bw, bh, ARC + 2, ARC + 2);
			g2.setColor(base);
			g2.fillRoundRect(off + 1, off + 1, bw - 2, bh - 2, ARC, ARC);

			g2.setFont(getFont());
			FontMetrics fm = g2.getFontMetrics();
			String text = getText();
			int dieWidth = icon[0].length() + DIE_GAP;
			int x = off + (bw - fm.stringWidth(text) - dieWidth) / 2;
			int y = off + (bh - fm.getHeight()) / 2 + fm.getAscent();
			Color ink = isEnabled() ? TEXT : new Color(40, 30, 15);
			// Pixel die before the label: an icon makes it read as a button, and says "roll"
			int dy = off + (bh - icon.length) / 2;
			for (int row = 0; row < icon.length; row++)
			{
				for (int col = 0; col < icon[row].length(); col++)
				{
					if (icon[row].charAt(col) == '#')
					{
						g2.setColor(ink);
						g2.fillRect(x + col, dy + row, 1, 1);
					}
				}
			}
			x += dieWidth;
			g2.setColor(ink);
			g2.drawString(text, x, y);
			g2.dispose();
		}
	}

	private static JButton button(String text, Color color)
	{
		JButton button = new JButton(text);
		button.setFont(FontManager.getRunescapeBoldFont());
		button.setForeground(color);
		button.setFocusable(false);
		return button;
	}

	private static JComponent progressBar(int value, int max, String center, Color color)
	{
		return new Bar(value, max, center, color);
	}

	/** Progress bar with bold, shadowed text so the numbers are easy to read on stream. */
	private static class Bar extends JComponent
	{
		private static final int HEIGHT = 20;
		private static final Color TRACK = ColorScheme.DARK_GRAY_HOVER_COLOR;

		private final float fraction;
		private final String text;
		private final Color color;

		Bar(int value, int max, String text, Color color)
		{
			this.fraction = max <= 0 ? 0f : Math.max(0f, Math.min(1f, value / (float) max));
			this.text = text;
			this.color = color;
			setPreferredSize(new Dimension(0, HEIGHT));
			setMinimumSize(new Dimension(0, HEIGHT));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int w = getWidth();
			int h = getHeight();
			g2.setColor(TRACK);
			g2.fillRoundRect(0, 0, w, h, 4, 4);
			int filled = Math.round(w * fraction);
			if (filled > 0)
			{
				g2.setClip(0, 0, filled, h);
				g2.setColor(color);
				g2.fillRoundRect(0, 0, w, h, 4, 4);
				g2.setClip(null);
			}
			g2.setFont(FontManager.getRunescapeBoldFont());
			FontMetrics fm = g2.getFontMetrics();
			int x = (w - fm.stringWidth(text)) / 2;
			int y = (h - fm.getHeight()) / 2 + fm.getAscent();
			// Solid 1px black outline (the text drawn at every neighbouring offset), then white on top,
			// so the numbers stay readable over both the orange fill and the dark track
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
			g2.dispose();
		}
	}

	/** Plain text with a 1px black outline, for titles and big numbers that need to read clearly on stream. */
	private static JLabel outlined(String text, Font font, Color color)
	{
		JLabel label = new OutlinedLabel(text);
		label.setFont(font);
		label.setForeground(color);
		return label;
	}

	/**
	 * Draws its (single-colour, non-HTML) text with a solid 1px black outline: the text in black at every
	 * neighbouring offset, then the real colour on top. The same technique as the progress bar numbers.
	 */
	private static class OutlinedLabel extends JLabel
	{
		OutlinedLabel(String text)
		{
			super(text);
		}

		@Override
		public Dimension getPreferredSize()
		{
			// Room for the outline on every side
			Dimension size = super.getPreferredSize();
			return new Dimension(size.width + 2, size.height + 2);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setFont(getFont());
			FontMetrics fm = g2.getFontMetrics();
			String text = getText();
			int x = getHorizontalAlignment() == SwingConstants.RIGHT || getHorizontalAlignment() == SwingConstants.TRAILING
				? getWidth() - fm.stringWidth(text) - 1
				: 1;
			int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
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
			g2.setColor(getForeground());
			g2.drawString(text, x, y);
			g2.dispose();
		}
	}

	private static JLabel label(String text, Font font, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(font);
		label.setForeground(color);
		return label;
	}

	private static JLabel small(String text, Color color)
	{
		return label(text, FontManager.getRunescapeSmallFont(), color);
	}

	/** Label whose (already HTML-escaped) text wraps instead of being cut off. */
	private static JLabel wrapped(String htmlText, Font font, Color color)
	{
		return wrapped(htmlText, font, color, TEXT_WIDTH);
	}

	private static JLabel wrapped(String htmlText, Font font, Color color, int width)
	{
		return label("<html><body style='width:" + width + "px'>" + htmlText + "</body></html>", font, color);
	}

	/** Bold orange NEW! for freshly rolled rows, matching the one the roll reveal pops in. */
	private static JLabel newBadge()
	{
		return label("NEW!", FontManager.getRunescapeBoldFont(), ORANGE);
	}

	/** Empty space the size of the NEW! badge, so rows keep the same layout without it. */
	private static JComponent newBadgeSpace()
	{
		JPanel space = new JPanel();
		space.setOpaque(false);
		space.setPreferredSize(newBadge().getPreferredSize());
		return space;
	}

	private static Font bigFont()
	{
		return FontManager.getRunescapeBoldFont().deriveFont(24f);
	}

	private static String html(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** Small triangle: pointing right when collapsed, down when expanded. */
	private static class Arrow extends JComponent
	{
		private static final int SIZE = 7;

		private final boolean collapsed;

		Arrow(boolean collapsed)
		{
			this.collapsed = collapsed;
			Dimension size = new Dimension(SIZE, SIZE);
			setPreferredSize(size);
			setMinimumSize(size);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(ORANGE);
			int x = 0;
			int y = (getHeight() - SIZE) / 2;
			Polygon triangle = collapsed
				? new Polygon(new int[]{x + 1, x + SIZE - 1, x + 1}, new int[]{y, y + SIZE / 2, y + SIZE - 1}, 3)
				: new Polygon(new int[]{x, x + SIZE - 1, x + SIZE / 2}, new int[]{y + 1, y + 1, y + SIZE - 1}, 3);
			g2.fill(triangle);
			g2.dispose();
		}
	}

	/** Orange diamond bullet with a black edge. 'o' is the colour, 'k' black. */
	private static final String[] DIAMOND = {
		"...k...",
		"..kok..",
		".koook.",
		"koooook",
		".koook.",
		"..kok..",
		"...k...",
	};

	/** Padlock in front of a locked tier's title: a chunky pixel lock with a 2px shackle and a keyhole. */
	private static final String[] PADLOCK = {
		"...ooooo...",
		"..ooooooo..",
		".ooo...ooo.",
		".oo.....oo.",
		".oo.....oo.",
		".oo.....oo.",
		"ooooooooooo",
		"ooooooooooo",
		"oooo...oooo",
		"oooo...oooo",
		"ooooo.ooooo",
		"ooooo.ooooo",
		"ooooooooooo",
		".ooooooooo.",
	};

	/** Pixel art from a pattern: 'o' in the given colour, 'k' black, anything else clear. */
	private static class PixelIcon implements javax.swing.Icon
	{
		private final String[] pattern;
		private final Color color;
		/** Pixels to draw it higher than where it's placed, to line it up with a label's letters. */
		private final int raise;

		PixelIcon(String[] pattern, Color color)
		{
			this(pattern, color, 0);
		}

		PixelIcon(String[] pattern, Color color, int raise)
		{
			this.pattern = pattern;
			this.color = color;
			this.raise = raise;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			for (int row = 0; row < pattern.length; row++)
			{
				for (int col = 0; col < pattern[row].length(); col++)
				{
					char ch = pattern[row].charAt(col);
					if (ch == 'o' || ch == 'k' || ch == 'l' || ch == 'd')
					{
						// 'l' and 'd' are lighter and darker shades of the colour
						g.setColor(ch == 'o' ? color
							: ch == 'l' ? PrimaryButton.mix(color, Color.WHITE, 0.25f)
							: ch == 'd' ? PrimaryButton.scale(color, 0.78f)
							: Color.BLACK);
						g.fillRect(x + col, y + row - raise, 1, 1);
					}
				}
			}
		}

		@Override
		public int getIconWidth()
		{
			return pattern[0].length();
		}

		@Override
		public int getIconHeight()
		{
			// Reported shorter by the raise, so it's centred as if it sat that much higher
			return pattern.length - raise;
		}
	}

	/** Row of small squares, one per level/kill needed. Filled squares are progress made. */
	private static class Pips extends JComponent
	{
		private static final int SIZE = 7;
		private static final int GAP = 2;

		private final int filled;
		private final int total;

		Pips(int filled, int total)
		{
			this.filled = filled;
			this.total = total;
			Dimension size = new Dimension(total * SIZE + (total - 1) * GAP, SIZE + 2);
			setPreferredSize(size);
			setMinimumSize(size);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int y = (getHeight() - SIZE) / 2;
			for (int i = 0; i < total; i++)
			{
				g2.setColor(i < filled ? GREEN : ColorScheme.MEDIUM_GRAY_COLOR);
				g2.fillRoundRect(i * (SIZE + GAP), y, SIZE, SIZE, 2, 2);
			}
			g2.dispose();
		}
	}
}
