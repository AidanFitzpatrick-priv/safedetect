package com.safedetect.agent;

/**
 * A SafeDetect add-on. Builtin modules ship in the jar. Extra ones can be dropped in
 * {@code config/safedetect-plugins/*.jar} with {@code Plugin-Class} in the manifest.
 */
public interface Plugin {
    PluginInfo info();

    void start(PluginApi api);

    void stop();

    /**
     * {@code /sd <id> ...} with {@code parts[1]} equal to {@link PluginInfo#id}.
     * @return true when this plugin handled the line
     */
    boolean command(String[] parts);
}
