package com.safedetect.agent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Chats nicked (UUID version 1) names at Bedwars start. Does not look them up. */
final class NickPingPlugin extends PluginPack.Base implements PluginEvents {
    private final Set<String> announced = new LinkedHashSet<String>();
    private boolean inGame;

    NickPingPlugin() {
        super("nickping", "NickPing",
                "At game start, lists tab names whose UUID is version 1 (nicked). No lookup, no name hide.",
                "", "/sd nickping");
    }

    @Override
    public boolean command(String[] parts) {
        api.chat("\u00a77NickPing lists nicked UUIDs when the game starts. Use NickFind to resolve them.");
        return true;
    }

    @Override
    public void world() {
        announced.clear();
        inGame = false;
    }

    @Override
    public void tab(List<PluginPlayer> players) {
        if (!inGame) {
            return;
        }
        List<String> nicked = new ArrayList<String>();
        for (PluginPlayer player : players) {
            if (player.id == null || player.name == null || !NickFindPlugin.nicked(player.id)) {
                continue;
            }
            if (Detector.hypixelBotName(player.name) || api.skipped(player.name)) {
                continue;
            }
            UUID self = api.selfUuid();
            if (self != null && self.equals(player.id)) {
                continue;
            }
            String low = player.name.toLowerCase(Locale.ROOT);
            if (!announced.add(low)) {
                continue;
            }
            nicked.add(player.name);
        }
        if (nicked.isEmpty()) {
            return;
        }
        api.sound();
        api.chat("\u00a76Nicked \u00a7f" + String.join("\u00a77, \u00a7f", nicked));
    }

    @Override
    public void chat(String plain) {
        String low = plain.toLowerCase(Locale.ROOT);
        if (low.startsWith("protect your bed") || low.equals("the game starts in 1 second!")) {
            inGame = true;
        }
    }
}
