package com.safedetect.agent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Friends, overlay bounds, and optional Hypixel API key. Lives next to the flags file.
 */
final class Settings {
    private static final String[] DEFAULT_FRIENDS = { "trnsmt", "zoxide" };

    private final File file;
    final Set<String> friends = new LinkedHashSet<String>();
    int overlayX = Integer.MIN_VALUE;
    int overlayY = Integer.MIN_VALUE;
    int overlayW = 760;
    int overlayH = 520;
    String hypixelKey = "";
    String sniperKey = "";
    String sniperUrl = "";
    boolean borderless = true;
    String urchinKey = "";
    String seraphKey = "";
    String discordAppId = "";
    String auroraKey = "";
    boolean tabMarks = true;
    boolean alertsChat = true;
    boolean alertSound = true;
    final java.util.Map<String, String> nicks = new java.util.LinkedHashMap<String, String>();

    Settings(File gameDir) {
        file = new File(gameDir, "config/safedetect-settings.json");
        load();
        if (friends.isEmpty()) {
            for (String name : DEFAULT_FRIENDS) {
                friends.add(name);
            }
            save();
        }
    }

    boolean isFriend(String name) {
        return name != null && friends.contains(name.toLowerCase(Locale.ROOT));
    }

    boolean addFriend(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        boolean added = friends.add(name.toLowerCase(Locale.ROOT));
        if (added) {
            save();
        }
        return added;
    }

    boolean removeFriend(String name) {
        if (name == null) {
            return false;
        }
        boolean removed = friends.remove(name.toLowerCase(Locale.ROOT));
        if (removed) {
            save();
        }
        return removed;
    }

    void setKey(String key) {
        hypixelKey = key == null ? "" : key.trim();
        save();
    }

    boolean hasKey() {
        return hypixelKey != null && !hypixelKey.isEmpty();
    }

    void setSniperKey(String key) {
        sniperKey = key == null ? "" : key.trim();
        save();
    }

    void setSniperUrl(String url) {
        sniperUrl = url == null ? "" : url.trim();
        save();
    }

    boolean hasSniper() {
        return (sniperKey != null && !sniperKey.isEmpty()) || (sniperUrl != null && !sniperUrl.isEmpty());
    }

    String sniperRequest(java.util.UUID id, String name) {
        String template = sniperUrl != null && !sniperUrl.isEmpty() ? sniperUrl : AntiSniper.DEFAULT_URL;
        return AntiSniper.expand(template, sniperKey, id, name);
    }

    void setUrchin(String key) {
        urchinKey = key == null ? "" : key.trim();
        save();
    }

    void setSeraph(String key) {
        seraphKey = key == null ? "" : key.trim();
        save();
    }

    void setDiscord(String id) {
        discordAppId = id == null ? "" : id.trim();
        save();
    }

    void setAurora(String key) {
        auroraKey = key == null ? "" : key.trim();
        save();
    }

    boolean hasAurora() {
        return auroraKey != null && !auroraKey.isEmpty();
    }

    String realName(String shown) {
        if (shown == null) {
            return null;
        }
        String mapped = nicks.get(shown.toLowerCase(Locale.ROOT));
        return mapped != null ? mapped : shown;
    }

    void mapNick(String shown, String real) {
        if (shown == null || real == null) {
            return;
        }
        nicks.put(shown.toLowerCase(Locale.ROOT), real);
        save();
    }

    void setBorderless(boolean on) {
        if (borderless == on) {
            return;
        }
        borderless = on;
        save();
    }

    void setTabMarks(boolean on) {
        if (tabMarks == on) {
            return;
        }
        tabMarks = on;
        save();
    }

    void setAlertsChat(boolean on) {
        if (alertsChat == on) {
            return;
        }
        alertsChat = on;
        save();
    }

    void setAlertSound(boolean on) {
        if (alertSound == on) {
            return;
        }
        alertSound = on;
        save();
    }

    void setOverlay(int x, int y, int w, int h) {
        if (x == overlayX && y == overlayY && w == overlayW && h == overlayH) {
            return;
        }
        overlayX = x;
        overlayY = y;
        overlayW = w;
        overlayH = h;
        save();
    }

    boolean hasOverlay() {
        return overlayX != Integer.MIN_VALUE && overlayW >= 200 && overlayH >= 200;
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
            Map<String, Object> map = (Map<String, Object>) root;
            if (map.get("friends") instanceof List) {
                for (Object entry : (List<Object>) map.get("friends")) {
                    if (entry instanceof String && !((String) entry).isEmpty()) {
                        friends.add(((String) entry).toLowerCase(Locale.ROOT));
                    }
                }
            }
            if (map.get("overlayX") instanceof Number) {
                overlayX = ((Number) map.get("overlayX")).intValue();
            }
            if (map.get("overlayY") instanceof Number) {
                overlayY = ((Number) map.get("overlayY")).intValue();
            }
            if (map.get("overlayW") instanceof Number) {
                overlayW = ((Number) map.get("overlayW")).intValue();
            }
            if (map.get("overlayH") instanceof Number) {
                overlayH = ((Number) map.get("overlayH")).intValue();
            }
            if (map.get("hypixelKey") instanceof String) {
                hypixelKey = (String) map.get("hypixelKey");
            }
            if (map.get("sniperKey") instanceof String) {
                sniperKey = (String) map.get("sniperKey");
            }
            if (map.get("sniperUrl") instanceof String) {
                sniperUrl = (String) map.get("sniperUrl");
            }
            if (map.get("borderless") instanceof Boolean) {
                borderless = Boolean.TRUE.equals(map.get("borderless"));
            }
            if (map.get("urchinKey") instanceof String) {
                urchinKey = (String) map.get("urchinKey");
            }
            if (map.get("seraphKey") instanceof String) {
                seraphKey = (String) map.get("seraphKey");
            }
            if (map.get("discordAppId") instanceof String) {
                discordAppId = (String) map.get("discordAppId");
            }
            if (map.get("auroraKey") instanceof String) {
                auroraKey = (String) map.get("auroraKey");
            }
            if (map.get("tabMarks") instanceof Boolean) {
                tabMarks = Boolean.TRUE.equals(map.get("tabMarks"));
            }
            if (map.get("alertsChat") instanceof Boolean) {
                alertsChat = Boolean.TRUE.equals(map.get("alertsChat"));
            }
            if (map.get("alertSound") instanceof Boolean) {
                alertSound = Boolean.TRUE.equals(map.get("alertSound"));
            }
            if (map.get("nicks") instanceof Map) {
                for (Map.Entry<String, Object> nick : ((Map<String, Object>) map.get("nicks")).entrySet()) {
                    if (nick.getValue() instanceof String) {
                        nicks.put(nick.getKey().toLowerCase(Locale.ROOT), (String) nick.getValue());
                    }
                }
            }
        } catch (Throwable thrown) {
            Log.once("settings read", thrown);
        }
    }

    synchronized void save() {
        StringBuilder out = new StringBuilder("{\n  \"friends\": [");
        int i = 0;
        for (String name : friends) {
            out.append(i++ == 0 ? "\n    " : ",\n    ").append(Json.quote(name));
        }
        out.append(i == 0 ? "],\n" : "\n  ],\n");
        out.append("  \"overlayX\": ").append(overlayX).append(",\n");
        out.append("  \"overlayY\": ").append(overlayY).append(",\n");
        out.append("  \"overlayW\": ").append(overlayW).append(",\n");
        out.append("  \"overlayH\": ").append(overlayH).append(",\n");
        out.append("  \"hypixelKey\": ").append(Json.quote(hypixelKey == null ? "" : hypixelKey)).append(",\n");
        out.append("  \"sniperKey\": ").append(Json.quote(sniperKey == null ? "" : sniperKey)).append(",\n");
        out.append("  \"sniperUrl\": ").append(Json.quote(sniperUrl == null ? "" : sniperUrl)).append(",\n");
        out.append("  \"borderless\": ").append(borderless).append(",\n");
        out.append("  \"urchinKey\": ").append(Json.quote(urchinKey == null ? "" : urchinKey)).append(",\n");
        out.append("  \"seraphKey\": ").append(Json.quote(seraphKey == null ? "" : seraphKey)).append(",\n");
        out.append("  \"discordAppId\": ").append(Json.quote(discordAppId == null ? "" : discordAppId)).append(",\n");
        out.append("  \"auroraKey\": ").append(Json.quote(auroraKey == null ? "" : auroraKey)).append(",\n");
        out.append("  \"tabMarks\": ").append(tabMarks).append(",\n");
        out.append("  \"alertsChat\": ").append(alertsChat).append(",\n");
        out.append("  \"alertSound\": ").append(alertSound).append(",\n");
        out.append("  \"nicks\": {");
        int n = 0;
        for (java.util.Map.Entry<String, String> nick : nicks.entrySet()) {
            out.append(n++ == 0 ? "\n    " : ",\n    ").append(Json.quote(nick.getKey())).append(": ")
                    .append(Json.quote(nick.getValue()));
        }
        out.append(n == 0 ? "}\n}\n" : "\n  }\n}\n");
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            parent.mkdirs();
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(out.toString());
        } catch (Throwable thrown) {
            Log.once("settings write", thrown);
        }
    }
}
