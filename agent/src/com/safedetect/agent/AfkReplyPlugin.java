package com.safedetect.agent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replies once per person when someone says your name while you marked AFK. Not a movement cheat.
 */
final class AfkReplyPlugin extends PluginPack.Base implements PluginEvents {
    private static final Pattern SPEAKER = Pattern.compile(
            "^(?:(?:Party|Guild|Officer) > |From |To |\\[(?:SHOUT|TEAM|SPECTATOR)\\] )?"
                    + "(?:\\[[^\\]]+\\] ?)*([A-Za-z0-9_]{1,16})(?: \\[[^\\]]+\\])?:");
    private final Map<String, Long> last = new ConcurrentHashMap<String, Long>();
    private boolean away;

    AfkReplyPlugin() {
        super("afk", "AfkReply",
                "When AFK, reply if someone says your name in chat. /sd afk [message] or /sd afk off.",
                "", "/sd afk [message|off]");
    }

    @Override
    public boolean command(String[] parts) {
        if (parts.length >= 3 && "off".equalsIgnoreCase(parts[2])) {
            away = false;
            api.chat("\u00a77AfkReply \u00a7coff");
            return true;
        }
        if (parts.length >= 3) {
            StringBuilder msg = new StringBuilder();
            for (int i = 2; i < parts.length; i++) {
                msg.append(i == 2 ? "" : " ").append(parts[i]);
            }
            api.config("afk.message", msg.toString());
            away = true;
        } else {
            away = !away;
        }
        api.chat("\u00a77AfkReply " + (away ? "\u00a7aon" : "\u00a7coff") + " \u00a78" + message());
        return true;
    }

    @Override
    public void world() {
        last.clear();
    }

    @Override
    public void tab(List<PluginPlayer> players) {
    }

    @Override
    public void chat(String plain) {
        String lowLine = plain.toLowerCase(Locale.ROOT);
        if (lowLine.startsWith("protect your bed") || lowLine.startsWith("the game starts in")) {
            away = false;
            return;
        }
        if (!away || !LobbyChat.playerChat(plain)) {
            return;
        }
        String me = api.selfName();
        if (me == null || me.isEmpty()) {
            return;
        }
        Matcher speaker = SPEAKER.matcher(plain);
        if (!speaker.find()) {
            return;
        }
        String who = speaker.group(1);
        if (who.equalsIgnoreCase(me)) {
            return;
        }
        String body = plain.substring(speaker.end()).toLowerCase(Locale.ROOT);
        if (!body.contains(me.toLowerCase(Locale.ROOT))) {
            return;
        }
        long now = System.currentTimeMillis();
        Long prev = last.get(who.toLowerCase(Locale.ROOT));
        if (prev != null && now - prev.longValue() < 60000L) {
            return;
        }
        last.put(who.toLowerCase(Locale.ROOT), Long.valueOf(now));
        api.send(message());
    }

    private String message() {
        String saved = api.config("afk.message");
        return saved.isEmpty() ? "afk" : saved;
    }
}
