package com.bswmagicgaming.fiveactive;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Filepath;

/**
 * The run's history log and its backups, kept as plain files per account in the plugin's data folder
 * (.runelite/plugin-data/five-active/), beside (not in) RuneLite's settings: the log grows all run long,
 * and appending a line to a file is cheaper and safer than rewriting one ever-growing setting.
 * It also means a run survives its settings being lost or reset.
 *
 * <ul>
 * <li>history.jsonl: one {@link HistoryEntry} per line, only ever appended to.</li>
 * <li>backups/: copies of the saved run (not the log) from each login, the newest {@link #MAX_BACKUPS} kept.</li>
 * </ul>
 * A save code is the run and its log together, compressed into one line of text to copy somewhere safe.
 *
 * The files are only ever touched on this class's own background thread, one job at a time and in order, so
 * the game never waits on the disk. The log's entries are kept in memory too, for the panel.
 */
@Slf4j
@Singleton
class RunHistory
{
	private static final String HISTORY_FILE = "history.jsonl";
	private static final String BACKUP_DIR = "backups";
	private static final String SHARE_DIR = "share";
	private static final int MAX_BACKUPS = 30;
	private static final String CODE_PREFIX = "FA1:";
	private static final String FILE_TIME = "yyyy-MM-dd_HH-mm-ss";

	private final Gson gson;
	private final ClientThread clientThread;
	/** The plugin's data folder, set on start up. */
	private Filepath root;
	/** Does the file work, in order. Null while the plugin is off. */
	private ExecutorService disk;
	/** The loaded account's folder, or null when logged out. */
	private volatile Filepath folder;
	/** Bumped on every load and unload, so a slow load for an account that's since gone is dropped. */
	private int generation;
	private final List<HistoryEntry> entries = new CopyOnWriteArrayList<>();

	@Inject
	RunHistory(Gson gson, ClientThread clientThread)
	{
		this.gson = gson;
		this.clientThread = clientThread;
	}

	/** A saved copy of the run. */
	@Value
	static class Backup
	{
		Filepath file;
		long time;
		String stateJson;
	}

	/** A pasted save code, decoded. history is null for a code without one. */
	@Value
	static class SaveCode
	{
		String stateJson;
		List<HistoryEntry> history;
	}

	void start(Filepath root)
	{
		this.root = root;
		disk = Executors.newSingleThreadExecutor(r ->
		{
			Thread thread = new Thread(r, "Five Active files");
			thread.setDaemon(true);
			return thread;
		});
		// So "Open backup folder" has somewhere to open even before any account has logged in
		onDisk(root::createDirectories);
	}

	void stop()
	{
		// Anything still queued (the last few log lines) is written, then the thread ends; nothing waits for it
		if (disk != null)
		{
			disk.shutdown();
			disk = null;
		}
		unload();
	}

	/** Runs file work on the background thread, in order with the rest. */
	private void onDisk(IoTask task)
	{
		ExecutorService executor = disk;
		if (executor == null)
		{
			return;
		}
		executor.execute(() ->
		{
			try
			{
				task.run();
			}
			catch (IOException | RuntimeException e)
			{
				log.warn("Five Active: file error", e);
			}
		});
	}

	private interface IoTask
	{
		void run() throws IOException;
	}

	// ---------------------------------------------------------------- Log

	/**
	 * Loads an account's log in the background. Meanwhile it backs up stateJson (the run as it was at login;
	 * may be null) and writes accountName (may be null) beside it so people can tell the folders apart.
	 * onLoaded gets the account's backups, newest first, on the client thread once the log is in.
	 */
	synchronized void load(String profileKey, String accountName, String stateJson, Consumer<List<Backup>> onLoaded)
	{
		if (root == null)
		{
			return;
		}
		Filepath account = root.joinSegment(profileKey.replaceAll("[^A-Za-z0-9._-]", "_"));
		folder = account;
		entries.clear();
		int loading = ++generation;
		onDisk(() ->
		{
			account.createDirectories();
			if (accountName != null)
			{
				account.joinSegment("account.txt").write(accountName);
			}
			List<HistoryEntry> read = read(account.joinSegment(HISTORY_FILE));
			if (stateJson != null)
			{
				writeBackup(account, stateJson);
			}
			List<Backup> backups = readBackups(account);
			clientThread.invoke(() ->
			{
				synchronized (this)
				{
					if (generation != loading)
					{
						return;
					}
					// Anything logged while this was loading goes after what was already there
					entries.addAll(0, read);
				}
				onLoaded.accept(backups);
			});
		});
	}

	synchronized void unload()
	{
		folder = null;
		generation++;
		entries.clear();
	}

	boolean isLoaded()
	{
		return folder != null;
	}

	/** Every entry, oldest first. Safe to read from any thread. */
	List<HistoryEntry> getEntries()
	{
		return Collections.unmodifiableList(entries);
	}

	boolean isEmpty()
	{
		return entries.isEmpty();
	}

	synchronized void add(HistoryEntry entry)
	{
		Filepath account = folder;
		if (account == null)
		{
			return;
		}
		entries.add(entry);
		String line = gson.toJson(entry) + "\n";
		onDisk(() -> account.joinSegment(HISTORY_FILE).write(line, StandardOpenOption.CREATE, StandardOpenOption.APPEND));
	}

	/** Swaps the whole log for another (restoring a save code). The old one is kept beside it, renamed. */
	synchronized void replace(List<HistoryEntry> history)
	{
		Filepath account = folder;
		if (account == null)
		{
			return;
		}
		entries.clear();
		entries.addAll(history);
		StringBuilder lines = new StringBuilder();
		for (HistoryEntry entry : history)
		{
			lines.append(gson.toJson(entry)).append('\n');
		}
		String renamed = "history-replaced-" + stamp(System.currentTimeMillis()) + ".jsonl";
		onDisk(() ->
		{
			Filepath file = account.joinSegment(HISTORY_FILE);
			if (file.exists())
			{
				file.moveTo(account.joinSegment(renamed));
			}
			file.write(lines.toString());
		});
	}

	/** Starts a new, empty log. The old one is kept beside it, renamed. */
	synchronized void archive()
	{
		replace(Collections.emptyList());
	}

	private List<HistoryEntry> read(Filepath file) throws IOException
	{
		List<HistoryEntry> read = new ArrayList<>();
		if (!file.exists())
		{
			return read;
		}
		try (BufferedReader reader = file.openBufferedReader())
		{
			for (String line; (line = reader.readLine()) != null; )
			{
				if (line.trim().isEmpty())
				{
					continue;
				}
				try
				{
					HistoryEntry entry = gson.fromJson(line, HistoryEntry.class);
					// Skip lines this version can't make sense of (e.g. written by a newer version)
					if (entry != null && entry.getKind() != null)
					{
						read.add(entry);
					}
				}
				catch (JsonParseException e)
				{
					log.debug("Five Active: skipping unreadable history line", e);
				}
			}
		}
		read.sort(Comparator.comparingLong(HistoryEntry::getTime));
		return read;
	}

	// ---------------------------------------------------------------- Backups

	/** Saves a copy of the run in the background, unless it's the same as the newest copy. */
	void backup(String stateJson)
	{
		Filepath account = folder;
		if (account != null && stateJson != null)
		{
			onDisk(() -> writeBackup(account, stateJson));
		}
	}

	/** Keeps the newest MAX_BACKUPS. On the background thread. */
	private void writeBackup(Filepath account, String stateJson) throws IOException
	{
		List<Backup> backups = readBackups(account);
		if (!backups.isEmpty() && backups.get(0).getStateJson().equals(stateJson))
		{
			return;
		}
		Filepath dir = account.joinSegment(BACKUP_DIR);
		dir.createDirectories();
		dir.joinSegment(stamp(System.currentTimeMillis()) + ".json").write(stateJson);
		List<Backup> all = readBackups(account);
		for (int i = MAX_BACKUPS; i < all.size(); i++)
		{
			all.get(i).getFile().deleteIfExists();
		}
	}

	/** Reads the account's backups in the background, then hands them (newest first) to done, on that thread. */
	void listBackups(Consumer<List<Backup>> done)
	{
		Filepath account = folder;
		if (account == null)
		{
			done.accept(Collections.emptyList());
			return;
		}
		onDisk(() -> done.accept(readBackups(account)));
	}

	private List<Backup> readBackups(Filepath account) throws IOException
	{
		List<Backup> backups = new ArrayList<>();
		Filepath dir = account.joinSegment(BACKUP_DIR);
		if (!dir.isDirectory())
		{
			return backups;
		}
		List<Filepath> files;
		try (Stream<Filepath> walk = dir.walk(1))
		{
			files = walk.filter(f -> f.getFileName().endsWith(".json")).collect(Collectors.toList());
		}
		for (Filepath file : files)
		{
			try (InputStream in = file.openInputStream())
			{
				long time = new SimpleDateFormat(FILE_TIME).parse(file.getFileName().replace(".json", "")).getTime();
				backups.add(new Backup(file, time, new String(readAll(in), StandardCharsets.UTF_8)));
			}
			catch (Exception e)
			{
				log.debug("Five Active: skipping unreadable backup {}", file, e);
			}
		}
		backups.sort(Comparator.comparingLong(Backup::getTime).reversed());
		return backups;
	}

	/** The account's folder (for "Open backup folder"), or the plugin's when logged out. */
	Filepath folder()
	{
		Filepath account = folder;
		return account != null ? account : root;
	}

	private static String stamp(long time)
	{
		return new SimpleDateFormat(FILE_TIME).format(new Date(time));
	}

	private static byte[] readAll(InputStream in) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		for (int n; (n = in.read(buffer)) > 0; )
		{
			out.write(buffer, 0, n);
		}
		return out.toByteArray();
	}

	// ---------------------------------------------------------------- Other files

	/** Writes text to a file the player chose, in the background; done gets the error, or null, on that thread. */
	void writeFile(Filepath file, String text, Consumer<IOException> done)
	{
		onDiskReporting(() -> file.write(text), done);
	}

	/** Reads a file the player chose, in the background; done gets its text (null if it couldn't be read). */
	void readFile(Filepath file, Consumer<String> done)
	{
		onDisk(() ->
		{
			String text = null;
			try (InputStream in = file.openInputStream())
			{
				text = new String(readAll(in), StandardCharsets.UTF_8);
			}
			catch (IOException e)
			{
				log.debug("Five Active: could not read {}", file, e);
			}
			done.accept(text);
		});
	}

	/** Saves a share card picture in the background; done gets where it went, or null, on that thread. */
	void saveShareCard(java.awt.image.BufferedImage image, String name, Consumer<Filepath> done)
	{
		Filepath base = root;
		if (base == null)
		{
			done.accept(null);
			return;
		}
		onDisk(() ->
		{
			Filepath file = null;
			try
			{
				Filepath dir = base.joinSegment(SHARE_DIR);
				dir.createDirectories();
				file = dir.joinSegment(name);
				try (OutputStream out = file.openOutputStream())
				{
					javax.imageio.ImageIO.write(image, "png", out);
				}
			}
			catch (IOException e)
			{
				log.debug("Five Active: could not save the share card", e);
				file = null;
			}
			done.accept(file);
		});
	}

	private void onDiskReporting(IoTask task, Consumer<IOException> done)
	{
		onDisk(() ->
		{
			IOException error = null;
			try
			{
				task.run();
			}
			catch (IOException e)
			{
				error = e;
			}
			done.accept(error);
		});
	}

	// ---------------------------------------------------------------- Save codes

	/** The run and its whole log as one line of text. */
	String saveCode(String stateJson)
	{
		JsonObject code = new JsonObject();
		code.add("state", gson.fromJson(stateJson, JsonElement.class));
		JsonArray history = new JsonArray();
		for (HistoryEntry entry : entries)
		{
			history.add(gson.toJsonTree(entry));
		}
		code.add("history", history);
		try
		{
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (OutputStream zip = new GZIPOutputStream(bytes))
			{
				zip.write(gson.toJson(code).getBytes(StandardCharsets.UTF_8));
			}
			return CODE_PREFIX + Base64.getEncoder().encodeToString(bytes.toByteArray());
		}
		catch (IOException e)
		{
			throw new IllegalStateException(e);
		}
	}

	/** Reads a pasted save code, or returns null if it isn't one (whitespace and line breaks are ignored). */
	SaveCode readSaveCode(String text)
	{
		String compact = text == null ? "" : text.replaceAll("\\s", "");
		if (!compact.startsWith(CODE_PREFIX))
		{
			return null;
		}
		try
		{
			byte[] zipped = Base64.getDecoder().decode(compact.substring(CODE_PREFIX.length()));
			byte[] json;
			try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(zipped)))
			{
				json = readAll(in);
			}
			JsonObject code = new JsonParser().parse(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonElement state = code.get("state");
			if (state == null || !state.isJsonObject())
			{
				return null;
			}
			List<HistoryEntry> history = null;
			if (code.has("history") && code.get("history").isJsonArray())
			{
				history = new ArrayList<>(Arrays.asList(gson.fromJson(code.get("history"), HistoryEntry[].class)));
				history.removeIf(e -> e == null || e.getKind() == null);
			}
			return new SaveCode(gson.toJson(state), history);
		}
		catch (IllegalArgumentException | IOException | IllegalStateException | JsonParseException e)
		{
			log.debug("Five Active: not a valid save code", e);
			return null;
		}
	}
}
