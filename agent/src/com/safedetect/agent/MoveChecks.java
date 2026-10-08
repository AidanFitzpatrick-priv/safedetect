package com.safedetect.agent;

/**
 * Movement and rotation checks built from position and head-yaw changes between client ticks.
 * Thresholds sit well above vanilla limits; anything that legitimately moves a player (knockback, explosions,
 * pearls, liquids, ladders, webs, riding, invisibility) pauses the checks instead of being judged.
 */
final class MoveChecks {
    private static final int WINDOW = 20;
    private static final long HURT_GRACE = 40L;
    private static final double TELEPORT = 4.0;

    /*
     * The speed limit comes from CheckConfig (default 0.42; vanilla flat-ground sprint-jumping averages about
     * 0.36 blocks per tick), and every tick or violation count below is scaled by its sensitivity.
     */
    private static final int TOWER_TICKS = 5;
    private static final int SPRINT_SCAFFOLD_TICKS = 20;
    private static final int VELOCITY_VL = 6;
    private static final int GOD_TICKS = 60;
    private static final int DIAGONAL_TICKS = 50;
    /** Vanilla jump-towering gains about 2 blocks per 20 ticks. */
    private static final double TOWER_RISE = 3.5;
    /** Walking backwards tops out near 0.22 blocks per tick without Speed. */
    private static final double BACKWARD_LIMIT = 0.25;

    private boolean hasLast;
    private double lastX;
    private double lastY;
    private double lastZ;
    private float lastHeadYaw;
    private boolean hasHeadYaw;
    private int samples;
    private long lastHurt = Long.MIN_VALUE / 2;

    private final double[] horizontal = new double[WINDOW];
    private final double[] vertical = new double[WINDOW];
    private int ringIndex;
    private int ringCount;

    private int speedTicks;
    private int towerRun;
    private int towerTicks;
    private int sprintScaffoldTicks;

    private boolean prevSwinging;
    private int prevSwingProgress;
    private long lastSwingStart = Long.MIN_VALUE / 2;
    private long lastSnap = Long.MIN_VALUE / 2;
    private long lastSnapHit = Long.MIN_VALUE / 2;
    private final long[] snapHits = new long[5];
    private int snapHitIndex;

    private boolean speedFailed;
    private boolean towerFailed;
    private boolean sprintScaffoldFailed;
    private boolean snapFailed;
    private boolean kbWatch;
    private double kbX;
    private double kbZ;
    private int kbTicks;
    private int velocityVl;
    private boolean velocityFailed;
    private int godTicks;
    private boolean godFailed;
    private int diagTicks;
    private boolean diagFailed;
    private float lastPitch;
    private boolean hasPitch;
    private final long[] tellyHits = new long[8];
    private int tellyIndex;
    private boolean tellyFailed;
    private double speedAverage;
    private double speedLimitUsed;
    private double towerRise;
    private double backwardSpeed;
    private boolean backwardSprinting;
    private double velocityMoved;

    /** What made a check fail; read before the matching reset. */
    String evidence(FlagStore.Flag flag) {
        switch (flag) {
            case SP:
                return String.format(java.util.Locale.US, "%.2f b/t avg (limit %.2f)", speedAverage, speedLimitUsed);
            case TW:
                return String.format(java.util.Locale.US, "rose %.1f blocks in %d ticks", towerRise, WINDOW);
            case SS:
                return String.format(java.util.Locale.US, "bridged backwards at %.2f b/t%s", backwardSpeed,
                        backwardSprinting ? " while sprinting" : "");
            case SA:
                return snapHits.length + " 100+ deg snaps on swings in 10 s";
            case VL:
                return String.format(java.util.Locale.US, "moved %.2f after hit", velocityMoved);
            case GB:
                return "sprint-bridged without sneaking " + godTicks + " ticks";
            case DS:
                return "diagonal sprint-bridged " + diagTicks + " ticks";
            case TL:
                return tellyHits.length + " pitch flicks in 2.5 s";
            default:
                return "";
        }
    }

    /**
     * @param inGame false in Hypixel lobbies and Skyblock, where high speed is legitimate
     */
    void update(PlayerView view, long tick, int elapsed, boolean inGame, boolean movementData) {
        rotations(view, tick);
        if (!movementData) {
            return;
        }
        if (view.hurtTime > 0) {
            lastHurt = tick;
        }
        if (!hasLast) {
            remember(view);
            return;
        }
        int steps = Math.max(1, elapsed);
        double dx = (view.posX - lastX) / steps;
        double dy = (view.posY - lastY) / steps;
        double dz = (view.posZ - lastZ) / steps;
        remember(view);
        if (Math.abs(dx) > TELEPORT || Math.abs(dy) > TELEPORT || Math.abs(dz) > TELEPORT) {
            clearMovement();
            return;
        }
        samples++;
        double h = Math.sqrt(dx * dx + dz * dz);
        horizontal[ringIndex] = h;
        vertical[ringIndex] = dy;
        ringIndex = (ringIndex + 1) % WINDOW;
        if (ringCount < WINDOW) {
            ringCount++;
        }

        boolean excused = view.riding || view.invisible || view.climbingOrSwimming || tick - lastHurt < HURT_GRACE;
        double speedFactor = view.speedAmplifier >= 0 ? 1.0 + 0.2 * (view.speedAmplifier + 1) : 1.0;

        speed(excused, inGame, speedFactor);
        tower(view, excused);
        sprintScaffold(view, excused, dx, dz, h, speedFactor);
        velocity(view, tick, steps, h);
        godBridge(view, excused, dx, dz, h);
        diagonal(view, excused, dx, dz, h);
        telly(view, tick, h);
    }

    boolean failedSpeed() {
        return speedFailed;
    }

    boolean failedTower() {
        return towerFailed;
    }

    boolean failedSprintScaffold() {
        return sprintScaffoldFailed;
    }

    boolean failedSnapAim() {
        return snapFailed;
    }

    void resetSpeed() {
        speedFailed = false;
        speedTicks = 0;
    }

    void resetTower() {
        towerFailed = false;
        towerTicks = 0;
        towerRun = 0;
    }

    void resetSprintScaffold() {
        sprintScaffoldFailed = false;
        sprintScaffoldTicks = 0;
    }

    void resetSnapAim() {
        snapFailed = false;
        java.util.Arrays.fill(snapHits, 0L);
    }

    boolean failedVelocity() {
        return velocityFailed;
    }

    boolean failedGodBridge() {
        return godFailed;
    }

    boolean failedDiagonal() {
        return diagFailed;
    }

    boolean failedTelly() {
        return tellyFailed;
    }

    void resetVelocity() {
        velocityFailed = false;
        velocityVl = 0;
        kbWatch = false;
    }

    void resetGodBridge() {
        godFailed = false;
        godTicks = 0;
    }

    void resetDiagonal() {
        diagFailed = false;
        diagTicks = 0;
    }

    void resetTelly() {
        tellyFailed = false;
        java.util.Arrays.fill(tellyHits, 0L);
    }

    /**
     * After a fresh hit, vanilla knockback moves you. Standing still for 8 ticks is anti-KB.
     * Walls and liquids are skipped via climbingOrSwimming; tiny 1-block traps still exist.
     */
    private void velocity(PlayerView view, long tick, int steps, double h) {
        if (view.hurtTime >= 8 && view.hurtTime > view.prevHurtTime && (view.sprinting || h > 0.08)) {
            kbWatch = true;
            kbX = view.posX;
            kbZ = view.posZ;
            kbTicks = 0;
        }
        if (!kbWatch) {
            return;
        }
        kbTicks += steps;
        if (view.riding || view.climbingOrSwimming) {
            kbWatch = false;
            return;
        }
        if (kbTicks < 8) {
            return;
        }
        kbWatch = false;
        double moved = Math.sqrt((view.posX - kbX) * (view.posX - kbX) + (view.posZ - kbZ) * (view.posZ - kbZ));
        if (moved < 0.12) {
            velocityVl += 2;
            velocityMoved = moved;
        } else if (velocityVl > 0) {
            velocityVl--;
        }
        velocityFailed |= velocityVl >= CheckConfig.current().scaled(VELOCITY_VL);
    }

    /** Sprint-placing glued to the ground looking down, never sneaking — cheat god-bridge machines. */
    private void godBridge(PlayerView view, boolean excused, double dx, double dz, double h) {
        double yaw = Math.toRadians(view.yaw);
        double forward = dx * -Math.sin(yaw) + dz * Math.cos(yaw);
        double strafe = dx * Math.cos(yaw) + dz * Math.sin(yaw);
        boolean run = !excused && view.held == Game.HELD_BLOCK && view.pitch >= 70.0f && view.sprinting
                && !view.sneaking && view.onGround && h > 0.22 && forward > 0.12 && Math.abs(strafe) < 0.3 * h;
        godTicks = run ? godTicks + 1 : 0;
        godFailed |= godTicks > CheckConfig.current().scaled(GOD_TICKS);
    }

    /**
     * Sprint-placing on a 45-degree strafe while looking down, never sneaking. God bridge is straight
     * forward; this is W+A/D diagonal scaffold that legit shift-bridgers do not hold.
     */
    private void diagonal(PlayerView view, boolean excused, double dx, double dz, double h) {
        double yaw = Math.toRadians(view.yaw);
        double forward = dx * -Math.sin(yaw) + dz * Math.cos(yaw);
        double strafe = dx * Math.cos(yaw) + dz * Math.sin(yaw);
        boolean run = !excused && view.held == Game.HELD_BLOCK && view.pitch >= 70.0f && view.sprinting
                && !view.sneaking && view.onGround && h > 0.22 && forward > 0.08 && Math.abs(strafe) > 0.4 * h;
        diagTicks = run ? diagTicks + 1 : 0;
        diagFailed |= diagTicks > CheckConfig.current().scaled(DIAGONAL_TICKS);
    }

    /** Instant pitch flicks while bridging. Legit telly is a few slower flicks, not a drumroll. */
    private void telly(PlayerView view, long tick, double h) {
        float pitch = view.pitch;
        if (hasPitch && view.held == Game.HELD_BLOCK && h > 0.12 && Math.abs(pitch - lastPitch) >= 55.0f) {
            tellyHits[tellyIndex] = tick;
            tellyIndex = (tellyIndex + 1) % tellyHits.length;
            int recent = 0;
            for (int i = 0; i < tellyHits.length; i++) {
                if (tellyHits[i] != 0L && tick - tellyHits[i] <= 50L) {
                    recent++;
                }
            }
            tellyFailed |= recent >= tellyHits.length;
        }
        lastPitch = pitch;
        hasPitch = true;
    }

    /** Sustained ground speed above the sprint-jump limit, scaled for Speed potions. */
    private void speed(boolean excused, boolean inGame, double speedFactor) {
        if (excused || !inGame || ringCount < WINDOW || samples < 2 * WINDOW) {
            speedTicks = 0;
            return;
        }
        CheckConfig config = CheckConfig.current();
        double average = sum(horizontal) / WINDOW;
        double limit = config.speedLimit * speedFactor;
        speedTicks = average > limit ? speedTicks + 1 : 0;
        if (speedTicks > 0) {
            speedAverage = average;
            speedLimitUsed = limit;
        }
        speedFailed |= speedTicks > config.scaled(WINDOW);
    }

    /** Rising faster than jump-placing allows while looking down at a block in hand. */
    private void tower(PlayerView view, boolean excused) {
        boolean placing = view.held == Game.HELD_BLOCK && view.pitch >= 70.0f;
        if (!placing || excused || view.jumpAmplifier >= 0) {
            towerRun = 0;
            towerTicks = 0;
            return;
        }
        towerRun++;
        if (towerRun < WINDOW || ringCount < WINDOW) {
            return;
        }
        double rise = sum(vertical);
        boolean fast = rise > TOWER_RISE && sum(horizontal) < 2.0;
        towerTicks = fast ? towerTicks + 1 : 0;
        if (fast) {
            towerRise = rise;
        }
        towerFailed |= towerTicks > CheckConfig.current().scaled(TOWER_TICKS);
    }

    /** Bridging backwards while sprinting, or faster than walking backwards allows. */
    private void sprintScaffold(PlayerView view, boolean excused, double dx, double dz, double h, double speedFactor) {
        boolean bridging = view.held == Game.HELD_BLOCK && view.pitch >= 70.0f;
        if (!bridging || excused || h < 0.15) {
            sprintScaffoldTicks = 0;
            return;
        }
        double yaw = Math.toRadians(view.yaw);
        double forward = dx * -Math.sin(yaw) + dz * Math.cos(yaw);
        boolean backward = forward < -0.7 * h;
        boolean impossible = view.sprinting || h > BACKWARD_LIMIT * speedFactor;
        sprintScaffoldTicks = backward && impossible ? sprintScaffoldTicks + 1 : 0;
        if (backward && impossible) {
            backwardSpeed = h;
            backwardSprinting = view.sprinting;
        }
        sprintScaffoldFailed |= sprintScaffoldTicks > CheckConfig.current().scaled(SPRINT_SCAFFOLD_TICKS);
    }

    /** Head turns of 100 degrees or more in one tick that line up with a swing, five times in ten seconds. */
    private void rotations(PlayerView view, long tick) {
        boolean swingStart = view.swinging && (!prevSwinging || view.swingProgressInt < prevSwingProgress);
        prevSwinging = view.swinging;
        prevSwingProgress = view.swingProgressInt;
        if (swingStart) {
            lastSwingStart = tick;
        }
        if (hasHeadYaw && Math.abs(wrap(view.headYaw - lastHeadYaw)) >= 100.0f) {
            lastSnap = tick;
        }
        lastHeadYaw = view.headYaw;
        hasHeadYaw = true;
        if (view.invisible || view.riding) {
            return;
        }
        if (Math.abs(tick - lastSnap) <= 2 && Math.abs(tick - lastSwingStart) <= 2 && tick - lastSnapHit > 3) {
            lastSnapHit = tick;
            snapHits[snapHitIndex] = tick;
            snapHitIndex = (snapHitIndex + 1) % snapHits.length;
            int recent = 0;
            for (long hit : snapHits) {
                if (hit != 0L && tick - hit <= 200L) {
                    recent++;
                }
            }
            snapFailed |= recent >= snapHits.length;
        }
    }

    private void remember(PlayerView view) {
        lastX = view.posX;
        lastY = view.posY;
        lastZ = view.posZ;
        hasLast = true;
    }

    private void clearMovement() {
        ringCount = 0;
        ringIndex = 0;
        samples = 0;
        speedTicks = 0;
        towerRun = 0;
        towerTicks = 0;
        sprintScaffoldTicks = 0;
        godTicks = 0;
        diagTicks = 0;
    }

    private static double sum(double[] values) {
        double total = 0.0;
        for (double value : values) {
            total += value;
        }
        return total;
    }

    private static float wrap(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped >= 180.0f) {
            wrapped -= 360.0f;
        }
        if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }
}
