package com.bswmagicgaming.fiveactive;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import lombok.Value;
import net.runelite.api.Quest;
import net.runelite.api.Skill;

/**
 * Finds the skill, boss or quest a player typed for ::fiveactive set. Skills and bosses need their exact name (any
 * case); quests ignore case and punctuation, and a unique start of a quest's name is enough. Shorthand (the lists
 * below) works for all three.
 */
final class NameLookup
{
	/** How many possible quests are listed when a name could mean several. */
	private static final int MAX_LISTED = 6;

	static final Map<String, String> SKILL_SHORTHAND = ImmutableMap.<String, String>builder()
		.put("att", "Attack")
		.put("str", "Strength")
		.put("def", "Defence")
		.put("range", "Ranged")
		.put("mage", "Magic")
		.put("pray", "Prayer")
		.put("rc", "Runecraft")
		.put("con", "Construction")
		.put("cons", "Construction")
		.put("agil", "Agility")
		.put("herb", "Herblore")
		.put("thiev", "Thieving")
		.put("craft", "Crafting")
		.put("fletch", "Fletching")
		.put("slay", "Slayer")
		.put("hunt", "Hunter")
		.put("mine", "Mining")
		.put("smith", "Smithing")
		.put("fish", "Fishing")
		.put("cook", "Cooking")
		.put("fm", "Firemaking")
		.put("wc", "Woodcutting")
		.put("farm", "Farming")
		.put("sail", "Sailing")
		.build();

	static final Map<String, String> BOSS_SHORTHAND = ImmutableMap.<String, String>builder()
		.put("cox", "Chambers of Xeric")
		.put("raids 1", "Chambers of Xeric")
		.put("tob", "Theatre of Blood")
		.put("raids 2", "Theatre of Blood")
		.put("toa", "Tombs of Amascut")
		.put("raids 3", "Tombs of Amascut")
		.put("kq", "Kalphite Queen")
		.put("kbd", "King Black Dragon")
		.put("dks", "Dagannoth Kings")
		.put("corp", "Corporeal Beast")
		.put("mole", "Giant Mole")
		.put("sire", "Abyssal Sire")
		.put("cerb", "Cerberus")
		.put("thermy", "Thermonuclear Smoke Devil")
		.put("hydra", "Alchemical Hydra")
		.put("zily", "Commander Zilyana")
		.put("sara", "Commander Zilyana")
		.put("graardor", "General Graardor")
		.put("bandos", "General Graardor")
		.put("kree", "Kree'arra")
		.put("kreearra", "Kree'arra")
		.put("arma", "Kree'arra")
		.put("kril", "K'ril Tsutsaroth")
		.put("zammy", "K'ril Tsutsaroth")
		.put("vetion", "Vet'ion")
		.put("calvarion", "Calvar'ion")
		.put("crazy arch", "Crazy Archaeologist")
		.put("deranged arch", "Deranged Archaeologist")
		.put("chaos ele", "Chaos Elemental")
		.put("fanatic", "Chaos Fanatic")
		.put("jad", "Fight Caves")
		.put("caves", "Fight Caves")
		.put("zuk", "Inferno")
		.put("colo", "Fortis Colosseum")
		.put("colosseum", "Fortis Colosseum")
		.put("gg", "Grotesque Guardians")
		.put("grotesques", "Grotesque Guardians")
		.put("gauntlet", "The Gauntlet")
		.put("huey", "The Hueycoatl")
		.put("hueycoatl", "The Hueycoatl")
		.put("levi", "The Leviathan")
		.put("leviathan", "The Leviathan")
		.put("nightmare", "The Nightmare")
		.put("whisperer", "The Whisperer")
		.put("duke", "Duke Sucellus")
		.put("muspah", "Phantom Muspah")
		.put("moons", "Perilous Moons")
		.put("doom", "Doom of Mokhaiotl")
		.put("titans", "Royal Titans")
		.put("wt", "Wintertodt")
		.put("todt", "Wintertodt")
		.put("temp", "Tempoross")
		.put("vork", "Vorkath")
		.put("zul", "Zulrah")
		.put("amox", "Amoxliatl")
		.put("gryphon", "Shellbane Gryphon")
		.build();

	static final Map<String, String> QUEST_SHORTHAND = ImmutableMap.<String, String>builder()
		.put("ds1", "Dragon Slayer I")
		.put("ds2", "Dragon Slayer II")
		.put("mm1", "Monkey Madness I")
		.put("mm2", "Monkey Madness II")
		.put("dt1", "Desert Treasure I")
		.put("dt2", "Desert Treasure II - The Fallen Empire")
		.put("me1", "Mourning's End Part I")
		.put("me2", "Mourning's End Part II")
		.put("ft1", "Fairytale I - Growing Pains")
		.put("ft2", "Fairytale II - Cure a Queen")
		.put("ew1", "Elemental Workshop I")
		.put("ew2", "Elemental Workshop II")
		.put("rag1", "Rag and Bone Man I")
		.put("rag2", "Rag and Bone Man II")
		.put("ma1", "Mage Arena I")
		.put("ma2", "Mage Arena II")
		.put("sote", "Song of the Elves")
		.put("sotf", "Sins of the Father")
		.put("sotn", "Secrets of the North")
		.put("wgs", "While Guthix Sleeps")
		.put("akd", "A Kingdom Divided")
		.put("bcs", "Beneath Cursed Sands")
		.put("rm", "Rune Mysteries")
		.put("pip", "Priest in Peril")
		.put("hftd", "Horror from the Deep")
		.put("up", "Underground Pass")
		.put("tgt", "The Grand Tree")
		.put("tgv", "Tree Gnome Village")
		.put("fremmy trials", "The Fremennik Trials")
		.put("fremmy isles", "The Fremennik Isles")
		.put("fremmy exiles", "The Fremennik Exiles")
		.put("lunar", "Lunar Diplomacy")
		.put("dm", "Dream Mentor")
		.put("ham", "Another Slice of H.A.M.")
		.build();

	/** What a lookup found: the one match, or the possibilities (several), or neither (no match). */
	@Value
	static class Result<T>
	{
		T match;
		List<String> options;
		/** More matched than are listed in options. */
		int more;

		static <T> Result<T> of(T match)
		{
			return new Result<>(match, Collections.emptyList(), 0);
		}

		static <T> Result<T> none()
		{
			return new Result<>(null, Collections.emptyList(), 0);
		}
	}

	static Result<Skill> skill(String typed)
	{
		return exact(typed, SKILL_SHORTHAND, Skill.values(), Skill::getName);
	}

	static Result<Boss> boss(String typed)
	{
		return exact(typed, BOSS_SHORTHAND, Boss.values(), Boss::getDisplayName);
	}

	static Result<Quest> quest(String typed)
	{
		String shorthand = QUEST_SHORTHAND.get(clean(typed));
		if (shorthand != null)
		{
			typed = shorthand;
		}
		String wanted = questKey(typed);
		if (wanted.isEmpty())
		{
			return Result.none();
		}
		List<Quest> starts = new ArrayList<>();
		for (Quest quest : Quest.values())
		{
			String key = questKey(quest.getName());
			if (key.equals(wanted))
			{
				return Result.of(quest);
			}
			if (key.startsWith(wanted))
			{
				starts.add(quest);
			}
		}
		if (starts.size() == 1)
		{
			return Result.of(starts.get(0));
		}
		List<String> names = new ArrayList<>();
		for (int i = 0; i < Math.min(MAX_LISTED, starts.size()); i++)
		{
			names.add(starts.get(i).getName());
		}
		return new Result<>(null, names, Math.max(0, starts.size() - MAX_LISTED));
	}

	/** Skills and bosses: shorthand, or the exact name in any case. */
	private static <T> Result<T> exact(String typed, Map<String, String> shorthand, T[] all, Function<T, String> name)
	{
		String wanted = clean(typed);
		String full = shorthand.getOrDefault(wanted, wanted);
		for (T value : all)
		{
			if (name.apply(value).equalsIgnoreCase(full))
			{
				return Result.of(value);
			}
		}
		return Result.none();
	}

	/** Lower case, single spaces, trimmed. */
	private static String clean(String typed)
	{
		return typed == null ? "" : typed.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	/**
	 * A quest name without case or punctuation, so "cooks assistant" is "Cook's Assistant". "&" counts as "and",
	 * and numbers 1 to 3 as I to III ("dragon slayer 2" is "Dragon Slayer II").
	 */
	static String questKey(String name)
	{
		String words = clean(name).replace("&", " and ")
			.replaceAll("\\b1\\b", "i").replaceAll("\\b2\\b", "ii").replaceAll("\\b3\\b", "iii");
		return words.replaceAll("[^a-z0-9]", "");
	}

	private NameLookup()
	{
	}
}
