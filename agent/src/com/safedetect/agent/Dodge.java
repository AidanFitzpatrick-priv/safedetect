package com.safedetect.agent;

import java.util.Locale;

/** Decides whether a lobby player is worth dodging. Pure, so it can be tested without a game. */
final class Dodge {
    final boolean enabled;
    final boolean blacklist;
    final boolean flagged;
    final boolean tags;
    /** 0 turns the FKDR rule off. */
    final double fkdr;
    final int stars;
    /** 0 turns the anti-sniper rule off. */
    final int sniper;

    Dodge(boolean enabled, boolean blacklist, boolean flagged, boolean tags, double fkdr, int stars, int sniper) {
        this.enabled = enabled;
        this.blacklist = blacklist;
        this.flagged = flagged;
        this.tags = tags;
        this.fkdr = fkdr;
        this.stars = stars;
        this.sniper = sniper;
    }

    static Dodge from(Settings settings) {
        if (settings == null) {
            return new Dodge(false, false, false, false, 0, 0, 0);
        }
        return new Dodge(settings.dodgeEnabled, settings.dodgeBlacklist, settings.dodgeFlagged, settings.dodgeTags,
                settings.dodgeFkdr, settings.dodgeStars, settings.dodgeSniper);
    }

    /**
     * Short reason to dodge, or null. Unknown stats are negative and never trigger a rule.
     *
     * @param apiTags Urchin labels, empty when none
     */
    String evaluate(boolean friend, boolean blacklisted, boolean cheat, String apiTags, int sniperScore,
            double playerFkdr, int playerStars) {
        return evaluate(friend, false, blacklisted, cheat, apiTags, sniperScore, playerFkdr, playerStars);
    }

    String evaluate(boolean friend, boolean party, boolean blacklisted, boolean cheat, String apiTags, int sniperScore,
            double playerFkdr, int playerStars) {
        if (!enabled || friend || party) {
            return null;
        }
        if (blacklist && blacklisted) {
            return "blacklisted";
        }
        if (flagged && cheat) {
            return "flagged";
        }
        if (tags && apiTags != null && !apiTags.trim().isEmpty()) {
            String body = apiTags.trim();
            if (body.contains("[U:")) {
                return "Urchin " + body;
            }
            return "tagged " + body;
        }
        if (sniper > 0 && sniperScore >= sniper) {
            return "sniper " + sniperScore;
        }
        if (fkdr > 0 && playerFkdr >= fkdr && playerStars >= stars) {
            return String.format(Locale.US, "%.1f FKDR", playerFkdr);
        }
        return null;
    }
}
