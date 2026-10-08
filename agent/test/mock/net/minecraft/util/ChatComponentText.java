package net.minecraft.util;

import java.util.ArrayList;
import java.util.List;

public class ChatComponentText implements IChatComponent {
    private final String text;
    private ChatStyle style = new ChatStyle();
    private final List<IChatComponent> siblings = new ArrayList<IChatComponent>();

    public ChatComponentText(String text) {
        this.text = text;
    }

    @Override
    public String getUnformattedText() {
        StringBuilder out = new StringBuilder(text == null ? "" : text);
        for (int i = 0; i < siblings.size(); i++) {
            out.append(siblings.get(i).getUnformattedText());
        }
        return out.toString();
    }

    @Override
    public String getUnformattedTextForChat() {
        return text == null ? "" : text;
    }

    @Override
    public List<IChatComponent> getSiblings() {
        return siblings;
    }

    @Override
    public ChatStyle getChatStyle() {
        return style;
    }

    @Override
    public IChatComponent setChatStyle(ChatStyle style) {
        this.style = style;
        return this;
    }

    @Override
    public IChatComponent appendSibling(IChatComponent sibling) {
        if (sibling != null) {
            siblings.add(sibling);
        }
        return this;
    }

    @Override
    public IChatComponent createCopy() {
        ChatComponentText copy = new ChatComponentText(text);
        copy.style = style;
        for (int i = 0; i < siblings.size(); i++) {
            copy.siblings.add(siblings.get(i).createCopy());
        }
        return copy;
    }
}
