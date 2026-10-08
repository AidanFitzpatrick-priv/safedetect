package net.minecraft.client.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NetHandlerPlayClient {
    public final List<NetworkPlayerInfo> players = new ArrayList<NetworkPlayerInfo>();

    public Collection<NetworkPlayerInfo> getPlayerInfoMap() {
        return players;
    }
}
