package com.safedetect.agent;

import java.util.UUID;

/** One tab-list player passed to {@link PluginEvents#tab}. */
public final class PluginPlayer {
    public final UUID id;
    public final String name;

    public PluginPlayer(UUID id, String name) {
        this.id = id;
        this.name = name;
    }
}
