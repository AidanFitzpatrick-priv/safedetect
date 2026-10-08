package com.safedetect.agent;

import java.util.Locale;
import java.util.UUID;

/**
 * Lobby-only hints: party mates are skipped for dodge, and nick confidence is display-only
 * (never a cheat signal).
 */
final class LobbyIntel {
    private LobbyIntel() {
    }

    static boolean inParty(LobbyChat chat, String name) {
        return chat != null && name != null && chat.party.contains(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Short overlay label, or empty when there is no nick signal. High = denick map or version-1 UUID;
     * mid = saved NK flag only.
     */
    static String nickConfidence(UUID id, String shown, Settings settings, boolean nickedFlag) {
        String mapped = null;
        if (settings != null && shown != null) {
            String real = settings.realName(shown);
            if (real != null && !real.equalsIgnoreCase(shown)) {
                mapped = real;
            }
        }
        if (mapped != null) {
            return "high · " + mapped;
        }
        if (id != null && id.version() == 1) {
            return "high · nick UUID";
        }
        if (nickedFlag) {
            return "mid · nicked";
        }
        return "";
    }
}
