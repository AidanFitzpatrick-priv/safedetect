package net.minecraft.entity.player;

import net.minecraft.entity.EntityLivingBase;

import java.util.UUID;

public abstract class EntityPlayer extends EntityLivingBase {
    public InventoryPlayer inventory = new InventoryPlayer();
    public PlayerCapabilities capabilities = new PlayerCapabilities();
    private final String name;
    public boolean usingItem;

    protected EntityPlayer(String name, UUID id) {
        this.name = name;
        if (id != null) {
            entityUniqueID = id;
        }
    }

    @Override
    public String getName() {
        return name;
    }

    public boolean isUsingItem() {
        return usingItem;
    }
}
