package net.minecraft.scoreboard;

public class ScorePlayerTeam {
    private final String prefix;
    private final String suffix;

    public ScorePlayerTeam(String prefix) {
        this(prefix, "");
    }

    public ScorePlayerTeam(String prefix, String suffix) {
        this.prefix = prefix;
        this.suffix = suffix;
    }

    public String getColorPrefix() {
        return prefix;
    }

    public String getColorSuffix() {
        return suffix;
    }
}
