package net.minecraft.util;

import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;

public class ChatStyle {
    public ClickEvent click;
    public HoverEvent hover;
    public String insertion;

    public ClickEvent getChatClickEvent() {
        return click;
    }

    public ChatStyle setChatClickEvent(ClickEvent event) {
        click = event;
        return this;
    }

    public ChatStyle setChatHoverEvent(HoverEvent event) {
        hover = event;
        return this;
    }

    public ChatStyle setInsertion(String text) {
        insertion = text;
        return this;
    }
}
