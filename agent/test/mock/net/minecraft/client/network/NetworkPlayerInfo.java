package net.minecraft.client.network;

import com.mojang.authlib.GameProfile;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.IChatComponent;

public class NetworkPlayerInfo {
    private final GameProfile profile;
    private IChatComponent displayName;
    public ScorePlayerTeam team;

    public NetworkPlayerInfo(GameProfile profile) {
        this.profile = profile;
    }

    public GameProfile getGameProfile() {
        return profile;
    }

    public IChatComponent getDisplayName() {
        return displayName;
    }

    public void setDisplayName(IChatComponent name) {
        displayName = name;
    }

    public ScorePlayerTeam getPlayerTeam() {
        return team;
    }
}
