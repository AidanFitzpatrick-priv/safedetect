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
 * Built-in estimate plus optional external anti-sniper (Cubelify-compatible URL or Antisniper key).
 */
final class AntiSniper {
    static final String DEFAULT_URL = "https://api.antisniper.net/v2/player?key={{key}}&player={{name}}";

    static final class Result {
        final UUID id;
        final String name;
        final int score;
        final String tag;

        Result(UUID id, String name, int score, String tag) {
            this.id = id;
            this.name = name;
            this.score = score;
            this.tag = tag;
        }
    }

    private static final class Job {
        final String url;
        final UUID id;
        final String name;

        Job(String url, UUID id, String name) {
            this.url = url;
            this.id = id;
            this.name = name;
        }
    }

    private final LinkedBlockingQueue<Job> jobs = new LinkedBlockingQueue<Job>(64);
    private final ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<Result>();
    private volatile boolean started;

    static int localScore(double fkdr, double wlr) {
        if (fkdr < 0) {
            return -1;
        }
        double ratio = wlr < 0 ? 1.0 : wlr;
        if (fkdr < 1.2 && ratio < 1.2) {
            return 0;
        }
        return (int) Math.max(0, Math.min(99, Math.round(fkdr * 3.2 + ratio * 4.5)));
    }

    /** Short Hypixel sessions are a common sniper tell. */
    static int withSession(int score, long lastLogin) {
        if (lastLogin <= 0L) {
            return score;
        }
        long mins = (System.currentTimeMillis() - lastLogin) / 60000L;
        if (mins < 0L) {
            return score;
        }
        int bump = 0;
        if (mins < 5L) {
            bump = 28;
        } else if (mins < 12L) {
            bump = 18;
        } else if (mins < 25L) {
            bump = 10;
        } else if (mins < 45L) {
            bump = 4;
        }
        int base = score < 0 ? 0 : score;
        return Math.max(0, Math.min(99, base + bump));
    }

    static String expand(String template, String key, UUID id, String name) {
        String url = template == null || template.isEmpty() ? DEFAULT_URL : template;
        String dashed = id == null ? "" : id.toString();
        String player = name == null ? "" : name;
        String token = key == null ? "" : key;
        boolean placeholders = url.contains("{{");
        String encodedName = encode(player);
        url = url.replace("{{id}}", dashed)
                .replace("{{uuid}}", dashed.replace("-", ""))
                .replace("{{name}}", encodedName)
                .replace("{{key}}", encode(token))
                .replace("{{sources}}", "GAME");
        if (!placeholders) {
            String join = url.contains("?") ? "&" : "?";
            url = url + join + "id=" + dashed + "&name=" + encodedName;
        }
        return url;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception ignored) {
            return value;
        }
    }

    /** False when the queue is full, so the caller can retry later. */
    boolean submit(String url, UUID id, String name) {
        if (url == null || url.isEmpty() || id == null || name == null) {
            return true;
        }
        start();
        return jobs.offer(new Job(url, id, name));
    }

    Result poll() {
        return results.poll();
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
        }, "SafeDetect-Sniper");
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
                Thread.sleep(350L);
            } catch (InterruptedException ignored) {
                return;
            } catch (Throwable thrown) {
                Log.once("antisniper", thrown);
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
            return parse(job.id, job.name, root);
        } catch (Throwable thrown) {
            Log.once("antisniper fetch", thrown);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    static Result parse(UUID id, String name, Object root) {
        if (!(root instanceof Map)) {
            return null;
        }
        Map<String, Object> map = (Map<String, Object>) root;
        int score = readScore(map);
        String tag = readTag(map);
        if (score < 0 && (tag == null || tag.isEmpty())) {
            return null;
        }
        return new Result(id, name, Math.max(0, score), tag == null ? "" : tag);
    }

    @SuppressWarnings("unchecked")
    private static int readScore(Map<String, Object> map) {
        Object score = map.get("score");
        if (score instanceof Number) {
            double n = ((Number) score).doubleValue();
            if (n >= 0.0 && n <= 1.0) {
                return (int) Math.round(n * 100.0);
            }
            return (int) Math.round(n);
        }
        if (score instanceof Map) {
            Map<?, ?> body = (Map<?, ?>) score;
            Object value = body.get("value");
            if (value instanceof Number) {
                double n = ((Number) value).doubleValue();
                if ("set".equals(String.valueOf(body.get("mode"))) && n <= 1.0) {
                    return (int) Math.round(n * 100.0);
                }
                if (n <= 1.0 && n >= 0.0) {
                    return (int) Math.round(n * 100.0);
                }
                return (int) Math.round(n);
            }
        }
        int direct = number(map, "score", "sniper", "sniper_score", "chance");
        if (direct >= 0) {
            return direct > 1 && direct <= 100 ? direct : (int) Math.round(direct * (direct <= 1 ? 100.0 : 1.0));
        }
        Object nested = map.get("data");
        if (nested instanceof Map) {
            return readScore((Map<String, Object>) nested);
        }
        nested = map.get("player");
        if (nested instanceof Map) {
            return readScore((Map<String, Object>) nested);
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private static String readTag(Map<String, Object> map) {
        Object tags = map.get("tags");
        if (tags instanceof List && !((List<?>) tags).isEmpty()) {
            Object first = ((List<?>) tags).get(0);
            if (first instanceof Map && ((Map<?, ?>) first).get("text") instanceof String) {
                return (String) ((Map<?, ?>) first).get("text");
            }
        }
        if (map.get("tag") instanceof String) {
            return (String) map.get("tag");
        }
        Object nested = map.get("data");
        if (nested instanceof Map) {
            return readTag((Map<String, Object>) nested);
        }
        return "";
    }

    private static int number(Map<String, Object> map, String... keys) {
        for (int i = 0; i < keys.length; i++) {
            Object value = map.get(keys[i]);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            if (value instanceof Boolean && Boolean.TRUE.equals(value)) {
                return 100;
            }
        }
        return -1;
    }
}
