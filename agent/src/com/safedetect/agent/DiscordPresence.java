package com.safedetect.agent;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Optional Discord Rich Presence over the local IPC pipe. Needs {@code /sd discord <app-id>}.
 */
final class DiscordPresence {
    private RandomAccessFile pipe;
    private String appId = "";
    private int games;
    private int wins;

    void setAppId(String id) {
        appId = id == null ? "" : id.trim();
        close();
        if (!appId.isEmpty()) {
            connect();
            push();
        }
    }

    void session(int games, int wins) {
        this.games = games;
        this.wins = wins;
        push();
    }

    private void connect() {
        try {
            String[] paths;
            if (Os.WIN) {
                paths = new String[] { "\\\\.\\pipe\\discord-ipc-0", "\\\\.\\pipe\\discord-ipc-1" };
            } else {
                String tmp = System.getenv("XDG_RUNTIME_DIR");
                if (tmp == null) {
                    tmp = System.getProperty("java.io.tmpdir");
                }
                String mac = System.getProperty("user.home") + "/Library/Application Support/discord";
                paths = new String[] {
                        tmp + "/discord-ipc-0",
                        tmp + "/discord-ipc-1",
                        mac + "/discord-ipc-0"
                };
            }
            for (int i = 0; i < paths.length; i++) {
                File file = new File(paths[i]);
                if (!Os.WIN && !file.exists()) {
                    continue;
                }
                try {
                    pipe = new RandomAccessFile(file, "rw");
                    String handshake = "{\"v\":1,\"client_id\":" + Json.quote(appId) + "}";
                    write(0, handshake);
                    return;
                } catch (Throwable ignored) {
                    close();
                }
            }
        } catch (Throwable thrown) {
            Log.once("discord", thrown);
            close();
        }
    }

    private void push() {
        if (pipe == null || appId.isEmpty()) {
            return;
        }
        try {
            String details = games <= 0 ? "Bedwars" : (games + " games \u00b7 " + wins + " wins");
            String payload = "{\"cmd\":\"SET_ACTIVITY\",\"nonce\":\"sd\"" + games
                    + ",\"args\":{\"pid\":" + pid() + ",\"activity\":{\"details\":" + Json.quote(details)
                    + ",\"state\":" + Json.quote("SafeDetect") + "}}}";
            write(1, payload);
        } catch (Throwable thrown) {
            Log.once("discord activity", thrown);
            close();
        }
    }

    private void write(int opcode, String json) throws Exception {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(opcode);
        header.putInt(body.length);
        pipe.write(header.array());
        pipe.write(body);
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
    }
}
