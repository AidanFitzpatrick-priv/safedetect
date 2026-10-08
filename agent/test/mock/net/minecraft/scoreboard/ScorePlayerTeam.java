package net.minecraft.scoreboard;

public class ScorePlayerTeam {
    private final String prefix;

    public ScorePlayerTeam(String prefix) {
        this.prefix = prefix;
    }

    public String getColorPrefix() {
        return prefix;
    }
}
