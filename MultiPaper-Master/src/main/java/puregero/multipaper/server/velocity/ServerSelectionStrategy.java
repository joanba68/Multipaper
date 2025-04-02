package puregero.multipaper.server.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import puregero.multipaper.server.ServerConnection;

import java.util.Collection;

public interface ServerSelectionStrategy {
    RegisteredServer selectServer(Collection<RegisteredServer> servers, Player player);

    ServerSelectionStrategy lowestTickTime = (servers, player) -> {
        RegisteredServer bestServer = null;
        long lowestTickTime = Long.MAX_VALUE;

        for (RegisteredServer server : servers) {
            String serverName = server.getServerInfo().getName();
            ServerConnection connection = ServerConnection.getConnection(serverName);

            if (connection != null && ServerConnection.isAlive(serverName)
                    && connection.getTimer().averageInMillis() < lowestTickTime) {
                lowestTickTime = connection.getTimer().averageInMillis();
                bestServer = server;
            }
        }

        return bestServer;
    };
}
