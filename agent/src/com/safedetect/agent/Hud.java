package com.safedetect.agent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Overlay data that lives in the game JVM without loading AWT. The Swing window runs in a child process
 * so Lunar's javaw only owns the LWJGL window.
 */
final class Hud {
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

    private static final AtomicBoolean REFRESH = new AtomicBoolean();
    private static final AtomicBoolean CLEAR = new AtomicBoolean();
    private static Boolean nextTab;
    private static Boolean nextChat;
    private static Boolean nextSound;
    private static File dir;
    private static Process child;
    private static long lastSpawn;
    private static boolean reopen;
    private static boolean forceRefresh;
    private static boolean hooked;
    private static String sessionText = "";
    private static int lastPlayers;
    private static List<Row> lastLobby = new ArrayList<Row>();
    private static List<Row> lastSaved = new ArrayList<Row>();

    private Hud() {
    }

    static void open(File gameDir) {
        dir = gameDir;
    }

    static void bind(Settings ignored) {
    }

    static void session(int games, int wins) {
        sessionText = games <= 0 ? "" : games + "g \u00b7 " + wins + "w";
    }

    static void reopen() {
        reopen = true;
        writeState(lastPlayers, lastLobby, lastSaved);
        spawn(true);
    }

    static void bump() {
        forceRefresh = true;
    }

    static boolean takeRefresh() {
        pullCommands();
        return REFRESH.getAndSet(false);
    }

    static String takeReport() {
        pullCommands();
        return reports.poll();
    }

    static boolean takeClear() {
        pullCommands();
        return CLEAR.getAndSet(false);
    }

    static Boolean takeTabMarks() {
        pullCommands();
        Boolean value = nextTab;
        nextTab = null;
        return value;
    }

    static Boolean takeAlertsChat() {
        pullCommands();
        Boolean value = nextChat;
        nextChat = null;
        return value;
    }

    static Boolean takeAlertSound() {
        pullCommands();
        Boolean value = nextSound;
        nextSound = null;
        return value;
    }

    private static final java.util.concurrent.ConcurrentLinkedQueue<String> reports =
            new java.util.concurrent.ConcurrentLinkedQueue<String>();

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
        if (Boolean.getBoolean("safedetect.nogui") || Boolean.getBoolean("safedetect.overlay")) {
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

    static File cmdFile() {
        return dir == null ? null : new File(dir, "config/safedetect-hud-cmd.json");
    }

    private static void spawn(boolean force) {
        if (dir == null || Boolean.getBoolean("safedetect.nogui")) {
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
            File jar = new File(Hud.class.getProtectionDomain().getCodeSource().getLocation().toURI());
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
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            parent.mkdirs();
        }
        lastPlayers = players;
        lastLobby = lobby != null ? new ArrayList<Row>(lobby) : new ArrayList<Row>();
        lastSaved = saved != null ? new ArrayList<Row>(saved) : new ArrayList<Row>();
        StringBuilder out = new StringBuilder("{\n");
        out.append("  \"players\": ").append(players).append(",\n");
        out.append("  \"session\": ").append(Json.quote(sessionText)).append(",\n");
        out.append("  \"reopen\": ").append(reopen).append(",\n");
        out.append("  \"refresh\": ").append(forceRefresh).append(",\n");
        out.append("  \"lobby\": ");
        writeRows(out, lobby);
        out.append(",\n  \"saved\": ");
        writeRows(out, saved);
        out.append("\n}\n");
        reopen = false;
        forceRefresh = false;
        File tmp = new File(file.getPath() + ".tmp");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            writer.write(out.toString());
        } catch (Throwable thrown) {
            Log.once("hud write", thrown);
            return;
        }
        try {
            Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable thrown) {
            Log.once("hud move", thrown);
        }
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
                out.append(",\"sniper\":").append(row.sniper).append('}');
            }
        }
        out.append(rows != null && !rows.isEmpty() ? "\n  ]" : "]");
    }

    @SuppressWarnings("unchecked")
    private static void pullCommands() {
        File file = cmdFile();
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            Object root = Json.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            file.delete();
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
            if (map.get("tab") instanceof Boolean) {
                nextTab = (Boolean) map.get("tab");
            }
            if (map.get("chat") instanceof Boolean) {
                nextChat = (Boolean) map.get("chat");
            }
            if (map.get("sound") instanceof Boolean) {
                nextSound = (Boolean) map.get("sound");
            }
            if (map.get("report") instanceof String) {
                String name = ((String) map.get("report")).trim();
                if (!name.isEmpty()) {
                    reports.offer(name);
                }
            }
        } catch (Throwable thrown) {
            Log.once("hud cmd", thrown);
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
            snap.reopen = Boolean.TRUE.equals(map.get("reopen"));
            snap.refresh = Boolean.TRUE.equals(map.get("refresh"));
            snap.lobby = readRows(map.get("lobby"));
            snap.saved = readRows(map.get("saved"));
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
            rows.add(new Row(str(map, "name"), str(map, "tags"), num(map, "times"), str(map, "last"),
                    lng(map, "lastAt"), num(map, "stars"), dbl(map, "fkdr"), dbl(map, "wlr"), num(map, "winstreak"),
                    num(map, "finals"), num(map, "wins"), num(map, "sniper")));
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
        writeCmd(refresh, false, report, null, null, null);
    }

    static void writeClear() {
        writeCmd(false, true, null, null, null, null);
    }

    static void writePref(String name, boolean on) {
        Boolean tab = "tab".equals(name) ? Boolean.valueOf(on) : null;
        Boolean chat = "chat".equals(name) ? Boolean.valueOf(on) : null;
        Boolean sound = "sound".equals(name) ? Boolean.valueOf(on) : null;
        writeCmd(false, false, null, tab, chat, sound);
    }

    @SuppressWarnings("unchecked")
    private static void writeCmd(boolean refresh, boolean clear, String report, Boolean tab, Boolean chat,
            Boolean sound) {
        File file = cmdFile();
        if (file == null) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            parent.mkdirs();
        }
        if (file.isFile()) {
            try {
                Object root = Json.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
                if (root instanceof Map) {
                    Map<String, Object> map = (Map<String, Object>) root;
                    refresh = refresh || Boolean.TRUE.equals(map.get("refresh"));
                    clear = clear || Boolean.TRUE.equals(map.get("clear"));
                    if (report == null && map.get("report") instanceof String) {
                        report = (String) map.get("report");
                    }
                    if (tab == null && map.get("tab") instanceof Boolean) {
                        tab = (Boolean) map.get("tab");
                    }
                    if (chat == null && map.get("chat") instanceof Boolean) {
                        chat = (Boolean) map.get("chat");
                    }
                    if (sound == null && map.get("sound") instanceof Boolean) {
                        sound = (Boolean) map.get("sound");
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        StringBuilder out = new StringBuilder("{\n  \"refresh\": ").append(refresh);
        out.append(",\n  \"clear\": ").append(clear);
        if (report != null && !report.isEmpty()) {
            out.append(",\n  \"report\": ").append(Json.quote(report));
        }
        if (tab != null) {
            out.append(",\n  \"tab\": ").append(tab);
        }
        if (chat != null) {
            out.append(",\n  \"chat\": ").append(chat);
        }
        if (sound != null) {
            out.append(",\n  \"sound\": ").append(sound);
        }
        out.append("\n}\n");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(out.toString());
        } catch (Throwable thrown) {
            Log.once("hud cmd write", thrown);
        }
    }

    static final class Snapshot {
        int players;
        String session = "";
        boolean reopen;
        boolean refresh;
        List<Row> lobby = new ArrayList<Row>();
        List<Row> saved = new ArrayList<Row>();
    }
}
