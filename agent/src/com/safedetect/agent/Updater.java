package com.safedetect.agent;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Daily check for a newer GitHub release. Every failure is silent; it is only a hint. */
final class Updater {
    static final long INTERVAL_MS = 24L * 60 * 60 * 1000;
    static final String RELEASES = "https://github.com/AidanFitzpatrick-priv/safedetect/releases/latest";
    private static final String API = "https://api.github.com/repos/AidanFitzpatrick-priv/safedetect/releases/latest";

    private volatile String found;
    private boolean started;

    /** Version from the jar manifest, or null when running from loose classes. */
    static String currentVersion() {
        Package pkg = Agent.class.getPackage();
        return pkg == null ? null : pkg.getImplementationVersion();
    }

    static boolean due(long lastCheck, long now) {
        return now - lastCheck >= INTERVAL_MS || now < lastCheck;
    }

    /** True when {@code latest} is a higher dotted version than {@code current}; a leading "v" is ignored. */
    static boolean newer(String latest, String current) {
        int[] a = parts(latest);
        int[] b = parts(current);
        if (a == null || b == null) {
            return false;
        }
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }

    private static int[] parts(String version) {
        if (version == null) {
            return null;
        }
        String text = version.trim();
        if (text.startsWith("v") || text.startsWith("V")) {
            text = text.substring(1);
        }
        int cut = 0;
        while (cut < text.length() && (Character.isDigit(text.charAt(cut)) || text.charAt(cut) == '.')) {
            cut++;
        }
        text = text.substring(0, cut);
        if (text.isEmpty()) {
            return null;
        }
        String[] pieces = text.split("\\.");
        int[] out = new int[pieces.length];
        try {
            for (int i = 0; i < pieces.length; i++) {
                out[i] = pieces[i].isEmpty() ? 0 : Integer.parseInt(pieces[i]);
            }
        } catch (NumberFormatException tooBig) {
            return null;
        }
        return out;
    }

    /** Starts one background check; later calls do nothing. */
    void start(final String current) {
        if (started || current == null || Boolean.getBoolean("safedetect.noUpdate")) {
            return;
        }
        started = true;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                String latest = fetchLatest();
                if (latest != null && newer(latest, current)) {
                    found = latest;
                }
            }
        }, "SafeDetect-Updater");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    /** Newer tag once, then null. */
    String take() {
        String tag = found;
        if (tag != null) {
            found = null;
        }
        return tag;
    }

    @SuppressWarnings("unchecked")
    private static String fetchLatest() {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(API).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "SafeDetect");
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            if (conn.getResponseCode() != 200) {
                return null;
            }
            InputStream stream = conn.getInputStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[2048];
            int n;
            while ((n = stream.read(chunk)) >= 0 && buf.size() < 1 << 20) {
                buf.write(chunk, 0, n);
            }
            stream.close();
            Object root = Json.parse(new String(buf.toByteArray(), StandardCharsets.UTF_8));
            Object tag = root instanceof Map ? ((Map<String, Object>) root).get("tag_name") : null;
            return tag instanceof String ? (String) tag : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
