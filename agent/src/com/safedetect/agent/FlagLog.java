package com.safedetect.agent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Append-only readable log of flags, nicks, and stats. {@code config/safedetect-flags.log}.
 */
final class FlagLog {
    private static PrintWriter out;

    private FlagLog() {
    }

    static synchronized void open(File gameDir) {
        try {
            File file = new File(gameDir, "config/safedetect-flags.log");
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                parent.mkdirs();
            }
            out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8), true);
        } catch (Throwable ignored) {
            out = null;
        }
    }

    static synchronized void line(String name, String check) {
        if (out == null) {
            return;
        }
        String when = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        out.println(when + "  " + (name == null ? "?" : name) + "  " + (check == null ? "?" : check));
    }
}
