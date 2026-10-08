package com.safedetect.agent;

import java.util.List;

/** Optional hooks a {@link Plugin} can implement. */
public interface PluginEvents {
    void chat(String plain);

    void world();

    void tab(List<PluginPlayer> players);
}
