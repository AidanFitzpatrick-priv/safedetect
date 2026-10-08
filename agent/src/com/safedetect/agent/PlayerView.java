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
}
