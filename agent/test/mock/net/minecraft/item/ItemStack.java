package net.minecraft.item;

public final class ItemStack {
    private final Item item;
    public int color;

    public ItemStack(Item item) {
        this.item = item;
    }

    public ItemStack(Item item, int color) {
        this.item = item;
        this.color = color;
    }

    public Item getItem() {
        return item;
    }
}
