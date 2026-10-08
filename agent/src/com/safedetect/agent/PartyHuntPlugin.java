package com.safedetect.agent;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sits in Bedwars lobby 1 and looks for a fill: public chat, then invite if they mention you and
 * meet the FKDR floor. Not auto-requeue and not auto-wdr.
 */
final class PartyHuntPlugin extends PluginPack.Base implements PluginEvents {
    private static final Pattern SPEAKER = Pattern.compile(
            "^(?:\\[[^\\]]+\\] ?)*([A-Za-z0-9_]{1,16})(?: \\[[^\\]]+\\])?: (.*)");
    private static final String[] WAVES = { "o/", "hi", "hello" };
    private volatile boolean active;
    private volatile boolean processing;
    private int mode = 2;
    private int need = 1;
    private double floor = 4.0;
    private int found;
    private int wave;
    private String waiting;
    private Thread loop;

    PartyHuntPlugin() {
        super("hunt", "PartyHunt",
                "Go to lobby 1 and advertise for a fill. Invites people who mention you above an FKDR floor.",
                "Hypixel API key", "/sd hunt 2s|3s|4s <need> [fkdr]  |  /sd hunt stop");
    }

    @Override
    public boolean command(String[] parts) {
        if (parts.length >= 3 && "stop".equalsIgnoreCase(parts[2])) {
            stopHunt("stopped");
            return true;
        }
        if (parts.length >= 4) {
            String size = parts[2].toLowerCase(Locale.ROOT).replace("v", "").replace("s", "");
            if ("2".equals(size) || "3".equals(size) || "4".equals(size)) {
                mode = Integer.parseInt(size);
            }
            try {
                need = Math.max(1, Math.min(mode - 1, Integer.parseInt(parts[3])));
            } catch (NumberFormatException ignored) {
                api.chat("\u00a7cUsage: /sd hunt 2s 1 4");
                return true;
            }
            if (parts.length >= 5) {
                try {
                    floor = Double.parseDouble(parts[4]);
                } catch (NumberFormatException ignored) {
                }
            }
            startHunt();
            return true;
        }
        api.chat("\u00a77Usage: \u00a7f/sd hunt 2s 1 4 \u00a78mode, people to find, min FKDR. \u00a7f/sd hunt stop");
        return true;
    }

    @Override
    public void world() {
        if (active && found == 0) {
            return;
        }
    }

    @Override
    public void tab(List<PluginPlayer> players) {
    }

    @Override
    public void chat(String plain) {
        if (!active) {
            return;
        }
        Matcher left = Pattern.compile("(?i)^(?:\\[[^\\]]+\\] )?([A-Za-z0-9_]{3,16}) (?:has )?left the party")
                .matcher(plain);
        if (left.find() && found > 0) {
            found--;
            api.chat("\u00a77PartyHunt: \u00a7f" + left.group(1) + " \u00a77left, looking again.");
            processing = false;
            waiting = null;
            return;
        }
        if (waiting != null) {
            if (plain.toLowerCase(Locale.ROOT).contains(waiting.toLowerCase(Locale.ROOT) + " joined the party")) {
                found++;
                api.chat("\u00a77PartyHunt: \u00a7f" + waiting + " \u00a77in. " + found + "/" + need);
                waiting = null;
                processing = false;
                if (found >= need) {
                    stopHunt("party full");
                }
                return;
            }
            if (plain.toLowerCase(Locale.ROOT).contains("has expired")
                    && plain.toLowerCase(Locale.ROOT).contains(waiting.toLowerCase(Locale.ROOT))) {
                api.chat("\u00a77PartyHunt: invite expired, still looking.");
                waiting = null;
                processing = false;
            }
            return;
        }
        if (processing) {
            return;
        }
        Matcher talk = SPEAKER.matcher(plain);
        if (!talk.find()) {
            return;
        }
        final String who = talk.group(1);
        String body = talk.group(2);
        String me = api.selfName();
        if (me == null || who.equalsIgnoreCase(me) || Detector.hypixelBotName(who)) {
            return;
        }
        if (!body.toLowerCase(Locale.ROOT).contains(me.toLowerCase(Locale.ROOT))) {
            return;
        }
        processing = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                BedStats stats = StatsMemo.get(api, who);
                if (stats == null || stats.nicked || stats.fkdr < floor) {
                    String why = stats == null ? "no stats" : String.format(Locale.US, "FKDR %.2f", Double.valueOf(stats.fkdr));
                    api.chat("\u00a77PartyHunt skip \u00a7f" + who + " \u00a78" + why);
                    processing = false;
                    return;
                }
                waiting = who;
                api.send("/p invite " + who);
                api.chat("\u00a77PartyHunt invite \u00a7f" + who + " \u00a78"
                        + String.format(Locale.US, "%.2f FKDR", Double.valueOf(stats.fkdr)));
            }
        }, "sd-hunt-invite").start();
    }

    @Override
    public void stop() {
        stopHunt(null);
        super.stop();
    }

    private void startHunt() {
        stopHunt(null);
        active = true;
        found = 0;
        wave = 0;
        processing = false;
        waiting = null;
        api.chat("\u00a77PartyHunt: lobby 1, looking for \u00a7f" + need + " \u00a77for " + mode + "s over \u00a7f"
                + String.format(Locale.US, "%.1f", Double.valueOf(floor)) + " FKDR.");
        loop = new Thread(new Runnable() {
            @Override
            public void run() {
                api.send("/bedwars");
                sleep(1200L);
                api.send("/swaplobby 1");
                sleep(2500L);
                while (active && found < need) {
                    if (!processing) {
                        int size = 1 + found;
                        String suffix = WAVES[wave % WAVES.length];
                        wave++;
                        api.send("/ac " + size + "/" + mode + " any " + suffix);
                    }
                    sleep(wave % WAVES.length == 0 ? 15000L : 10000L);
                }
            }
        }, "sd-hunt");
        loop.setDaemon(true);
        loop.start();
    }

    private void stopHunt(String why) {
        active = false;
        processing = false;
        waiting = null;
        if (loop != null) {
            loop.interrupt();
            loop = null;
        }
        if (why != null && api != null) {
            api.chat("\u00a77PartyHunt " + why + ".");
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
