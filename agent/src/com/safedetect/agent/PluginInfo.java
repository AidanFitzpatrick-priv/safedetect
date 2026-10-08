package com.safedetect.agent;

/** Marketplace card for a plugin. */
public final class PluginInfo {
    public final String id;
    public final String name;
    public final String author;
    public final String version;
    public final String blurb;
    /** Extra key or setup the card should mention, or empty. */
    public final String needs;
    public final String commands;

    public PluginInfo(String id, String name, String author, String version, String blurb, String needs,
            String commands) {
        this.id = id;
        this.name = name;
        this.author = author;
        this.version = version;
        this.blurb = blurb;
        this.needs = needs == null ? "" : needs;
        this.commands = commands == null ? "" : commands;
    }
}
