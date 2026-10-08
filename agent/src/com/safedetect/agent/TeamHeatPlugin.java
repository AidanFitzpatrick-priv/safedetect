package com.safedetect.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * After /who at game start, ranks enemy teams by combined FKDR and stars. Chat only — never auto-leaves.
 */
final class TeamHeatPlugin extends PluginPack.Base implements PluginEvents {
    private static final Pattern WHO = Pattern.compile("(?i)^ONLINE:\\s*(.+)$");
    private boolean gameOn;
    private boolean ranked;

    TeamHeatPlugin() {
        super("heat", "TeamHeat",
                "When the game starts, rank enemy teams by stars and FKDR in chat (or /pc).",
                "Hypixel API key", "/sd heat [pc]");
    }

    @Override
    public boolean command(String[] parts) {
        if (parts.length >= 3 && "pc".equalsIgnoreCase(parts[2])) {
            api.config("heat.pc", String.valueOf(!api.configOn("heat.pc", false)));
        }
        boolean pc = api.configOn("heat.pc", false);
        api.chat("\u00a77TeamHeat ranks enemies at /who. Output \u00a7f" + (pc ? "party chat" : "your chat only")
                + "\u00a77. \u00a78/sd heat pc to toggle.");
        return true;
    }

    @Override
    public void world() {
        gameOn = false;
        ranked = false;
    }

    @Override
    public void tab(List<PluginPlayer> players) {
    }

    @Override
    public void chat(String plain) {
        String low = plain.toLowerCase(Locale.ROOT);
        if (low.startsWith("the game starts in") || low.startsWith("protect your bed")) {
            gameOn = true;
            return;
        }
        Matcher who = WHO.matcher(plain);
        if (!who.find() || !gameOn || ranked) {
            return;
        }
        ranked = true;
        final List<String> names = PluginPack.splitNames(who.group(1));
        new Thread(new Runnable() {
            @Override
            public void run() {
                rank(names);
            }
        }, "sd-heat").start();
    }

    private void rank(List<String> names) {
        String me = api.selfName();
        String myLetter = BedStats.teamLetter(api.teamPrefix(me));
        Map<String, Team> teams = new LinkedHashMap<String, Team>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (Detector.hypixelBotName(name)) {
                continue;
            }
            String letter = BedStats.teamLetter(api.teamPrefix(name));
            if (letter.isEmpty()) {
                continue;
            }
            BedStats stats = StatsMemo.get(api, name);
            if (stats == null) {
                continue;
            }
            Team team = teams.get(letter);
            if (team == null) {
                team = new Team(letter);
                teams.put(letter, team);
            }
            team.add(stats);
        }
        List<Team> enemies = new ArrayList<Team>();
        for (Team team : teams.values()) {
            if (!team.letter.equals(myLetter)) {
                enemies.add(team);
            }
        }
        Collections.sort(enemies, new Comparator<Team>() {
            @Override
            public int compare(Team a, Team b) {
                return Double.compare(b.threat, a.threat);
            }
        });
        if (enemies.isEmpty()) {
            say("\u00a77TeamHeat: no enemy teams on /who.");
            return;
        }
        int show = Math.min(4, enemies.size());
        StringBuilder line = new StringBuilder("\u00a77Heat \u00a78");
        for (int i = 0; i < show; i++) {
            Team team = enemies.get(i);
            if (i > 0) {
                line.append(" \u00a78// ");
            }
            line.append('\u00a7').append(BedStats.teamColor(team.letter)).append(BedStats.teamName(team.letter))
                    .append(" \u00a7f").append(team.stars).append("\u272b \u00a7f")
                    .append(String.format(Locale.US, "%.1f", Double.valueOf(team.fkdr)));
        }
        say(line.toString());
    }

    private void say(String text) {
        if (api.configOn("heat.pc", false)) {
            api.send("/pc " + text.replaceAll("\u00a7.", ""));
        } else {
            api.chat(text);
        }
    }

    private static final class Team {
        final String letter;
        int stars;
        double fkdr;
        double threat;
        int n;

        Team(String letter) {
            this.letter = letter;
        }

        void add(BedStats stats) {
            stars += stats.stars;
            fkdr += stats.fkdr;
            threat += stats.threat();
            n++;
        }
    }
}
