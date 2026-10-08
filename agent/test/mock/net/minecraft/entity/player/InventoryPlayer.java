package net.minecraft.entity.player;

import net.minecraft.item.ItemStack;

public class InventoryPlayer {
    public ItemStack[] mainInventory = new ItemStack[36];
    public ItemStack[] armorInventory = new ItemStack[4];
    public int currentItem;
}
