package com.safedetect.agent;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Same file and layout as the Forge mod: config/safedetect-flags.json under the game directory.
 */
final class FlagStore {
    enum Flag {
        KA("\u00a7c"),
        SI("\u00a7c"),
        VL("\u00a7c"),
        RE("\u00a7c"),
        AC("\u00a76"),
        BB("\u00a7c"),
        SA("\u00a7c"),
        LS("\u00a7c"),
        SS("\u00a7c"),
        GB("\u00a7c"),
        TL("\u00a7c"),
        TW("\u00a7c"),
        FL("\u00a7c"),
        SP("\u00a76"),
        AB("\u00a76"),
        NS("\u00a7e"),
        SN("\u00a7d"),
        SM("\u00a7b"),
        DS("\u00a7c"),
        NK("\u00a7d"),
        FK("\u00a7b"),
        AL("\u00a7e");

        final String color;

        Flag(String color) {
            this.color = color;
        }

        String bracket() {
            return "\u00a77[" + color + name() + "\u00a77]";
        }
    }

    static final class Record {
        String uuid;
        String name;
        final Set<String> flags = new LinkedHashSet<String>();
        final Map<String, String> details = new LinkedHashMap<String, String>();
        final Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        /** Flag name to what triggered it most recently, e.g. "reach 3.71 (3 long hits)". */
        final Map<String, String> evidence = new LinkedHashMap<String, String>();
        int times;
        String lastCheck;
        long lastCheckAt;
        long first;
        long last;
    }

    private static final long FLUSH_MS = 2000L;

    private final Map<String, Record> byUuid = new LinkedHashMap<String, Record>();
    private final Map<String, Record> byName = new LinkedHashMap<String, Record>();
    private final File file;
    private List<Record> recordsView;
    private List<Record> cheatersView;
    private boolean dirty;
    private long lastFlush;

    FlagStore(File gameDir) {
        file = new File(gameDir, "config/safedetect-flags.json");
        load();
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    String text = pendingJson();
                    if (text != null) {
                        Files2.writeAtomic(file, text);
                    }
                }
            }, "SafeDetect-Flags-Flush"));
        } catch (Throwable ignored) {
        }
    }

    synchronized int size() {
        return byUuid.size();
    }

    /** Read-only and cached until the store changes. */
    synchronized List<Record> records() {
        if (recordsView == null) {
            recordsView = Collections.unmodifiableList(new ArrayList<Record>(byUuid.values()));
        }
        return recordsView;
    }

    /** Records with at least one cheat flag; read-only and cached until the store changes. */
    synchronized List<Record> cheaters() {
        if (cheatersView == null) {
            List<Record> out = new ArrayList<Record>();
            for (Record record : byUuid.values()) {
                if (hasCheat(record)) {
                    out.add(record);
                }
            }
            cheatersView = Collections.unmodifiableList(out);
        }
        return cheatersView;
    }

    synchronized int clear() {
        int n = byUuid.size();
        byUuid.clear();
        byName.clear();
        changed();
        return n;
    }

    /** Writes on the background writer at most every two seconds unless forced. */
    void flush(boolean force) {
        long now = System.currentTimeMillis();
        String text;
        synchronized (this) {
            if (!dirty || (!force && now - lastFlush < FLUSH_MS)) {
                return;
            }
            lastFlush = now;
            text = pendingJson();
        }
        if (text != null) {
            Files2.writeLater(file, text);
        }
    }

    private synchronized String pendingJson() {
        if (!dirty) {
            return null;
        }
        dirty = false;
        return json();
    }

    private void changed() {
        dirty = true;
        recordsView = null;
        cheatersView = null;
    }

    /** Re-reads the file, dropping unsaved changes; flush and drain first to keep them. */
    synchronized void reload() {
        byUuid.clear();
        byName.clear();
        load();
        recordsView = null;
        cheatersView = null;
        dirty = false;
    }

    synchronized Record get(UUID uuid, String name) {
        Record record = uuid == null ? null : byUuid.get(uuid.toString());
        if (record == null && name != null) {
            record = byName.get(name.toLowerCase(Locale.ROOT));
        }
        return record;
    }

    String brackets(Record record) {
        if (record == null || record.flags.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Flag flag : Flag.values()) {
            if (record.flags.contains(flag.name())) {
                out.append(flag.bracket());
            }
        }
        return out.toString();
    }

    String plainTags(Record record) {
        if (record == null || record.flags.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Flag flag : Flag.values()) {
            if (!record.flags.contains(flag.name())) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append('[').append(flag.name());
            int n = count(record, flag.name());
            if (n > 1) {
                out.append('x').append(n);
            }
            out.append(']');
        }
        return out.toString();
    }

    boolean hasCheat(Record record) {
        if (record == null) {
            return false;
        }
        for (Flag flag : Flag.values()) {
            if (!cheat(flag)) {
                continue;
            }
            if (record.flags.contains(flag.name())) {
                return true;
            }
        }
        return false;
    }

    static boolean cheat(Flag flag) {
        return flag != Flag.SN && flag != Flag.SM && flag != Flag.NK && flag != Flag.FK && flag != Flag.AL;
    }

    int count(Record record, String flag) {
        if (record == null) {
            return 0;
        }
        Integer n = record.counts.get(flag);
        if (n != null) {
            return n.intValue();
        }
        return record.flags.contains(flag) ? 1 : 0;
    }

    synchronized boolean add(UUID uuid, String name, Flag flag, String detail) {
        return add(uuid, name, flag, detail, null);
    }

    /** Returns true when the flag type is new for this player. Always increments the times counter. */
    synchronized boolean add(UUID uuid, String name, Flag flag, String detail, String evidence) {
        if (uuid == null || flag == null) {
            return false;
        }
        Record record = byUuid.get(uuid.toString());
        boolean created = record == null;
        if (created) {
            record = new Record();
            record.uuid = uuid.toString();
            record.first = System.currentTimeMillis();
        }
        if (name != null) {
            record.name = name;
        }
        record.last = System.currentTimeMillis();
        if (evidence != null && !evidence.isEmpty()) {
            record.evidence.put(flag.name(), evidence);
        }
        record.lastCheck = evidence != null && !evidence.isEmpty() ? evidence : detail != null ? detail : flag.name();
        record.lastCheckAt = record.last;
        boolean added = record.flags.add(flag.name());
        record.times++;
        Integer n = record.counts.get(flag.name());
        record.counts.put(flag.name(), n == null ? 1 : n.intValue() + 1);
        if (detail != null) {
            record.details.put(flag.name(), detail);
        }
        index(record);
        changed();
        return added;
    }

    private void index(Record record) {
        byUuid.put(record.uuid, record);
        if (record.name != null) {
            byName.put(record.name.toLowerCase(Locale.ROOT), record);
        }
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (!file.isFile()) {
            return;
        }
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Object root = Json.parse(text);
            if (!(root instanceof Map)) {
                return;
            }
            Object players = ((Map<String, Object>) root).get("players");
            if (!(players instanceof List)) {
                return;
            }
            for (Object entry : (List<Object>) players) {
                if (!(entry instanceof Map)) {
                    continue;
                }
                Map<String, Object> map = (Map<String, Object>) entry;
                if (!(map.get("uuid") instanceof String)) {
                    continue;
                }
                Record record = new Record();
                record.uuid = (String) map.get("uuid");
                record.name = map.get("name") instanceof String ? (String) map.get("name") : null;
                if (map.get("flags") instanceof List) {
                    for (Object flag : (List<Object>) map.get("flags")) {
                        if (flag instanceof String) {
                            record.flags.add((String) flag);
                        }
                    }
                }
                if (map.get("details") instanceof Map) {
                    for (Map.Entry<String, Object> detail : ((Map<String, Object>) map.get("details")).entrySet()) {
                        if (detail.getValue() instanceof String) {
                            record.details.put(detail.getKey(), (String) detail.getValue());
                        }
                    }
                }
                if (map.get("evidence") instanceof Map) {
                    for (Map.Entry<String, Object> item : ((Map<String, Object>) map.get("evidence")).entrySet()) {
                        if (item.getValue() instanceof String) {
                            record.evidence.put(item.getKey(), (String) item.getValue());
                        }
                    }
                }
                if (map.get("counts") instanceof Map) {
                    for (Map.Entry<String, Object> count : ((Map<String, Object>) map.get("counts")).entrySet()) {
                        if (count.getValue() instanceof Number) {
                            record.counts.put(count.getKey(), ((Number) count.getValue()).intValue());
                        }
                    }
                }
                record.times = map.get("times") instanceof Number ? ((Number) map.get("times")).intValue() : 0;
                if (record.times <= 0) {
                    record.times = record.flags.size();
                }
                record.lastCheck = map.get("lastCheck") instanceof String ? (String) map.get("lastCheck") : null;
                record.lastCheckAt = map.get("lastCheckAt") instanceof Number
                        ? ((Number) map.get("lastCheckAt")).longValue() : 0L;
                record.first = map.get("first") instanceof Number ? ((Number) map.get("first")).longValue() : 0L;
                record.last = map.get("last") instanceof Number ? ((Number) map.get("last")).longValue() : 0L;
                if (record.lastCheck == null && !record.details.isEmpty()) {
                    record.lastCheck = record.details.values().iterator().next();
                    record.lastCheckAt = record.last;
                }
                index(record);
            }
        } catch (Throwable thrown) {
            Log.once("flag file read", thrown);
        }
    }

    private String json() {
        List<Record> records = new ArrayList<Record>(byUuid.values());
        StringBuilder out = new StringBuilder("{\n  \"players\": [");
        for (int i = 0; i < records.size(); i++) {
            Record record = records.get(i);
            out.append(i == 0 ? "\n" : ",\n").append("    {\n");
            out.append("      \"uuid\": ").append(Json.quote(record.uuid)).append(",\n");
            if (record.name != null) {
                out.append("      \"name\": ").append(Json.quote(record.name)).append(",\n");
            }
            out.append("      \"flags\": [");
            int f = 0;
            for (String flag : record.flags) {
                out.append(f++ == 0 ? "\n" : ",\n").append("        ").append(Json.quote(flag));
            }
            out.append(f == 0 ? "],\n" : "\n      ],\n");
            out.append("      \"details\": {");
            int d = 0;
            for (Map.Entry<String, String> detail : record.details.entrySet()) {
                out.append(d++ == 0 ? "\n" : ",\n").append("        ").append(Json.quote(detail.getKey()))
                        .append(": ").append(Json.quote(detail.getValue()));
            }
            out.append(d == 0 ? "},\n" : "\n      },\n");
            if (!record.evidence.isEmpty()) {
                out.append("      \"evidence\": {");
                int e = 0;
                for (Map.Entry<String, String> item : record.evidence.entrySet()) {
                    out.append(e++ == 0 ? "\n" : ",\n").append("        ").append(Json.quote(item.getKey()))
                            .append(": ").append(Json.quote(item.getValue()));
                }
                out.append("\n      },\n");
            }
            out.append("      \"counts\": {");
            int c = 0;
            for (Map.Entry<String, Integer> count : record.counts.entrySet()) {
                out.append(c++ == 0 ? "\n" : ",\n").append("        ").append(Json.quote(count.getKey()))
                        .append(": ").append(count.getValue());
            }
            out.append(c == 0 ? "},\n" : "\n      },\n");
            out.append("      \"times\": ").append(record.times).append(",\n");
            if (record.lastCheck != null) {
                out.append("      \"lastCheck\": ").append(Json.quote(record.lastCheck)).append(",\n");
                out.append("      \"lastCheckAt\": ").append(record.lastCheckAt).append(",\n");
            }
            out.append("      \"first\": ").append(record.first).append(",\n");
            out.append("      \"last\": ").append(record.last).append("\n    }");
        }
        out.append(records.isEmpty() ? "]\n}" : "\n  ]\n}");
        return out.toString();
    }
}
