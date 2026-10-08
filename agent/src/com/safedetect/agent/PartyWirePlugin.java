package com.safedetect.agent;

import java.util.List;
import java.util.Locale;

/** Switches to party chat when you join a party, and all chat when you leave. */
final class PartyWirePlugin extends PluginPack.Base implements PluginEvents {
    private boolean inParty;

    PartyWirePlugin() {
        super("pchat", "PartyWire",
                "Runs /chat p when you join a party and /chat a when you leave.",
                "", "/sd pchat");
    }

    @Override
    public boolean command(String[] parts) {
        api.chat("\u00a77PartyWire " + (inParty ? "\u00a7ain party chat" : "\u00a7cnot in a party")
                + "\u00a77.");
        return true;
    }

    @Override
    public void world() {
    }

    @Override
    public void tab(List<PluginPlayer> players) {
    }

    @Override
    public void chat(String plain) {
        String low = plain.toLowerCase(Locale.ROOT);
        if (low.contains("you have joined") && low.contains("party")
                || low.startsWith("party leader:")
                || low.contains("joined the party")) {
            if (!inParty && (low.contains("you have joined") || low.startsWith("party leader:"))) {
                inParty = true;
                api.send("/chat p");
                api.chat("\u00a77PartyWire \u00a7a/chat p");
            }
            return;
        }
        if (low.contains("you left the party") || low.contains("party was disbanded")
                || low.contains("you have been kicked from the party")
                || low.contains("you are not currently in a party")) {
            if (inParty) {
                inParty = false;
                api.send("/chat a");
                api.chat("\u00a77PartyWire \u00a7c/chat a");
            }
        }
    }
}
