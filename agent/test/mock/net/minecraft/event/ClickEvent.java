package net.minecraft.event;

public class ClickEvent {
    public enum Action {
        OPEN_URL,
        OPEN_FILE,
        RUN_COMMAND,
        TWITCH_USER_INFO,
        SUGGEST_COMMAND
    }

    private final Action action;
    private final String value;

    public ClickEvent(Action action, String value) {
        this.action = action;
        this.value = value;
    }

    public Action getAction() {
        return action;
    }

    public String getValue() {
        return value;
    }
}
