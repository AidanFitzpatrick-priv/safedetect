package net.minecraft.util;

public class ChatComponentText implements IChatComponent {
    private final String text;
    private ChatStyle style = new ChatStyle();

    public ChatComponentText(String text) {
        this.text = text;
    }

    @Override
    public String getUnformattedText() {
        return text;
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
}
