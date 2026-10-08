package com.safedetect.agent;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Countdown pings at 20s / 10s / game start. No movement macros. */
final class StartBeepPlugin extends PluginPack.Base implements PluginEvents {
    private static final Pattern COUNT = Pattern.compile("(?i)^the game starts in (\\d+) seconds?!$");

    StartBeepPlugin() {
        super("beep", "StartBeep",
                "Plays a ping at configured pre-game seconds and when the game starts.",
                "", "/sd beep [20,10,1]");
    }

    @Override
    public boolean command(String[] parts) {
        if (parts.length >= 3) {
            api.config("beep.at", parts[2].replace(" ", ""));
        }
        api.chat("\u00a77StartBeep at \u00a7f" + at() + "s\u00a77, plus game start.");
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
        if (low.startsWith("protect your bed")) {
            api.sound();
            return;
        }
        Matcher count = COUNT.matcher(plain);
        if (!count.find()) {
            return;
        }
        int seconds = Integer.parseInt(count.group(1));
        if (wanted(seconds)) {
            api.sound();
        }
    }

    private String at() {
        String saved = api.config("beep.at");
        return saved.isEmpty() ? "20,10,5" : saved;
    }

    private boolean wanted(int seconds) {
        for (String part : at().split(",")) {
            try {
                if (Integer.parseInt(part.trim()) == seconds) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }
}
