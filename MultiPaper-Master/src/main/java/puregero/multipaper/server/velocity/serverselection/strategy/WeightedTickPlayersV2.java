package puregero.multipaper.server.velocity.serverselection.strategy;

import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import puregero.multipaper.server.ServerConnection;
import puregero.multipaper.server.velocity.MultiPaperVelocity;
import puregero.multipaper.server.velocity.metric.MetricReporter;
import puregero.multipaper.server.velocity.metric.Metrics;

public class WeightedTickPlayersV2 implements ServerSelectionStrategy {

    @Override
    public RegisteredServer selectServer(Player player, MultiPaperVelocity plugin) {
        
        RegisteredServer bestServer = null;
        double bestQuality = (double) Long.MAX_VALUE;
        double timeW;
        double playerW;

        MetricReporter metricRep;

        Toml config = plugin.getConfig();

        timeW   = config.getDouble("quality.timeW", 5.0);
        playerW = config.getDouble("quality.playerW", 1.0);

        metricRep = plugin.getMetricReporter();

        if (metricRep == null) {
            // Calculation with default values
            plugin.getLogger().info("Metrics not available, working with defaults !!");

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
        } else {
            
            Metrics bestMetric = metricRep.getMetrics().stream()
                .max((Metrics m1, Metrics m2) -> Double.compare(m1.getQuality(), m2.getQuality()))
                .orElse(null);

            if (bestMetric != null) {
                bestQuality = bestMetric.getQuality();
                bestServer = plugin.getProxy().getServer(bestMetric.getName()).orElse(null);
            } else {
                plugin.getLogger().info("Not able to find the best server for login !!!");
                return null;
            }
        }

        plugin.getLogger().info("V2, best quality found: {}", Math.round(bestQuality));

        return bestServer;
    }
}
