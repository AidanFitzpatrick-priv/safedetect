package net.minecraft.client.gui;

import net.minecraft.util.IChatComponent;

public class ChatLine {
    private final IChatComponent lineString;

    public ChatLine(IChatComponent component) {
        lineString = component;
    }

    public IChatComponent getChatComponent() {
        return lineString;
    }
}
