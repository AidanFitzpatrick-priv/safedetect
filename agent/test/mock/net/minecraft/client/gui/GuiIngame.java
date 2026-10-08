package net.minecraft.client.gui;

public class GuiIngame {
    public final GuiNewChat persistantChatGUI = new GuiNewChat();

    public GuiNewChat getChatGUI() {
        return persistantChatGUI;
    }
}
