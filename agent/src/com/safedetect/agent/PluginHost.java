package com.safedetect.agent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Loads builtin plugins plus drop-in jars, and fans chat/world/tab out to the ones that are on.
 */
final class PluginHost {
    static final String[] DEFAULT_ON = { "play" };

    private final List<Loaded> all = new ArrayList<Loaded>();
    private final Set<String> enabled = new LinkedHashSet<String>();
    private final Map<UUID, String> suffixes = new LinkedHashMap<UUID, String>();
    private final Map<String, String> store = new LinkedHashMap<String, String>();
    private Game game;
    private Settings settings;
    private File file;
    private File jarDir;
    private Object mc;
    private Object self;
    private ChatSink chat;
    private boolean opened;

    interface ChatSink {
        void line(String text);

        void ping();
    }

    private static final class Loaded {
        final Plugin plugin;
        final PluginInfo info;
        boolean on;
        boolean started;

        Loaded(Plugin plugin) {
            this.plugin = plugin;
            this.info = plugin.info();
        }
    }

    void open(File root, Game game, Settings settings, ChatSink chat) {
        this.game = game;
        this.settings = settings;
        this.chat = chat;
        this.file = new File(root, "config/safedetect-plugins.json");
        this.jarDir = new File(root, "config/safedetect-plugins");
        if (!jarDir.isDirectory()) {
            jarDir.mkdirs();
        }
        all.clear();
        for (Plugin plugin : PluginPack.all()) {
            all.add(new Loaded(plugin));
        }
        loadJars();
        loadState();
        if (!file.isFile()) {
            for (String id : DEFAULT_ON) {
                enabled.add(id);
            }
        }
        opened = true;
        applyEnabled();
        saveState();
    }

    void bind(Object mc, Object self) {
        this.mc = mc;
        this.self = self;
    }

    void world() {
        suffixes.clear();
        for (Loaded loaded : all) {
            if (loaded.on && loaded.plugin instanceof PluginEvents) {
                try {
                    ((PluginEvents) loaded.plugin).world();
                } catch (Throwable thrown) {
                    Log.once("plugin world " + loaded.info.id, thrown);
                }
            }
        }
    }

    void chatLine(String plain) {
        if (plain == null || plain.isEmpty()) {
            return;
        }
        for (Loaded loaded : all) {
            if (loaded.on && loaded.plugin instanceof PluginEvents) {
                try {
                    ((PluginEvents) loaded.plugin).chat(plain);
                } catch (Throwable thrown) {
                    Log.once("plugin chat " + loaded.info.id, thrown);
                }
            }
        }
    }

    void tab(Object[] tabs) {
        if (game == null || tabs == null) {
            return;
        }
        List<PluginPlayer> entries = new ArrayList<PluginPlayer>();
        try {
            for (int i = 0; i < tabs.length; i++) {
                if (tabs[i] == null) {
                    continue;
                }
                UUID id = game.tabUuid(tabs[i]);
                String name = game.tabName(tabs[i]);
                if (id != null && name != null) {
                    entries.add(new PluginPlayer(id, name));
                }
            }
        } catch (Throwable thrown) {
            Log.once("plugin tab", thrown);
            return;
        }
        for (Loaded loaded : all) {
            if (loaded.on && loaded.plugin instanceof PluginEvents) {
                try {
                    ((PluginEvents) loaded.plugin).tab(entries);
                } catch (Throwable thrown) {
                    Log.once("plugin tab " + loaded.info.id, thrown);
                }
            }
        }
    }

    String suffix(UUID id) {
        if (id == null) {
            return "";
        }
        String text = suffixes.get(id);
        return text == null ? "" : text;
    }

    boolean command(String[] parts) {
        if (parts.length < 2) {
            return false;
        }
        String sub = parts[1].toLowerCase(Locale.ROOT);
        if ("plugins".equals(sub) || "plugin".equals(sub) || "addons".equals(sub)) {
            pluginsCommand(parts);
            return true;
        }
        for (Loaded loaded : all) {
            if (loaded.info.id.equals(sub)) {
                if (!loaded.on) {
                    say("\u00a77" + loaded.info.name + " is off. \u00a78/sd plugins on " + loaded.info.id);
                    return true;
                }
                try {
                    if (!loaded.plugin.command(parts)) {
                        say("\u00a77" + loaded.info.commands);
                    }
                } catch (Throwable thrown) {
                    Log.once("plugin cmd " + loaded.info.id, thrown);
                    say("\u00a7c" + loaded.info.name + " command failed.");
                }
                return true;
            }
        }
        return false;
    }

    void setEnabled(String id, boolean on) {
        Loaded loaded = find(id);
        if (loaded == null) {
            return;
        }
        if (on) {
            enabled.add(loaded.info.id);
        } else {
            enabled.remove(loaded.info.id);
        }
        applyEnabled();
        saveState();
    }

    boolean enabled(String id) {
        return enabled.contains(id);
    }

    String catalogJson() {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < all.size(); i++) {
            Loaded loaded = all.get(i);
            PluginInfo info = loaded.info;
            if (i > 0) {
                out.append(',');
            }
            out.append("{\"id\":").append(Json.quote(info.id));
            out.append(",\"name\":").append(Json.quote(info.name));
            out.append(",\"author\":").append(Json.quote(info.author));
            out.append(",\"version\":").append(Json.quote(info.version));
            out.append(",\"blurb\":").append(Json.quote(info.blurb));
            out.append(",\"needs\":").append(Json.quote(info.needs));
            out.append(",\"commands\":").append(Json.quote(info.commands));
            out.append(",\"on\":").append(loaded.on).append('}');
        }
        return out.append(']').toString();
    }

    private void pluginsCommand(String[] parts) {
        if (parts.length >= 4 && ("on".equalsIgnoreCase(parts[2]) || "off".equalsIgnoreCase(parts[2]))) {
            Loaded loaded = find(parts[3]);
            if (loaded == null) {
                say("\u00a77Unknown plugin. \u00a78Settings \u2192 Plugins");
                return;
            }
            boolean on = "on".equalsIgnoreCase(parts[2]);
            setEnabled(loaded.info.id, on);
            say("\u00a77" + loaded.info.name + (on ? " \u00a7aon" : " \u00a7coff") + "\u00a77.");
            return;
        }
        say("\u00a77Plugins \u00a78(Settings \u2192 Plugins, or /sd plugins on|off <id>)");
        for (Loaded loaded : all) {
            say("\u00a78  " + (loaded.on ? "\u00a7aon " : "\u00a78off ") + "\u00a7f" + loaded.info.id + " \u00a77"
                    + loaded.info.name + " \u00a78" + loaded.info.commands);
        }
    }

    private void applyEnabled() {
        Api api = new Api();
        for (Loaded loaded : all) {
            boolean want = enabled.contains(loaded.info.id);
            if (want && !loaded.started) {
                try {
                    loaded.plugin.start(api);
                    loaded.started = true;
                    loaded.on = true;
                    Log.info("Plugin on: " + loaded.info.id);
                } catch (Throwable thrown) {
                    Log.once("plugin start " + loaded.info.id, thrown);
                    loaded.on = false;
                }
            } else if (!want && loaded.started) {
                try {
                    loaded.plugin.stop();
                } catch (Throwable thrown) {
                    Log.once("plugin stop " + loaded.info.id, thrown);
                }
                loaded.started = false;
                loaded.on = false;
            } else {
                loaded.on = want && loaded.started;
            }
        }
    }

    private Loaded find(String id) {
        if (id == null) {
            return null;
        }
        String key = id.toLowerCase(Locale.ROOT);
        for (Loaded loaded : all) {
            if (loaded.info.id.equals(key)) {
                return loaded;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void loadState() {
        enabled.clear();
        store.clear();
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            Object root = Json.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            if (!(root instanceof Map)) {
                return;
            }
            Map<String, Object> map = (Map<String, Object>) root;
            if (map.get("enabled") instanceof List) {
                for (Object id : (List<Object>) map.get("enabled")) {
                    if (id instanceof String) {
                        enabled.add(((String) id).toLowerCase(Locale.ROOT));
                    }
                }
            }
            if (map.get("config") instanceof Map) {
                for (Map.Entry<String, Object> entry : ((Map<String, Object>) map.get("config")).entrySet()) {
                    if (entry.getValue() instanceof String || entry.getValue() instanceof Number
                            || entry.getValue() instanceof Boolean) {
                        store.put(entry.getKey(), String.valueOf(entry.getValue()));
                    }
                }
            }
        } catch (Throwable thrown) {
            Log.once("plugins read", thrown);
        }
    }

    private void saveState() {
        if (file == null || !opened) {
            return;
        }
        StringBuilder out = new StringBuilder("{\n  \"enabled\": [");
        int i = 0;
        for (String id : enabled) {
            out.append(i++ == 0 ? "" : ", ").append(Json.quote(id));
        }
        out.append("],\n  \"config\": {");
        i = 0;
        for (Map.Entry<String, String> entry : store.entrySet()) {
            out.append(i++ == 0 ? "" : ", ").append(Json.quote(entry.getKey())).append(':')
                    .append(Json.quote(entry.getValue()));
        }
        out.append("}\n}\n");
        Files2.writeAtomic(file, out.toString());
    }

    private void loadJars() {
        File[] jars = jarDir == null ? null : jarDir.listFiles();
        if (jars == null) {
            return;
        }
        for (File jar : jars) {
            if (jar == null || !jar.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                continue;
            }
            try {
                JarFile packed = new JarFile(jar);
                Manifest manifest = packed.getManifest();
                packed.close();
                String className = manifest == null || manifest.getMainAttributes() == null ? null
                        : manifest.getMainAttributes().getValue("Plugin-Class");
                if (className == null || className.trim().isEmpty()) {
                    Log.info("Plugin jar missing Plugin-Class: " + jar.getName());
                    continue;
                }
                URLClassLoader loader = new URLClassLoader(new URL[] { jar.toURI().toURL() },
                        Plugin.class.getClassLoader());
                Class<?> type = Class.forName(className.trim(), true, loader);
                Object instance = type.getDeclaredConstructor().newInstance();
                if (!(instance instanceof Plugin)) {
                    Log.info("Plugin-Class is not a Plugin: " + className);
                    continue;
                }
                all.add(new Loaded((Plugin) instance));
                Log.info("Loaded plugin jar " + jar.getName());
            } catch (Throwable thrown) {
                Log.once("plugin jar " + jar.getName(), thrown);
            }
        }
    }

    private void say(String text) {
        if (chat != null) {
            chat.line(text);
        }
    }

    private final class Api implements PluginApi {
        @Override
        public void chat(String text) {
            say(text);
        }

        @Override
        public void sound() {
            try {
                if (game != null && self != null) {
                    game.playSound(self, "note.pling", 1.0f, 2.0f);
                }
            } catch (Throwable thrown) {
                Log.once("plugin sound", thrown);
            }
        }

        @Override
        public void send(String message) {
            try {
                if (game != null && self != null && message != null) {
                    game.sendChat(self, message);
                }
            } catch (Throwable thrown) {
                Log.once("plugin send", thrown);
            }
        }

        @Override
        public String selfName() {
            try {
                return game == null || self == null ? "" : game.name(self);
            } catch (Throwable thrown) {
                return "";
            }
        }

        @Override
        public UUID selfUuid() {
            try {
                return game == null || self == null ? null : game.uuid(self);
            } catch (Throwable thrown) {
                return null;
            }
        }

        @Override
        public boolean skipped(String name) {
            return settings != null && settings.isFriend(name);
        }

        @Override
        public String hypixelKey() {
            return settings == null || settings.hypixelKey == null ? "" : settings.hypixelKey;
        }

        @Override
        public String urchinKey() {
            return settings == null || settings.urchinKey == null ? "" : settings.urchinKey;
        }

        @Override
        public String teamPrefix(String name) {
            if (game == null || mc == null || name == null) {
                return "";
            }
            try {
                Object[] tabs = game.tabEntries(mc);
                for (int i = 0; i < tabs.length; i++) {
                    if (name.equalsIgnoreCase(game.tabName(tabs[i]))) {
                        String decorated = game.teamDecorated(tabs[i], name);
                        int at = decorated.indexOf(name);
                        return at > 0 ? decorated.substring(0, at) : "";
                    }
                }
            } catch (Throwable thrown) {
                Log.once("plugin team", thrown);
            }
            return "";
        }

        @Override
        public void tabSuffix(UUID id, String suffix) {
            if (id != null) {
                if (suffix == null || suffix.isEmpty()) {
                    suffixes.remove(id);
                } else {
                    suffixes.put(id, suffix);
                }
            }
        }

        @Override
        public void clearTabSuffix(UUID id) {
            if (id != null) {
                suffixes.remove(id);
            }
        }

        @Override
        public String config(String key) {
            String value = store.get(key);
            return value == null ? "" : value;
        }

        @Override
        public void config(String key, String value) {
            if (key == null) {
                return;
            }
            if (value == null || value.isEmpty()) {
                store.remove(key);
            } else {
                store.put(key, value);
            }
            saveState();
        }

        @Override
        public boolean configOn(String key, boolean fallback) {
            String value = store.get(key);
            if (value == null) {
                return fallback;
            }
            return "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value) || "1".equals(value);
        }

        @Override
        public int configInt(String key, int fallback) {
            try {
                String value = store.get(key);
                return value == null ? fallback : Integer.parseInt(value.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        @Override
        public String httpGet(String url, String headerName, String headerValue) {
            return http("GET", url, null, headerName, headerValue);
        }

        @Override
        public String httpPost(String url, String jsonBody, String headerName, String headerValue) {
            return http("POST", url, jsonBody, headerName, headerValue);
        }

        private String http(String method, String url, String body, String headerName, String headerValue) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod(method);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", "SafeDetect-Plugin");
                conn.setRequestProperty("Accept", "application/json");
                if (headerName != null && headerValue != null && !headerName.isEmpty()) {
                    conn.setRequestProperty(headerName, headerValue);
                }
                if (body != null) {
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
                    OutputStream out = conn.getOutputStream();
                    out.write(bytes);
                    out.close();
                }
                int code = conn.getResponseCode();
                InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                if (stream == null) {
                    return code == 404 ? "" : null;
                }
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[2048];
                int n;
                while ((n = stream.read(chunk)) >= 0 && buf.size() < 1 << 20) {
                    buf.write(chunk, 0, n);
                }
                stream.close();
                if (code == 401 || code == 403) {
                    return "INVALID_KEY";
                }
                if (code == 429) {
                    return "RATE";
                }
                if (code != 200 && code != 204) {
                    return null;
                }
                return new String(buf.toByteArray(), StandardCharsets.UTF_8);
            } catch (Throwable ignored) {
                return null;
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
    }
}
