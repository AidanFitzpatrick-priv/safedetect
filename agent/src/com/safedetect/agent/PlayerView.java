package com.safedetect.agent;

/**
 * One player's state for the current tick, read once on the client thread and shared by every check.
 */
final class PlayerView {
    java.util.UUID uuid;
    double posX;
    double posY;
    double posZ;
    boolean onGround;
    float pitch;
    float yaw;
    float headYaw;
    boolean sneaking;
    boolean sprinting;
    boolean usingItem;
    boolean swinging;
    boolean swingStart;
    int swingProgressInt;
    boolean riding;
    int held;
    int speedAmplifier;
    int jumpAmplifier;
    int hurtTime;
    int prevHurtTime;
    boolean climbingOrSwimming;
    boolean invisible;

    void copyFrom(PlayerView src) {
        if (src == null) {
            return;
        }
        uuid = src.uuid;
        posX = src.posX;
        posY = src.posY;
        posZ = src.posZ;
        onGround = src.onGround;
        pitch = src.pitch;
        yaw = src.yaw;
        headYaw = src.headYaw;
        sneaking = src.sneaking;
        sprinting = src.sprinting;
        usingItem = src.usingItem;
        swinging = src.swinging;
        swingStart = src.swingStart;
        swingProgressInt = src.swingProgressInt;
        riding = src.riding;
        held = src.held;
        speedAmplifier = src.speedAmplifier;
        jumpAmplifier = src.jumpAmplifier;
        hurtTime = src.hurtTime;
        prevHurtTime = src.prevHurtTime;
        climbingOrSwimming = src.climbingOrSwimming;
        invisible = src.invisible;
    }
}
