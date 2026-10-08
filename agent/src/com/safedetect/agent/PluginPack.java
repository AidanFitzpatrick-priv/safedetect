package com.safedetect.agent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builtin marketplace plugins. Extra jars still load from {@code config/safedetect-plugins}. */
final class PluginPack {
    private PluginPack() {
    }

    static Plugin[] all() {
        return new Plugin[] {
                new NickFindPlugin(),
                new DuelDeskPlugin(),
                new PartyWarnPlugin(),
                new SplitPingPlugin(),
                new SnipeWatchPlugin(),
                new AutoGgPlugin(),
                new QuickPlayPlugin(),
                new PartyDodgePlugin(),
                new MeowPlugin(),
                new HeightCallPlugin(),
                new SessionPadPlugin(),
                new TagPeekPlugin(),
                new StatsCallPlugin(),
        };
    }

    static abstract class Base implements Plugin {
        PluginApi api;
        final PluginInfo info;

        Base(String id, String name, String blurb, String needs, String commands) {
            this.info = new PluginInfo(id, name, "SafeDetect", "1.0.0", blurb, needs, commands);
        }

        @Override
        public PluginInfo info() {
            return info;
        }

        @Override
        public void start(PluginApi api) {
            this.api = api;
        }

        @Override
        public void stop() {
            api = null;
        }

        @Override
        public boolean command(String[] parts) {
            return false;
        }
    }

    /** Party chat when an enemy is tagged as a cheater. */
    static final class PartyWarnPlugin extends Base implements PluginEvents {
        private static final Pattern WHO = Pattern.compile("(?i)^ONLINE:\\s*(.+)$");
        private final Set<String> party = new LinkedHashSet<String>();

        PartyWarnPlugin() {
            super("partywarn", "PartyWarn",
                    "If the other team has an Urchin cheater tag, send it to party chat at game start.",
                    "Urchin key (Keys page)", "/sd partywarn");
        }

        @Override
        public boolean command(String[] parts) {
            api.chat("\u00a77PartyWarn is on. Tags other teams into /pc at /who.");
            return true;
        }

        @Override
        public void world() {
            party.clear();
        }

        @Override
        public void tab(List<PluginPlayer> players) {
        }

        @Override
        public void chat(String plain) {
            if (plain.startsWith("Party Leader:") || plain.startsWith("Party Members:")
                    || plain.startsWith("Party Moderators:")) {
                Matcher names = Pattern.compile("[A-Za-z0-9_]{3,16}").matcher(plain);
                while (names.find()) {
                    party.add(names.group().toLowerCase(Locale.ROOT));
                }
                return;
            }
            Matcher who = WHO.matcher(plain);
            if (!who.find()) {
                return;
            }
            final List<String> names = splitNames(who.group(1));
            final String me = api.selfName() == null ? "" : api.selfName().toLowerCase(Locale.ROOT);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String key = api.urchinKey();
                    if (key == null || key.isEmpty()) {
                        return;
                    }
                    List<String> enemies = new ArrayList<String>();
                    for (String name : names) {
                        String low = name.toLowerCase(Locale.ROOT);
                        if (low.equals(me) || party.contains(low) || api.skipped(name)) {
                            continue;
                        }
                        enemies.add(name);
                    }
                    if (enemies.isEmpty()) {
                        return;
                    }
                    String body = "{\"usernames\":[";
                    for (int i = 0; i < enemies.size(); i++) {
                        body += (i == 0 ? "" : ",") + Json.quote(enemies.get(i));
                    }
                    body += "]}";
                    String path = "https://urchin.ws/player?key=" + encode(key) + "&sources=MANUAL";
                    String raw = api.httpPost(path, body, null, null);
                    if (raw == null || "INVALID_KEY".equals(raw)) {
                        return;
                    }
                    try {
                        Object parsed = Json.parse(raw);
                        if (!(parsed instanceof Map)) {
                            return;
                        }
                        Object players = ((Map<?, ?>) parsed).get("players");
                        if (!(players instanceof Map)) {
                            return;
                        }
                        for (Map.Entry<?, ?> entry : ((Map<?, ?>) players).entrySet()) {
                            if (!(entry.getValue() instanceof List) || ((List<?>) entry.getValue()).isEmpty()) {
                                continue;
                            }
                            Object first = ((List<?>) entry.getValue()).get(0);
                            if (!(first instanceof Map)) {
                                continue;
                            }
                            Object type = ((Map<?, ?>) first).get("type");
                            if (!(type instanceof String)) {
                                continue;
                            }
                            String tag = ((String) type).toLowerCase(Locale.ROOT);
                            if (tag.contains("cheater") || tag.contains("sniper")) {
                                api.send("/pc " + entry.getKey() + " [" + type + "]");
                                try {
                                    Thread.sleep(400L);
                                } catch (InterruptedException ignored) {
                                    return;
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }, "sd-partywarn").start();
        }
    }

    /** Sound when you and a teammate die close together. */
    static final class SplitPingPlugin extends Base implements PluginEvents {
        private static final Pattern KILL = Pattern.compile("(?i)^(?:\\[[^\\]]+\\] )?([A-Za-z0-9_]{3,16}) was.+$");
        private static final Pattern FINAL = Pattern.compile("(?i).+FINAL KILL.+$");
        private long lastDeath;

        SplitPingPlugin() {
            super("split", "SplitPing",
                    "Plays a sound when you and a teammate die close together, to remind you to split the gen.",
                    "", "/sd split [ms]");
        }

        @Override
        public boolean command(String[] parts) {
            if (parts.length >= 3) {
                try {
                    api.config("split.windowMs", String.valueOf(Integer.parseInt(parts[2])));
                } catch (NumberFormatException ignored) {
                    api.chat("\u00a7cUsage: /sd split [window-ms]");
                    return true;
                }
            }
            api.chat("\u00a77Split window \u00a7f" + api.configInt("split.windowMs", 8000) + "ms\u00a77.");
            return true;
        }

        @Override
        public void world() {
            lastDeath = 0L;
        }

        @Override
        public void tab(List<PluginPlayer> players) {
        }

        @Override
        public void chat(String plain) {
            if (!KILL.matcher(plain).find() && !FINAL.matcher(plain).find()) {
                return;
            }
            String me = api.selfName();
            boolean us = me != null && !me.isEmpty() && plain.toLowerCase(Locale.ROOT).contains(me.toLowerCase(Locale.ROOT));
            long now = System.currentTimeMillis();
            if (us) {
                lastDeath = now;
                return;
            }
            if (lastDeath > 0L && now - lastDeath <= api.configInt("split.windowMs", 8000)) {
                api.sound();
                api.chat("\u00a7eSplit the gen!");
                lastDeath = 0L;
            }
        }
    }

    /** Track named snipers and alert if they appear in /who. */
    static final class SnipeWatchPlugin extends Base implements PluginEvents {
        private static final Pattern WHO = Pattern.compile("(?i)^ONLINE:\\s*(.+)$");

        SnipeWatchPlugin() {
            super("snipe", "SnipeWatch",
                    "Watch named snipers. Alerts in chat and party if they are in your game.",
                    "Optional Urchin key to /sd snipe peek <name>",
                    "/sd snipe add|remove|list|clear <ign>");
        }

        @Override
        public boolean command(String[] parts) {
            if (parts.length < 3) {
                return false;
            }
            String sub = parts[2].toLowerCase(Locale.ROOT);
            Set<String> list = list();
            if ("add".equals(sub) && parts.length >= 4) {
                list.add(parts[3].toLowerCase(Locale.ROOT));
                save(list);
                api.chat("\u00a7aWatching \u00a7f" + parts[3] + "\u00a7a.");
                return true;
            }
            if ("remove".equals(sub) && parts.length >= 4) {
                list.remove(parts[3].toLowerCase(Locale.ROOT));
                save(list);
                api.chat("\u00a77Stopped watching \u00a7f" + parts[3] + "\u00a77.");
                return true;
            }
            if ("clear".equals(sub)) {
                save(new LinkedHashSet<String>());
                api.chat("\u00a77Sniper list cleared.");
                return true;
            }
            if ("list".equals(sub) || "check".equals(sub)) {
                if (list.isEmpty()) {
                    api.chat("\u00a77No watched snipers. \u00a78/sd snipe add <ign>");
                } else {
                    api.chat("\u00a77Watching: \u00a7f" + String.join(" ", list));
                }
                return true;
            }
            return false;
        }

        @Override
        public void world() {
        }

        @Override
        public void tab(List<PluginPlayer> players) {
        }

        @Override
        public void chat(String plain) {
            Matcher who = WHO.matcher(plain);
            if (!who.find()) {
                return;
            }
            Set<String> watch = list();
            if (watch.isEmpty()) {
                return;
            }
            for (String name : splitNames(who.group(1))) {
                if (!watch.contains(name.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                api.chat("\u00a7cSNIPE \u00a7f" + name + " \u00a77is in this game.");
                api.sound();
                if (api.configOn("snipe.party", true)) {
                    api.send("/pc " + name + " might be a sniper");
                }
            }
        }

        private Set<String> list() {
            Set<String> out = new LinkedHashSet<String>();
            for (String name : api.config("snipe.list").split(",")) {
                if (!name.trim().isEmpty()) {
                    out.add(name.trim().toLowerCase(Locale.ROOT));
                }
            }
            return out;
        }

        private void save(Set<String> names) {
            api.config("snipe.list", String.join(",", names));
        }
    }

    static final class AutoGgPlugin extends Base implements PluginEvents {
        AutoGgPlugin() {
            super("autogg", "AutoGG", "Sends a custom GG after a Bedwars win or loss.", "",
                    "/sd autogg [message]");
        }

        @Override
        public boolean command(String[] parts) {
            if (parts.length >= 3) {
                StringBuilder msg = new StringBuilder();
                for (int i = 2; i < parts.length; i++) {
                    msg.append(i == 2 ? "" : " ").append(parts[i]);
                }
                api.config("autogg.message", msg.toString());
            }
            String msg = api.config("autogg.message");
            api.chat("\u00a77AutoGG: \u00a7f" + (msg.isEmpty() ? "gg" : msg));
            return true;
        }

        @Override
        public void world() {
        }

        @Override
        public void tab(List<PluginPlayer> players) {
        }

        @Override
        public void chat(String plain) {
            String low = plain.toLowerCase(Locale.ROOT);
            if (!low.equals("victory!") && !low.startsWith("you won") && !low.equals("game over!")
                    && !low.startsWith("you lost")) {
                return;
            }
            String msg = api.config("autogg.message");
            api.send(msg.isEmpty() ? "gg" : msg);
        }
    }

    static final class QuickPlayPlugin extends Base {
        private static final String[][] MODES = {
                { "1s", "bedwars_eight_one", "Solos" },
                { "2s", "bedwars_eight_two", "Doubles" },
                { "3s", "bedwars_four_three", "3v3v3v3" },
                { "4s", "bedwars_four_four", "4v4v4v4" },
                { "44s", "bedwars_two_four", "4v4" },
                { "rush", "bedwars_eight_two_rush", "Doubles Rush" },
        };

        QuickPlayPlugin() {
            super("play", "QuickPlay", "Short /sd play queues for Bedwars modes.", "",
                    "/sd play 1s|2s|3s|4s|44s|rush");
        }

        @Override
        public boolean command(String[] parts) {
            if (parts.length < 3) {
                return false;
            }
            String mode = parts[2].toLowerCase(Locale.ROOT);
            for (String[] row : MODES) {
                if (row[0].equals(mode)) {
                    api.send("/play " + row[1]);
                    api.chat("\u00a77Queueing \u00a7f" + row[2] + "\u00a77.");
                    return true;
                }
            }
            api.chat("\u00a7cUsage: /sd play 1s|2s|3s|4s|44s|rush");
            return true;
        }
    }

    /** Requeue when a full party dumps into the lobby. */
    static final class PartyDodgePlugin extends Base implements PluginEvents {
        private static final Pattern JOIN = Pattern.compile("(?i)^(?:\\[[^\\]]+\\] )?([A-Za-z0-9_]{3,16}) has joined \\((\\d+)/(\\d+)\\)");
        private final List<Long> joins = new ArrayList<Long>();
        private int max;
        private String lastPlay = "bedwars_eight_two";

        PartyDodgePlugin() {
            super("pdodge", "PartyDodge",
                    "Requeues when a full party (2/3/4) dumps into your Bedwars lobby.",
                    "", "/sd pdodge [on|off] [1s|2s|3s|4s]");
        }

        @Override
        public boolean command(String[] parts) {
            if (parts.length >= 3) {
                String arg = parts[2].toLowerCase(Locale.ROOT);
                if ("on".equals(arg) || "off".equals(arg)) {
                    api.config("pdodge.on", String.valueOf("on".equals(arg)));
                } else if ("1s".equals(arg) || "2s".equals(arg) || "3s".equals(arg) || "4s".equals(arg)) {
                    lastPlay = playId(arg);
                    api.config("pdodge.play", lastPlay);
                }
            }
            boolean on = api.configOn("pdodge.on", true);
            api.chat("\u00a77PartyDodge " + (on ? "\u00a7aon" : "\u00a7coff") + "\u00a77. Requeue \u00a7f"
                    + api.config("pdodge.play"));
            return true;
        }

        @Override
        public void world() {
            joins.clear();
        }

        @Override
        public void tab(List<PluginPlayer> players) {
        }

        @Override
        public void chat(String plain) {
            Matcher join = JOIN.matcher(plain);
            if (!join.find()) {
                return;
            }
            max = Integer.parseInt(join.group(3));
            long now = System.currentTimeMillis();
            joins.add(Long.valueOf(now));
            while (!joins.isEmpty() && now - joins.get(0).longValue() > 1200L) {
                joins.remove(0);
            }
            int party = partySize(max);
            if (party > 1 && joins.size() >= party && api.configOn("pdodge.on", true)) {
                String play = api.config("pdodge.play");
                if (play.isEmpty()) {
                    play = lastPlay;
                }
                api.send("/play " + play);
                api.chat("\u00a7ePartyDodge: full party, requeueing.");
                joins.clear();
            }
        }

        private static int partySize(int lobbyMax) {
            if (lobbyMax <= 8) {
                return 1;
            }
            if (lobbyMax == 12) {
                return 3;
            }
            if (lobbyMax == 16) {
                return 2;
            }
            return 2;
        }

        private static String playId(String shortName) {
            if ("1s".equals(shortName)) {
                return "bedwars_eight_one";
            }
            if ("3s".equals(shortName)) {
                return "bedwars_four_three";
            }
            if ("4s".equals(shortName)) {
                return "bedwars_four_four";
            }
            return "bedwars_eight_two";
        }
    }

    static final class MeowPlugin extends Base implements PluginEvents {
        MeowPlugin() {
            super("meow", "Meow", "Replies to chat meows with a purr.", "", "/sd meow");
        }

        @Override
        public boolean command(String[] parts) {
            api.chat("\u00a77Meow will purr back. :3");
            return true;
        }

        @Override
        public void world() {
        }

        @Override
        public void tab(List<PluginPlayer> players) {
        }

        @Override
        public void chat(String plain) {
            String low = plain.toLowerCase(Locale.ROOT);
            if (!low.contains("meow") && !low.contains("mrow") && !low.contains("nya")) {
                return;
            }
            if (LobbyChat.playerChat(plain) || low.contains(":")) {
                String me = api.selfName();
                if (me != null && low.contains(me.toLowerCase(Locale.ROOT) + ":")) {
                    return;
                }
                api.send("mrowww~ :3");
            }
        }
    }

    static final class HeightCallPlugin extends Base implements PluginEvents {
        private static final String[][] MAPS = {
                { "acropolis", "90" }, { "airshow", "110" }, { "amazon", "86" }, { "apollo", "90" },
                { "archway", "89" }, { "ashfire", "92" }, { "boletum", "91" }, { "carapace", "89" },
                { "cascade", "93" }, { "castle", "86" }, { "catalyst", "94" }, { "crypt", "89" },
                { "dragonstar", "99" }, { "gateway", "96" }, { "glacier", "86" }, { "hollow", "93" },
                { "lighthouse", "86" }, { "lotus", "90" }, { "orbit", "94" }, { "picnic", "86" },
                { "playbed", "90" }, { "rooted", "90" }, { "speedway", "75" }, { "steampunk", "88" },
                { "swashbuckle", "90" }, { "treenan", "96" }, { "waterfall", "94" }, { "yue", "91" },
        };

        HeightCallPlugin() {
            super("height", "HeightCall", "Looks up Bedwars map height limits, and can announce them on join.",
                    "", "/sd height [map]");
        }

        @Override
        public boolean command(String[] parts) {
            if (parts.length < 3) {
                api.chat("\u00a7cUsage: /sd height <map>");
                return true;
            }
            String map = parts[2];
            String limit = lookup(map);
            if (limit == null) {
                api.chat("\u00a77Unknown map \u00a7f" + map + "\u00a77. Try lighthouse, lotus, speedway…");
            } else {
                api.chat("\u00a77" + map + " height limit \u00a7fY" + limit);
            }
            return true;
        }

        @Override
        public void world() {
        }

        @Override
        public void tab(List<PluginPlayer> players) {
        }

        @Override
        public void chat(String plain) {
            if (!plain.toLowerCase(Locale.ROOT).startsWith("sending you to")) {
                return;
            }
            if (!api.configOn("height.auto", true)) {
                return;
            }
            for (String[] row : MAPS) {
                if (plain.toLowerCase(Locale.ROOT).contains(row[0])) {
                    api.chat("\u00a77Height limit \u00a7fY" + row[1] + " \u00a78(" + row[0] + ")");
                    return;
                }
            }
        }

        static String lookup(String map) {
            if (map == null) {
                return null;
            }
            String key = map.toLowerCase(Locale.ROOT).replace(" ", "");
            for (String[] row : MAPS) {
                if (row[0].equals(key) || row[0].startsWith(key) || key.startsWith(row[0])) {
                    return row[1];
                }
            }
            return null;
        }
    }

    static final class SessionPadPlugin extends Base implements PluginEvents {
        private int games;
        private int wins;
        private int losses;
        private boolean paused;

        SessionPadPlugin() {
            super("ses", "SessionPad", "Bedwars session W/L you can pause, reset, or share.", "",
                    "/sd ses [reset|pause|share]");
        }

        @Override
        public boolean command(String[] parts) {
            if (parts.length >= 3) {
                String sub = parts[2].toLowerCase(Locale.ROOT);
                if ("reset".equals(sub) || "end".equals(sub)) {
                    games = wins = losses = 0;
                    paused = false;
                    api.chat("\u00a77Session reset.");
                    return true;
                }
                if ("pause".equals(sub)) {
                    paused = true;
                    api.chat("\u00a77Session paused.");
                    return true;
                }
                if ("resume".equals(sub)) {
                    paused = false;
                    api.chat("\u00a77Session resumed.");
                    return true;
                }
                if ("share".equals(sub)) {
                    api.send(strip(line()));
                    return true;
                }
            }
            api.chat(line());
            return true;
        }

        @Override
        public void world() {
        }

        @Override
        public void tab(List<PluginPlayer> players) {
        }

        @Override
        public void chat(String plain) {
            if (paused) {
                return;
            }
            String low = plain.toLowerCase(Locale.ROOT);
            if (low.equals("the game starts in 1 second!")) {
                games++;
            } else if (low.equals("victory!")) {
                wins++;
            } else if (low.equals("game over!") || low.startsWith("you lost")) {
                losses++;
            }
        }

        private String line() {
            return "\u00a77Session \u00a7f" + wins + "W " + losses + "L \u00a78(" + games + " games"
                    + (paused ? ", paused" : "") + ")";
        }

        private static String strip(String text) {
            return text.replaceAll("\u00a7.", "");
        }
    }

    static final class TagPeekPlugin extends Base {
        TagPeekPlugin() {
            super("tags", "TagPeek", "Look up Urchin tags for a name without replacing tab.",
                    "Urchin key", "/sd tags <name>");
        }

        @Override
        public boolean command(String[] parts) {
            if (parts.length < 3) {
                api.chat("\u00a7cUsage: /sd tags <name>");
                return true;
            }
            final String name = parts[2];
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String key = api.urchinKey();
                    String path = "https://urchin.ws/player" + (key.isEmpty() ? "" : "?key=" + encode(key))
                            + (key.isEmpty() ? "?" : "&") + "sources=MANUAL";
                    String raw = api.httpPost(path, "{\"usernames\":[" + Json.quote(name) + "]}", null, null);
                    if (raw == null || "INVALID_KEY".equals(raw)) {
                        api.chat("\u00a7cUrchin lookup failed.");
                        return;
                    }
                    try {
                        Object parsed = Json.parse(raw);
                        Object players = parsed instanceof Map ? ((Map<?, ?>) parsed).get("players") : null;
                        Object tags = players instanceof Map ? ((Map<?, ?>) players).get(name) : null;
                        if (!(tags instanceof List) || ((List<?>) tags).isEmpty()) {
                            api.chat("\u00a77No Urchin tags for \u00a7f" + name);
                            return;
                        }
                        StringBuilder line = new StringBuilder("\u00a77" + name + ":");
                        for (Object tag : (List<?>) tags) {
                            if (tag instanceof Map && ((Map<?, ?>) tag).get("type") instanceof String) {
                                line.append(" \u00a7f").append(((Map<?, ?>) tag).get("type"));
                            }
                        }
                        api.chat(line.toString());
                    } catch (Throwable thrown) {
                        api.chat("\u00a7cCould not read tags.");
                    }
                }
            }, "sd-tags").start();
            return true;
        }
    }

    /** Mention-stats: when someone says your name in lobby chat, print their stars if we can. */
    static final class StatsCallPlugin extends Base implements PluginEvents {
        StatsCallPlugin() {
            super("statcall", "StatCall",
                    "When someone mentions you in pre-game chat, look up their Bedwars FKDR.",
                    "Hypixel API key", "/sd statcall");
        }

        @Override
        public boolean command(String[] parts) {
            api.chat("\u00a77StatCall looks up anyone who mentions you in lobby chat.");
            return true;
        }

        @Override
        public void world() {
        }

        @Override
        public void tab(List<PluginPlayer> players) {
        }

        @Override
        public void chat(String plain) {
            String me = api.selfName();
            if (me == null || me.isEmpty() || !LobbyChat.playerChat(plain)) {
                return;
            }
            if (!plain.toLowerCase(Locale.ROOT).contains(me.toLowerCase(Locale.ROOT))) {
                return;
            }
            Matcher name = Pattern.compile("^(?:(?:Party|Guild) > )?(?:\\[[^\\]]+\\] ?)*([A-Za-z0-9_]{1,16}):")
                    .matcher(plain);
            if (!name.find()) {
                return;
            }
            final String who = name.group(1);
            if (who.equalsIgnoreCase(me)) {
                return;
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String key = api.hypixelKey();
                    if (key.isEmpty()) {
                        return;
                    }
                    String mojang = api.httpGet(
                            "https://api.mojang.com/users/profiles/minecraft/" + encode(who), null, null);
                    String uuid = jsonId(mojang);
                    if (uuid == null) {
                        return;
                    }
                    String raw = api.httpGet("https://api.hypixel.net/v2/player?uuid=" + uuid, "API-Key", key);
                    if (raw == null) {
                        return;
                    }
                    try {
                        Object player = ((Map<?, ?>) Json.parse(raw)).get("player");
                        Map<?, ?> bw = player instanceof Map
                                ? (Map<?, ?>) ((Map<?, ?>) ((Map<?, ?>) player).get("stats")).get("Bedwars")
                                : null;
                        if (bw == null) {
                            return;
                        }
                        int stars = player instanceof Map && ((Map<?, ?>) player).get("achievements") instanceof Map
                                ? num((Map<?, ?>) ((Map<?, ?>) player).get("achievements"), "bedwars_level")
                                : 0;
                        int fk = num(bw, "final_kills_bedwars");
                        int fd = num(bw, "final_deaths_bedwars");
                        double fkdr = fd > 0 ? fk / (double) fd : fk;
                        api.chat("\u00a77" + who + " \u00a78" + stars + "\u272b \u00a77FKDR \u00a7f"
                                + String.format(Locale.US, "%.2f", Double.valueOf(fkdr)));
                    } catch (Throwable ignored) {
                    }
                }
            }, "sd-statcall").start();
        }

        private static int num(Map<?, ?> map, String key) {
            Object value = map.get(key);
            return value instanceof Number ? ((Number) value).intValue() : 0;
        }

        @SuppressWarnings("unchecked")
        private static String jsonId(String json) {
            try {
                Object parsed = Json.parse(json);
                if (parsed instanceof Map && ((Map<String, Object>) parsed).get("id") instanceof String) {
                    return (String) ((Map<String, Object>) parsed).get("id");
                }
            } catch (Throwable ignored) {
            }
            return null;
        }
    }

    static List<String> splitNames(String csv) {
        List<String> names = new ArrayList<String>();
        if (csv == null) {
            return names;
        }
        for (String part : csv.split(",")) {
            String name = part.replaceAll("\\[[^\\]]+\\]", "").trim();
            if (name.length() >= 1 && name.length() <= 16) {
                names.add(name);
            }
        }
        return names;
    }

    static String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception ignored) {
            return value;
        }
    }
}
