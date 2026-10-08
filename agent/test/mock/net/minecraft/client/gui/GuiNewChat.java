package net.minecraft.client.gui;

import net.minecraft.util.IChatComponent;

import java.util.ArrayList;
import java.util.List;

/** Matches vanilla 1.8.9: received lines are newest first and capped at 100; sent history skips repeats. */
public class GuiNewChat {
    public final List<String> sentMessages = new ArrayList<String>();
    public final List<ChatLine> chatLines = new ArrayList<ChatLine>();

    public List<String> getSentMessages() {
        return sentMessages;
    }

    public void printChatMessage(IChatComponent component) {
        chatLines.add(0, new ChatLine(component));
        while (chatLines.size() > 100) {
            chatLines.remove(chatLines.size() - 1);
        }
    }

    public void addToSentMessages(String message) {
        if (sentMessages.isEmpty() || !sentMessages.get(sentMessages.size() - 1).equals(message)) {
            sentMessages.add(message);
        }
    }
}
