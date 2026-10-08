package com.safedetect.agent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Prints Bedwars stats when someone sends a friend request. */
final class FriendPeekPlugin extends PluginPack.Base implements PluginEvents {
    private static final Pattern FROM = Pattern.compile(
            "(?i)friend request from (?:\\[[^\\]]+\\] )?([A-Za-z0-9_]{3,16})");
    private static final Pattern SENT = Pattern.compile(
            "(?i)^(?:\\[[^\\]]+\\] )?([A-Za-z0-9_]{3,16}) (?:has sent you a friend request|wants to be your friend)");
    private final Set<String> shown = new LinkedHashSet<String>();

    FriendPeekPlugin() {
        super("freq", "FriendPeek",
                "When a friend request arrives, print that player's stars and FKDR.",
                "Hypixel API key", "/sd freq");
    }

    @Override
    public boolean command(String[] parts) {
        api.chat("\u00a77FriendPeek prints stats on incoming friend requests. \u00a78Hypixel key on the Keys page.");
        return true;
    }

    @Override
    public void world() {
        shown.clear();
    }

    @Override
    public void tab(List<PluginPlayer> players) {
    }

    @Override
    public void chat(String plain) {
        Matcher from = FROM.matcher(plain);
        String who = from.find() ? from.group(1) : null;
        if (who == null) {
            Matcher sent = SENT.matcher(plain);
            if (sent.find()) {
                who = sent.group(1);
            }
        }
        if (who == null || Detector.hypixelBotName(who)) {
            return;
        }
        final String name = who;
        if (!shown.add(name.toLowerCase(Locale.ROOT))) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                BedStats stats = StatsMemo.get(api, name);
                if (stats != null) {
                    api.chat("\u00a77Friend request \u00a7f" + stats.chatLine());
                } else {
                    api.chat("\u00a77Friend request from \u00a7f" + name);
                }
            }
        }, "sd-freq").start();
    }
}
