package com.safedetect.agent;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * How often each player has shared a lobby with you, in {@code config/safedetect-seen.json}. A player is
 * counted once per world. Entries unseen for 120 days are dropped on load, and the least recently seen
 * entries go first once the cap is reached.
 */
final class Encounters {
    static final long PRUNE_MS = 120L * 24L * 60L * 60L * 1000L;
    static final int CAP = 50000;
    private static final long FLUSH_MS = 2000L;

    interface Clock {
        long now();
    }

    static final Clock SYSTEM = new Clock() {
        @Override
        public long now() {
            return System.currentTimeMillis();
        }
    };

    static final class Entry {
        final String key;
        String name;
        int count;
        long first;
        long last;
        /** When they were seen before the current world, or 0 the first time. */
        long previous;
        long world = Long.MIN_VALUE;

        Entry(String key) {
            this.key = key;
        }
    }

    private final File file;
    private final Clock clock;
    private final int cap;
    private final Map<String, Entry> byKey;
    private final Map<String, Entry> index = new java.util.HashMap<String, Entry>();
    private final Map<String, String> keyByName = new java.util.HashMap<String, String>();
    private boolean dirty;
    private long lastFlush;

    Encounters(File gameDir) {
        this(new File(gameDir, "config/safedetect-seen.json"), SYSTEM, CAP);
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    String text = pendingJson();
                    if (text != null) {
                        Files2.writeAtomic(file, text);
                    }
                }
            }, "SafeDetect-Seen-Flush"));
        } catch (Throwable ignored) {
        }
    }

    Encounters(File file, Clock clock, final int cap) {
        this.file = file;
        this.clock = clock;
        this.cap = cap;
        byKey = new LinkedHashMap<String, Entry>(256, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                if (size() <= Encounters.this.cap) {
                    return false;
                }
                forgetName(eldest.getValue());
                index.remove(eldest.getKey());
                return true;
            }
        };
        load();
    }

    static String key(UUID id, String name) {
        if (id != null) {
            return id.toString();
        }
        return name == null ? null : "name:" + name.toLowerCase(Locale.ROOT);
    }

    /** Counts the player once for {@code world}; later calls in the same world only return the entry. */
    synchronized Entry saw(UUID id, String name, long world) {
        String key = key(id, name);
        if (key == null) {
            return null;
        }
        Entry entry = byKey.get(key);
        if (entry != null && entry.world == world) {
            return entry;
        }
        long now = clock.now();
        if (entry == null) {
            entry = new Entry(key);
            entry.first = now;
            index.put(key, entry);
            byKey.put(key, entry);
        }
        entry.previous = entry.last;
        entry.last = now;
        entry.count++;
        entry.world = world;
        if (name != null && !name.isEmpty()) {
            if (entry.name != null && !entry.name.equalsIgnoreCase(name)) {
                forgetName(entry);
            }
            entry.name = name;
            keyByName.put(name.toLowerCase(Locale.ROOT), key);
        }
        dirty = true;
        return entry;
    }

    synchronized Entry get(UUID id, String name) {
        Entry entry = id == null ? null : peek(id.toString());
        if (entry == null && name != null) {
            String key = keyByName.get(name.toLowerCase(Locale.ROOT));
            entry = key == null ? null : peek(key);
            if (entry == null) {
                entry = peek(key(null, name));
            }
        }
        return entry;
    }

    /** Lookup that does not count as an access, so reading never changes which entry is dropped first. */
    private Entry peek(String key) {
        return key == null ? null : index.get(key);
    }

    synchronized int size() {
        return byKey.size();
    }

    /** Copy, oldest seen first. */
    synchronized List<Entry> entries() {
        return new ArrayList<Entry>(byKey.values());
    }

    synchronized void reload() {
        byKey.clear();
        index.clear();
        keyByName.clear();
        dirty = false;
        load();
    }

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

    private void forgetName(Entry entry) {
        if (entry.name != null && entry.key.equals(keyByName.get(entry.name.toLowerCase(Locale.ROOT)))) {
            keyByName.remove(entry.name.toLowerCase(Locale.ROOT));
        }
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            Object root = Json.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            if (!(root instanceof Map) || !(((Map<String, Object>) root).get("players") instanceof List)) {
                return;
            }
            long cutoff = clock.now() - PRUNE_MS;
            List<Entry> loaded = new ArrayList<Entry>();
            for (Object item : (List<Object>) ((Map<String, Object>) root).get("players")) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<String, Object> map = (Map<String, Object>) item;
                if (!(map.get("key") instanceof String)) {
                    continue;
                }
                Entry entry = new Entry((String) map.get("key"));
                entry.name = map.get("name") instanceof String ? (String) map.get("name") : null;
                entry.count = map.get("count") instanceof Number ? ((Number) map.get("count")).intValue() : 0;
                entry.first = map.get("first") instanceof Number ? ((Number) map.get("first")).longValue() : 0L;
                entry.last = map.get("last") instanceof Number ? ((Number) map.get("last")).longValue() : 0L;
                if (entry.count <= 0 || entry.last < cutoff) {
                    dirty = true;
                    continue;
                }
                loaded.add(entry);
            }
            Collections.sort(loaded, new Comparator<Entry>() {
                @Override
                public int compare(Entry a, Entry b) {
                    return Long.compare(a.last, b.last);
                }
            });
            for (Entry entry : loaded) {
                index.put(entry.key, entry);
                byKey.put(entry.key, entry);
                if (entry.name != null) {
                    keyByName.put(entry.name.toLowerCase(Locale.ROOT), entry.key);
                }
            }
            if (loaded.size() > byKey.size()) {
                dirty = true;
            }
        } catch (Throwable thrown) {
            Log.once("seen file read", thrown);
        }
    }

    private String json() {
        StringBuilder out = new StringBuilder("{\n  \"players\": [");
        int i = 0;
        for (Entry entry : byKey.values()) {
            out.append(i++ == 0 ? "\n    " : ",\n    ");
            out.append("{\"key\":").append(Json.quote(entry.key));
            if (entry.name != null) {
                out.append(",\"name\":").append(Json.quote(entry.name));
            }
            out.append(",\"count\":").append(entry.count);
            out.append(",\"first\":").append(entry.first);
            out.append(",\"last\":").append(entry.last).append('}');
        }
        out.append(i == 0 ? "]\n}\n" : "\n  ]\n}\n");
        return out.toString();
    }
}
