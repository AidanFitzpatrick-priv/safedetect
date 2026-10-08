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
            FlagStore.Flag.SP, FlagStore.Flag.LS, FlagStore.Flag.SS, FlagStore.Flag.GB, FlagStore.Flag.DS,
            FlagStore.Flag.TL, FlagStore.Flag.TW };

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
            case TL: return "Telly";
            case TW: return "Tower";
            case FL: return "Fly";
            case SP: return "Speed";
            case AB: return "AutoBlock";
            case NS: return "NoSlow";
            case DS: return "Diagonal Scaffold";
            default: return flag.name();
        }
    }
}
