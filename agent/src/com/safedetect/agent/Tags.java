package com.safedetect.agent;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Urchin (Coral) community tags. Off the client thread.
 */
final class Tags {
    static final class Result {
        final UUID id;
        final String name;
        /** "U" Urchin. */
        final String source;
        final String labels;
        final int score;

        Result(UUID id, String name, String source, String labels, int score) {
            this.id = id;
            this.name = name;
            this.source = source;
            this.labels = labels;
            this.score = score;
        }
    }

    private static final class Job {
        final String url;
        final String headerKey;
        final String source;
        final UUID id;
        final String name;

        Job(String url, String headerKey, String source, UUID id, String name) {
            this.url = url;
            this.headerKey = headerKey;
            this.source = source;
            this.id = id;
            this.name = name;
        }
    }

    private final LinkedBlockingQueue<Job> jobs = new LinkedBlockingQueue<Job>(64);
    private final ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<Result>();
    private volatile boolean started;

    /** False when the queue is full, so the caller can retry later. */
    boolean urchin(String key, UUID id, String name) {
        if (key == null || key.isEmpty() || id == null) {
            return true;
        }
        try {
            String url = "https://api.urchin.gg/v3/cubelify?uuid=" + id.toString().replace("-", "")
                    + "&key=" + URLEncoder.encode(key, "UTF-8");
            return submit(url, key, "U", id, name);
        } catch (Exception ignored) {
            return true;
        }
    }

    Result poll() {
        return results.poll();
    }

    private boolean submit(String url, String key, String source, UUID id, String name) {
        start();
        return jobs.offer(new Job(url, key, source, id, name));
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
        }, "SafeDetect-Tags");
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
                Result result = fetch(job);
                if (result != null) {
                    results.add(result);
                }
                Thread.sleep(300L);
            } catch (InterruptedException ignored) {
                return;
            } catch (Throwable thrown) {
                Log.once("tags", thrown);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Result fetch(Job job) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(job.url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "SafeDetect");
            if (job.headerKey != null && !job.headerKey.isEmpty()) {
                conn.setRequestProperty("X-API-Key", job.headerKey);
            }
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
            if (!(root instanceof Map)) {
                return null;
            }
            Map<String, Object> map = (Map<String, Object>) root;
            if (map.get("error") instanceof String) {
                Log.once("tags api", new IllegalStateException((String) map.get("error")));
                return null;
            }
            AntiSniper.Result parsed = AntiSniper.parse(job.id, job.name, map);
            String labels = labels(map);
            int score = parsed == null ? -1 : parsed.score;
            if ((labels == null || labels.isEmpty()) && score < 0) {
                return null;
            }
            return new Result(job.id, job.name, job.source, labels == null ? "" : labels, score);
        } catch (Throwable thrown) {
            Log.once("tags fetch", thrown);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static String labels(Map<String, Object> map) {
        StringBuilder out = new StringBuilder();
        Object tags = map.get("tags");
        if (tags instanceof List) {
            for (Object entry : (List<Object>) tags) {
                if (!(entry instanceof Map)) {
                    continue;
                }
                Object text = ((Map<?, ?>) entry).get("text");
                Object tooltip = ((Map<?, ?>) entry).get("tooltip");
                String label = text instanceof String && !((String) text).isEmpty() ? (String) text
                        : tooltip instanceof String ? (String) tooltip : null;
                if (label == null || label.isEmpty()) {
                    continue;
                }
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append('[').append(shorten(label)).append(']');
            }
        }
        if (map.get("tag") instanceof String) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append('[').append(shorten((String) map.get("tag"))).append(']');
        }
        return out.toString();
    }

    private static String shorten(String label) {
        String t = label.trim();
        if (t.length() <= 10) {
            return t;
        }
        return t.substring(0, 10);
    }

    static String sourceName(String source) {
        if ("U".equals(source)) {
            return "Urchin";
        }
        return "SD";
    }

    /** Turns `[Cheater]` into `[U:Cheater]` so overlay chips show the source. */
    static String brand(String prefix, String labels) {
        if (labels == null || labels.trim().isEmpty()) {
            return "";
        }
        Matcher matcher = Pattern.compile("\\[([^\\]]+)\\]").matcher(labels);
        StringBuffer out = new StringBuffer();
        boolean any = false;
        while (matcher.find()) {
            any = true;
            String inner = matcher.group(1).trim();
            if (!inner.startsWith("SD:") && !inner.startsWith("U:")) {
                inner = prefix + ":" + inner;
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement("[" + inner + "]"));
        }
        matcher.appendTail(out);
        if (any) {
            return out.toString().trim();
        }
        return "[" + prefix + ":" + labels.trim() + "]";
    }

    static String merge(String left, String right) {
        if (right == null || right.isEmpty()) {
            return left == null ? "" : left;
        }
        if (left == null || left.isEmpty()) {
            return right;
        }
        if (left.contains(right)) {
            return left;
        }
        return left + " " + right;
    }
}
