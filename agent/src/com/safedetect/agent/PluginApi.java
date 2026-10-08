package com.safedetect.agent;

import java.util.UUID;

/** What a plugin may do. Runs on the Minecraft client thread except HTTP helpers. */
public interface PluginApi {
    void chat(String text);

    void sound();

    /** Sends a chat packet to the server (play commands, /who, …). */
    void send(String message);

    String selfName();

    UUID selfUuid();

    boolean skipped(String name);

    String hypixelKey();

    String urchinKey();

    String teamPrefix(String name);

    void tabSuffix(UUID id, String suffix);

    void clearTabSuffix(UUID id);

    String config(String key);

    void config(String key, String value);

    boolean configOn(String key, boolean fallback);

    int configInt(String key, int fallback);

    /** Blocking HTTP GET. Call from a worker thread. */
    String httpGet(String url, String headerName, String headerValue);

    /** Blocking HTTP POST with a JSON body. Call from a worker thread. */
    String httpPost(String url, String jsonBody, String headerName, String headerValue);
}
