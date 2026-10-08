package com.safedetect.agent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Warns when you land on a map you marked. Does not leave or requeue.
 */
final class MapWarnPlugin extends PluginPack.Base implements PluginEvents {
    MapWarnPlugin() {
        super("maps", "MapWarn",
                "Chat-warns on maps you dislike. Never auto-leaves.",
                "", "/sd maps add|remove|list <map>");
    }

    @Override
    public boolean command(String[] parts) {
        Set<String> list = list();
        if (parts.length >= 4 && "add".equalsIgnoreCase(parts[2])) {
            list.add(parts[3].toLowerCase(Locale.ROOT).replace(" ", ""));
            save(list);
            api.chat("\u00a7eWill warn on \u00a7f" + parts[3] + "\u00a7e.");
            return true;
        }
        if (parts.length >= 4 && "remove".equalsIgnoreCase(parts[2])) {
            list.remove(parts[3].toLowerCase(Locale.ROOT).replace(" ", ""));
            save(list);
            api.chat("\u00a77Stopped warning on \u00a7f" + parts[3] + "\u00a77.");
            return true;
        }
        if (list.isEmpty()) {
            api.chat("\u00a77No maps marked. \u00a78/sd maps add speedway");
        } else {
            api.chat("\u00a77Warn maps: \u00a7f" + String.join(" ", list));
        }
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
        Set<String> list = list();
        if (list.isEmpty()) {
            return;
        }
        String low = plain.toLowerCase(Locale.ROOT);
        if (!low.contains("sending you to") && !low.contains("the map") && !low.contains("playing on")
                && !low.startsWith("you are playing")) {
            return;
        }
        String compact = low.replace(" ", "");
        for (String map : list) {
            if (map.length() >= 3 && compact.contains(map)) {
                api.sound();
                api.chat("\u00a7eMapWarn \u00a7f" + map + " \u00a77(you marked this). Not leaving.");
                return;
            }
        }
    }

    private Set<String> list() {
        Set<String> out = new LinkedHashSet<String>();
        for (String map : api.config("maps.list").split(",")) {
            if (!map.trim().isEmpty()) {
                out.add(map.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private void save(Set<String> maps) {
        api.config("maps.list", String.join(",", maps));
    }
}
