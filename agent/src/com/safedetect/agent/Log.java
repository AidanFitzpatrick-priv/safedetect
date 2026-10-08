package com.safedetect.agent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Writes to safedetect-agent.log beside the agent jar and to stdout, which Lunar copies into latest.log.
 */
final class Log {
    private static final Set<String> ONCE = Collections.synchronizedSet(new HashSet<String>());
    private static PrintWriter out;

    private Log() {
    }

    static synchronized void open(File file) {
        try {
            out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8), true);
        } catch (Throwable ignored) {
            out = null;
        }
    }

    static synchronized void info(String message) {
        String line = new SimpleDateFormat("HH:mm:ss").format(new Date()) + " " + message;
        System.out.println("[SafeDetect] " + message);
        if (out != null) {
            out.println(line);
        }
    }

    static void once(String where, Throwable thrown) {
        if (thrown == null || !ONCE.add(where)) {
            return;
        }
        Throwable cause = thrown.getCause() != null && thrown instanceof java.lang.reflect.InvocationTargetException
                ? thrown.getCause() : thrown;
        info("Skipped " + where + ": " + cause);
    }
}
