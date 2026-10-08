package com.safedetect.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Colours lobby join lines and calls out party dumps. Display only — does not requeue.
 */
final class JoinPadPlugin extends PluginPack.Base implements PluginEvents {
    private static final Pattern JOIN = Pattern.compile(
            "(?i)^(?:\\[[^\\]]+\\] )?([A-Za-z0-9_]{3,16}) has joined \\((\\d+)/(\\d+)\\)");
    private static final Pattern QUIT = Pattern.compile(
            "(?i)^(?:\\[[^\\]]+\\] )?([A-Za-z0-9_]{3,16}) has quit \\((\\d+)/(\\d+)\\)");
    private final List<Long> burst = new ArrayList<Long>();
    private int lastCount;
    private int lastMax;

    JoinPadPlugin() {
        super("joins", "JoinPad",
                "Shows lobby fill (n/max) and a party-of-N line when several people dump in at once. Friends get an extra ping. Does not leave.",
                "", "/sd joins");
    }

    @Override
    public boolean command(String[] parts) {
        if (lastMax > 0) {
            api.chat("\u00a77Lobby \u00a7e" + lastCount + "/" + lastMax
                    + "\u00a77. Party dump size for this mode is \u00a7f" + partySize(lastMax) + "\u00a77.");
        } else {
            api.chat("\u00a77JoinPad waits for Hypixel join chat. Friends on the skip list get a ping.");
        }
        return true;
    }

    @Override
    public void world() {
        burst.clear();
        lastCount = 0;
        lastMax = 0;
    }

    @Override
    public void tab(List<PluginPlayer> players) {
    }

    @Override
    public void chat(String plain) {
        Matcher join = JOIN.matcher(plain);
        if (join.find()) {
            String name = join.group(1);
            lastCount = Integer.parseInt(join.group(2));
            lastMax = Integer.parseInt(join.group(3));
            if (api.configOn("joins.echo", false)) {
                api.chat("\u00a7a" + name + " \u00a77joined \u00a7e(" + lastCount + "/" + lastMax + ")");
            }
            if (api.skipped(name)) {
                api.sound();
                api.chat("\u00a7aFriend \u00a7f" + name + " \u00a77is in this lobby \u00a7e(" + lastCount + "/"
                        + lastMax + ")");
            }
            long now = System.currentTimeMillis();
            burst.add(Long.valueOf(now));
            while (!burst.isEmpty() && now - burst.get(0).longValue() > 1200L) {
                burst.remove(0);
            }
            int party = partySize(lastMax);
            if (party > 1 && burst.size() >= party) {
                api.chat("\u00a7cParty of " + burst.size() + " joined.");
                api.sound();
                burst.clear();
            }
            return;
        }
        Matcher quit = QUIT.matcher(plain);
        if (quit.find()) {
            lastCount = Integer.parseInt(quit.group(2));
            lastMax = Integer.parseInt(quit.group(3));
            if (api.configOn("joins.echo", false)) {
                api.chat("\u00a77" + quit.group(1) + " left \u00a7e(" + lastCount + "/" + lastMax + ")");
            }
        }
    }

    static int partySize(int lobbyMax) {
        if (lobbyMax <= 8) {
            return 1;
        }
        if (lobbyMax == 12) {
            return 3;
        }
        return 2;
    }
}
