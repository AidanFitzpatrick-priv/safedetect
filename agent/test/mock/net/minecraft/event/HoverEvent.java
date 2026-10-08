package net.minecraft.event;

import net.minecraft.util.IChatComponent;

public class HoverEvent {
    public enum Action {
        SHOW_TEXT,
        SHOW_ITEM,
        SHOW_ACHIEVEMENT,
        SHOW_ENTITY
    }

    private final Action action;
    private final IChatComponent value;

    public HoverEvent(Action action, IChatComponent value) {
        this.action = action;
        this.value = value;
    }

    public Action getAction() {
        return action;
    }

    public IChatComponent getValue() {
        return value;
    }
}
