package com.safedetect.agent;

import java.util.List;
import java.util.Locale;

/**
 * Counts diamond and emerald generator spawns from game start. Clock only — not inventory reading.
 */
final class GenPadPlugin extends PluginPack.Base implements PluginEvents {
    private long startedAt;
    private int lastDiamond;
    private int lastEmerald;

    GenPadPlugin() {
        super("gens", "GenPad",
                "Counts diamond and emerald gens spawned this game from a timer. /sd gens to print.",
                "", "/sd gens");
    }

    @Override
    public boolean command(String[] parts) {
        if (startedAt <= 0L) {
            api.chat("\u00a77GenPad starts when the Bedwars game starts.");
            return true;
        }
        api.chat(line(System.currentTimeMillis()));
        return true;
    }

    @Override
    public void world() {
        startedAt = 0L;
        lastDiamond = 0;
        lastEmerald = 0;
    }

    @Override
    public void tab(List<PluginPlayer> players) {
        if (startedAt <= 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        int diamondMs = api.configInt("gens.diamondMs", 30000);
        int emeraldMs = api.configInt("gens.emeraldMs", 55000);
        long elapsed = now - startedAt;
        int diamonds = spawned(elapsed, diamondMs);
        int emeralds = spawned(elapsed, emeraldMs);
        if (api.configOn("gens.announce", false)) {
            if (diamonds > lastDiamond) {
                api.chat("\u00a7bDiamond spawn \u00a7f#" + diamonds);
            }
            if (emeralds > lastEmerald) {
                api.chat("\u00a7aEmerald spawn \u00a7f#" + emeralds);
            }
        }
        lastDiamond = diamonds;
        lastEmerald = emeralds;
    }

    @Override
    public void chat(String plain) {
        String low = plain.toLowerCase(Locale.ROOT);
        if (low.equals("the game starts in 1 second!") || low.startsWith("protect your bed")) {
            if (startedAt <= 0L) {
                startedAt = System.currentTimeMillis();
                lastDiamond = 0;
                lastEmerald = 0;
            }
        }
    }

    private String line(long now) {
        int diamondMs = api.configInt("gens.diamondMs", 30000);
        int emeraldMs = api.configInt("gens.emeraldMs", 55000);
        long elapsed = now - startedAt;
        int diamonds = spawned(elapsed, diamondMs);
        int emeralds = spawned(elapsed, emeraldMs);
        return "\u00a77Gens \u00a7bD" + diamonds + " \u00a78(next " + (nextMs(elapsed, diamondMs) / 1000) + "s) \u00a7aE"
                + emeralds + " \u00a78(next " + (nextMs(elapsed, emeraldMs) / 1000) + "s)";
    }

    static int spawned(long elapsedMs, int intervalMs) {
        if (elapsedMs < 0L || intervalMs <= 0) {
            return 0;
        }
        return (int) (elapsedMs / intervalMs);
    }

    static int nextMs(long elapsedMs, int intervalMs) {
        if (intervalMs <= 0) {
            return 0;
        }
        if (elapsedMs <= 0L) {
            return intervalMs;
        }
        int rem = (int) (elapsedMs % intervalMs);
        return rem == 0 ? intervalMs : intervalMs - rem;
    }
}
