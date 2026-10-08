package com.safedetect.agent;

/**
 * Per-player state for the four live checks, adapted from Meowtils 2.0.1 the same way the Forge mod was.
 * {@code tick} is the agent's own client tick counter; {@code elapsed} is how many client ticks passed since
 * the previous sample (normally 1).
 */
final class Checks {
    /** Hits farther than this are not treated as melee (pearls, bow, fall). */
    private static final double HIT_RANGE = 6.0;
    /** Further off than a legit crosshair hit as seen from another client. */
    private static final double KA_ANGLE = 80.0;
    private static final int KA_VL = 6;
    private static final int SILENT_VL = 8;
    /** Vanilla melee is 3.0 to the hitbox; extra slack is interpolation on other clients. */
    private static final double REACH_FLAG = 3.35;
    private static final double REACH_BLATANT = 3.8;
    private static final int AC_WINDOW = 20;
    private static final int AC_WINDOWS = 5;
    private static final int AC_MIN_CPS = 15;
    private static final int AC_KEEP = 100;
    private static final long SCAFFOLD_COOLDOWN = 60L;
    /** Consecutive script-timed crouches needed; one slow or unplaced crouch starts the count over. */
    private static final int SCAFFOLD_STREAK = 8;
    /** A crouch this many ticks after the previous counted one no longer belongs to the same bridge. */
    private static final long SCAFFOLD_GAP = 40L;
    private static final long PLACE_WINDOW = 3L;
    private static final int BRIDGE_WINDOW = 10;
    /** Backwards speed (blocks per tick, averaged over BRIDGE_WINDOW) that only real bridging reaches. */
    private static final double BRIDGE_SPEED = 0.05;

    private int autoBlockTicks;

    private int noSlowTicks;
    private double lastPosX;
    private double lastPosZ;
    private boolean hasLastPos;

    private boolean kaWasSwinging;
    private int killauraViolations;
    private boolean killauraFailed;
    private int silentViolations;
    private boolean silentFailed;
    private int reachViolations;
    private boolean reachFailed;
    private boolean acWasSwinging;
    private final long[] clickTimes = new long[AC_KEEP];
    private int clickIndex;
    private int clickCount;
    private boolean autoclickerFailed;

    private boolean wasSneaking;
    private boolean wasSwinging;
    private long crouchStart = Long.MIN_VALUE;
    private long pendingRelease = Long.MIN_VALUE;
    private boolean pendingQuick;
    private long lastCounted = Long.MIN_VALUE;
    private int streak;
    private long lastScaffoldFlag = Long.MIN_VALUE / 2;
    private boolean scaffoldFailed;
    private boolean hasBridgePos;
    private double bridgeX;
    private double bridgeZ;
    private final double[] backwards = new double[BRIDGE_WINDOW];
    private int backwardsIndex;
    private int backwardsCount;

    void update(PlayerView view, long tick, int elapsed) {
        autoBlock(view);
        noSlow(view, elapsed);
        scaffold(view, tick, elapsed);
        autoclicker(view, tick);
    }

    boolean failedAutoBlock() {
        return autoBlockTicks > 10;
    }

    boolean failedNoSlow() {
        return noSlowTicks > 20;
    }

    boolean failedKillaura() {
        return killauraFailed;
    }

    boolean failedSilentAura() {
        return silentFailed;
    }

    boolean failedReach() {
        return reachFailed;
    }

    boolean failedAutoclicker() {
        return autoclickerFailed;
    }

    boolean failedLegitScaffold() {
        return scaffoldFailed;
    }

    void resetAutoBlock() {
        autoBlockTicks = 0;
    }

    void resetNoSlow() {
        noSlowTicks = 0;
    }

    void resetKillaura() {
        killauraFailed = false;
        killauraViolations = 0;
    }

    void resetSilentAura() {
        silentFailed = false;
        silentViolations = 0;
    }

    void resetReach() {
        reachFailed = false;
        reachViolations = 0;
    }

    void resetAutoclicker() {
        autoclickerFailed = false;
        clickCount = 0;
        clickIndex = 0;
    }

    void resetLegitScaffold() {
        scaffoldFailed = false;
        crouchStart = Long.MIN_VALUE;
        pendingRelease = Long.MIN_VALUE;
        lastCounted = Long.MIN_VALUE;
        streak = 0;
    }

    private void autoBlock(PlayerView view) {
        boolean blocking = view.usingItem && view.held == Game.HELD_SWORD;
        autoBlockTicks = view.swinging && blocking ? autoBlockTicks + 1 : 0;
    }

    private void noSlow(PlayerView view, int elapsed) {
        if (!hasLastPos) {
            lastPosX = view.posX;
            lastPosZ = view.posZ;
            hasLastPos = true;
            return;
        }
        double deltaX = view.posX - lastPosX;
        double deltaZ = view.posZ - lastPosZ;
        lastPosX = view.posX;
        lastPosZ = view.posZ;
        double speed = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ) / Math.max(1, elapsed);
        if (view.sprinting && view.usingItem && !view.riding) {
            double threshold = 0.05;
            if (view.speedAmplifier >= 0) {
                threshold *= 1.0 + 0.2 * (view.speedAmplifier + 1);
            }
            noSlowTicks = speed > threshold ? noSlowTicks + 1 : 0;
        } else {
            noSlowTicks = 0;
        }
    }

    /**
     * Melee as seen from another client. Multi-target on one swing is killaura; hitting someone they are
     * not looking at is silent aura; hitbox distance above vanilla 3.0 is reach.
     */
    void melee(PlayerView attacker, PlayerView[] nearby) {
        if (attacker.riding) {
            kaWasSwinging = attacker.swinging;
            return;
        }
        if (!attacker.swingStart) {
            kaWasSwinging = attacker.swinging;
            return;
        }
        kaWasSwinging = attacker.swinging;
        int hits = 0;
        int offAngle = 0;
        int longHits = 0;
        int blatantHits = 0;
        for (int i = 0; i < nearby.length; i++) {
            PlayerView other = nearby[i];
            if (other == null || other.uuid == null || other.uuid.equals(attacker.uuid)) {
                continue;
            }
            if (other.hurtTime < 8 || other.hurtTime <= other.prevHurtTime) {
                continue;
            }
            double dist = aabbReach(attacker, other);
            if (dist > HIT_RANGE || dist < 0.05) {
                continue;
            }
            if (!closestSwing(attacker, other, nearby)) {
                continue;
            }
            hits++;
            if (lookAngle(attacker, other) >= KA_ANGLE) {
                offAngle++;
            }
            if (dist >= REACH_BLATANT) {
                blatantHits++;
                longHits++;
            } else if (dist >= REACH_FLAG) {
                longHits++;
            }
        }
        if (hits >= 2) {
            killauraViolations += 3;
        } else if (killauraViolations > 0 && hits <= 1) {
            killauraViolations--;
        }
        killauraFailed |= killauraViolations >= KA_VL;

        if (hits >= 1 && offAngle == hits) {
            silentViolations += 2;
        } else if (hits >= 1) {
            silentViolations = Math.max(0, silentViolations - 2);
        }
        silentFailed |= silentViolations >= SILENT_VL;

        if (blatantHits > 0) {
            reachViolations += 3;
        } else if (longHits > 0) {
            reachViolations += 2;
        } else if (hits >= 1) {
            reachViolations = Math.max(0, reachViolations - 2);
        }
        reachFailed |= reachViolations >= KA_VL;
    }

    /** Only the nearest player who started a swing this tick is blamed for a hit. */
    static boolean closestSwing(PlayerView attacker, PlayerView victim, PlayerView[] nearby) {
        double best = aabbReach(attacker, victim);
        for (int i = 0; i < nearby.length; i++) {
            PlayerView other = nearby[i];
            if (other == null || other.uuid == null || other.uuid.equals(attacker.uuid) || other.uuid.equals(victim.uuid)) {
                continue;
            }
            if (!other.swingStart) {
                continue;
            }
            if (aabbReach(other, victim) < best) {
                return false;
            }
        }
        return true;
    }

    /** Eye-to-hitbox distance, which is what vanilla reach actually uses. */
    static double aabbReach(PlayerView from, PlayerView to) {
        double eyeX = from.posX;
        double eyeY = from.posY + 1.62;
        double eyeZ = from.posZ;
        double half = 0.3;
        double cx = clamp(eyeX, to.posX - half, to.posX + half);
        double cy = clamp(eyeY, to.posY, to.posY + 1.8);
        double cz = clamp(eyeZ, to.posZ - half, to.posZ + half);
        double dx = eyeX - cx;
        double dy = eyeY - cy;
        double dz = eyeZ - cz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }

    /**
     * Other clients only show swing starts. A human 16 CPS still wobbles (15 one second, 17 the next).
     * An autoclicker holds the same CPS. Flag only when five stretches of 20 ticks are all 15+ CPS
     * and those five CPS values are identical.
     */
    private void autoclicker(PlayerView view, long tick) {
        boolean swingStart = view.swingProgressInt == 1 || (view.swinging && !acWasSwinging);
        acWasSwinging = view.swinging;
        if (view.held != Game.HELD_SWORD) {
            clickCount = 0;
            return;
        }
        if (swingStart) {
            clickTimes[clickIndex] = tick;
            clickIndex = (clickIndex + 1) % clickTimes.length;
            if (clickCount < clickTimes.length) {
                clickCount++;
            }
        }
        int span = AC_WINDOW * AC_WINDOWS;
        if (clickCount < AC_MIN_CPS * AC_WINDOWS || oldestClick() > tick - span + 1) {
            return;
        }
        int min = Integer.MAX_VALUE;
        int max = 0;
        for (int w = 0; w < AC_WINDOWS; w++) {
            long end = tick - (long) w * AC_WINDOW;
            int cps = swingsIn(end - AC_WINDOW + 1, end);
            if (cps < min) {
                min = cps;
            }
            if (cps > max) {
                max = cps;
            }
        }
        autoclickerFailed |= min >= AC_MIN_CPS && max == min;
    }

    private long oldestClick() {
        int n = clickCount < clickTimes.length ? clickCount : clickTimes.length;
        long oldest = Long.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (clickTimes[i] < oldest) {
                oldest = clickTimes[i];
            }
        }
        return oldest;
    }

    private int swingsIn(long from, long to) {
        int n = clickCount < clickTimes.length ? clickCount : clickTimes.length;
        int found = 0;
        for (int i = 0; i < n; i++) {
            long at = clickTimes[i];
            if (at >= from && at <= to) {
                found++;
            }
        }
        return found;
    }

    /** Smallest angle from the attacker's look to the victim's feet, chest or head. */
    static double lookAngle(PlayerView from, PlayerView to) {
        double yaw = Math.toRadians(from.headYaw);
        double pitch = Math.toRadians(from.pitch);
        double lx = -Math.sin(yaw) * Math.cos(pitch);
        double ly = -Math.sin(pitch);
        double lz = Math.cos(yaw) * Math.cos(pitch);
        double eyeX = from.posX;
        double eyeY = from.posY + 1.62;
        double eyeZ = from.posZ;
        double best = 180.0;
        double[] heights = {0.4, 0.9, 1.62};
        for (int i = 0; i < heights.length; i++) {
            double dx = to.posX - eyeX;
            double dy = to.posY + heights[i] - eyeY;
            double dz = to.posZ - eyeZ;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1.0e-6) {
                return 0.0;
            }
            double cos = (lx * dx + ly * dy + lz * dz) / len;
            if (cos > 1.0) {
                cos = 1.0;
            } else if (cos < -1.0) {
                cos = -1.0;
            }
            double degrees = Math.toDegrees(Math.acos(cos));
            if (degrees < best) {
                best = degrees;
            }
        }
        return best;
    }

    static double distance(PlayerView a, PlayerView b) {
        double dx = a.posX - b.posX;
        double dy = a.posY - b.posY;
        double dz = a.posZ - b.posZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Counts crouches that look scripted: 1-2 ticks long, released on the ground while looking down with a
     * block, while walking backwards, and followed by a placement within {@link #PLACE_WINDOW} ticks. Any
     * crouch that breaks the pattern, or a pause of {@link #SCAFFOLD_GAP} ticks, resets the streak.
     */
    private void scaffold(PlayerView view, long tick, int elapsed) {
        trackBackwards(view, elapsed);
        boolean swingStart = view.swingProgressInt == 1 || (view.swinging && !wasSwinging);
        wasSwinging = view.swinging;
        boolean sneaking = view.sneaking;
        if (sneaking && !wasSneaking) {
            crouchStart = tick;
        } else if (!sneaking && wasSneaking) {
            if (pendingRelease != Long.MIN_VALUE) {
                streak = 0;
            }
            boolean pose = view.pitch >= 60.0f && view.onGround && view.held == Game.HELD_BLOCK && !view.riding;
            if (pose) {
                long duration = crouchStart == Long.MIN_VALUE ? Long.MAX_VALUE : tick - crouchStart;
                pendingRelease = tick;
                pendingQuick = duration >= 1 && duration <= 2 && bridgingBackwards();
            } else {
                pendingRelease = Long.MIN_VALUE;
                streak = 0;
            }
        }
        wasSneaking = sneaking;

        if (pendingRelease != Long.MIN_VALUE) {
            if (swingStart && tick - pendingRelease <= PLACE_WINDOW) {
                if (pendingQuick) {
                    streak++;
                    lastCounted = tick;
                } else {
                    streak = 0;
                }
                pendingRelease = Long.MIN_VALUE;
            } else if (tick - pendingRelease > PLACE_WINDOW) {
                streak = 0;
                pendingRelease = Long.MIN_VALUE;
            }
        }
        if (lastCounted != Long.MIN_VALUE && tick - lastCounted > SCAFFOLD_GAP) {
            streak = 0;
        }
        if (streak >= SCAFFOLD_STREAK && tick - lastScaffoldFlag >= SCAFFOLD_COOLDOWN) {
            scaffoldFailed = true;
            lastScaffoldFlag = tick;
        }
    }

    private void trackBackwards(PlayerView view, int elapsed) {
        if (!hasBridgePos) {
            bridgeX = view.posX;
            bridgeZ = view.posZ;
            hasBridgePos = true;
            return;
        }
        int steps = Math.max(1, elapsed);
        double dx = (view.posX - bridgeX) / steps;
        double dz = (view.posZ - bridgeZ) / steps;
        bridgeX = view.posX;
        bridgeZ = view.posZ;
        if (Math.abs(dx) > 4.0 || Math.abs(dz) > 4.0) {
            backwardsCount = 0;
            return;
        }
        double yaw = Math.toRadians(view.yaw);
        backwards[backwardsIndex] = -(dx * -Math.sin(yaw) + dz * Math.cos(yaw));
        backwardsIndex = (backwardsIndex + 1) % BRIDGE_WINDOW;
        if (backwardsCount < BRIDGE_WINDOW) {
            backwardsCount++;
        }
    }

    private boolean bridgingBackwards() {
        if (backwardsCount < BRIDGE_WINDOW) {
            return false;
        }
        double total = 0.0;
        for (double value : backwards) {
            total += value;
        }
        return total / BRIDGE_WINDOW >= BRIDGE_SPEED;
    }
}
