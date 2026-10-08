package com.safedetect.agent;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Optional Hypixel API lookups for nicked names and Bedwars FKDR. Off the client thread.
 */
final class Hypixel {
    static final class Result {
        final UUID id;
        final String name;
        final boolean nicked;
        final double fkdr;
        final double wlr;
        final int stars;
        final int finals;
        final int wins;
        final int winstreak;
        final long lastLogin;

        Result(UUID id, String name, boolean nicked, double fkdr, double wlr, int stars, int finals, int wins,
                int winstreak, long lastLogin) {
            this.id = id;
            this.name = name;
            this.nicked = nicked;
            this.fkdr = fkdr;
            this.wlr = wlr;
            this.stars = stars;
            this.finals = finals;
            this.wins = wins;
            this.winstreak = winstreak;
            this.lastLogin = lastLogin;
        }
    }

    private static final class Job {
        final String key;
        final UUID id;
        final String name;

        Job(String key, UUID id, String name) {
            this.key = key;
            this.id = id;
            this.name = name;
        }
    }

    private final LinkedBlockingQueue<Job> jobs = new LinkedBlockingQueue<Job>(64);
    private final ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<Result>();
    private volatile boolean started;

    void submit(String key, UUID id, String name) {
        if (key == null || key.isEmpty() || name == null || name.isEmpty()) {
            return;
        }
        start();
        jobs.offer(new Job(key, id, name));
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
        }, "SafeDetect-Hypixel");
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
                Thread.sleep(400L);
            } catch (InterruptedException ignored) {
                return;
            } catch (Throwable thrown) {
                Log.once("hypixel", thrown);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Result fetch(Job job) {
        try {
            UUID uuid = job.id != null ? job.id : mojangUuid(job.name);
            if (uuid == null) {
                return new Result(null, job.name, true, 0.0, 0.0, 0, 0, 0, -1, 0L);
            }
            String url = "https://api.hypixel.net/v2/player?uuid=" + uuid.toString().replace("-", "");
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "SafeDetect");
            conn.setRequestProperty("API-Key", job.key);
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
            if (!Boolean.TRUE.equals(map.get("success"))) {
                Log.once("hypixel api", new IllegalStateException(String.valueOf(map.get("cause"))));
                return null;
            }
            Object player = map.get("player");
            if (player == null) {
                return new Result(uuid, job.name, true, 0.0, 0.0, 0, 0, 0, -1, 0L);
            }
            if (!(player instanceof Map)) {
                return null;
            }
            Map<String, Object> body = (Map<String, Object>) player;
            int stars = 0;
            Object achievements = body.get("achievements");
            if (achievements instanceof Map && ((Map<?, ?>) achievements).get("bedwars_level") instanceof Number) {
                stars = ((Number) ((Map<?, ?>) achievements).get("bedwars_level")).intValue();
            }
            int kills = 0;
            int deaths = 0;
            int wins = 0;
            int losses = 0;
            int winstreak = -1;
            Object stats = body.get("stats");
            if (stats instanceof Map) {
                Object bedwars = ((Map<?, ?>) stats).get("Bedwars");
                if (bedwars instanceof Map) {
                    Map<?, ?> bw = (Map<?, ?>) bedwars;
                    if (bw.get("final_kills_bedwars") instanceof Number) {
                        kills = ((Number) bw.get("final_kills_bedwars")).intValue();
                    }
                    if (bw.get("final_deaths_bedwars") instanceof Number) {
                        deaths = ((Number) bw.get("final_deaths_bedwars")).intValue();
                    }
                    if (bw.get("wins_bedwars") instanceof Number) {
                        wins = ((Number) bw.get("wins_bedwars")).intValue();
                    }
                    if (bw.get("losses_bedwars") instanceof Number) {
                        losses = ((Number) bw.get("losses_bedwars")).intValue();
                    }
                    if (bw.get("winstreak") instanceof Number) {
                        winstreak = ((Number) bw.get("winstreak")).intValue();
                    }
                }
            }
            long lastLogin = 0L;
            if (body.get("lastLogin") instanceof Number) {
                lastLogin = ((Number) body.get("lastLogin")).longValue();
            }
            double fkdr = kills / (double) Math.max(1, deaths);
            double wlr = wins / (double) Math.max(1, losses);
            return new Result(uuid, job.name, false, fkdr, wlr, stars, kills, wins, winstreak, lastLogin);
        } catch (Throwable thrown) {
            Log.once("hypixel fetch", thrown);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static UUID mojangUuid(String name) {
        try {
            String url = "https://api.mojang.com/users/profiles/minecraft/" + URLEncoder.encode(name, "UTF-8");
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestProperty("User-Agent", "SafeDetect");
            int code = conn.getResponseCode();
            if (code == 204 || code == 404) {
                return null;
            }
            InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (stream == null) {
                return null;
            }
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[512];
            int n;
            while ((n = stream.read(chunk)) >= 0) {
                buf.write(chunk, 0, n);
            }
            stream.close();
            Object root = Json.parse(new String(buf.toByteArray(), StandardCharsets.UTF_8));
            if (!(root instanceof Map) || !(((Map<?, ?>) root).get("id") instanceof String)) {
                return null;
            }
            String raw = ((String) ((Map<?, ?>) root).get("id")).replace("-", "");
            if (raw.length() != 32) {
                return null;
            }
            return UUID.fromString(raw.substring(0, 8) + "-" + raw.substring(8, 12) + "-" + raw.substring(12, 16)
                    + "-" + raw.substring(16, 20) + "-" + raw.substring(20));
        } catch (Throwable thrown) {
            Log.once("mojang uuid", thrown);
            return null;
        }
    }
}
