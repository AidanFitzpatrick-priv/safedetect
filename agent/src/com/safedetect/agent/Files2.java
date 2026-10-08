package com.safedetect.agent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Crash-safe file writes. {@link #writeLater} keeps disk I/O off Minecraft's client thread; one writer
 * thread runs jobs in submission order, so a later write to the same file always wins.
 */
final class Files2 {
    /** Windows refuses to replace a file another process (the overlay, antivirus) has open for a moment. */
    private static final int MOVE_ATTEMPTS = 6;

    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "SafeDetect-Writer");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        }
    });

    static {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    drain(2000L);
                }
            }, "SafeDetect-Writer-Drain"));
        } catch (Throwable ignored) {
        }
    }

    private Files2() {
    }

    static boolean writeAtomic(File file, String text) {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            parent.mkdirs();
        }
        File tmp = new File(file.getPath() + ".tmp");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            writer.write(text);
        } catch (Throwable thrown) {
            Log.once("write " + file.getName(), thrown);
            return false;
        }
        for (int attempt = 1; ; attempt++) {
            try {
                move(tmp, file);
                return true;
            } catch (FileSystemException busy) {
                if (attempt >= MOVE_ATTEMPTS) {
                    Log.once("move " + file.getName(), busy);
                    tmp.delete();
                    return false;
                }
                try {
                    Thread.sleep(10L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            } catch (Throwable thrown) {
                Log.once("move " + file.getName(), thrown);
                return false;
            }
        }
    }

    private static void move(File from, File to) throws java.io.IOException {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void writeLater(final File file, final String text) {
        try {
            WRITER.execute(new Runnable() {
                @Override
                public void run() {
                    writeAtomic(file, text);
                }
            });
        } catch (Throwable thrown) {
            writeAtomic(file, text);
        }
    }

    /** Blocks until every queued write has finished, or the timeout passes. */
    static void drain(long millis) {
        try {
            WRITER.submit(new Runnable() {
                @Override
                public void run() {
                }
            }).get(millis, TimeUnit.MILLISECONDS);
        } catch (Throwable ignored) {
        }
    }
}
