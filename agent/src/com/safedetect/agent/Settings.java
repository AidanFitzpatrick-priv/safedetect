package com.safedetect.agent;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Friends, preferences, game options, and optional API keys. Lives next to the flags file. Only the game
 * process writes this file; the overlay keeps its own look and window bounds in its own file. The
 * overlayX/Y/W/H fields are kept so older files round-trip and the overlay can migrate them once.
 */
final class Settings {
    private static final String[] DEFAULT_FRIENDS = { "trnsmt", "zoxide" };
    static final String[] SENSITIVITIES = { "lenient", "normal", "strict" };

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
    String discordAppId = "";
    String auroraKey = "";
    boolean tabMarks = true;
    boolean alertsChat = true;
    boolean alertSound = true;
    boolean chatHovers = true;
    final java.util.Map<String, String> nicks = new java.util.LinkedHashMap<String, String>();

    boolean dodgeEnabled = true;
    double dodgeFkdr = 8.0;
    int dodgeStars = 0;
    int dodgeSniper = 60;
    boolean dodgeBlacklist = true;
    boolean dodgeFlagged = true;
    boolean dodgeTags = true;

    final Set<String> disabledChecks = new LinkedHashSet<String>();
    double reachFlag = 3.35;
    double kaAngle = 80.0;
    int acMinCps = 15;
    double speedLimit = 0.42;
    String sensitivity = "normal";

    boolean updateCheck = true;
    long lastUpdateCheck;

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
        if (real.equals(nicks.put(shown.toLowerCase(Locale.ROOT), real))) {
            return;
        }
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

    boolean checkEnabled(FlagStore.Flag flag) {
        return flag == null || !disabledChecks.contains(flag.name());
    }

    /** Returns false when the flag name is unknown. */
    boolean setCheckEnabled(String flag, boolean on) {
        FlagStore.Flag parsed = flag(flag);
        if (parsed == null) {
            return false;
        }
        boolean changed = on ? disabledChecks.remove(parsed.name()) : disabledChecks.add(parsed.name());
        if (changed) {
            save();
        }
        return true;
    }

    void setLastUpdateCheck(long when) {
        lastUpdateCheck = when;
        save();
    }

    /**
     * Applies one game option sent by the overlay. Unknown names and badly typed values are rejected so
     * the overlay can only change what the settings panel offers.
     */
    boolean set(String name, Object value) {
        if (name == null) {
            return false;
        }
        if (name.startsWith("check.")) {
            return value instanceof Boolean && setCheckEnabled(name.substring(6), Boolean.TRUE.equals(value));
        }
        if ("tabMarks".equals(name) || "alertsChat".equals(name) || "alertSound".equals(name)
                || "chatHovers".equals(name)
                || "borderless".equals(name) || "dodgeEnabled".equals(name) || "dodgeBlacklist".equals(name)
                || "dodgeFlagged".equals(name) || "dodgeTags".equals(name) || "updateCheck".equals(name)) {
            if (!(value instanceof Boolean)) {
                return false;
            }
            boolean on = Boolean.TRUE.equals(value);
            if ("tabMarks".equals(name)) {
                tabMarks = on;
            } else if ("alertsChat".equals(name)) {
                alertsChat = on;
            } else if ("alertSound".equals(name)) {
                alertSound = on;
            } else if ("chatHovers".equals(name)) {
                chatHovers = on;
            } else if ("borderless".equals(name)) {
                borderless = on;
            } else if ("dodgeEnabled".equals(name)) {
                dodgeEnabled = on;
            } else if ("dodgeBlacklist".equals(name)) {
                dodgeBlacklist = on;
            } else if ("dodgeFlagged".equals(name)) {
                dodgeFlagged = on;
            } else if ("dodgeTags".equals(name)) {
                dodgeTags = on;
            } else {
                updateCheck = on;
            }
            save();
            return true;
        }
        if ("sensitivity".equals(name)) {
            if (!(value instanceof String)) {
                return false;
            }
            String next = ((String) value).toLowerCase(Locale.ROOT);
            for (String allowed : SENSITIVITIES) {
                if (allowed.equals(next)) {
                    sensitivity = next;
                    save();
                    return true;
                }
            }
            return false;
        }
        if (!(value instanceof Number)) {
            return false;
        }
        double n = ((Number) value).doubleValue();
        if ("dodgeFkdr".equals(name) && n >= 0 && n <= 100) {
            dodgeFkdr = n;
        } else if ("dodgeStars".equals(name) && n >= 0 && n <= 5000) {
            dodgeStars = (int) n;
        } else if ("dodgeSniper".equals(name) && n >= 0 && n <= 100) {
            dodgeSniper = (int) n;
        } else if ("reachFlag".equals(name) && n >= 3.0 && n <= 6.0) {
            reachFlag = n;
        } else if ("kaAngle".equals(name) && n >= 30 && n <= 180) {
            kaAngle = n;
        } else if ("acMinCps".equals(name) && n >= 8 && n <= 30) {
            acMinCps = (int) n;
        } else if ("speedLimit".equals(name) && n >= 0.3 && n <= 1.5) {
            speedLimit = n;
        } else {
            return false;
        }
        save();
        return true;
    }

    /** Every option the overlay settings panel edits, as a JSON object. */
    String optionsJson() {
        StringBuilder out = new StringBuilder("{");
        out.append("\"tabMarks\":").append(tabMarks);
        out.append(",\"alertsChat\":").append(alertsChat);
        out.append(",\"alertSound\":").append(alertSound);
        out.append(",\"chatHovers\":").append(chatHovers);
        out.append(",\"borderless\":").append(borderless);
        out.append(",\"dodgeEnabled\":").append(dodgeEnabled);
        out.append(",\"dodgeFkdr\":").append(dodgeFkdr);
        out.append(",\"dodgeStars\":").append(dodgeStars);
        out.append(",\"dodgeSniper\":").append(dodgeSniper);
        out.append(",\"dodgeBlacklist\":").append(dodgeBlacklist);
        out.append(",\"dodgeFlagged\":").append(dodgeFlagged);
        out.append(",\"dodgeTags\":").append(dodgeTags);
        out.append(",\"reachFlag\":").append(reachFlag);
        out.append(",\"kaAngle\":").append(kaAngle);
        out.append(",\"acMinCps\":").append(acMinCps);
        out.append(",\"speedLimit\":").append(speedLimit);
        out.append(",\"sensitivity\":").append(Json.quote(sensitivity));
        out.append(",\"updateCheck\":").append(updateCheck);
        out.append(",\"disabledChecks\":[");
        int i = 0;
        for (String flag : disabledChecks) {
            out.append(i++ == 0 ? "" : ",").append(Json.quote(flag));
        }
        return out.append("]}").toString();
    }

    static FlagStore.Flag flag(String name) {
        if (name == null) {
            return null;
        }
        try {
            return FlagStore.Flag.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** Names match {@link Hud#KEY_NAMES}. */
    boolean hasKeyNamed(String name) {
        String value = keyNamed(name);
        return value != null && !value.isEmpty();
    }

    private String keyNamed(String name) {
        if ("hypixel".equals(name)) {
            return hypixelKey;
        }
        if ("sniperKey".equals(name)) {
            return sniperKey;
        }
        if ("sniperUrl".equals(name)) {
            return sniperUrl;
        }
        if ("urchin".equals(name)) {
            return urchinKey;
        }
        if ("aurora".equals(name)) {
            return auroraKey;
        }
        if ("discord".equals(name)) {
            return discordAppId;
        }
        return null;
    }

    /** Returns false for an unknown name. An empty value clears the key. */
    boolean setKeyNamed(String name, String value) {
        if ("hypixel".equals(name)) {
            setKey(value);
        } else if ("sniperKey".equals(name)) {
            setSniperKey(value);
        } else if ("sniperUrl".equals(name)) {
            setSniperUrl(value);
        } else if ("urchin".equals(name)) {
            setUrchin(value);
        } else if ("aurora".equals(name)) {
            setAurora(value);
        } else if ("discord".equals(name)) {
            setDiscord(value);
        } else {
            return false;
        }
        return true;
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
            overlayX = intOf(map, "overlayX", overlayX);
            overlayY = intOf(map, "overlayY", overlayY);
            overlayW = intOf(map, "overlayW", overlayW);
            overlayH = intOf(map, "overlayH", overlayH);
            hypixelKey = strOf(map, "hypixelKey", hypixelKey);
            sniperKey = strOf(map, "sniperKey", sniperKey);
            sniperUrl = strOf(map, "sniperUrl", sniperUrl);
            borderless = boolOf(map, "borderless", borderless);
            urchinKey = strOf(map, "urchinKey", urchinKey);
            discordAppId = strOf(map, "discordAppId", discordAppId);
            auroraKey = strOf(map, "auroraKey", auroraKey);
            tabMarks = boolOf(map, "tabMarks", tabMarks);
            alertsChat = boolOf(map, "alertsChat", alertsChat);
            alertSound = boolOf(map, "alertSound", alertSound);
            chatHovers = boolOf(map, "chatHovers", chatHovers);
            dodgeEnabled = boolOf(map, "dodgeEnabled", dodgeEnabled);
            dodgeFkdr = dblOf(map, "dodgeFkdr", dodgeFkdr);
            dodgeStars = intOf(map, "dodgeStars", dodgeStars);
            dodgeSniper = intOf(map, "dodgeSniper", dodgeSniper);
            dodgeBlacklist = boolOf(map, "dodgeBlacklist", dodgeBlacklist);
            dodgeFlagged = boolOf(map, "dodgeFlagged", dodgeFlagged);
            dodgeTags = boolOf(map, "dodgeTags", dodgeTags);
            reachFlag = dblOf(map, "reachFlag", reachFlag);
            kaAngle = dblOf(map, "kaAngle", kaAngle);
            acMinCps = intOf(map, "acMinCps", acMinCps);
            speedLimit = dblOf(map, "speedLimit", speedLimit);
            sensitivity = strOf(map, "sensitivity", sensitivity);
            updateCheck = boolOf(map, "updateCheck", updateCheck);
            if (map.get("lastUpdateCheck") instanceof Number) {
                lastUpdateCheck = ((Number) map.get("lastUpdateCheck")).longValue();
            }
            if (map.get("disabledChecks") instanceof List) {
                for (Object entry : (List<Object>) map.get("disabledChecks")) {
                    FlagStore.Flag flag = entry instanceof String ? flag((String) entry) : null;
                    if (flag != null) {
                        disabledChecks.add(flag.name());
                    }
                }
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

    private static int intOf(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static double dblOf(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    private static boolean boolOf(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value instanceof Boolean ? Boolean.TRUE.equals(value) : fallback;
    }

    private static String strOf(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : fallback;
    }

    synchronized void save() {
        StringBuilder out = new StringBuilder("{\n  \"friends\": [");
        int i = 0;
        for (String name : friends) {
            out.append(i++ == 0 ? "\n    " : ",\n    ").append(Json.quote(name));
        }
        out.append(i == 0 ? "],\n" : "\n  ],\n");
        line(out, "overlayX", String.valueOf(overlayX));
        line(out, "overlayY", String.valueOf(overlayY));
        line(out, "overlayW", String.valueOf(overlayW));
        line(out, "overlayH", String.valueOf(overlayH));
        line(out, "hypixelKey", Json.quote(hypixelKey == null ? "" : hypixelKey));
        line(out, "sniperKey", Json.quote(sniperKey == null ? "" : sniperKey));
        line(out, "sniperUrl", Json.quote(sniperUrl == null ? "" : sniperUrl));
        line(out, "borderless", String.valueOf(borderless));
        line(out, "urchinKey", Json.quote(urchinKey == null ? "" : urchinKey));
        line(out, "discordAppId", Json.quote(discordAppId == null ? "" : discordAppId));
        line(out, "auroraKey", Json.quote(auroraKey == null ? "" : auroraKey));
        line(out, "tabMarks", String.valueOf(tabMarks));
        line(out, "alertsChat", String.valueOf(alertsChat));
        line(out, "alertSound", String.valueOf(alertSound));
        line(out, "chatHovers", String.valueOf(chatHovers));
        line(out, "dodgeEnabled", String.valueOf(dodgeEnabled));
        line(out, "dodgeFkdr", String.valueOf(dodgeFkdr));
        line(out, "dodgeStars", String.valueOf(dodgeStars));
        line(out, "dodgeSniper", String.valueOf(dodgeSniper));
        line(out, "dodgeBlacklist", String.valueOf(dodgeBlacklist));
        line(out, "dodgeFlagged", String.valueOf(dodgeFlagged));
        line(out, "dodgeTags", String.valueOf(dodgeTags));
        line(out, "reachFlag", String.valueOf(reachFlag));
        line(out, "kaAngle", String.valueOf(kaAngle));
        line(out, "acMinCps", String.valueOf(acMinCps));
        line(out, "speedLimit", String.valueOf(speedLimit));
        line(out, "sensitivity", Json.quote(sensitivity == null ? "normal" : sensitivity));
        line(out, "updateCheck", String.valueOf(updateCheck));
        line(out, "lastUpdateCheck", String.valueOf(lastUpdateCheck));
        out.append("  \"disabledChecks\": [");
        int d = 0;
        for (String flag : disabledChecks) {
            out.append(d++ == 0 ? "" : ", ").append(Json.quote(flag));
        }
        out.append("],\n");
        out.append("  \"nicks\": {");
        int n = 0;
        for (java.util.Map.Entry<String, String> nick : nicks.entrySet()) {
            out.append(n++ == 0 ? "\n    " : ",\n    ").append(Json.quote(nick.getKey())).append(": ")
                    .append(Json.quote(nick.getValue()));
        }
        out.append(n == 0 ? "}\n}\n" : "\n  }\n}\n");
        Files2.writeLater(file, out.toString());
    }

    private static void line(StringBuilder out, String key, String value) {
        out.append("  ").append(Json.quote(key)).append(": ").append(value).append(",\n");
    }
}
