package com.safedetect.agent;

import java.awt.Font;
import java.util.Locale;

/**
 * Runtime OS helpers so the overlay behaves on Windows, macOS, and Linux Lunar.
 */
final class Os {
    static final boolean WIN;
    static final boolean MAC;
    static final boolean LINUX;

    static {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        WIN = name.contains("win");
        MAC = name.contains("mac") || name.contains("darwin");
        LINUX = !WIN && !MAC;
    }

    private Os() {
    }

    static Font font(int style, int size) {
        String[] names;
        if (MAC) {
            names = new String[] { ".AppleSystemUIFont", "SF Pro Text", "Helvetica Neue", "Lucida Grande", "SansSerif" };
        } else if (LINUX) {
            names = new String[] { "Inter", "Ubuntu", "Cantarell", "DejaVu Sans", "SansSerif" };
        } else {
            names = new String[] { "Segoe UI Variable", "Segoe UI", "SansSerif" };
        }
        for (int i = 0; i < names.length; i++) {
            Font font = new Font(names[i], style, size);
            String family = font.getFamily();
            if (names[i].equals(family) || !"Dialog".equals(family) || i == names.length - 1) {
                return font;
            }
        }
        return new Font("SansSerif", style, size);
    }

    /** Rounded frames are unreliable on some Linux WMs. */
    static boolean roundCorners() {
        return !LINUX;
    }
}
