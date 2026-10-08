package net.minecraft.util;

public interface IChatComponent {
    String getUnformattedText();

    ChatStyle getChatStyle();

    IChatComponent setChatStyle(ChatStyle style);
}
