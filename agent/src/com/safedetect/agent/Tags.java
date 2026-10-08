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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Urchin (Coral) and optional Seraph community tags. Off the client thread.
 */
final class Tags {
    static final class Result {
        final UUID id;
        final String name;
        final String labels;
        final int score;

        Result(UUID id, String name, String labels, int score) {
            this.id = id;
            this.name = name;
            this.labels = labels;
            this.score = score;
        }
    }

    private static final class Job {
        final String url;
        final String headerKey;
        final UUID id;
        final String name;

        Job(String url, String headerKey, UUID id, String name) {
            this.url = url;
            this.headerKey = headerKey;
            this.id = id;
            this.name = name;
        }
    }

    private final LinkedBlockingQueue<Job> jobs = new LinkedBlockingQueue<Job>(64);
    private final ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<Result>();
    private volatile boolean started;

    void urchin(String key, UUID id, String name) {
        if (key == null || key.isEmpty() || id == null) {
            return;
        }
        try {
            String url = "https://api.urchin.gg/v3/cubelify?uuid=" + id.toString().replace("-", "")
                    + "&key=" + URLEncoder.encode(key, "UTF-8");
            submit(url, key, id, name);
        } catch (Exception ignored) {
        }
    }

    void seraph(String key, UUID id, String name) {
        if (key == null || key.isEmpty() || id == null) {
            return;
        }
        try {
            String url = "https://api.seraph.si/v1/player?uuid=" + id.toString()
                    + "&key=" + URLEncoder.encode(key, "UTF-8");
            submit(url, key, id, name);
        } catch (Exception ignored) {
        }
    }

    Result poll() {
        return results.poll();
    }

    private void submit(String url, String key, UUID id, String name) {
        start();
        jobs.offer(new Job(url, key, id, name));
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
            return new Result(job.id, job.name, labels == null ? "" : labels, score);
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
}
