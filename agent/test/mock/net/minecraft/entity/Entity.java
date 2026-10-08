package net.minecraft.entity;

import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import java.util.UUID;

public abstract class Entity {
    public double posX;
    public double posY = 64.0;
    public double posZ;
    public boolean onGround = true;
    public float rotationYaw;
    public float rotationPitch;
    public boolean isDead;
    public World worldObj;
    public Entity ridingEntity;
    public int ticksExisted;
    protected boolean isInWeb;
    protected UUID entityUniqueID = UUID.randomUUID();
    public boolean sneaking;
    public boolean sprinting;
    public boolean inWater;
    public boolean invisible;

    public UUID getUniqueID() {
        return entityUniqueID;
    }

    public abstract String getName();

    public boolean isSneaking() {
        return sneaking;
    }

    public boolean isSprinting() {
        return sprinting;
    }

    public boolean isInWater() {
        return inWater;
    }

    public boolean isInLava() {
        return false;
    }

    public boolean isInvisible() {
        return invisible;
    }

    public void addChatMessage(IChatComponent component) {
    }

    public void playSound(String name, float volume, float pitch) {
    }
}
