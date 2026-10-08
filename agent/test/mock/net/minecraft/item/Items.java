package net.minecraft.item;

public final class Items {
    public static final Item SWORD = new ItemSword();
    public static final Item WOOL = new ItemBlock();
    public static final Item APPLE = new ItemFood();
    public static final ItemArmor LEATHER_CHEST = new ItemArmor(ItemArmor.ArmorMaterial.LEATHER);
    public static final ItemArmor IRON_CHEST = new ItemArmor(ItemArmor.ArmorMaterial.IRON);
    public static final Item COMPASS = new Item(345);

    private Items() {
    }
}
