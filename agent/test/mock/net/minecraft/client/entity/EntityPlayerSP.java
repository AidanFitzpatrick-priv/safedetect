package net.minecraft.client.entity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.IChatComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EntityPlayerSP extends EntityPlayer {
    public final List<String> chat = new ArrayList<String>();

    public EntityPlayerSP(String name) {
        super(name, UUID.randomUUID());
    }

    @Override
    public void addChatMessage(IChatComponent component) {
        String text = component.getUnformattedText();
        chat.add(text);
        System.out.println("CHAT: " + text.replaceAll("\u00a7.", ""));
        if (component.getChatStyle() != null && component.getChatStyle().click != null) {
            System.out.println("CLICK: " + component.getChatStyle().click.getValue());
        }
    }

    public void sendChatMessage(String message) {
        net.minecraft.client.Minecraft.getMinecraft().ingameGUI.getChatGUI().sentMessages.add(message);
    }
}
