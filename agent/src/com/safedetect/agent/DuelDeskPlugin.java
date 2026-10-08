package com.safedetect.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Opponent stats, session record, Discord webhook, and duel quick-join. Commands under {@code /sd duel}.
 */
final class DuelDeskPlugin implements Plugin, PluginEvents {
    private static final Pattern OPPONENT = Pattern.compile("(?i)^Opponent:\\s*(?:\\[[^\\]]+\\]\\s*)*([A-Za-z0-9_]{1,16})");
    private static final Pattern WINNER = Pattern.compile("WINNER!", Pattern.CASE_INSENSITIVE);
    private static final Pattern NO_STATS = Pattern.compile("(?i)^No stats will be affected in this round!");
    private static final String[] LAYOUTS = { "full", "minimal", "numbers" };
    private static final Map<String, String> QUEUES = new LinkedHashMap<String, String>();

    static {
        QUEUES.put("classic", "duels_classic_duel");
        QUEUES.put("bridge", "duels_bridge_duel");
        QUEUES.put("sw", "duels_sw_duel");
        QUEUES.put("uhc", "duels_uhc_duel");
        QUEUES.put("op", "duels_op_duel");
        QUEUES.put("sumo", "duels_sumo_duel");
        QUEUES.put("combo", "duels_combo_duel");
        QUEUES.put("bow", "duels_bow_duel");
        QUEUES.put("nodebuff", "duels_potion_duel");
        QUEUES.put("blitz", "duels_blitz_duel");
        QUEUES.put("mw", "duels_mw_duel");
        QUEUES.put("parkour", "duels_parkour_eight");
        QUEUES.put("spleef", "duels_spleef_duel");
        QUEUES.put("quake", "duels_quake_duel");
        QUEUES.put("classic2s", "duels_classic_doubles");
        QUEUES.put("sw2s", "duels_sw_doubles");
        QUEUES.put("bridge2s", "duels_bridge_doubles");
        QUEUES.put("op2s", "duels_op_doubles");
        QUEUES.put("uhc2s", "duels_uhc_doubles");
        QUEUES.put("uhc4s", "duels_uhc_four");
        QUEUES.put("bw", "bedwars_two_one");
        QUEUES.put("bwrush", "bedwars_two_one_rush");
    }

    private PluginApi api;
    private boolean inMatch;
    private String opponent;
    private String lastOpponent;
    private boolean unranked;
    private String unrankedWhy;
    private int wins;
    private int losses;
    private long started;

    @Override
    public PluginInfo info() {
        return new PluginInfo("duel", "DuelDesk", "SafeDetect", "1.0.0",
                "Opponent stats on duel start, session W/L, Discord webhook, and quick-join queues.",
                "Hypixel API key (Keys page). Optional Vega/bordic key and Discord webhook.",
                "/sd duel layout|session|webhook|q <mode>|setkey");
    }

    @Override
    public void start(PluginApi api) {
        this.api = api;
    }

    @Override
    public void stop() {
        api = null;
        inMatch = false;
        opponent = null;
    }

    @Override
    public boolean command(String[] parts) {
        if (parts.length < 3) {
            return false;
        }
        String sub = parts[2].toLowerCase(Locale.ROOT);
        if ("layout".equals(sub) && parts.length >= 4) {
            String style = parts[3].toLowerCase(Locale.ROOT);
            if (!validLayout(style)) {
                api.chat("\u00a7cUsage: /sd duel layout <full|minimal|numbers>");
                return true;
            }
            api.config("duel.layout", style);
            api.chat("\u00a7aStats layout set to \u00a7f" + style + "\u00a7a.");
            return true;
        }
        if ("session".equals(sub)) {
            api.chat(sessionLine());
            return true;
        }
        if ("webhook".equals(sub) && parts.length >= 4) {
            String url = parts[3].trim();
            if (!url.startsWith("https://discord.com/api/webhooks/")
                    && !url.startsWith("https://discordapp.com/api/webhooks/")) {
                api.chat("\u00a7cThat does not look like a Discord webhook URL.");
                return true;
            }
            api.config("duel.webhook", url);
            api.chat("\u00a7aDiscord webhook saved.");
            return true;
        }
        if ("clearwebhook".equals(sub)) {
            api.config("duel.webhook", "");
            api.chat("\u00a7aWebhook cleared.");
            return true;
        }
        if ("setkey".equals(sub)) {
            api.chat("\u00a77Use the overlay Keys page (or /sd key) for your Hypixel API key.");
            return true;
        }
        if ("q".equals(sub) && parts.length >= 4) {
            join(parts[3]);
            return true;
        }
        if (QUEUES.containsKey(sub)) {
            join(sub);
            return true;
        }
        return false;
    }

    @Override
    public void world() {
        inMatch = false;
        opponent = null;
        unranked = false;
        unrankedWhy = null;
    }

    @Override
    public void tab(List<PluginPlayer> players) {
    }

    @Override
    public void chat(String plain) {
        if (NO_STATS.matcher(plain).find()) {
            unranked = true;
        }
        if (plain.contains("dueled someone in your party")) {
            unrankedWhy = "P";
        } else if (plain.contains("that round was a rematch")) {
            unrankedWhy = "RM";
        } else if (plain.contains("/duel'ed") || plain.contains("/duel'ed your opponent")
                || plain.toLowerCase(Locale.ROOT).contains("/duel")) {
            if (plain.contains("stats did not change")) {
                unrankedWhy = "D";
            }
        }
        Matcher vs = OPPONENT.matcher(plain);
        if (vs.find()) {
            onStart(vs.group(1));
            return;
        }
        if (inMatch && WINNER.matcher(plain).find()) {
            onEnd(plain);
        }
    }

    private void onStart(String name) {
        inMatch = true;
        opponent = name;
        lastOpponent = name;
        started = System.currentTimeMillis();
        api.chat("\u00a7cdx \u00a7bvs \u00a7f" + name + (unranked ? " \u00a78[unranked]" : ""));
        if (api.configOn("duel.stats", true)) {
            final String who = name;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    showStats(who);
                }
            }, "sd-duel-stats").start();
        }
    }

    private void onEnd(String line) {
        boolean win = ownWin(line);
        if (!unranked) {
            if (win) {
                wins++;
            } else {
                losses++;
            }
        }
        String tag = unranked ? "[" + (unrankedWhy == null ? "U" : unrankedWhy) + "] " : "";
        String verb = win ? "Beat" : "Lost to";
        long ms = Math.max(0L, System.currentTimeMillis() - started);
        String body = tag + verb + " " + (opponent == null ? "?" : opponent) + " (" + (ms / 1000L) + "s). "
                + strip(sessionLine());
        hook(body);
        inMatch = false;
        opponent = null;
        unranked = false;
        unrankedWhy = null;
    }

    private boolean ownWin(String line) {
        String me = api.selfName();
        if (me == null || me.isEmpty()) {
            return line.toLowerCase(Locale.ROOT).contains("you");
        }
        int at = line.toLowerCase(Locale.ROOT).indexOf(me.toLowerCase(Locale.ROOT));
        int winAt = line.toLowerCase(Locale.ROOT).indexOf("winner");
        return at >= 0 && (winAt < 0 || at < winAt + 16);
    }

    @SuppressWarnings("unchecked")
    private void showStats(String name) {
        String key = api.hypixelKey();
        if (key == null || key.isEmpty()) {
            api.chat("\u00a78[\u00a7cDuel\u00a78] \u00a7cSet a Hypixel key on the Keys page.");
            return;
        }
        String mojang = api.httpGet("https://api.mojang.com/users/profiles/minecraft/" + encode(name), null, null);
        if (mojang == null || mojang.isEmpty() || "INVALID_KEY".equals(mojang)) {
            api.chat("\u00a78[\u00a7cDuel\u00a78] \u00a7cCould not resolve " + name);
            return;
        }
        String uuid = jsonString(mojang, "id");
        if (uuid == null) {
            api.chat("\u00a78[\u00a7cDuel\u00a78] \u00a7cCould not resolve " + name);
            return;
        }
        String raw = api.httpGet("https://api.hypixel.net/v2/player?uuid=" + uuid, "API-Key", key);
        if (raw == null || "INVALID_KEY".equals(raw)) {
            api.chat("\u00a78[\u00a7cDuel\u00a78] \u00a7cHypixel lookup failed.");
            return;
        }
        try {
            Object parsed = Json.parse(raw);
            if (!(parsed instanceof Map)) {
                return;
            }
            Object player = ((Map<String, Object>) parsed).get("player");
            if (!(player instanceof Map)) {
                api.chat("\u00a78[\u00a7cDuel\u00a78] \u00a7cNo Hypixel data for " + name);
                return;
            }
            Map<String, Object> stats = asMap(((Map<String, Object>) player).get("stats"));
            Map<String, Object> duels = stats == null ? null : asMap(stats.get("Duels"));
            int w = num(duels, "wins");
            int l = num(duels, "losses");
            int k = num(duels, "kills");
            int d = num(duels, "deaths");
            int ws = num(duels, "current_winstreak");
            if (ws < 0) {
                ws = num(duels, "winstreak");
            }
            int best = num(duels, "best_overall_winstreak");
            String style = api.config("duel.layout");
            if (!validLayout(style)) {
                style = "minimal";
            }
            double wlr = l > 0 ? w / (double) l : w;
            double kdr = d > 0 ? k / (double) d : k;
            api.chat("\u00a78[\u00a7cDuel\u00a78] \u00a7f" + name + " \u00a77W/L " + field(w, l, wlr, style)
                    + " \u00a78K/D " + field(k, d, kdr, style) + " \u00a78WS \u00a7f" + Math.max(0, ws) + "\u00a78("
                    + Math.max(0, best) + ")");
        } catch (Throwable thrown) {
            api.chat("\u00a78[\u00a7cDuel\u00a78] \u00a7cCould not read stats.");
        }
    }

    private static String field(int a, int b, double ratio, String style) {
        String r = String.format(Locale.US, "%.2f", Double.valueOf(ratio));
        if ("full".equals(style)) {
            return a + "/" + b + " (" + r + ")";
        }
        if ("numbers".equals(style)) {
            return a + "/" + b;
        }
        return r;
    }

    private void join(String mode) {
        String id = QUEUES.get(mode.toLowerCase(Locale.ROOT));
        if (id == null) {
            api.chat("\u00a7cUnknown queue. Try classic, bridge, sw, uhc, op, sumo, combo, bow.");
            return;
        }
        api.send("/play " + id);
        api.chat("\u00a77Queueing \u00a7f" + mode + "\u00a77.");
    }

    private void hook(String content) {
        final String url = api.config("duel.webhook").trim();
        if (url.isEmpty() || !api.configOn("duel.webhookOn", true)) {
            return;
        }
        final String body = "{\"content\":" + Json.quote(content) + "}";
        new Thread(new Runnable() {
            @Override
            public void run() {
                api.httpPost(url, body, null, null);
            }
        }, "sd-duel-hook").start();
    }

    private String sessionLine() {
        double wlr = losses > 0 ? wins / (double) losses : wins;
        return "\u00a7cdx \u00a77Session \u00a7f" + wins + "\u00a78/\u00a7f" + losses + " \u00a78("
                + String.format(Locale.US, "%.2f", Double.valueOf(wlr)) + " WLR)";
    }

    private static boolean validLayout(String style) {
        for (String each : LAYOUTS) {
            if (each.equals(style)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private static int num(Map<String, Object> map, String key) {
        if (map == null || !(map.get(key) instanceof Number)) {
            return 0;
        }
        return ((Number) map.get(key)).intValue();
    }

    @SuppressWarnings("unchecked")
    private static String jsonString(String json, String key) {
        try {
            Object parsed = Json.parse(json);
            if (parsed instanceof Map && ((Map<String, Object>) parsed).get(key) instanceof String) {
                return (String) ((Map<String, Object>) parsed).get(key);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String strip(String text) {
        return text == null ? "" : text.replaceAll("\u00a7.", "");
    }

    private static String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception ignored) {
            return value;
        }
    }
}
