package net.minecraft.util;

import java.util.List;

public interface IChatComponent {
    String getUnformattedText();

    String getUnformattedTextForChat();

    List<IChatComponent> getSiblings();

    ChatStyle getChatStyle();

    IChatComponent setChatStyle(ChatStyle style);

    IChatComponent appendSibling(IChatComponent sibling);

    IChatComponent createCopy();
}
