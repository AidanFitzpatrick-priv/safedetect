package com.safedetect.agent;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Overlay data that lives in the game JVM without loading AWT. The Swing window runs in a child process
 * so Lunar's javaw only owns the LWJGL window.
 *
 * The game writes {@code safedetect-hud.json}; the overlay drops one file per command into
 * {@code safedetect-cmd/}. Each command file is renamed into place complete, and only the game deletes it.
 */
final class Hud {
    static final String[] KEY_NAMES = { "hypixel", "sniperKey", "sniperUrl", "urchin", "aurora", "discord" };

    static final class Row {
        final String name;
        final String tags;
        final int times;
        final String last;
        final long lastAt;
        final int stars;
        final double fkdr;
        final double wlr;
        final int winstreak;
        final int finals;
        final int wins;
        final int sniper;
        /** Minecraft colour code char of the player's team, or empty. */
        String team = "";
        boolean blacklisted;
        boolean friend;
        int seen = -1;
        long seenAt;
        String threat = "";
        String evidence = "";

        Row(String name, String tags, int times, String last, long lastAt) {
            this(name, tags, times, last, lastAt, -1, -1.0, -1.0, -1, -1, -1, -1);
        }

        Row(String name, String tags, int times, String last, long lastAt, int stars, double fkdr, double wlr,
                int winstreak, int finals, int wins, int sniper) {
            this.name = name;
            this.tags = tags == null ? "" : tags;
            this.last = last == null ? "" : last;
            this.times = times;
            this.lastAt = lastAt;
            this.stars = stars;
            this.fkdr = fkdr;
            this.wlr = wlr;
            this.winstreak = winstreak;
            this.finals = finals;
            this.wins = wins;
            this.sniper = sniper;
        }
    }

    private static final long POLL_MS = 100L;
    private static final AtomicBoolean REFRESH = new AtomicBoolean();
    private static final AtomicBoolean CLEAR = new AtomicBoolean();
    private static final AtomicBoolean UPDATE = new AtomicBoolean();
    private static final AtomicInteger CMD_COUNTER = new AtomicInteger();
    private static final ConcurrentLinkedQueue<String> REPORTS = new ConcurrentLinkedQueue<String>();
    private static final ConcurrentLinkedQueue<Map<String, String>> KEYS = new ConcurrentLinkedQueue<Map<String, String>>();
    private static final ConcurrentLinkedQueue<Map<String, Object>> SETS = new ConcurrentLinkedQueue<Map<String, Object>>();
    private static final ConcurrentLinkedQueue<String[]> ACTIONS = new ConcurrentLinkedQueue<String[]>();
    private static final ConcurrentLinkedQueue<String[]> PLUGINS = new ConcurrentLinkedQueue<String[]>();
    private static volatile String pluginsJson = "[]";
    static final String[] ROW_ACTIONS = { "unblacklist", "friend", "unfriend" };
    private static File dir;
    private static Settings settings;
    private static Process child;
    private static long lastSpawn;
    private static long lastPoll;
    private static long reopenSeq;
    private static long refreshSeq;
    private static boolean hooked;
    private static boolean holdSpawn;
    private static String sessionText = "";
    private static volatile String notice = "";
    private static volatile String lastWritten;
    private static int lastPlayers;
    private static List<Row> lastLobby = new ArrayList<Row>();
    private static List<Row> lastSaved = new ArrayList<Row>();

    private Hud() {
    }

    static void open(File gameDir) {
        dir = gameDir;
    }

    static void bind(Settings next) {
        settings = next;
    }

    static void session(int games, int wins) {
        sessionText = games <= 0 ? "" : games + "g \u00b7 " + wins + "w";
    }

    static void reopen() {
        reopenSeq++;
        writeState(lastPlayers, lastLobby, lastSaved);
        spawn(true);
    }

    static void bump() {
        refreshSeq++;
    }

    static boolean takeRefresh() {
        return REFRESH.getAndSet(false);
    }

    static String takeReport() {
        return REPORTS.poll();
    }

    static Map<String, String> takeKeys() {
        return KEYS.poll();
    }

    static Map<String, Object> takeSet() {
        return SETS.poll();
    }

    /** {action, name} where action is one of {@link #ROW_ACTIONS}. */
    static String[] takeAction() {
        return ACTIONS.poll();
    }

    static boolean takeClear() {
        return CLEAR.getAndSet(false);
    }

    static boolean takeUpdate() {
        return UPDATE.getAndSet(false);
    }

    /** {id, "1"|"0"} from the overlay Plugins page. */
    static String[] takePlugin() {
        return PLUGINS.poll();
    }

    static void plugins(String json) {
        pluginsJson = json == null || json.isEmpty() ? "[]" : json;
        lastWritten = null;
    }

    static void notice(String text) {
        notice = text == null ? "" : text;
        lastWritten = null;
    }

    static String notice() {
        return notice == null ? "" : notice;
    }

    static String wdr(String name) {
        return "/wdr " + name + " cheating";
    }

    static String lastLabel(FlagStore.Record record) {
        if (record == null || record.lastCheck == null || record.lastCheck.isEmpty()) {
            return "";
        }
        long ago = System.currentTimeMillis() - record.lastCheckAt;
        if (ago < 0L) {
            ago = 0L;
        }
        long seconds = ago / 1000L;
        String when = seconds < 2L ? "now" : seconds < 60L ? seconds + "s" : (seconds / 60L) + "m";
        return record.lastCheck + " · " + when;
    }

    static void show(int players, List<Row> lobby, List<Row> saved) {
        if (Boolean.getBoolean("safedetect.overlay")) {
            return;
        }
        writeState(players, lobby, saved);
        spawn(false);
    }

    static void clip(String text) {
        if (text == null || !Os.WIN) {
            return;
        }
        try {
            Process process = new ProcessBuilder("clip").start();
            OutputStream out = process.getOutputStream();
            out.write((byte) 0xFF);
            out.write((byte) 0xFE);
            out.write(text.getBytes("UTF-16LE"));
            out.close();
        } catch (Throwable thrown) {
            Log.once("clipboard", thrown);
        }
    }

    static File hudFile() {
        return dir == null ? null : new File(dir, "config/safedetect-hud.json");
    }

    static File cmdDir() {
        return dir == null ? null : new File(dir, "config/safedetect-cmd");
    }

    /** Stops the overlay so the jar isn't locked while an update replaces it. */
    static void pauseOverlay() {
        holdSpawn = true;
        stopChild();
    }

    static void resumeOverlay() {
        holdSpawn = false;
        lastSpawn = 0L;
        spawn(true);
    }

    private static void stopChild() {
        if (child == null) {
            return;
        }
        try {
            child.destroy();
            for (int i = 0; i < 20 && childIsAlive(); i++) {
                Thread.sleep(50L);
            }
        } catch (Throwable ignored) {
        }
        child = null;
    }

    private static void spawn(boolean force) {
        if (holdSpawn || dir == null || Boolean.getBoolean("safedetect.nogui")) {
            return;
        }
        if (child != null && childIsAlive()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now - lastSpawn < 4000L) {
            return;
        }
        lastSpawn = now;
        try {
            File jar = overlayJar();
            File java = new File(System.getProperty("java.home"), "bin/javaw.exe");
            if (!java.isFile()) {
                java = new File(System.getProperty("java.home"), "bin/java");
            }
            ProcessBuilder builder = new ProcessBuilder(java.getAbsolutePath(), "-Dsafedetect.overlay=true",
                    "-Xmx64m", "-cp", jar.getAbsolutePath(), "com.safedetect.agent.Overlay", dir.getAbsolutePath());
            builder.redirectErrorStream(true);
            builder.redirectOutput(new File(dir, "config/safedetect-overlay.log"));
            child = builder.start();
            if (!hooked) {
                hooked = true;
                Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (child != null) {
                                child.destroy();
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }));
            }
        } catch (Throwable thrown) {
            Log.once("overlay spawn", thrown);
        }
    }

    private static File overlayJar() throws java.net.URISyntaxException {
        File loc = new File(Hud.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (!loc.isFile()) {
            return loc;
        }
        File pending = new File(loc.getParentFile(), Updater.PENDING_NAME);
        return pending.isFile() && pending.length() > 1024L ? pending : loc;
    }

    private static boolean childIsAlive() {
        try {
            child.exitValue();
            return false;
        } catch (IllegalThreadStateException running) {
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void writeState(int players, List<Row> lobby, List<Row> saved) {
        File file = hudFile();
        if (file == null) {
            return;
        }
        lastPlayers = players;
        lastLobby = lobby != null ? new ArrayList<Row>(lobby) : new ArrayList<Row>();
        lastSaved = saved != null ? new ArrayList<Row>(saved) : new ArrayList<Row>();
        StringBuilder out = new StringBuilder("{\n");
        out.append("  \"players\": ").append(players).append(",\n");
        out.append("  \"session\": ").append(Json.quote(sessionText)).append(",\n");
        out.append("  \"notice\": ").append(Json.quote(notice == null ? "" : notice)).append(",\n");
        out.append("  \"reopen\": ").append(reopenSeq).append(",\n");
        out.append("  \"refresh\": ").append(refreshSeq).append(",\n");
        Settings s = settings;
        out.append("  \"options\": ").append(s == null ? "{}" : s.optionsJson()).append(",\n");
        out.append("  \"keys\": {");
        for (int i = 0; i < KEY_NAMES.length; i++) {
            out.append(i == 0 ? "" : ",").append(Json.quote(KEY_NAMES[i])).append(':')
                    .append(s != null && s.hasKeyNamed(KEY_NAMES[i]));
        }
        out.append("},\n");
        out.append("  \"lobby\": ");
        writeRows(out, lobby);
        out.append(",\n  \"saved\": ");
        writeRows(out, saved);
        out.append(",\n  \"plugins\": ").append(pluginsJson == null || pluginsJson.isEmpty() ? "[]" : pluginsJson);
        out.append("\n}\n");
        String text = out.toString();
        if (text.equals(lastWritten)) {
            return;
        }
        lastWritten = text;
        Files2.writeLater(file, text);
    }

    private static void writeRows(StringBuilder out, List<Row> rows) {
        out.append('[');
        if (rows != null) {
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                if (i > 0) {
                    out.append(',');
                }
                out.append("\n    {\"name\":").append(Json.quote(row.name));
                out.append(",\"tags\":").append(Json.quote(row.tags));
                out.append(",\"times\":").append(row.times);
                out.append(",\"last\":").append(Json.quote(row.last));
                out.append(",\"lastAt\":").append(row.lastAt);
                out.append(",\"stars\":").append(row.stars);
                out.append(",\"fkdr\":").append(row.fkdr);
                out.append(",\"wlr\":").append(row.wlr);
                out.append(",\"winstreak\":").append(row.winstreak);
                out.append(",\"finals\":").append(row.finals);
                out.append(",\"wins\":").append(row.wins);
                out.append(",\"sniper\":").append(row.sniper);
                out.append(",\"team\":").append(Json.quote(row.team == null ? "" : row.team));
                out.append(",\"bl\":").append(row.blacklisted);
                out.append(",\"friend\":").append(row.friend);
                out.append(",\"seen\":").append(row.seen);
                out.append(",\"seenAt\":").append(row.seenAt);
                out.append(",\"threat\":").append(Json.quote(row.threat == null ? "" : row.threat));
                out.append(",\"evidence\":").append(Json.quote(row.evidence == null ? "" : row.evidence)).append('}');
            }
        }
        out.append(rows != null && !rows.isEmpty() ? "\n  ]" : "]");
    }

    /** Reads and deletes pending overlay commands, oldest first. Throttled; call once per frame. */
    static void pollCommands() {
        long now = System.currentTimeMillis();
        if (now - lastPoll < POLL_MS) {
            return;
        }
        lastPoll = now;
        File folder = cmdDir();
        if (folder == null || !folder.isDirectory()) {
            return;
        }
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            return;
        }
        Arrays.sort(files);
        for (File file : files) {
            if (!file.getName().endsWith(".json")) {
                continue;
            }
            try {
                String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                file.delete();
                apply(Json.parse(text));
            } catch (Throwable thrown) {
                file.delete();
                Log.once("hud cmd", thrown);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void apply(Object root) {
        if (!(root instanceof Map)) {
            return;
        }
        Map<String, Object> map = (Map<String, Object>) root;
        if (Boolean.TRUE.equals(map.get("refresh"))) {
            REFRESH.set(true);
        }
        if (Boolean.TRUE.equals(map.get("clear"))) {
            CLEAR.set(true);
        }
        if (Boolean.TRUE.equals(map.get("update"))) {
            UPDATE.set(true);
        }
        String[][] legacy = { { "tab", "tabMarks" }, { "chat", "alertsChat" }, { "sound", "alertSound" } };
        for (String[] pair : legacy) {
            if (map.get(pair[0]) instanceof Boolean) {
                Map<String, Object> one = new LinkedHashMap<String, Object>();
                one.put(pair[1], map.get(pair[0]));
                SETS.offer(one);
            }
        }
        if (map.get("report") instanceof String) {
            String name = ((String) map.get("report")).trim();
            if (!name.isEmpty()) {
                REPORTS.offer(name);
            }
        }
        if (map.get("keys") instanceof Map) {
            Map<String, String> keys = new LinkedHashMap<String, String>();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) map.get("keys")).entrySet()) {
                if (entry.getValue() instanceof String) {
                    keys.put(entry.getKey(), (String) entry.getValue());
                }
            }
            if (!keys.isEmpty()) {
                KEYS.offer(keys);
            }
        }
        if (map.get("set") instanceof Map) {
            Map<String, Object> values = (Map<String, Object>) map.get("set");
            if (!values.isEmpty()) {
                SETS.offer(new LinkedHashMap<String, Object>(values));
            }
        }
        for (String action : ROW_ACTIONS) {
            if (map.get(action) instanceof String && !((String) map.get(action)).trim().isEmpty()) {
                ACTIONS.offer(new String[] { action, ((String) map.get(action)).trim() });
            }
        }
        if (map.get("plugin") instanceof String) {
            String id = ((String) map.get("plugin")).trim();
            if (!id.isEmpty()) {
                boolean on = !Boolean.FALSE.equals(map.get("on"));
                PLUGINS.offer(new String[] { id, on ? "1" : "0" });
            }
        }
    }

    @SuppressWarnings("unchecked")
    static Snapshot readState() {
        File file = hudFile();
        if (file == null || !file.isFile()) {
            return null;
        }
        try {
            Object root = Json.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            if (!(root instanceof Map)) {
                return null;
            }
            Map<String, Object> map = (Map<String, Object>) root;
            Snapshot snap = new Snapshot();
            snap.players = map.get("players") instanceof Number ? ((Number) map.get("players")).intValue() : 0;
            snap.session = map.get("session") instanceof String ? (String) map.get("session") : "";
            snap.notice = map.get("notice") instanceof String ? (String) map.get("notice") : "";
            snap.reopen = lng(map, "reopen");
            snap.refresh = lng(map, "refresh");
            if (map.get("options") instanceof Map) {
                snap.options.putAll((Map<String, Object>) map.get("options"));
            }
            if (map.get("keys") instanceof Map) {
                for (Map.Entry<String, Object> entry : ((Map<String, Object>) map.get("keys")).entrySet()) {
                    snap.keysSet.put(entry.getKey(), Boolean.TRUE.equals(entry.getValue()));
                }
            }
            snap.lobby = readRows(map.get("lobby"));
            snap.saved = readRows(map.get("saved"));
            snap.plugins = readPlugins(map.get("plugins"));
            return snap;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Row> readRows(Object value) {
        List<Row> rows = new ArrayList<Row>();
        if (!(value instanceof List)) {
            return rows;
        }
        for (Object entry : (List<Object>) value) {
            if (!(entry instanceof Map)) {
                continue;
            }
            Map<String, Object> map = (Map<String, Object>) entry;
            Row row = new Row(str(map, "name"), str(map, "tags"), num(map, "times"), str(map, "last"),
                    lng(map, "lastAt"), num(map, "stars"), dbl(map, "fkdr"), dbl(map, "wlr"), num(map, "winstreak"),
                    num(map, "finals"), num(map, "wins"), num(map, "sniper"));
            row.team = str(map, "team");
            row.blacklisted = Boolean.TRUE.equals(map.get("bl"));
            row.friend = Boolean.TRUE.equals(map.get("friend"));
            row.seen = num(map, "seen");
            row.seenAt = lng(map, "seenAt");
            row.threat = str(map, "threat");
            row.evidence = str(map, "evidence");
            rows.add(row);
        }
        return rows;
    }

    private static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : "";
    }

    private static int num(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number ? ((Number) value).intValue() : -1;
    }

    private static long lng(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static double dbl(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number ? ((Number) value).doubleValue() : -1.0;
    }

    static void writeCommand(boolean refresh, String report) {
        StringBuilder out = new StringBuilder("{\"refresh\":").append(refresh);
        if (report != null && !report.isEmpty()) {
            out.append(",\"report\":").append(Json.quote(report));
        }
        writeCmd(out.append('}').toString());
    }

    static void writeClear() {
        writeCmd("{\"clear\":true}");
    }

    static void writeUpdate() {
        writeCmd("{\"update\":true}");
    }

    /** Values are sent as typed; an empty string clears that key. */
    static void writeKeys(Map<String, String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        StringBuilder out = new StringBuilder("{\"keys\":{");
        int i = 0;
        for (Map.Entry<String, String> entry : keys.entrySet()) {
            out.append(i++ == 0 ? "" : ",").append(Json.quote(entry.getKey())).append(':')
                    .append(Json.quote(entry.getValue() == null ? "" : entry.getValue()));
        }
        writeCmd(out.append("}}").toString());
    }

    /** value must be a Boolean, Number or String. */
    static void writeSet(String name, Object value) {
        String json = value instanceof String ? Json.quote((String) value) : String.valueOf(value);
        writeCmd("{\"set\":{" + Json.quote(name) + ":" + json + "}}");
    }

    static void writeAction(String action, String name) {
        if (name != null && !name.isEmpty() && Arrays.asList(ROW_ACTIONS).contains(action)) {
            writeCmd("{" + Json.quote(action) + ":" + Json.quote(name) + "}");
        }
    }

    static void writePlugin(String id, boolean on) {
        if (id != null && !id.isEmpty()) {
            writeCmd("{\"plugin\":" + Json.quote(id) + ",\"on\":" + on + "}");
        }
    }

    private static void writeCmd(String json) {
        File folder = cmdDir();
        if (folder == null) {
            return;
        }
        String name = String.format("%013d-%06d.json", Long.valueOf(System.currentTimeMillis()),
                Integer.valueOf(CMD_COUNTER.incrementAndGet() % 1000000));
        Files2.writeAtomic(new File(folder, name), json);
    }

    static final class Snapshot {
        int players;
        String session = "";
        String notice = "";
        long reopen;
        long refresh;
        final Map<String, Object> options = new LinkedHashMap<String, Object>();
        final Map<String, Boolean> keysSet = new LinkedHashMap<String, Boolean>();
        List<Row> lobby = new ArrayList<Row>();
        List<Row> saved = new ArrayList<Row>();
        List<PluginCard> plugins = new ArrayList<PluginCard>();
    }

    static final class PluginCard {
        String id = "";
        String name = "";
        String author = "";
        String version = "";
        String blurb = "";
        String needs = "";
        String commands = "";
        boolean on;
    }

    @SuppressWarnings("unchecked")
    static List<PluginCard> readPlugins(Object value) {
        List<PluginCard> cards = new ArrayList<PluginCard>();
        if (!(value instanceof List)) {
            return cards;
        }
        for (Object entry : (List<Object>) value) {
            if (!(entry instanceof Map)) {
                continue;
            }
            Map<String, Object> map = (Map<String, Object>) entry;
            PluginCard card = new PluginCard();
            card.id = str(map, "id");
            card.name = str(map, "name");
            card.author = str(map, "author");
            card.version = str(map, "version");
            card.blurb = str(map, "blurb");
            card.needs = str(map, "needs");
            card.commands = str(map, "commands");
            card.on = Boolean.TRUE.equals(map.get("on"));
            if (!card.id.isEmpty()) {
                cards.add(card);
            }
        }
        return cards;
    }
}
