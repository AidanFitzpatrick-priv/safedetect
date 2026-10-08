package net.minecraft.entity;

import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

public abstract class EntityLivingBase extends Entity {
    public boolean isSwingInProgress;
    public int swingProgressInt;
    public float rotationYawHead;
    public int hurtTime;
    public PotionEffect speed;
    public PotionEffect jump;

    public PotionEffect getActivePotionEffect(Potion potion) {
        if (potion == Potion.moveSpeed) {
            return speed;
        }
        return potion == Potion.jump ? jump : null;
    }

    public boolean isOnLadder() {
        return false;
    }
}
