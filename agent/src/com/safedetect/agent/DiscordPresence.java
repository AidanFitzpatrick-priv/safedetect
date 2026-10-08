package com.safedetect.agent;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Optional Discord Rich Presence over the local IPC pipe. Needs {@code /sd discord <app-id>}.
 * All pipe I/O runs on its own daemon thread: callers only post the latest state, so the client thread
 * never blocks on Discord. Every frame Discord sends back is read, so the pipe never fills up.
 */
final class DiscordPresence {
    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;
    private static final int MAX_FRAME = 1 << 20;

    private final Object lock = new Object();
    private String wantedAppId = "";
    private int games;
    private int wins;
    private boolean dirty;
    private Thread worker;

    private RandomAccessFile pipe;
    private String connectedAppId = "";

    void setAppId(String id) {
        synchronized (lock) {
            wantedAppId = id == null ? "" : id.trim();
            post();
        }
    }

    void session(int games, int wins) {
        synchronized (lock) {
            this.games = games;
            this.wins = wins;
            post();
        }
    }

    static String activityJson(int games, int wins, int pid) {
        String details = games <= 0 ? "Bedwars" : (games + " games \u00b7 " + wins + " wins");
        return "{\"cmd\":\"SET_ACTIVITY\",\"nonce\":" + Json.quote("sd" + games)
                + ",\"args\":{\"pid\":" + pid + ",\"activity\":{\"details\":" + Json.quote(details)
                + ",\"state\":" + Json.quote("SafeDetect") + "}}}";
    }

    static String handshakeJson(String appId) {
        return "{\"v\":1,\"client_id\":" + Json.quote(appId) + "}";
    }

    private void post() {
        dirty = true;
        if (worker == null) {
            if (wantedAppId.isEmpty()) {
                return;
            }
            worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    loop();
                }
            }, "SafeDetect-Discord");
            worker.setDaemon(true);
            worker.setPriority(Thread.MIN_PRIORITY);
            worker.start();
        }
        lock.notifyAll();
    }

    private void loop() {
        while (true) {
            String appId;
            int g;
            int w;
            synchronized (lock) {
                while (!dirty) {
                    try {
                        lock.wait();
                    } catch (InterruptedException ignored) {
                        close();
                        return;
                    }
                }
                dirty = false;
                appId = wantedAppId;
                g = games;
                w = wins;
            }
            try {
                if (!appId.equals(connectedAppId)) {
                    close();
                }
                if (appId.isEmpty()) {
                    continue;
                }
                if (pipe == null && !connect(appId)) {
                    continue;
                }
                write(OP_FRAME, activityJson(g, w, pid()));
                readFrame();
            } catch (Throwable thrown) {
                Log.once("discord activity", thrown);
                close();
            }
        }
    }

    private boolean connect(String appId) {
        String[] paths;
        if (Os.WIN) {
            paths = new String[] { "\\\\.\\pipe\\discord-ipc-0", "\\\\.\\pipe\\discord-ipc-1" };
        } else {
            String tmp = System.getenv("XDG_RUNTIME_DIR");
            if (tmp == null) {
                tmp = System.getProperty("java.io.tmpdir");
            }
            String mac = System.getProperty("user.home") + "/Library/Application Support/discord";
            paths = new String[] { tmp + "/discord-ipc-0", tmp + "/discord-ipc-1", mac + "/discord-ipc-0" };
        }
        for (int i = 0; i < paths.length; i++) {
            File file = new File(paths[i]);
            if (!Os.WIN && !file.exists()) {
                continue;
            }
            try {
                pipe = new RandomAccessFile(file, "rw");
                write(OP_HANDSHAKE, handshakeJson(appId));
                readFrame();
                if (pipe != null) {
                    connectedAppId = appId;
                    return true;
                }
            } catch (Throwable ignored) {
                close();
            }
        }
        return false;
    }

    private void write(int opcode, String json) throws Exception {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(opcode);
        header.putInt(body.length);
        pipe.write(header.array());
        pipe.write(body);
    }

    /** Reads and discards one reply frame; a close frame drops the connection. */
    private void readFrame() throws Exception {
        byte[] head = new byte[8];
        pipe.readFully(head);
        ByteBuffer header = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        int opcode = header.getInt();
        int length = header.getInt();
        if (length < 0 || length > MAX_FRAME) {
            throw new IllegalStateException("Bad Discord frame length " + length);
        }
        pipe.readFully(new byte[length]);
        if (opcode == OP_CLOSE) {
            close();
        }
    }

    private static int pid() {
        try {
            String name = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
            int at = name.indexOf('@');
            return at > 0 ? Integer.parseInt(name.substring(0, at)) : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private void close() {
        if (pipe != null) {
            try {
                pipe.close();
            } catch (Throwable ignored) {
            }
            pipe = null;
        }
        connectedAppId = "";
    }
}
