package net.minecraft.client.gui;

import java.util.ArrayList;
import java.util.List;

public class GuiIngame {
    public final GuiNewChat persistantChatGUI = new GuiNewChat();
    public final List<String> titles = new ArrayList<String>();

    public GuiNewChat getChatGUI() {
        return persistantChatGUI;
    }

    public void displayTitle(String title, String subTitle, int fadeIn, int display, int fadeOut) {
        if (title != null) {
            titles.add(title);
        }
    }
}
