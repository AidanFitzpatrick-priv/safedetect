package com.safedetect.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Mojang name history via public lookup APIs. Command only. */
final class NameLogPlugin extends PluginPack.Base {
    private static final Pattern IGN = Pattern.compile("^[A-Za-z0-9_]{1,16}$");

    NameLogPlugin() {
        super("names", "NameLog",
                "Prints past Minecraft names for an IGN.",
                "", "/sd names <ign>");
    }

    @Override
    public boolean command(String[] parts) {
        if (parts.length < 3 || !IGN.matcher(parts[2]).matches()) {
            api.chat("\u00a7cUsage: /sd names <ign>");
            return true;
        }
        final String who = parts[2];
        new Thread(new Runnable() {
            @Override
            public void run() {
                lookup(who);
            }
        }, "sd-names").start();
        return true;
    }

    private void lookup(String who) {
        String raw = api.httpGet("https://api.ashcon.app/mojang/v2/user/" + PluginPack.encode(who), null, null);
        List<String> names = parse(raw);
        if (names.isEmpty()) {
            String uuid = uuidOf(who);
            if (uuid != null) {
                raw = api.httpGet("https://laby.net/api/v3/user/" + uuid + "/names", null, null);
                names = parse(raw);
            }
        }
        if (names.isEmpty()) {
            api.chat("\u00a7cNo name history for \u00a7f" + who + "\u00a7c.");
            return;
        }
        StringBuilder line = new StringBuilder("\u00a77").append(who).append(":");
        for (int i = 0; i < names.size(); i++) {
            line.append(i == 0 ? " \u00a7f" : " \u00a78\u2192 \u00a7f").append(names.get(i));
        }
        api.chat(line.toString());
    }

    @SuppressWarnings("unchecked")
    private String uuidOf(String who) {
        String mojang = api.httpGet("https://api.mojang.com/users/profiles/minecraft/" + PluginPack.encode(who),
                null, null);
        if (mojang == null || mojang.isEmpty()) {
            return null;
        }
        try {
            Object parsed = Json.parse(mojang);
            if (parsed instanceof Map && ((Map<String, Object>) parsed).get("id") instanceof String) {
                return (String) ((Map<String, Object>) parsed).get("id");
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static List<String> parse(String json) {
        List<String> out = new ArrayList<String>();
        if (json == null || json.isEmpty() || "RATE".equals(json) || "INVALID_KEY".equals(json)) {
            return out;
        }
        try {
            Object root = Json.parse(json);
            if (root instanceof Map) {
                Object history = ((Map<?, ?>) root).get("username_history");
                if (history instanceof List) {
                    for (Object row : (List<?>) history) {
                        if (row instanceof Map && ((Map<?, ?>) row).get("username") instanceof String) {
                            out.add((String) ((Map<?, ?>) row).get("username"));
                        }
                    }
                }
            } else if (root instanceof List) {
                for (Object row : (List<?>) root) {
                    if (row instanceof Map) {
                        Object name = ((Map<?, ?>) row).get("name");
                        if (name == null) {
                            name = ((Map<?, ?>) row).get("username");
                        }
                        if (name instanceof String) {
                            out.add((String) name);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }
}
