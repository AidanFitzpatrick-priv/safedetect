package com.safedetect.agent;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Every column the overlay table can show, with how to format, sort and colour it. */
final class OverlayColumns {
    static final class Column {
        final String id;
        final String header;
        final String label;
        final int width;
        final boolean right;

        Column(String id, String header, String label, int width, boolean right) {
            this.id = id;
            this.header = header;
            this.label = label;
            this.width = width;
            this.right = right;
        }
    }

    static final Column[] ALL = {
            new Column("lvl", "Lvl", "Level (stars)", 48, true),
            new Column("name", "Name", "Name", 140, false),
            new Column("team", "Team", "Team", 64, false),
            new Column("flags", "Flags", "Flags and tags", 90, false),
            new Column("ws", "WS", "Winstreak", 40, true),
            new Column("fkdr", "FKDR", "Final K/D", 52, true),
            new Column("wlr", "WLR", "Win/loss", 48, true),
            new Column("finals", "Finals", "Final kills", 64, true),
            new Column("wins", "Wins", "Wins", 56, true),
            new Column("sniper", "Sniper", "Sniper score", 56, true),
            new Column("seen", "Seen", "Times seen", 72, false),
            new Column("last", "Last", "Last flag", 120, false) };
    /** Only shown on the Saved tab. */
    static final Column TIMES = new Column("times", "Times", "Times flagged", 52, true);
    static final String[] DEFAULT = { "lvl", "name", "flags", "ws", "fkdr", "wlr", "finals", "wins", "sniper" };
    static final String[] SAVED = { "name", "flags", "times", "last" };

    private OverlayColumns() {
    }

    static Column byId(String id) {
        if (TIMES.id.equals(id)) {
            return TIMES;
        }
        for (Column column : ALL) {
            if (column.id.equals(id)) {
                return column;
            }
        }
        return null;
    }

    /** Known lobby ids in the given order, without repeats; name is always kept. */
    static List<String> sanitize(List<String> ids) {
        Set<String> out = new LinkedHashSet<String>();
        if (ids != null) {
            for (String id : ids) {
                Column column = byId(id);
                if (column != null && column != TIMES) {
                    out.add(id);
                }
            }
        }
        if (!out.contains("name")) {
            List<String> withName = new ArrayList<String>(out);
            withName.add(Math.min(1, withName.size()), "name");
            return withName;
        }
        return new ArrayList<String>(out);
    }

    static String text(String id, Hud.Row row) {
        if ("lvl".equals(id)) {
            return row.stars < 0 ? "" : Integer.toString(row.stars);
        }
        if ("name".equals(id)) {
            return row.name;
        }
        if ("team".equals(id)) {
            return Theme.teamName(row.team);
        }
        if ("flags".equals(id)) {
            return row.tags;
        }
        if ("ws".equals(id)) {
            return row.winstreak < 0 ? "?" : Integer.toString(row.winstreak);
        }
        if ("fkdr".equals(id)) {
            return dec(row.fkdr);
        }
        if ("wlr".equals(id)) {
            return dec(row.wlr);
        }
        if ("finals".equals(id)) {
            return count(row.finals);
        }
        if ("wins".equals(id)) {
            return count(row.wins);
        }
        if ("sniper".equals(id)) {
            return row.sniper < 0 ? "" : Integer.toString(row.sniper);
        }
        if ("seen".equals(id)) {
            return seen(row.seen, row.seenAt, System.currentTimeMillis());
        }
        if ("last".equals(id)) {
            return row.last;
        }
        if ("times".equals(id)) {
            return row.times > 0 ? "x" + row.times : "";
        }
        return "";
    }

    /** "3x · 2d": encounters and time since the previous one; "1x · new" the first time. */
    static String seen(int count, long previous, long now) {
        if (count <= 0) {
            return "";
        }
        if (previous <= 0L) {
            return count + "x \u00b7 new";
        }
        return count + "x \u00b7 " + ago(now - previous);
    }

    static String ago(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        if (seconds < 60L) {
            return "now";
        }
        if (seconds < 3600L) {
            return (seconds / 60L) + "m";
        }
        if (seconds < 86400L) {
            return (seconds / 3600L) + "h";
        }
        return (seconds / 86400L) + "d";
    }

    static Comparator<Hud.Row> comparator(final String id) {
        if ("name".equals(id) || "team".equals(id) || "last".equals(id)) {
            return new Comparator<Hud.Row>() {
                @Override
                public int compare(Hud.Row a, Hud.Row b) {
                    if ("last".equals(id)) {
                        return Long.compare(a.lastAt, b.lastAt);
                    }
                    String x = "name".equals(id) ? a.name : a.team;
                    String y = "name".equals(id) ? b.name : b.team;
                    return (x == null ? "" : x).compareToIgnoreCase(y == null ? "" : y);
                }
            };
        }
        return new Comparator<Hud.Row>() {
            @Override
            public int compare(Hud.Row a, Hud.Row b) {
                return Double.compare(number(id, a), number(id, b));
            }
        };
    }

    static double number(String id, Hud.Row row) {
        if ("lvl".equals(id)) {
            return row.stars;
        }
        if ("flags".equals(id)) {
            return (row.tags.isEmpty() ? 0 : 1000) + row.times;
        }
        if ("ws".equals(id)) {
            return row.winstreak;
        }
        if ("fkdr".equals(id)) {
            return row.fkdr;
        }
        if ("wlr".equals(id)) {
            return row.wlr;
        }
        if ("finals".equals(id)) {
            return row.finals;
        }
        if ("wins".equals(id)) {
            return row.wins;
        }
        if ("sniper".equals(id)) {
            return row.sniper;
        }
        if ("seen".equals(id)) {
            return row.seen;
        }
        if ("times".equals(id)) {
            return row.times;
        }
        return 0;
    }

    /** The order used when no column is picked: flagged, then most dangerous by FKDR and stars. */
    static final Comparator<Hud.Row> THREAT_ORDER = new Comparator<Hud.Row>() {
        @Override
        public int compare(Hud.Row a, Hud.Row b) {
            boolean aTag = !a.tags.isEmpty();
            boolean bTag = !b.tags.isEmpty();
            if (aTag != bTag) {
                return aTag ? -1 : 1;
            }
            int byIndex = Double.compare(index(b), index(a));
            if (byIndex != 0) {
                return byIndex;
            }
            if (a.sniper != b.sniper) {
                return b.sniper - a.sniper;
            }
            return a.name.compareToIgnoreCase(b.name);
        }
    };

    static double index(Hud.Row row) {
        double fkdr = row.fkdr < 0 ? 0.0 : row.fkdr;
        int stars = row.stars < 1 ? 1 : row.stars;
        return fkdr * fkdr * stars;
    }

    static boolean flagged(Hud.Row row) {
        return !row.tags.isEmpty() || row.blacklisted || (row.threat != null && !row.threat.isEmpty());
    }

    static Color color(String id, Hud.Row row, Theme theme) {
        if (row.friend && ("name".equals(id) || "lvl".equals(id))) {
            return theme.muted;
        }
        if ("lvl".equals(id) || "name".equals(id)) {
            return theme.prestige(row.stars);
        }
        if ("team".equals(id)) {
            Color team = theme.team(row.team);
            return team != null ? team : theme.muted;
        }
        if ("flags".equals(id)) {
            return row.tags.isEmpty() ? theme.muted : theme.accent;
        }
        if ("fkdr".equals(id)) {
            return theme.heat(row.fkdr, 2.0, 6.0, 12.0);
        }
        if ("wlr".equals(id)) {
            return theme.heat(row.wlr, 1.5, 4.0, 8.0);
        }
        if ("sniper".equals(id)) {
            return theme.heat(row.sniper, 15, 40, 70);
        }
        if ("seen".equals(id)) {
            return row.seen > 1 ? theme.fg : theme.muted;
        }
        if ("last".equals(id) || "times".equals(id)) {
            return theme.muted;
        }
        if ("ws".equals(id) && row.winstreak < 0) {
            return theme.muted;
        }
        return theme.fg;
    }

    private static String count(int value) {
        if (value < 0) {
            return "";
        }
        return value >= 1000 ? String.format(Locale.US, "%,d", Integer.valueOf(value)) : Integer.toString(value);
    }

    private static String dec(double value) {
        return value < 0 ? "" : String.format(Locale.US, "%.2f", value);
    }
}
