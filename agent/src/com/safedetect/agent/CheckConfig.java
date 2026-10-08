package com.safedetect.agent;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Which live checks run and how strict they are. Immutable; the client thread swaps in a new snapshot
 * whenever the settings change, and the checks read {@link #current()} each tick.
 */
final class CheckConfig {
    /** Live cheat checks in the order the settings panel and /sd checks list them. */
    static final FlagStore.Flag[] CHECKS = { FlagStore.Flag.KA, FlagStore.Flag.SI, FlagStore.Flag.RE,
            FlagStore.Flag.AC, FlagStore.Flag.AB, FlagStore.Flag.NS, FlagStore.Flag.VL, FlagStore.Flag.SA,
            FlagStore.Flag.SP, FlagStore.Flag.LS, FlagStore.Flag.SS, FlagStore.Flag.GB, FlagStore.Flag.KY,
            FlagStore.Flag.AS, FlagStore.Flag.DS, FlagStore.Flag.TL, FlagStore.Flag.TW };

    /** Removed checks. Old flag files still parse them; they are not toggles and nothing writes new ones. */
    static boolean retired(FlagStore.Flag flag) {
        return flag == FlagStore.Flag.BB || flag == FlagStore.Flag.FL;
    }

    static boolean live(FlagStore.Flag flag) {
        if (flag == null || retired(flag)) {
            return false;
        }
        for (int i = 0; i < CHECKS.length; i++) {
            if (CHECKS[i] == flag) {
                return true;
            }
        }
        return false;
    }

    static final double DEFAULT_REACH = 3.35;
    static final double DEFAULT_KA_ANGLE = 80.0;
    static final int DEFAULT_AC_CPS = 15;
    static final double DEFAULT_SPEED = 0.42;

    private static volatile CheckConfig current = new CheckConfig(Collections.<String>emptySet(), DEFAULT_REACH,
            DEFAULT_KA_ANGLE, DEFAULT_AC_CPS, DEFAULT_SPEED, "normal");

    final Set<String> disabled;
    final double reachFlag;
    final double kaAngle;
    final int acMinCps;
    final double speedLimit;
    final String sensitivity;
    final double multiplier;

    CheckConfig(Set<String> disabled, double reachFlag, double kaAngle, int acMinCps, double speedLimit,
            String sensitivity) {
        this.disabled = Collections.unmodifiableSet(new LinkedHashSet<String>(disabled));
        this.reachFlag = reachFlag;
        this.kaAngle = kaAngle;
        this.acMinCps = acMinCps;
        this.speedLimit = speedLimit;
        this.sensitivity = sensitivity == null ? "normal" : sensitivity.toLowerCase(Locale.ROOT);
        this.multiplier = multiplier(this.sensitivity);
    }

    static CheckConfig current() {
        return current;
    }

    static void use(CheckConfig next) {
        if (next != null) {
            current = next;
        }
    }

    static CheckConfig from(Settings settings) {
        return new CheckConfig(settings.disabledChecks, settings.reachFlag, settings.kaAngle, settings.acMinCps,
                settings.speedLimit, settings.sensitivity);
    }

    /** Lenient needs half again as much evidence; strict needs a quarter less. */
    static double multiplier(String sensitivity) {
        if ("lenient".equals(sensitivity)) {
            return 1.5;
        }
        if ("strict".equals(sensitivity)) {
            return 0.75;
        }
        return 1.0;
    }

    boolean enabled(FlagStore.Flag flag) {
        return flag == null || !disabled.contains(flag.name());
    }

    /** A violation or tick-count threshold scaled by sensitivity; never below 1. */
    int scaled(int base) {
        return Math.max(1, (int) Math.round(base * multiplier));
    }

    static String label(FlagStore.Flag flag) {
        switch (flag) {
            case KA: return "Killaura";
            case SI: return "Silent Aura";
            case VL: return "Velocity";
            case RE: return "Reach";
            case AC: return "Autoclicker";
            case BB: return "BedBreaker";
            case SA: return "Snap Aim";
            case LS: return "Legit Scaffold";
            case SS: return "Sprint Scaffold";
            case GB: return "God Bridge";
            case KY: return "Keep-Y";
            case AS: return "Air Scaffold";
            case TL: return "Telly";
            case TW: return "Tower";
            case FL: return "Fly";
            case SP: return "Speed";
            case AB: return "AutoBlock";
            case NS: return "NoSlow";
            case DS: return "Diagonal Scaffold";
            case SN: return "Sniper";
            case SM: return "Smurf";
            case NK: return "Nicked";
            case FK: return "High FKDR";
            case AL: return "Alt";
            default: return flag.name();
        }
    }

    static String meaning(FlagStore.Flag flag) {
        switch (flag) {
            case KA: return "Hits you while looking somewhere else.";
            case SI: return "Hits you without turning their head.";
            case RE: return "Hits from further than vanilla reach.";
            case AC: return "Clicks faster than a person usually can.";
            case AB: return "Swings the sword while blocking.";
            case NS: return "Full walk speed while using an item (bow, food, rod).";
            case VL: return "Takes no knockback when you hit them.";
            case SA: return "Instant large look snaps when they swing.";
            case SP: return "Moves faster than vanilla sprinting allows.";
            case LS: return "Scripted sneak-place bridging (same crouch rhythm).";
            case SS: return "Sprints while bridging backwards.";
            case GB: return "God-bridges without sneaking.";
            case KY: return "Keeps the same Y while bridging without sneaking.";
            case AS: return "Places in the air looking down for too long.";
            case DS: return "Sprint-bridges on a diagonal.";
            case TL: return "Telly-bridging: fast pitch flicks while placing.";
            case TW: return "Towers up faster than placing should allow.";
            case SN: return "Known sniper name or sniper-looking stats.";
            case NK: return "Nicked account (Hypixel nick).";
            case FK: return "Very high Bedwars FKDR.";
            case AL: return "Name looks like an alt of someone already flagged.";
            default: return "";
        }
    }

    /** Overlay chips that are not live checks. */
    static final String[][] OTHER_SD = {
            { "SD:SN", "Sniper name list or sniper-looking stats." },
            { "SD:NK", "Nicked account." },
            { "SD:FK", "Sweat: high stars and FKDR." },
            { "SD:AL", "Possible alt of a player you already flagged." },
            { "SD:BL", "On your local blacklist (overlay click or /sd bl)." },
    };

    /** Urchin community tags. Overlay chips are U: plus a short label. */
    static final String[][] URCHIN = {
            { "U:Account", "Account note from Urchin, not a cheat tag." },
            { "U:Info", "Staff/community note. Not necessarily cheating." },
            { "U:Caution", "Weak warning. Treat as a maybe." },
            { "U:Possible Sniper", "Might be lobby sniping." },
            { "U:Sniper", "Tagged as a sniper." },
            { "U:Legit Sniper", "High-skill sniper (stats), not a cheat client." },
            { "U:Closet Cheater", "Suspected hidden cheats. Usually right, some falses." },
            { "U:Blatant Cheater", "Obvious cheating." },
            { "U:Confirmed Cheater", "Staff reviewed with evidence. Most reliable Urchin tag." },
    };
}
