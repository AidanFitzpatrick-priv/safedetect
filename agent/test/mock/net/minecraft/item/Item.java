package net.minecraft.item;

public class Item {
    private final int id;

    public Item() {
        this(1);
    }

    public Item(int id) {
        this.id = id;
    }

    public static int getIdFromItem(Item item) {
        return item == null ? 0 : item.id;
    }
}
