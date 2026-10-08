package com.safedetect.agent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pings when a listed staff IGN or a staff rank prefix is in tab. Alert only — no auto-wdr.
 */
final class StaffWatchPlugin extends PluginPack.Base implements PluginEvents {
    private static final Pattern RANK = Pattern.compile("\\[(STAFF|ADMIN|OWNER|GM|PIG\\+*)\\]", Pattern.CASE_INSENSITIVE);
    private final Set<String> seen = new LinkedHashSet<String>();

    StaffWatchPlugin() {
        super("staff", "StaffWatch",
                "Alerts if a listed name or a staff/admin/GM rank is in the lobby tab.",
                "", "/sd staff add|remove|list <ign>");
    }

    @Override
    public boolean command(String[] parts) {
        Set<String> list = list();
        if (parts.length >= 4 && "add".equalsIgnoreCase(parts[2])) {
            list.add(parts[3].toLowerCase(Locale.ROOT));
            save(list);
            api.chat("\u00a7aWatching staff \u00a7f" + parts[3] + "\u00a7a.");
            return true;
        }
        if (parts.length >= 4 && "remove".equalsIgnoreCase(parts[2])) {
            list.remove(parts[3].toLowerCase(Locale.ROOT));
            save(list);
            api.chat("\u00a77Stopped watching \u00a7f" + parts[3] + "\u00a77.");
            return true;
        }
        if (list.isEmpty()) {
            api.chat("\u00a77No extra names. Rank prefixes still ping. \u00a78/sd staff add <ign>");
        } else {
            api.chat("\u00a77Staff list: \u00a7f" + String.join(" ", list));
        }
        return true;
    }

    @Override
    public void world() {
        seen.clear();
    }

    @Override
    public void tab(List<PluginPlayer> players) {
        Set<String> watch = list();
        for (PluginPlayer player : players) {
            if (player.name == null || Detector.hypixelBotName(player.name) || api.skipped(player.name)) {
                continue;
            }
            String low = player.name.toLowerCase(Locale.ROOT);
            if (seen.contains(low)) {
                continue;
            }
            seen.add(low);
            String prefix = api.teamPrefix(player.name);
            boolean rank = staffRank(prefix);
            boolean listed = watch.contains(low);
            if (!rank && !listed) {
                continue;
            }
            api.sound();
            api.chat("\u00a7cStaff \u00a7f" + player.name + (rank ? " \u00a77" + strip(prefix).trim() : "")
                    + " \u00a77is in this lobby.");
        }
    }

    @Override
    public void chat(String plain) {
    }

    static boolean staffRank(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return false;
        }
        Matcher rank = RANK.matcher(strip(prefix));
        return rank.find();
    }

    private Set<String> list() {
        Set<String> out = new LinkedHashSet<String>();
        for (String name : api.config("staff.list").split(",")) {
            if (!name.trim().isEmpty()) {
                out.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private void save(Set<String> names) {
        api.config("staff.list", String.join(",", names));
    }

    private static String strip(String text) {
        return text == null ? "" : text.replaceAll("\u00a7.", "");
    }
}
