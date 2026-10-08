package com.safedetect.agent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One-shot chat reminders. */
final class RemindPlugin extends PluginPack.Base implements PluginEvents {
    private static final Pattern DURATION = Pattern.compile("(?i)^(?:(\\d+)m)?(?:(\\d+)s)?$");
    private final List<Note> notes = new ArrayList<Note>();

    private static final class Note {
        final long at;
        final String text;

        Note(long at, String text) {
            this.at = at;
            this.text = text;
        }
    }

    RemindPlugin() {
        super("remind", "Remind",
                "Ping yourself later. /sd remind 90s take mid  or  /sd remind 2m bed.",
                "", "/sd remind <time> <text> | list | clear");
    }

    @Override
    public boolean command(String[] parts) {
        if (parts.length >= 3 && "clear".equalsIgnoreCase(parts[2])) {
            notes.clear();
            api.chat("\u00a77Reminders cleared.");
            return true;
        }
        if (parts.length >= 3 && "list".equalsIgnoreCase(parts[2])) {
            if (notes.isEmpty()) {
                api.chat("\u00a77No reminders.");
            } else {
                long now = System.currentTimeMillis();
                for (Note note : notes) {
                    long left = Math.max(0L, (note.at - now) / 1000L);
                    api.chat("\u00a77" + left + "s \u00a7f" + note.text);
                }
            }
            return true;
        }
        if (parts.length < 4) {
            api.chat("\u00a7cUsage: /sd remind 90s <text>");
            return true;
        }
        int ms = parseDuration(parts[2]);
        if (ms <= 0) {
            api.chat("\u00a7cTime like 90s or 2m.");
            return true;
        }
        StringBuilder text = new StringBuilder();
        for (int i = 3; i < parts.length; i++) {
            text.append(i == 3 ? "" : " ").append(parts[i]);
        }
        notes.add(new Note(System.currentTimeMillis() + ms, text.toString()));
        api.chat("\u00a77Remind in \u00a7f" + (ms / 1000) + "s\u00a77: " + text);
        return true;
    }

    @Override
    public void world() {
    }

    @Override
    public void tab(List<PluginPlayer> players) {
        if (notes.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Note> it = notes.iterator();
        while (it.hasNext()) {
            Note note = it.next();
            if (now >= note.at) {
                it.remove();
                api.sound();
                api.chat("\u00a7eRemind \u00a7f" + note.text);
            }
        }
    }

    @Override
    public void chat(String plain) {
    }

    static int parseDuration(String raw) {
        if (raw == null || raw.isEmpty()) {
            return -1;
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        if (text.matches("\\d+")) {
            return Integer.parseInt(text) * 1000;
        }
        Matcher match = DURATION.matcher(text);
        if (!match.matches() || (match.group(1) == null && match.group(2) == null)) {
            return -1;
        }
        int ms = 0;
        if (match.group(1) != null) {
            ms += Integer.parseInt(match.group(1)) * 60000;
        }
        if (match.group(2) != null) {
            ms += Integer.parseInt(match.group(2)) * 1000;
        }
        return ms <= 0 ? -1 : ms;
    }
}
