package com.safedetect.agent;

/**
 * LWJGL 2 key codes for the overlay hide/show bind. Detected in the game JVM (the overlay process
 * never sees Minecraft input), then toggled over hud.json.
 */
final class OverlayKeys {
    static final int RSHIFT = 54;
    static final String[] NAMES = { "Right Shift", "Left Shift", "Right Ctrl", "Left Ctrl", "Right Alt", "Insert",
            "H", "R", "Grave", "F7" };
    private static final int[] CODES = { 54, 42, 157, 29, 184, 210, 35, 19, 41, 65 };

    private OverlayKeys() {
    }

    static int codeAt(int index) {
        return index >= 0 && index < CODES.length ? CODES[index] : RSHIFT;
    }

    static int indexOf(int code) {
        for (int i = 0; i < CODES.length; i++) {
            if (CODES[i] == code) {
                return i;
            }
        }
        return 0;
    }

    static int clamp(int code) {
        return indexOf(code) == 0 && code != RSHIFT ? RSHIFT : code;
    }

    static String name(int code) {
        return NAMES[indexOf(clamp(code))];
    }

    static boolean known(int code) {
        for (int i = 0; i < CODES.length; i++) {
            if (CODES[i] == code) {
                return true;
            }
        }
        return false;
    }
}
