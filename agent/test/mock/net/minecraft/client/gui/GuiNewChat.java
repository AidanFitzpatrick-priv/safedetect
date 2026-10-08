package net.minecraft.client.gui;

import java.util.ArrayList;
import java.util.List;

public class GuiNewChat {
    public final List<String> sentMessages = new ArrayList<String>();
    public final List<ChatLine> chatLines = new ArrayList<ChatLine>();

    public List<String> getSentMessages() {
        return sentMessages;
    }
}
