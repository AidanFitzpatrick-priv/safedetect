package com.safedetect.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Resolves nicked Bedwars players through Bedlify at game start. Commands live under {@code /sd nickfind}.
 */
final class NickFindPlugin implements Plugin, PluginEvents {
    private static final Pattern NICK = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final String HOST = "https://api.bedlify.xyz";

    private PluginApi api;
    private final Map<UUID, String> tab = new ConcurrentHashMap<UUID, String>();
    private final Map<UUID, String> shown = new LinkedHashMap<UUID, String>();
    private final Set<String> checked = ConcurrentHashMap.newKeySet();
    private final Map<String, String> cache = new ConcurrentHashMap<String, String>();
    private final List<Job> queue = new ArrayList<Job>();
    private boolean gameStarted;
    private boolean working;

    private static final class Job {
        final UUID id;
        final String nick;
        final boolean force;

        Job(UUID id, String nick, boolean force) {
            this.id = id;
            this.nick = nick;
            this.force = force;
        }
    }

    @Override
    public PluginInfo info() {
        return new PluginInfo("nickfind", "NickFind", "Starfish / SafeDetect", "1.0.0",
                "Looks up nicked players with the Bedlify API when a Bedwars game starts.",
                "Bedlify API key from discord.gg/fpo",
                "/sd nickfind setkey <key> | lookup <nick> | scan");
    }

    @Override
    public void start(PluginApi api) {
        this.api = api;
    }

    @Override
    public void stop() {
        reset();
        api = null;
    }

    @Override
    public boolean command(String[] parts) {
        if (parts.length < 3) {
            return false;
        }
        String sub = parts[2].toLowerCase(Locale.ROOT);
        if ("setkey".equals(sub) && parts.length >= 4) {
            api.config("nickfind.apiKey", parts[3].trim());
            api.chat("\u00a7aBedlify key saved.");
            return true;
        }
        if ("lookup".equals(sub) && parts.length >= 4) {
            lookupCommand(parts[3]);
            return true;
        }
        if ("scan".equals(sub)) {
            scan(true);
            return true;
        }
        return false;
    }

    @Override
    public void world() {
        reset();
    }

    @Override
    public void tab(List<PluginPlayer> players) {
        tab.clear();
        for (PluginPlayer player : players) {
            tab.put(player.id, player.name);
            if (gameStarted && nicked(player.id)) {
                enqueue(player.id, player.name, false);
            }
        }
    }

    @Override
    public void chat(String plain) {
        if (!"Protect your bed and destroy the enemy beds.".equals(plain)) {
            return;
        }
        if (gameStarted) {
            return;
        }
        gameStarted = true;
        final int delay = api.configInt("nickfind.delayMs", 1000);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(Math.max(0, delay));
                } catch (InterruptedException ignored) {
                    return;
                }
                scan(false);
            }
        }, "sd-nickfind").start();
    }

    static boolean nicked(UUID id) {
        return id != null && id.version() == 1;
    }

    private void reset() {
        tab.clear();
        shown.clear();
        checked.clear();
        queue.clear();
        gameStarted = false;
        working = false;
    }

    private String key() {
        return api == null ? "" : api.config("nickfind.apiKey").trim();
    }

    private void lookupCommand(String nick) {
        if (!NICK.matcher(nick).matches()) {
            api.chat("\u00a7cUsage: /sd nickfind lookup <nick>");
            return;
        }
        if (key().isEmpty()) {
            api.chat("\u00a7cNo Bedlify key. /sd nickfind setkey <key>");
            return;
        }
        final String name = nick;
        new Thread(new Runnable() {
            @Override
            public void run() {
                String real = lookup(name);
                if ("INVALID_KEY".equals(real)) {
                    api.chat("\u00a7cInvalid Bedlify key.");
                    return;
                }
                if (real == null) {
                    api.chat("\u00a7eNo known player for nick \u00a76" + name + "\u00a7e.");
                    return;
                }
                cache.put(name.toLowerCase(Locale.ROOT), real);
                api.chat("\u00a76" + name + "\u00a7e was used by \u00a76" + real + "\u00a7e.");
            }
        }, "sd-nickfind-lookup").start();
    }

    private void scan(boolean manual) {
        if (api == null) {
            return;
        }
        if (key().isEmpty()) {
            if (manual) {
                api.chat("\u00a7cNo Bedlify key. /sd nickfind setkey <key>");
            }
            return;
        }
        UUID own = api.selfUuid();
        int found = 0;
        for (Map.Entry<UUID, String> entry : new ArrayList<Map.Entry<UUID, String>>(tab.entrySet())) {
            if (entry.getKey().equals(own) || !nicked(entry.getKey())) {
                continue;
            }
            found++;
            if (manual) {
                checked.remove(entry.getValue().toLowerCase(Locale.ROOT));
            }
            enqueue(entry.getKey(), entry.getValue(), manual);
        }
        if (manual && found == 0) {
            api.chat("\u00a7eNo nicked players in this lobby.");
        }
    }

    private void enqueue(UUID id, String nick, boolean force) {
        if (id == null || nick == null || !NICK.matcher(nick).matches()) {
            return;
        }
        String key = nick.toLowerCase(Locale.ROOT);
        if (!force && !checked.add(key)) {
            return;
        }
        checked.add(key);
        if (!force && cache.containsKey(key)) {
            handle(id, nick, cache.get(key));
            return;
        }
        synchronized (queue) {
            queue.add(new Job(id, nick, force));
        }
        pump();
    }

    private void pump() {
        synchronized (queue) {
            if (working) {
                return;
            }
            working = true;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    Job job;
                    synchronized (queue) {
                        if (queue.isEmpty()) {
                            working = false;
                            return;
                        }
                        job = queue.remove(0);
                    }
                    String real = lookup(job.nick);
                    if ("INVALID_KEY".equals(real)) {
                        api.chat("\u00a7cInvalid Bedlify key. /sd nickfind setkey <key>");
                        synchronized (queue) {
                            queue.clear();
                            working = false;
                        }
                        return;
                    }
                    if (real != null) {
                        cache.put(job.nick.toLowerCase(Locale.ROOT), real);
                    }
                    if (stillOnTab(job)) {
                        handle(job.id, job.nick, real);
                    }
                }
            }
        }, "sd-nickfind-q").start();
    }

    private boolean stillOnTab(Job job) {
        return job.nick.equals(tab.get(job.id));
    }

    @SuppressWarnings("unchecked")
    private String lookup(String nick) {
        String body = api.httpGet(HOST + "/nick?nick=" + encode(nick) + "&limit=5", "X-API-Key", key());
        if ("INVALID_KEY".equals(body) || "RATE".equals(body) || body == null) {
            return body;
        }
        if (body.isEmpty()) {
            return null;
        }
        try {
            Object parsed = Json.parse(body);
            if (!(parsed instanceof Map)) {
                return null;
            }
            Object results = ((Map<String, Object>) parsed).get("results");
            if (!(results instanceof List)) {
                return null;
            }
            for (Object row : (List<Object>) results) {
                if (!(row instanceof Map)) {
                    continue;
                }
                Map<String, Object> map = (Map<String, Object>) row;
                Object used = map.get("nick");
                Object name = map.get("name");
                if (used instanceof String && name instanceof String
                        && ((String) used).equalsIgnoreCase(nick)) {
                    return (String) name;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void handle(UUID id, String nick, String real) {
        if (api == null) {
            return;
        }
        String team = api.teamPrefix(nick);
        String labelled = (team == null || team.isEmpty() ? "\u00a7f" : team) + nick;
        if (real == null || real.isEmpty()) {
            if (api.configOn("nickfind.showUnknown", false)) {
                api.chat("\u00a7bNF \u00a7r" + labelled + "\u00a77 is nicked \u00a78(unknown)");
            }
            return;
        }
        api.chat("\u00a7bNF \u00a76" + real + "\u00a77 is nicked as " + labelled + "\u00a77.");
        if (api.configOn("nickfind.sound", true)) {
            api.sound();
        }
        shown.put(id, real);
        if (api.configOn("nickfind.tab", true)) {
            api.tabSuffix(id, " \u00a76(" + real + ")");
        }
    }

    private static String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception ignored) {
            return value;
        }
    }
}
