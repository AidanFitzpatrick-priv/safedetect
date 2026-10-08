package com.safedetect.agent;

import java.awt.Color;
import java.awt.Font;
import java.util.Locale;

/** Overlay palette, accent and font scale. Immutable; the overlay rebuilds its widgets when this changes. */
final class Theme {
    static final String DARK = "Dark";
    static final String[] NAMES = { DARK, "Light", "Classic Hypixel", "High contrast" };
    /** Accent presets as {label, hex}; an empty accent means the theme's own. */
    static final String[][] ACCENTS = {
            { "Indigo", "#7B8CFF" }, { "Orange", "#F0904A" }, { "Blue", "#4A9DFF" }, { "Green", "#3FCF8E" },
            { "Purple", "#A77BFF" }, { "Pink", "#F06CB4" }, { "Teal", "#2CC5BD" }, { "Gold", "#F2B744" } };
    static final String[] DENSITIES = { "compact", "normal", "comfortable" };

    final String name;
    final boolean light;
    final boolean classic;
    final Color bg;
    final Color header;
    final Color line;
    final Color fg;
    final Color muted;
    final Color accent;
    final Color select;
    final Color field;
    final Color thumb;
    final Color threatRow;
    /** Raised surfaces: settings cards, menus, inputs on cards. */
    final Color card;
    final Color hover;
    final Color rowLine;
    final Color accentSoft;
    /** Text drawn on a filled accent background. */
    final Color onAccent;
    final Color danger;
    final Color warn;
    final Color success;
    final int fontPercent;
    final Font title;
    final Font body;
    final Font small;
    final Font bold;
    /** Uppercase table headers and section labels. */
    final Font caption;

    private Theme(String name, Color bg, Color header, Color line, Color fg, Color muted, Color accent, Color select,
            Color field, Color thumb, int fontPercent) {
        this.name = name;
        this.light = luminance(bg) > 0.5;
        this.classic = "Classic Hypixel".equals(name);
        this.bg = bg;
        this.header = header;
        this.line = line;
        this.fg = fg;
        this.muted = muted;
        this.accent = accent;
        this.select = select;
        this.field = field;
        this.thumb = thumb;
        boolean contrast = "High contrast".equals(name);
        this.danger = contrast ? rgb(0xFF4D4D) : light ? rgb(0xD93C42) : rgb(0xF2555A);
        this.warn = contrast ? rgb(0xFFD000) : light ? rgb(0xB7791F) : rgb(0xF5B544);
        this.success = contrast ? rgb(0x00FF7F) : light ? rgb(0x1F9D63) : rgb(0x3FCF8E);
        this.threatRow = mix(bg, danger, light ? 0.07 : 0.11);
        this.card = light ? rgb(0xFFFFFF) : mix(bg, fg, contrast ? 0.0 : 0.035);
        this.hover = mix(bg, fg, light ? 0.035 : 0.045);
        this.rowLine = mix(bg, line, 0.55);
        this.accentSoft = mix(bg, accent, light ? 0.12 : 0.18);
        this.onAccent = luminance(accent) > 0.62 ? rgb(0x101114) : Color.WHITE;
        this.fontPercent = clampFont(fontPercent);
        float scale = this.fontPercent / 100f;
        title = Os.font(Font.BOLD, Math.round(14 * scale));
        body = Os.font(Font.PLAIN, Math.round(13 * scale));
        small = Os.font(Font.PLAIN, Math.round(12 * scale));
        bold = Os.font(Font.BOLD, Math.round(13 * scale));
        caption = Os.font(Font.BOLD, Math.round(10.5f * scale));
    }

    /** Unknown names fall back to Dark; a blank or invalid accent keeps the theme's own. */
    static Theme of(String name, String accent, int fontPercent) {
        String key = canonical(name);
        Color custom = parseHex(accent);
        Theme base;
        if ("Light".equals(key)) {
            base = new Theme(key, rgb(0xFFFFFF), rgb(0xF6F7F9), rgb(0xE3E6EB), rgb(0x15171C), rgb(0x6B7280),
                    rgb(0x5465E8), rgb(0xECEFFD), rgb(0xF2F4F7), rgb(0xCDD2DA), fontPercent);
        } else if ("Classic Hypixel".equals(key)) {
            base = new Theme(key, rgb(0x18181B), rgb(0x1D1D21), rgb(0x2D2D33), rgb(0xFFFFFF), rgb(0xA8A8B0),
                    rgb(0xFFAA00), rgb(0x2B2B33), rgb(0x24242A), rgb(0x46464F), fontPercent);
        } else if ("High contrast".equals(key)) {
            base = new Theme(key, rgb(0x000000), rgb(0x000000), rgb(0x9A9A9A), rgb(0xFFFFFF), rgb(0xD0D0D0),
                    rgb(0xFFFF00), rgb(0x003C99), rgb(0x0E0E0E), rgb(0xA0A0A0), fontPercent);
        } else {
            base = new Theme(DARK, rgb(0x101217), rgb(0x14161C), rgb(0x23262E), rgb(0xE8EAF0), rgb(0x8A90A0),
                    rgb(0x7B8CFF), rgb(0x1E2230), rgb(0x1A1D24), rgb(0x30343E), fontPercent);
        }
        if (custom == null) {
            return base;
        }
        return new Theme(base.name, base.bg, base.header, base.line, base.fg, base.muted, custom, base.select,
                base.field, base.thumb, fontPercent);
    }

    static String canonical(String name) {
        if (name != null) {
            for (String known : NAMES) {
                if (known.equalsIgnoreCase(name.trim())) {
                    return known;
                }
            }
        }
        return DARK;
    }

    static int clampFont(int percent) {
        return Math.max(80, Math.min(150, percent));
    }

    /** Row height in pixels for a density, before mini mode. */
    int rowHeight(String density, boolean mini) {
        int base = mini ? 24 : "compact".equals(density) ? 26 : "comfortable".equals(density) ? 38 : 32;
        return Math.round(base * Math.max(1f, fontPercent / 100f));
    }

    Color prestige(int stars) {
        if (stars < 0) {
            return fg;
        }
        if (stars < 100) {
            return classic ? rgb(0xAAAAAA) : muted;
        }
        if (stars < 200) {
            return fg;
        }
        int[] ramp = { 0xFFAA00, 0x55FFFF, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0x5555FF, 0xFF55FF };
        int index = stars / 100 - 2;
        return readable(rgb(index < ramp.length ? ramp[index] : 0xFF5555));
    }

    Color heat(double value, double low, double mid, double high) {
        if (value < 0) {
            return muted;
        }
        if (classic) {
            return rgb(value >= high ? 0xFF5555 : value >= mid ? 0xFFAA00 : value >= low ? 0xFFFF55 : 0x55FF55);
        }
        if (value >= high) {
            return readable(new Color(255, 92, 138));
        }
        if (value >= mid) {
            return readable(new Color(255, 138, 76));
        }
        if (value >= low) {
            return readable(new Color(245, 192, 74));
        }
        return fg;
    }

    /** Chat colour for a colour code char, or null when the code is empty or unknown. */
    Color team(String code) {
        if (code == null || code.length() != 1) {
            return null;
        }
        int index = "0123456789abcdef".indexOf(Character.toLowerCase(code.charAt(0)));
        return index < 0 ? null : readable(rgb(Game.CHAT_RGB[index]));
    }

    static String teamName(String code) {
        if (code == null || code.length() != 1) {
            return "";
        }
        String[] names = { "Black", "Dark Blue", "Dark Green", "Dark Aqua", "Dark Red", "Purple", "Gold", "Gray",
                "Dark Gray", "Blue", "Green", "Aqua", "Red", "Pink", "Yellow", "White" };
        int index = "0123456789abcdef".indexOf(Character.toLowerCase(code.charAt(0)));
        return index < 0 ? "" : names[index];
    }

    /** Darkens bright colours on a light background so text stays legible. */
    Color readable(Color color) {
        if (!light || luminance(color) < 0.55) {
            return color;
        }
        return new Color((int) (color.getRed() * 0.6), (int) (color.getGreen() * 0.6), (int) (color.getBlue() * 0.6));
    }

    static Color parseHex(String text) {
        if (text == null) {
            return null;
        }
        String hex = text.trim().toLowerCase(Locale.ROOT);
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6) {
            return null;
        }
        try {
            return rgb(Integer.parseInt(hex, 16));
        } catch (NumberFormatException bad) {
            return null;
        }
    }

    static String hex(Color color) {
        return String.format("#%06X", Integer.valueOf(color.getRGB() & 0xFFFFFF));
    }

    static Color mix(Color a, Color b, double amount) {
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * amount),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * amount),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * amount));
    }

    private static double luminance(Color color) {
        return (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue()) / 255.0;
    }

    private static Color rgb(int value) {
        return new Color(value & 0xFFFFFF);
    }
}
