package puregero.multipaper.server.velocity.serverselection.strategy;

import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import puregero.multipaper.server.ServerConnection;
import puregero.multipaper.server.velocity.MultiPaperVelocity;

public class WeightedTickPlayers implements ServerSelectionStrategy {

    protected Toml config;
    protected double timeW;
    protected double playerW;

    public WeightedTickPlayers() {
        this.config = null;

        timeW   = 1.0;
        playerW = 1.0;
    }

    public WeightedTickPlayers(Toml config) {
        this.config = config;

        loadConfig();
    }

    protected void loadConfig(){

        try {
            timeW   = config.getDouble("server-selection.timeW");
            playerW = config.getDouble("server-selection.playerW");
        } catch (IllegalStateException e) {
            timeW   = 5.0;
            playerW = 1.0;
        }

    }

    @Override
    public RegisteredServer selectServer(Player player, MultiPaperVelocity plugin) {
        
        RegisteredServer bestServer = null;
        double bestQuality = (double) Long.MAX_VALUE;

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

        return bestServer;
    }
}
