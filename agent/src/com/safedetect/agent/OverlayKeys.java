package com.safedetect.agent;

/**
 * LWJGL 2 key codes for the overlay hide/show bind. Detected in the game JVM (the overlay process
 * never sees Minecraft input), then toggled over hud.json. Off by default; Right Shift from 1.2.0
 * is treated as none.
 */
final class OverlayKeys {
    static final int NONE = 0;
    /** Old 1.2.0 default; ignored so it cannot open or hide the overlay. */
    private static final int LEGACY_RSHIFT = 54;
    static final String[] NAMES = { "None", "Left Shift", "Right Ctrl", "Left Ctrl", "Right Alt", "Insert", "H", "R",
            "Grave", "F7" };
    private static final int[] CODES = { 0, 42, 157, 29, 184, 210, 35, 19, 41, 65 };

    private OverlayKeys() {
    }

    static int codeAt(int index) {
        return index >= 0 && index < CODES.length ? CODES[index] : NONE;
    }

    static int indexOf(int code) {
        int clamped = clamp(code);
        for (int i = 0; i < CODES.length; i++) {
            if (CODES[i] == clamped) {
                return i;
            }
        }
        return 0;
    }

    static int clamp(int code) {
        if (code == LEGACY_RSHIFT) {
            return NONE;
        }
        return known(code) ? code : NONE;
    }

    static String name(int code) {
        return NAMES[indexOf(code)];
    }

    static boolean known(int code) {
        if (code == LEGACY_RSHIFT) {
            return false;
        }
        for (int i = 0; i < CODES.length; i++) {
            if (CODES[i] == code) {
                return true;
            }
        }
        return false;
    }
}
