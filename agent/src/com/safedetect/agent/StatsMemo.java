package com.safedetect.agent;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cached Hypixel Bedwars lookups for plugins. One fetch per name for five minutes so TalkLine, StarTab
 * and TeamHeat do not stampede the API.
 */
final class StatsMemo {
    private static final long TTL_MS = 5L * 60L * 1000L;
    private static final ConcurrentHashMap<String, Hit> CACHE = new ConcurrentHashMap<String, Hit>();

    private static final class Hit {
        final BedStats stats;
        final long at;

        Hit(BedStats stats, long at) {
            this.stats = stats;
            this.at = at;
        }
    }

    private StatsMemo() {
    }

    static BedStats get(PluginApi api, String name) {
        if (api == null || name == null || name.isEmpty()) {
            return null;
        }
        String key = name.toLowerCase(Locale.ROOT);
        Hit hit = CACHE.get(key);
        long now = System.currentTimeMillis();
        if (hit != null && now - hit.at < TTL_MS) {
            return hit.stats;
        }
        String hypixel = api.hypixelKey();
        if (hypixel == null || hypixel.isEmpty()) {
            return null;
        }
        String uuid = uuidOf(api, name);
        if (uuid == null || uuid.isEmpty()) {
            return null;
        }
        String raw = api.httpGet("https://api.hypixel.net/v2/player?uuid=" + uuid.replace("-", ""), "API-Key", hypixel);
        BedStats stats = BedStats.parse(raw, name);
        if (stats != null) {
            CACHE.put(key, new Hit(stats, now));
        }
        return stats;
    }

    static void forget(String name) {
        if (name != null) {
            CACHE.remove(name.toLowerCase(Locale.ROOT));
        }
    }

    @SuppressWarnings("unchecked")
    private static String uuidOf(PluginApi api, String name) {
        String mojang = api.httpGet("https://api.mojang.com/users/profiles/minecraft/" + PluginPack.encode(name),
                null, null);
        if (mojang == null || mojang.isEmpty()) {
            return null;
        }
        try {
            Object parsed = Json.parse(mojang);
            if (parsed instanceof Map && ((Map<String, Object>) parsed).get("id") instanceof String) {
                return (String) ((Map<String, Object>) parsed).get("id");
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
