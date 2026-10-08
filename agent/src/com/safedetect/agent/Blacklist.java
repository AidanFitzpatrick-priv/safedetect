package com.safedetect.agent;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Local cheater/sniper list, Cubelify/Mellow style. Names only — not a live check.
 */
final class Blacklist {
    private final File file;
    final Set<String> names = new LinkedHashSet<String>();

    Blacklist(File gameDir) {
        file = new File(gameDir, "config/safedetect-blacklist.txt");
        load(file);
    }

    File file() {
        return file;
    }

    void reload() {
        names.clear();
        load(file);
    }

    boolean contains(String name) {
        return name != null && names.contains(name.toLowerCase(Locale.ROOT));
    }

    boolean add(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        boolean added = names.add(name.toLowerCase(Locale.ROOT));
        if (added) {
            save();
        }
        return added;
    }

    boolean remove(String name) {
        if (name == null) {
            return false;
        }
        boolean removed = names.remove(name.toLowerCase(Locale.ROOT));
        if (removed) {
            save();
        }
        return removed;
    }

    int importFile(File other) {
        int before = names.size();
        load(other);
        if (names.size() != before) {
            save();
        }
        return names.size() - before;
    }

    @SuppressWarnings("unchecked")
    private void load(File from) {
        if (from == null || !from.isFile()) {
            return;
        }
        try {
            String text = new String(Files.readAllBytes(from.toPath()), StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) {
                return;
            }
            if (text.startsWith("{") || text.startsWith("[")) {
                Object root = Json.parse(text);
                if (root instanceof List) {
                    addList((List<Object>) root);
                } else if (root instanceof Map) {
                    Map<String, Object> map = (Map<String, Object>) root;
                    for (String key : new String[] { "players", "names", "blacklist", "users", "list" }) {
                        Object value = map.get(key);
                        if (value instanceof List) {
                            addList((List<Object>) value);
                        }
                    }
                }
                return;
            }
            String[] lines = text.split("\\r?\\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                    continue;
                }
                int space = line.indexOf(' ');
                if (space > 0 && !line.startsWith("http")) {
                    line = line.substring(0, space);
                }
                addQuiet(line);
            }
        } catch (Throwable thrown) {
            Log.once("blacklist read", thrown);
        }
    }

    private void addList(List<Object> list) {
        for (Object entry : list) {
            if (entry instanceof String) {
                addQuiet((String) entry);
            } else if (entry instanceof Map) {
                Object name = ((Map<?, ?>) entry).get("name");
                if (name == null) {
                    name = ((Map<?, ?>) entry).get("username");
                }
                if (name == null) {
                    name = ((Map<?, ?>) entry).get("ign");
                }
                if (name instanceof String) {
                    addQuiet((String) name);
                }
            }
        }
    }

    private void addQuiet(String name) {
        if (name == null) {
            return;
        }
        String clean = name.trim().replaceAll("\u00a7.", "");
        if (clean.length() < 3 || clean.length() > 16) {
            return;
        }
        names.add(clean.toLowerCase(Locale.ROOT));
    }

    private void save() {
        StringBuilder out = new StringBuilder("# SafeDetect local blacklist — one name per line\n");
        List<String> sorted = new ArrayList<String>(names);
        for (int i = 0; i < sorted.size(); i++) {
            out.append(sorted.get(i)).append('\n');
        }
        Files2.writeLater(file, out.toString());
    }
}
