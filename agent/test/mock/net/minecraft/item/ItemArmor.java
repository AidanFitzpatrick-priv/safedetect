package net.minecraft.item;

public class ItemArmor extends Item {
    public enum ArmorMaterial {
        LEATHER,
        IRON
    }

    private final ArmorMaterial material;

    public ItemArmor(ArmorMaterial material) {
        this.material = material;
    }

    public ArmorMaterial getArmorMaterial() {
        return material;
    }

    public int getColor(ItemStack stack) {
        return material == ArmorMaterial.LEATHER ? stack.color : -1;
    }
}
