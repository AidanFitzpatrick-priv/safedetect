package com.safedetect.agent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hypixel Bedwars lobby/party/who lines. Names only — not a cheat signal.
 */
final class LobbyChat {
    private static final Pattern JOINED = Pattern.compile("(?i)^(?:\\[[^\\]]+\\] )?([A-Za-z0-9_]{3,16}) has joined \\(");
    private static final Pattern WHO = Pattern.compile("(?i)^ONLINE:\\s*(.+)$");
    private static final Pattern PARTY_JOINED_YOU = Pattern.compile("(?i)You have joined (?:\\[[^\\]]+\\] )?([A-Za-z0-9_]{3,16})(?:'s)? party");
    private static final Pattern PARTY_JOINED = Pattern.compile("(?i)^(?:\\[[^\\]]+\\] )?([A-Za-z0-9_]{3,16}) joined the party");
    private static final Pattern PARTY_LEFT = Pattern.compile("(?i)^(?:\\[[^\\]]+\\] )?([A-Za-z0-9_]{3,16}) (?:has )?left the party");
    private static final Pattern PARTY_LEADER = Pattern.compile("(?i)Party Leader:\\s*(?:\\[[^\\]]+\\]\\s*)*([A-Za-z0-9_]{3,16})");
    private static final Pattern PARTY_MEMBERS = Pattern.compile("(?i)Party Members:\\s*(.+)");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern GAME_START = Pattern.compile("(?i)the game starts in 1 second");
    private static final Pattern WIN = Pattern.compile("(?i)(you won|bed wars?.*victory|\\bvictory\\b)");
    private static final Pattern LOSS = Pattern.compile("(?i)(you lost|game over|you have been eliminated)");

    final Set<String> fromChat = new LinkedHashSet<String>();
    final Set<String> party = new LinkedHashSet<String>();
    boolean autoWho;
    boolean won;
    boolean lost;

    void line(String raw) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        String text = raw.replaceAll("\u00a7.", "").trim();
        if (text.isEmpty()) {
            return;
        }
        Matcher join = JOINED.matcher(text);
        if (join.find()) {
            addPlayer(join.group(1));
            return;
        }
        Matcher who = WHO.matcher(text);
        if (who.find()) {
            addNames(who.group(1));
            return;
        }
        Matcher youParty = PARTY_JOINED_YOU.matcher(text);
        if (youParty.find()) {
            party.add(youParty.group(1).toLowerCase(Locale.ROOT));
            return;
        }
        Matcher pJoin = PARTY_JOINED.matcher(text);
        if (pJoin.find()) {
            party.add(pJoin.group(1).toLowerCase(Locale.ROOT));
            return;
        }
        Matcher pLeft = PARTY_LEFT.matcher(text);
        if (pLeft.find()) {
            party.remove(pLeft.group(1).toLowerCase(Locale.ROOT));
            return;
        }
        if (text.toLowerCase(Locale.ROOT).contains("you left the party")
                || text.toLowerCase(Locale.ROOT).contains("party was disbanded")
                || text.toLowerCase(Locale.ROOT).contains("you are not currently in a party")) {
            party.clear();
            return;
        }
        Matcher leader = PARTY_LEADER.matcher(text);
        if (leader.find()) {
            party.add(leader.group(1).toLowerCase(Locale.ROOT));
        }
        Matcher members = PARTY_MEMBERS.matcher(text);
        if (members.find()) {
            addPartyNames(members.group(1));
            return;
        }
        if (GAME_START.matcher(text).find()) {
            autoWho = true;
            return;
        }
        if (WIN.matcher(text).find()) {
            won = true;
        }
        if (LOSS.matcher(text).find()) {
            lost = true;
        }
    }

    boolean takeAutoWho() {
        boolean send = autoWho;
        autoWho = false;
        return send;
    }

    private void addPlayer(String name) {
        if (name == null || Detector.hypixelBotName(name)) {
            return;
        }
        fromChat.add(name);
    }

    private void addNames(String blob) {
        Matcher names = NAME.matcher(blob);
        while (names.find()) {
            addPlayer(names.group());
        }
    }

    private void addPartyNames(String blob) {
        String cleaned = blob.replaceAll("\\[[^\\]]+\\]", " ");
        Matcher names = NAME.matcher(cleaned);
        while (names.find()) {
            String name = names.group();
            if (!"Party".equalsIgnoreCase(name) && !"Members".equalsIgnoreCase(name)) {
                party.add(name.toLowerCase(Locale.ROOT));
            }
        }
    }

    static List<String> names(String blob) {
        List<String> out = new ArrayList<String>();
        Matcher names = NAME.matcher(blob);
        while (names.find()) {
            out.add(names.group());
        }
        return out;
    }
}
