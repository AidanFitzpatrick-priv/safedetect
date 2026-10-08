package com.safedetect.agent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * GitHub release check. Daily it only hints; the overlay Update button downloads the jar, restarts the
 * overlay, and stages a copy so the next Lunar launch loads the new checks.
 */
final class Updater {
    static final long INTERVAL_MS = 24L * 60 * 60 * 1000;
    static final String RELEASES = "https://github.com/AidanFitzpatrick-priv/safedetect/releases/latest";
    static final String JAR_NAME = "safedetect-agent.jar";
    static final String PENDING_NAME = "safedetect-agent.jar.new";
    private static final String API = "https://api.github.com/repos/AidanFitzpatrick-priv/safedetect/releases/latest";
    private static final long MAX_JAR_BYTES = 20L * 1024 * 1024;
    private static final String APPLY_BAT = "safedetect-apply-update.bat";

    static final class Release {
        String tag;
        String jarUrl;
    }

    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile String chatLine;

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

    /** Starts a background check; {@code install} also downloads and stages the jar. */
    void request(final String current, final boolean install) {
        if (Boolean.getBoolean("safedetect.noUpdate")) {
            if (install) {
                Hud.notice("Updates are disabled for this session.");
                chatLine = "Updates are disabled.";
            }
            return;
        }
        if (current == null || current.isEmpty()) {
            if (install) {
                Hud.notice("This build has no version. Use a released jar.");
                chatLine = "This SafeDetect build has no version, so it cannot self-update.";
            }
            return;
        }
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        if (install) {
            Hud.notice("Checking GitHub for a newer SafeDetect\u2026");
        }
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    runCheck(current, install);
                } finally {
                    busy.set(false);
                }
            }
        }, "SafeDetect-Updater");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    /** Chat body once, then null. */
    String takeChat() {
        String line = chatLine;
        if (line != null) {
            chatLine = null;
        }
        return line;
    }

    @SuppressWarnings("unchecked")
    static Release parseRelease(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        Object root;
        try {
            root = Json.parse(json);
        } catch (Throwable ignored) {
            return null;
        }
        if (!(root instanceof Map)) {
            return null;
        }
        Map<String, Object> map = (Map<String, Object>) root;
        Object tag = map.get("tag_name");
        if (!(tag instanceof String) || ((String) tag).trim().isEmpty()) {
            return null;
        }
        Release release = new Release();
        release.tag = ((String) tag).trim();
        Object assets = map.get("assets");
        if (assets instanceof List) {
            for (Object entry : (List<Object>) assets) {
                if (!(entry instanceof Map)) {
                    continue;
                }
                Map<String, Object> asset = (Map<String, Object>) entry;
                if (JAR_NAME.equals(asset.get("name")) && asset.get("browser_download_url") instanceof String) {
                    String url = ((String) asset.get("browser_download_url")).trim();
                    if (!url.isEmpty()) {
                        release.jarUrl = url;
                        break;
                    }
                }
            }
        }
        if (release.jarUrl == null) {
            release.jarUrl = "https://github.com/AidanFitzpatrick-priv/safedetect/releases/download/"
                    + release.tag + "/" + JAR_NAME;
        }
        return release;
    }

    static File installedJar() {
        try {
            File loc = new File(Agent.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return loc.isFile() ? loc : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static File pendingJar(File installed) {
        return installed == null ? null : new File(installed.getParentFile(), PENDING_NAME);
    }

    static boolean isSafeDetectJar(File jar) {
        String title = manifest(jar, "Implementation-Title");
        return title != null && title.contains("SafeDetect");
    }

    static String manifest(File jar, String key) {
        if (jar == null || !jar.isFile() || key == null) {
            return null;
        }
        ZipFile zip = null;
        try {
            zip = new ZipFile(jar);
            ZipEntry entry = zip.getEntry("META-INF/MANIFEST.MF");
            if (entry == null) {
                return null;
            }
            InputStream in = zip.getInputStream(entry);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[256];
            int n;
            while ((n = in.read(chunk)) >= 0 && buf.size() < 8192) {
                buf.write(chunk, 0, n);
            }
            in.close();
            String prefix = key + ":";
            String[] lines = new String(buf.toByteArray(), StandardCharsets.UTF_8).split("\r?\n");
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].startsWith(prefix)) {
                    return lines[i].substring(prefix.length()).trim();
                }
            }
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    /** Copies {@code pending} over {@code dest} and deletes the pending file. */
    static boolean applyPending(File pending, File dest) {
        if (pending == null || dest == null || !pending.isFile()) {
            return false;
        }
        try {
            if (pending.getCanonicalFile().equals(dest.getCanonicalFile())) {
                return true;
            }
            Files.copy(pending.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            pending.delete();
            return dest.isFile() && dest.length() > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static String applyBatText() {
        return "@echo off\r\n"
                + "set \"SRC=%~dp0" + PENDING_NAME + "\"\r\n"
                + "set \"DST=%~dp0" + JAR_NAME + "\"\r\n"
                + "if not exist \"%SRC%\" exit /b 0\r\n"
                + ":retry\r\n"
                + "timeout /t 2 /nobreak >nul\r\n"
                + "copy /y \"%SRC%\" \"%DST%\" >nul 2>&1\r\n"
                + "if errorlevel 1 goto retry\r\n"
                + "del \"%SRC%\" >nul 2>&1\r\n"
                + "del \"%~dp0" + JAR_NAME + ".old\" >nul 2>&1\r\n"
                + "del \"%~f0\" >nul 2>&1\r\n";
    }

    static File writeApplyBat(File dir) {
        if (dir == null) {
            return null;
        }
        File bat = new File(dir, APPLY_BAT);
        return Files2.writeAtomic(bat, applyBatText()) ? bat : null;
    }

    private void runCheck(String current, boolean install) {
        Release release = fetchRelease();
        if (release == null) {
            if (install) {
                Hud.notice("Couldn't reach GitHub releases.");
                chatLine = "Couldn't reach GitHub for a SafeDetect update.";
            }
            return;
        }
        if (!newer(release.tag, current)) {
            if (install) {
                Hud.notice("You're on " + current + " \u2014 that's the latest.");
                chatLine = "SafeDetect \u00a7f" + current + "\u00a77 is the latest.";
            }
            return;
        }
        if (!install) {
            Hud.notice("SafeDetect " + release.tag + " is out. Press Update to install.");
            chatLine = "SafeDetect \u00a7f" + release.tag + "\u00a77 is out \u00a78(you have " + current
                    + "). Press Update in the overlay.";
            return;
        }
        File dest = installedJar();
        if (dest == null) {
            Hud.notice("Can't replace this build. Download from GitHub.");
            chatLine = "This SafeDetect build isn't a jar, so it can't self-update.";
            return;
        }
        Hud.notice("Downloading SafeDetect " + release.tag + "\u2026");
        File pending = pendingJar(dest);
        if (!download(release.jarUrl, pending, MAX_JAR_BYTES) || !isSafeDetectJar(pending)) {
            if (pending != null) {
                pending.delete();
            }
            Hud.notice("Download failed, or the file wasn't a SafeDetect jar.");
            chatLine = "SafeDetect update download failed.";
            return;
        }
        Hud.notice("Installing " + release.tag + "\u2026");
        Hud.pauseOverlay();
        boolean replaced = replaceInstalled(pending, dest);
        if (!replaced && pending != null && pending.isFile()) {
            armApplyLater(dest.getParentFile());
        }
        Hud.resumeOverlay();
        if (replaced || (pending != null && pending.isFile())) {
            Hud.notice("Updated to " + release.tag
                    + ". Overlay restarted. Fully restart Lunar so checks load it.");
            chatLine = "SafeDetect \u00a7f" + release.tag
                    + "\u00a77 installed. Overlay is updated. Fully restart Lunar so checks load it.";
        } else {
            Hud.notice("Downloaded " + release.tag + " but couldn't replace the jar.");
            chatLine = "Downloaded SafeDetect \u00a7f" + release.tag
                    + "\u00a77 but the jar is in use. Fully close Lunar and copy "
                    + PENDING_NAME + " over " + JAR_NAME + ".";
        }
    }

    static boolean replaceInstalled(File pending, File dest) {
        if (pending == null || dest == null || !pending.isFile()) {
            return false;
        }
        File bak = new File(dest.getPath() + ".old");
        bak.delete();
        if (dest.isFile() && !dest.renameTo(bak)) {
            return applyPending(pending, dest);
        }
        if (pending.renameTo(dest)) {
            bak.delete();
            return true;
        }
        boolean copied = applyPending(pending, dest);
        if (!copied && bak.isFile() && !dest.isFile()) {
            bak.renameTo(dest);
        }
        if (copied) {
            bak.delete();
        }
        return copied;
    }

    static void armApplyLater(File dir) {
        if (!Os.WIN) {
            return;
        }
        File bat = writeApplyBat(dir);
        if (bat == null || !bat.isFile()) {
            return;
        }
        try {
            new ProcessBuilder("cmd.exe", "/c", "start", "", "/min", bat.getAbsolutePath())
                    .directory(dir)
                    .redirectErrorStream(true)
                    .redirectOutput(new File("NUL"))
                    .start();
        } catch (Throwable thrown) {
            Log.once("update helper", thrown);
        }
    }

    private static Release fetchRelease() {
        String json = get(API, "application/vnd.github+json", 8000, 1 << 20);
        return parseRelease(json);
    }

    static boolean download(String url, File dest, long maxBytes) {
        if (url == null || dest == null) {
            return false;
        }
        File part = new File(dest.getPath() + ".part");
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("User-Agent", "SafeDetect");
            conn.setRequestProperty("Accept", "application/octet-stream");
            if (conn.getResponseCode() != 200) {
                return false;
            }
            long claimed = conn.getContentLengthLong();
            if (claimed > maxBytes) {
                return false;
            }
            InputStream stream = conn.getInputStream();
            File parent = dest.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                parent.mkdirs();
            }
            FileOutputStream out = new FileOutputStream(part);
            byte[] chunk = new byte[8192];
            long total = 0;
            int n;
            while ((n = stream.read(chunk)) >= 0) {
                total += n;
                if (total > maxBytes) {
                    out.close();
                    stream.close();
                    part.delete();
                    return false;
                }
                out.write(chunk, 0, n);
            }
            out.close();
            stream.close();
            if (total < 64) {
                part.delete();
                return false;
            }
            Files.move(part.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return dest.isFile();
        } catch (Throwable ignored) {
            part.delete();
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String get(String url, String accept, int timeoutMs, int maxBytes) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("User-Agent", "SafeDetect");
            conn.setRequestProperty("Accept", accept);
            if (conn.getResponseCode() != 200) {
                return null;
            }
            InputStream stream = conn.getInputStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[2048];
            int n;
            while ((n = stream.read(chunk)) >= 0 && buf.size() < maxBytes) {
                buf.write(chunk, 0, n);
            }
            stream.close();
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
