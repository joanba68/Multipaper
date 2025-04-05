package puregero.multipaper.server.velocity.serverselection.strategy;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import puregero.multipaper.server.ServerConnection;
import puregero.multipaper.server.velocity.MultiPaperVelocity;

public class LowestTickTime implements ServerSelectionStrategy {

    @Override
    public RegisteredServer selectServer(Player player, MultiPaperVelocity plugin) {
        RegisteredServer bestServer = null;
        long lowestTickTime = Long.MAX_VALUE;

        for (RegisteredServer server : plugin.getProxy().getAllServers()) {
            String serverName = server.getServerInfo().getName();
            ServerConnection connection = ServerConnection.getConnection(serverName);

            if (connection != null && ServerConnection.isAlive(serverName)
                    && connection.getTimer().averageInMillis() < lowestTickTime) {
                lowestTickTime = connection.getTimer().averageInMillis();
                bestServer = server;
            }
        }

        return bestServer;
    }
}
