package com.safedetect.agent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prints stars and FKDR when someone talks in a pre-game lobby. Inspired by BWU auto-stats-on-chat;
 * display only, not a cheat check.
 */
final class TalkLinePlugin extends PluginPack.Base implements PluginEvents {
    private static final Pattern SPEAKER = Pattern.compile(
            "^(?:(?:Party|Guild|Officer) > |From |To |\\[(?:SHOUT|TEAM|SPECTATOR)\\] )?"
                    + "(?:\\[[^\\]]+\\] ?)*([A-Za-z0-9_]{1,16})(?: \\[[^\\]]+\\])?:");
    private final Set<String> shown = new LinkedHashSet<String>();
    private boolean inGame;

    TalkLinePlugin() {
        super("talk", "TalkLine",
                "When someone talks in pre-game chat, print their stars, FKDR, beds and winstreak.",
                "Hypixel API key", "/sd talk");
    }

    @Override
    public boolean command(String[] parts) {
        api.chat("\u00a77TalkLine prints stats for lobby chat. Off in-game. \u00a78Hypixel key on the Keys page.");
        return true;
    }

    @Override
    public void world() {
        shown.clear();
        inGame = false;
    }

    @Override
    public void tab(List<PluginPlayer> players) {
    }

    @Override
    public void chat(String plain) {
        String low = plain.toLowerCase(Locale.ROOT);
        if (low.startsWith("the game starts in") || low.startsWith("protect your bed")) {
            inGame = true;
            return;
        }
        if (inGame || !LobbyChat.playerChat(plain)) {
            return;
        }
        Matcher speaker = SPEAKER.matcher(plain);
        if (!speaker.find()) {
            return;
        }
        final String who = speaker.group(1);
        String me = api.selfName();
        if (who.equalsIgnoreCase(me) || Detector.hypixelBotName(who) || api.skipped(who)) {
            return;
        }
        String token = who.toLowerCase(Locale.ROOT);
        if (!shown.add(token)) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                BedStats stats = StatsMemo.get(api, who);
                if (stats != null) {
                    api.chat(stats.chatLine());
                }
            }
        }, "sd-talk").start();
    }
}
