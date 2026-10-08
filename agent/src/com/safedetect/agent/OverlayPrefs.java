package com.safedetect.agent;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * How the overlay looks: bounds, theme, columns and modes. Owned by the overlay process alone, in
 * {@code config/safedetect-overlay.json}. Older files held only {@code {x,y,w,h}}; those values are kept.
 */
final class OverlayPrefs {
    static final int VERSION = 2;
    static final long SAVE_DELAY_MS = 500L;
    static final String MINI_THREATS = "threats";
    static final String MINI_ALL = "all";

    int x = Integer.MIN_VALUE;
    int y = Integer.MIN_VALUE;
    int w = 760;
    int h = 500;
    String theme = Theme.DARK;
    String accent = "";
    int fontScale = 100;
    int opacity = 100;
    boolean mini;
    String miniFilter = MINI_THREATS;
    boolean groupByTeam;
    boolean pinFlagged = true;
    String density = "normal";
    final List<String> columns = new ArrayList<String>(Arrays.asList(OverlayColumns.DEFAULT));
    final Map<String, Integer> widths = new LinkedHashMap<String, Integer>();
    String sortColumn = "";
    boolean sortAscending;

    private File file;
    private Timer timer;
    private TimerTask pending;

    static OverlayPrefs load(File dataDir) {
        OverlayPrefs prefs = new OverlayPrefs();
        prefs.file = new File(dataDir, "config/safedetect-overlay.json");
        Map<String, Object> map = read(prefs.file);
        if (map != null) {
            prefs.apply(map);
        } else {
            Map<String, Object> legacy = read(new File(dataDir, "config/safedetect-settings.json"));
            if (legacy != null) {
                prefs.x = intOf(legacy, "overlayX", prefs.x);
                prefs.y = intOf(legacy, "overlayY", prefs.y);
                prefs.w = intOf(legacy, "overlayW", prefs.w);
                prefs.h = intOf(legacy, "overlayH", prefs.h);
            }
        }
        return prefs;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> read(File source) {
        if (source == null || !source.isFile()) {
            return null;
        }
        try {
            Object root = Json.parse(new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8));
            return root instanceof Map ? (Map<String, Object>) root : null;
        } catch (Throwable thrown) {
            Log.once("overlay prefs read", thrown);
            return null;
        }
    }

    /** Reads a parsed prefs file; also accepts the old bounds-only layout. */
    @SuppressWarnings("unchecked")
    void apply(Map<String, Object> map) {
        x = intOf(map, "x", x);
        y = intOf(map, "y", y);
        w = intOf(map, "w", w);
        h = intOf(map, "h", h);
        theme = Theme.canonical(strOf(map, "theme", theme));
        accent = strOf(map, "accent", accent);
        fontScale = Theme.clampFont(intOf(map, "fontScale", fontScale));
        opacity = Math.max(40, Math.min(100, intOf(map, "opacity", opacity)));
        mini = boolOf(map, "mini", mini);
        miniFilter = MINI_ALL.equals(strOf(map, "miniFilter", miniFilter)) ? MINI_ALL : MINI_THREATS;
        groupByTeam = boolOf(map, "groupByTeam", groupByTeam);
        pinFlagged = boolOf(map, "pinFlagged", pinFlagged);
        String nextDensity = strOf(map, "density", density);
        density = Arrays.asList(Theme.DENSITIES).contains(nextDensity) ? nextDensity : "normal";
        if (map.get("columns") instanceof List) {
            List<String> ids = new ArrayList<String>();
            for (Object id : (List<Object>) map.get("columns")) {
                if (id instanceof String) {
                    ids.add((String) id);
                }
            }
            columns.clear();
            columns.addAll(OverlayColumns.sanitize(ids));
        }
        if (map.get("widths") instanceof Map) {
            widths.clear();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) map.get("widths")).entrySet()) {
                if (entry.getValue() instanceof Number && OverlayColumns.byId(entry.getKey()) != null) {
                    widths.put(entry.getKey(), Math.max(24, ((Number) entry.getValue()).intValue()));
                }
            }
        }
        String sort = strOf(map, "sortColumn", sortColumn);
        sortColumn = OverlayColumns.byId(sort) != null ? sort : "";
        sortAscending = boolOf(map, "sortAscending", sortAscending);
    }

    String json() {
        StringBuilder out = new StringBuilder("{\n");
        out.append("  \"version\": ").append(VERSION).append(",\n");
        out.append("  \"x\": ").append(x).append(",\n");
        out.append("  \"y\": ").append(y).append(",\n");
        out.append("  \"w\": ").append(w).append(",\n");
        out.append("  \"h\": ").append(h).append(",\n");
        out.append("  \"theme\": ").append(Json.quote(theme)).append(",\n");
        out.append("  \"accent\": ").append(Json.quote(accent == null ? "" : accent)).append(",\n");
        out.append("  \"fontScale\": ").append(fontScale).append(",\n");
        out.append("  \"opacity\": ").append(opacity).append(",\n");
        out.append("  \"mini\": ").append(mini).append(",\n");
        out.append("  \"miniFilter\": ").append(Json.quote(miniFilter)).append(",\n");
        out.append("  \"groupByTeam\": ").append(groupByTeam).append(",\n");
        out.append("  \"pinFlagged\": ").append(pinFlagged).append(",\n");
        out.append("  \"density\": ").append(Json.quote(density)).append(",\n");
        out.append("  \"columns\": [");
        for (int i = 0; i < columns.size(); i++) {
            out.append(i == 0 ? "" : ", ").append(Json.quote(columns.get(i)));
        }
        out.append("],\n  \"widths\": {");
        int i = 0;
        for (Map.Entry<String, Integer> entry : widths.entrySet()) {
            out.append(i++ == 0 ? "" : ", ").append(Json.quote(entry.getKey())).append(": ").append(entry.getValue());
        }
        out.append("},\n");
        out.append("  \"sortColumn\": ").append(Json.quote(sortColumn)).append(",\n");
        out.append("  \"sortAscending\": ").append(sortAscending).append("\n}\n");
        return out.toString();
    }

    void resetColumns() {
        columns.clear();
        columns.addAll(Arrays.asList(OverlayColumns.DEFAULT));
        widths.clear();
        sortColumn = "";
        sortAscending = false;
    }

    boolean hasBounds() {
        return x != Integer.MIN_VALUE && w >= 200 && h >= 80;
    }

    /** Coalesces bursts of changes (slider drags, column resizes) into one write. */
    synchronized void saveLater() {
        if (file == null) {
            return;
        }
        if (timer == null) {
            timer = new Timer("SafeDetect-Prefs", true);
        }
        if (pending != null) {
            pending.cancel();
        }
        pending = new TimerTask() {
            @Override
            public void run() {
                save();
            }
        };
        timer.schedule(pending, SAVE_DELAY_MS);
    }

    void save() {
        if (file != null) {
            Files2.writeAtomic(file, json());
        }
    }

    private static int intOf(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static boolean boolOf(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value instanceof Boolean ? Boolean.TRUE.equals(value) : fallback;
    }

    private static String strOf(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : fallback;
    }
}
