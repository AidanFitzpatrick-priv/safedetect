package com.safedetect.agent;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Puts stars and FKDR on the tab list. Display only — tab is never a cheat source.
 */
final class StarTabPlugin extends PluginPack.Base implements PluginEvents {
    private final ConcurrentLinkedQueue<PluginPlayer> queue = new ConcurrentLinkedQueue<PluginPlayer>();
    private final Set<String> done = new HashSet<String>();
    private volatile boolean worker;

    StarTabPlugin() {
        super("startab", "StarTab",
                "Stars and FKDR after names in tab (lobby and game). Display only.",
                "Hypixel API key", "/sd startab");
    }

    @Override
    public boolean command(String[] parts) {
        api.chat("\u00a77StarTab writes \u00a7f12\u272b 3.2 \u00a77after tab names. Needs a Hypixel key.");
        return true;
    }

    @Override
    public void world() {
        done.clear();
        queue.clear();
    }

    @Override
    public void chat(String plain) {
    }

    @Override
    public void tab(List<PluginPlayer> players) {
        if (players == null) {
            return;
        }
        String me = api.selfName();
        for (int i = 0; i < players.size(); i++) {
            PluginPlayer player = players.get(i);
            if (player == null || player.name == null) {
                continue;
            }
            String low = player.name.toLowerCase(Locale.ROOT);
            if (done.contains(low) || Detector.notPlayer(player.id, player.name) || Detector.hypixelBotName(player.name)
                    || (me != null && player.name.equalsIgnoreCase(me))) {
                continue;
            }
            done.add(low);
            queue.offer(player);
        }
        pump();
    }

    private synchronized void pump() {
        if (worker) {
            return;
        }
        worker = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    PluginPlayer next;
                    while ((next = queue.poll()) != null) {
                        BedStats stats = StatsMemo.get(api, next.name);
                        if (stats != null && !stats.nicked) {
                            UUID id = next.id;
                            api.tabSuffix(id, stats.tabSuffix());
                        }
                    }
                } finally {
                    worker = false;
                    if (!queue.isEmpty()) {
                        pump();
                    }
                }
            }
        }, "sd-startab").start();
    }
}
