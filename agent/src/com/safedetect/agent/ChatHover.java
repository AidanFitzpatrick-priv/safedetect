package com.safedetect.agent;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minecraft chat hover on a player name: every source we know, Cubelify-style.
 * Not a cheat signal — display only, like tab marks.
 */
final class ChatHover {
    static final String TITLE = "Flags";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern URCHIN = Pattern.compile("\\[U:([^\\]]+)\\]");
    private static final Pattern COLOUR = Pattern.compile("\u00a7.");

    static final class Info {
        FlagStore.Record record;
        String extra = "";
        boolean blacklisted;
        int sniper = -1;
        boolean nicked;
        double fkdr = -1;
        int stars = -1;
    }

    private ChatHover() {
    }

    /** Display name if {@code piece} contains a known player, otherwise null. */
    static String matchName(String piece, Map<String, String> names) {
        if (piece == null || names == null || names.isEmpty()) {
            return null;
        }
        String plain = COLOUR.matcher(piece).replaceAll("").trim();
        if (plain.endsWith(":")) {
            plain = plain.substring(0, plain.length() - 1).trim();
        }
        Matcher matcher = NAME.matcher(plain);
        while (matcher.find()) {
            String mapped = names.get(matcher.group().toLowerCase(Locale.ROOT));
            if (mapped != null) {
                return mapped;
            }
        }
        return null;
    }

    /** Formatted hover, or null when there is nothing to show. */
    static String build(String name, Info info) {
        if (info == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        out.append("\u00a79\u00a7l").append(TITLE).append("\u00a7r\n");
        out.append("\u00a78\u00a7m                    \u00a7r");
        int before = out.length();
        FlagStore.Record record = info.record;
        if (record != null) {
            for (FlagStore.Flag flag : FlagStore.Flag.values()) {
                if (!record.flags.contains(flag.name())) {
                    continue;
                }
                String label = CheckConfig.label(flag);
                String evidence = record.evidence.get(flag.name());
                if (evidence == null || evidence.isEmpty()) {
                    evidence = record.details.get(flag.name());
                }
                if (evidence == null || evidence.isEmpty()) {
                    evidence = CheckConfig.meaning(flag);
                }
                int n = 1;
                if (record.counts.get(flag.name()) != null) {
                    n = record.counts.get(flag.name()).intValue();
                }
                section(out, "\u00a7bSafeDetect", flag.color + label + " \u00a78[" + flag.name() + "]",
                        evidence, n, record.lastCheckAt > 0L ? record.lastCheckAt : record.last);
            }
        }
        if (info.extra != null && !info.extra.isEmpty()) {
            Matcher matcher = URCHIN.matcher(info.extra);
            while (matcher.find()) {
                String tag = matcher.group(1).trim();
                section(out, "\u00a7dUrchin", "\u00a7c" + tag + " \u00a78[" + initials(tag) + "]",
                        urchinMeaning(tag), 0, 0L);
            }
        }
        if (info.blacklisted) {
            section(out, "\u00a74Blacklist", "\u00a7cLocal \u00a78[BL]", "On your SafeDetect blacklist.", 0, 0L);
        }
        if (info.nicked && (record == null || !record.flags.contains("NK"))) {
            section(out, "\u00a7bSafeDetect", "\u00a7dNicked \u00a78[NK]", CheckConfig.meaning(FlagStore.Flag.NK), 0, 0L);
        }
        if (info.sniper >= 60 && (record == null || !record.flags.contains("SN"))) {
            section(out, "\u00a7bSafeDetect", "\u00a7dSniper \u00a78[SN]", "Score " + info.sniper, 0, 0L);
        }
        if (out.length() == before) {
            return null;
        }
        out.append("\n\u00a78Click to copy /wdr");
        if (name != null && !name.isEmpty()) {
            out.append(" ").append(name);
        }
        return out.toString();
    }

    static boolean ours(String hover) {
        if (hover == null) {
            return false;
        }
        String plain = COLOUR.matcher(hover).replaceAll("");
        return plain.startsWith(TITLE);
    }

    static String ago(long at, long now) {
        if (at <= 0L || now < at) {
            return "";
        }
        long sec = (now - at) / 1000L;
        if (sec < 60L) {
            return "just now";
        }
        if (sec < 3600L) {
            long n = sec / 60L;
            return n + (n == 1L ? " minute ago" : " minutes ago");
        }
        if (sec < 86400L) {
            long n = sec / 3600L;
            return n + (n == 1L ? " hour ago" : " hours ago");
        }
        if (sec < 30L * 86400L) {
            long n = sec / 86400L;
            return n + (n == 1L ? " day ago" : " days ago");
        }
        long n = sec / (30L * 86400L);
        return n + (n == 1L ? " month ago" : " months ago");
    }

    private static void section(StringBuilder out, String source, String title, String quote, int times, long at) {
        out.append("\n").append(source).append(" \u00a77- ").append(title).append("\u00a7r");
        if (quote != null && !quote.isEmpty()) {
            out.append("\n\u00a77\"").append(quote).append("\"");
        }
        String when = ago(at, System.currentTimeMillis());
        if (times > 1 || !when.isEmpty()) {
            out.append("\n\u00a78- ");
            if (times > 1) {
                out.append("x").append(times);
                if (!when.isEmpty()) {
                    out.append(" \u00b7 ");
                }
            }
            if (!when.isEmpty()) {
                out.append("Added ").append(when);
            }
        }
    }

    private static String initials(String tag) {
        String t = tag.trim();
        if (t.length() <= 3) {
            return t.toUpperCase(Locale.ROOT);
        }
        StringBuilder out = new StringBuilder();
        for (String word : t.split("\\s+")) {
            if (!word.isEmpty()) {
                out.append(Character.toUpperCase(word.charAt(0)));
            }
        }
        return out.length() > 0 ? out.toString() : t.substring(0, 2).toUpperCase(Locale.ROOT);
    }

    private static String urchinMeaning(String tag) {
        String key = "U:" + tag;
        for (int i = 0; i < CheckConfig.URCHIN.length; i++) {
            if (CheckConfig.URCHIN[i][0].equalsIgnoreCase(key) || CheckConfig.URCHIN[i][0].equalsIgnoreCase(tag)) {
                return CheckConfig.URCHIN[i][1];
            }
        }
        return "";
    }
}
