package net.minecraft.client.gui;

public class GuiTextField {
    private String text = "";

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
    }
}
