package puregero.multipaper.server.velocity.serverselection.strategy;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import puregero.multipaper.server.velocity.MultiPaperVelocity;

public class LowestPlayers implements ServerSelectionStrategy {
    @Override
    public RegisteredServer selectServer(Player player, MultiPaperVelocity plugin) {
        RegisteredServer bestServer = null;
        long lowestPlayers = Integer.MAX_VALUE;

        for (RegisteredServer server : plugin.getProxy().getAllServers()) {
            if (server.getPlayersConnected().size() < lowestPlayers) {
                lowestPlayers = server.getPlayersConnected().size();
                bestServer = server;
            }
        }

        return bestServer;
    }
}
