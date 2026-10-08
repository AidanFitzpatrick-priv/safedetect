package com.safedetect.agent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skin hash denick (learn real skins, match nicked players) plus optional Aurora/Bordic lookup.
 */
final class Denick {
    static final class Result {
        final UUID id;
        final String shown;
        final String real;
        final String how;

        Result(UUID id, String shown, String real, String how) {
            this.id = id;
            this.shown = shown;
            this.real = real;
            this.how = how;
        }
    }

    private static final class Job {
        final String key;
        final UUID id;
        final String shown;

        Job(String key, UUID id, String shown) {
            this.key = key;
            this.id = id;
            this.shown = shown;
        }
    }

    private static final Pattern TEXTURE = Pattern.compile("textures\\.minecraft\\.net/texture/([a-fA-F0-9]+)");
    private final File file;
    private final Map<String, String> skins = new LinkedHashMap<String, String>();
    private final LinkedBlockingQueue<Job> jobs = new LinkedBlockingQueue<Job>(64);
    private final ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<Result>();
    private volatile boolean started;

    Denick(File gameDir) {
        file = new File(gameDir, "config/safedetect-skins.json");
        load();
    }

    String realForSkin(String hash) {
        if (hash == null || hash.isEmpty()) {
            return null;
        }
        return skins.get(hash.toLowerCase(Locale.ROOT));
    }

    void learn(String hash, String name) {
        if (hash == null || name == null || name.isEmpty()) {
            return;
        }
        String key = hash.toLowerCase(Locale.ROOT);
        String value = name.toLowerCase(Locale.ROOT);
        String previous = skins.get(key);
        if (value.equals(previous)) {
            return;
        }
        skins.put(key, value);
        save();
    }

    /** False when the queue is full, so the caller can retry later. */
    boolean submitAurora(String key, UUID id, String shown) {
        if (key == null || key.isEmpty() || shown == null || shown.isEmpty()) {
            return true;
        }
        start();
        return jobs.offer(new Job(key, id, shown));
    }

    Result poll() {
        return results.poll();
    }

    static String hashFromTextures(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            String json = value.trim().startsWith("{") ? value
                    : new String(Base64.getDecoder().decode(value.replace("\n", "")), StandardCharsets.UTF_8);
            Matcher match = TEXTURE.matcher(json);
            if (match.find()) {
                return match.group(1).toLowerCase(Locale.ROOT);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Game.useGameLoader();
                loop();
            }
        }, "SafeDetect-Denick");
        Game.useGameLoader(thread);
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private void loop() {
        while (true) {
            try {
                Job job = jobs.poll(1L, TimeUnit.SECONDS);
                if (job == null) {
                    continue;
                }
                String real = fetchAurora(job.key, job.shown);
                if (real != null && !real.equalsIgnoreCase(job.shown)) {
                    results.add(new Result(job.id, job.shown, real, "aurora"));
                }
                Thread.sleep(400L);
            } catch (InterruptedException ignored) {
                return;
            } catch (Throwable thrown) {
                Log.once("denick", thrown);
            }
        }
    }

    private String fetchAurora(String key, String shown) {
        String[] urls = {
                "https://api.bordic.xyz/v3/player/profile?key=" + enc(key) + "&username=" + enc(shown),
                "https://api.bordic.xyz/v3/player/profile?key=" + enc(key) + "&nick=" + enc(shown),
                "https://bordic.xyz/api/v2/resources/denick?key=" + enc(key) + "&username=" + enc(shown),
                "https://api.bordic.xyz/v2/convert/mojang?key=" + enc(key) + "&nick=" + enc(shown)
        };
        for (int i = 0; i < urls.length; i++) {
            String ign = fetchOne(urls[i]);
            if (ign != null) {
                return ign;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String fetchOne(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "SafeDetect");
            int code = conn.getResponseCode();
            InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (stream == null) {
                return null;
            }
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[2048];
            int n;
            while ((n = stream.read(chunk)) >= 0) {
                buf.write(chunk, 0, n);
            }
            stream.close();
            Object root = Json.parse(new String(buf.toByteArray(), StandardCharsets.UTF_8));
            return findIgn(root, 0);
        } catch (Throwable thrown) {
            Log.once("aurora fetch", thrown);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static String findIgn(Object node, int depth) {
        if (node == null || depth > 5) {
            return null;
        }
        if (node instanceof String) {
            return null;
        }
        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            for (String key : new String[] { "ign", "username", "player", "name", "denick", "real", "nickname",
                    "unnick" }) {
                Object value = map.get(key);
                if (value instanceof String && looksLikeName((String) value)) {
                    if ("nickname".equals(key) || "nick".equals(key)) {
                        continue;
                    }
                    return (String) value;
                }
            }
            Object player = map.get("player");
            if (player instanceof Map) {
                String nested = findIgn(player, depth + 1);
                if (nested != null) {
                    return nested;
                }
            }
            Object data = map.get("data");
            if (data != null) {
                String nested = findIgn(data, depth + 1);
                if (nested != null) {
                    return nested;
                }
            }
        }
        if (node instanceof List) {
            for (Object entry : (List<Object>) node) {
                String nested = findIgn(entry, depth + 1);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static boolean looksLikeName(String value) {
        if (value == null) {
            return false;
        }
        String t = value.trim();
        if (t.length() < 3 || t.length() > 16) {
            return false;
        }
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c != '_' && (c < '0' || c > '9') && (c < 'A' || c > 'Z') && (c < 'a' || c > 'z')) {
                return false;
            }
        }
        return true;
    }

    private static String enc(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception ignored) {
            return value;
        }
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (!file.isFile()) {
            return;
        }
        try {
            Object root = Json.parse(new String(FilesBytes(file), StandardCharsets.UTF_8));
            if (!(root instanceof Map)) {
                return;
            }
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) root).entrySet()) {
                if (entry.getValue() instanceof String) {
                    skins.put(entry.getKey().toLowerCase(Locale.ROOT),
                            ((String) entry.getValue()).toLowerCase(Locale.ROOT));
                }
            }
        } catch (Throwable thrown) {
            Log.once("skins read", thrown);
        }
    }

    private static byte[] FilesBytes(File file) throws Exception {
        return java.nio.file.Files.readAllBytes(file.toPath());
    }

    private void save() {
        StringBuilder out = new StringBuilder("{\n");
        int n = 0;
        for (Map.Entry<String, String> entry : skins.entrySet()) {
            out.append(n++ == 0 ? "  " : ",\n  ").append(Json.quote(entry.getKey())).append(": ")
                    .append(Json.quote(entry.getValue()));
        }
        out.append(n == 0 ? "}\n" : "\n}\n");
        Files2.writeLater(file, out.toString());
    }
}
