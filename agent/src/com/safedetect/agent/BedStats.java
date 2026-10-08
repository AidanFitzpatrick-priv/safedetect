package com.safedetect.agent;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bedwars numbers parsed from a Hypixel player payload. Display and ranking only — not a cheat signal.
 */
final class BedStats {
    private static final Pattern BRACKET = Pattern.compile("\\[([RBGYAWPS])\\]");
    static final String[] TEAM_ORDER = { "R", "B", "G", "Y", "A", "W", "P", "S" };
    private static final String[] TEAM_NAME = { "Red", "Blue", "Green", "Yellow", "Aqua", "White", "Pink", "Gray" };
    private static final String[] TEAM_COLOR = { "c", "9", "a", "e", "b", "f", "d", "7" };

    final String name;
    final int stars;
    final int beds;
    final int winstreak;
    final int finals;
    final double fkdr;
    final double wlr;
    final boolean nicked;

    BedStats(String name, int stars, int beds, int winstreak, int finals, double fkdr, double wlr, boolean nicked) {
        this.name = name;
        this.stars = stars;
        this.beds = beds;
        this.winstreak = winstreak;
        this.finals = finals;
        this.fkdr = fkdr;
        this.wlr = wlr;
        this.nicked = nicked;
    }

    @SuppressWarnings("unchecked")
    static BedStats parse(String json, String name) {
        if (json == null || json.isEmpty() || "RATE".equals(json) || "INVALID_KEY".equals(json)) {
            return null;
        }
        try {
            Object root = Json.parse(json);
            if (!(root instanceof Map) || !Boolean.TRUE.equals(((Map<?, ?>) root).get("success"))) {
                return null;
            }
            Object player = ((Map<?, ?>) root).get("player");
            if (player == null) {
                return new BedStats(name, 0, 0, -1, 0, 0, 0, true);
            }
            if (!(player instanceof Map)) {
                return null;
            }
            Map<String, Object> body = (Map<String, Object>) player;
            int stars = 0;
            Object achievements = body.get("achievements");
            if (achievements instanceof Map && ((Map<?, ?>) achievements).get("bedwars_level") instanceof Number) {
                stars = ((Number) ((Map<?, ?>) achievements).get("bedwars_level")).intValue();
            }
            int kills = 0;
            int deaths = 0;
            int wins = 0;
            int losses = 0;
            int beds = 0;
            int winstreak = -1;
            Object stats = body.get("stats");
            if (stats instanceof Map) {
                Object bedwars = ((Map<?, ?>) stats).get("Bedwars");
                if (bedwars instanceof Map) {
                    Map<?, ?> bw = (Map<?, ?>) bedwars;
                    kills = num(bw, "final_kills_bedwars");
                    deaths = num(bw, "final_deaths_bedwars");
                    wins = num(bw, "wins_bedwars");
                    losses = num(bw, "losses_bedwars");
                    beds = num(bw, "beds_broken_bedwars");
                    if (bw.get("winstreak") instanceof Number) {
                        winstreak = ((Number) bw.get("winstreak")).intValue();
                    }
                }
            }
            double fkdr = kills / (double) Math.max(1, deaths);
            double wlr = wins / (double) Math.max(1, losses);
            return new BedStats(name, stars, beds, winstreak, kills, fkdr, wlr, false);
        } catch (Throwable ignored) {
            return null;
        }
    }

    String chatLine() {
        String ws = winstreak >= 0 ? " \u00a77WS \u00a7f" + winstreak : "";
        return "\u00a77" + name + " \u00a7f" + stars + "\u272b \u00a77FKDR \u00a7f"
                + String.format(Locale.US, "%.2f", Double.valueOf(fkdr)) + " \u00a77beds \u00a7f" + beds + ws;
    }

    /** Short tab suffix; display only. */
    String tabSuffix() {
        return " \u00a78[\u00a7f" + stars + "\u272b \u00a7f" + String.format(Locale.US, "%.1f", Double.valueOf(fkdr))
                + "\u00a78]";
    }

    /** 0–100, weighted like BWU: mostly FKDR, then WS, WLR, stars. */
    double threat() {
        double nFkdr = sigmoid(0.8, fkdr - 3.0);
        double nWlr = sigmoid(1.0, wlr - 2.0);
        double nWs = sigmoid(0.5, (winstreak < 0 ? 0 : winstreak) - 3.0);
        double nStars = sigmoid(0.01, stars - 250.0);
        return 100.0 * (0.70 * nFkdr + 0.10 * nWlr + 0.15 * nWs + 0.05 * nStars);
    }

    static double sigmoid(double k, double x) {
        return 1.0 / (1.0 + Math.exp(-k * x));
    }

    static String teamLetter(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return "";
        }
        Matcher bracket = BRACKET.matcher(prefix);
        if (bracket.find()) {
            return bracket.group(1);
        }
        int mark = prefix.indexOf('\u00a7');
        if (mark >= 0 && mark + 1 < prefix.length()) {
            char color = Character.toLowerCase(prefix.charAt(mark + 1));
            for (int i = 0; i < TEAM_COLOR.length; i++) {
                if (TEAM_COLOR[i].charAt(0) == color) {
                    return TEAM_ORDER[i];
                }
            }
        }
        return "";
    }

    static String teamName(String letter) {
        int i = indexOf(letter);
        return i < 0 ? "Team" : TEAM_NAME[i];
    }

    static String teamColor(String letter) {
        int i = indexOf(letter);
        return i < 0 ? "7" : TEAM_COLOR[i];
    }

    private static int indexOf(String letter) {
        if (letter == null) {
            return -1;
        }
        for (int i = 0; i < TEAM_ORDER.length; i++) {
            if (TEAM_ORDER[i].equals(letter)) {
                return i;
            }
        }
        return -1;
    }

    private static int num(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }
}
