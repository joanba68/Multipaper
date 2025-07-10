package puregero.multipaper.server.velocity.serverselection.strategy;

import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import puregero.multipaper.server.ServerConnection;
import puregero.multipaper.server.velocity.MultiPaperVelocity;

public class WeightedTickPlayers implements ServerSelectionStrategy {

    @Override
    public RegisteredServer selectServer(Player player, MultiPaperVelocity plugin) {
        
        RegisteredServer bestServer = null;
        double bestQuality = (double) Long.MAX_VALUE;
        double timeW;
        double playerW;

        Toml config = plugin.getConfig();

        timeW   = config.getDouble("quality.timeW", 5.0);
        playerW = config.getDouble("quality.playerW", 1.0);


        plugin.getLogger().info("Weights configured: {} * time + {} * players", timeW, playerW);

        for (RegisteredServer server : plugin.getProxy().getAllServers()) {
            String serverName = server.getServerInfo().getName();
            long players = server.getPlayersConnected().size();
            ServerConnection connection = ServerConnection.getConnection(serverName);

            if (connection != null && ServerConnection.isAlive(serverName)
                    && ((double) connection.getTimer().averageInMillis() * timeW + (double) players * playerW < bestQuality)) {
                // bestServer calculation formula
                bestQuality = (double) connection.getTimer().averageInMillis() * timeW + (double) players * playerW;
                bestServer = server;
            }
        }
        plugin.getLogger().info("Best quality found: {} for server {}", Math.round(bestQuality), bestServer.getServerInfo().getName());

        return bestServer;
    }
}
