package puregero.multipaper.server.velocity.scaling.strategy;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import puregero.multipaper.server.ServerConnection;
import puregero.multipaper.server.velocity.BaseStrategy;
import puregero.multipaper.server.velocity.MultiPaperVelocity;

import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

public class TickLength extends BaseStrategy {
    private static final int DEFAULT_MSPT_HIGH = 40;
    private static final int DEFAULT_MSPT_LOW = 10;

    private int msptHigh;
    private int msptLow;

    public TickLength(Long interval, TimeUnit timeUnit) {
        super(interval, timeUnit);
    }

    @Override
    public void onStartup(MultiPaperVelocity plugin) {
        super.onStartup(plugin);
        this.msptHigh = Math.toIntExact(config.getLong("scaling.tick_length.high", (long) DEFAULT_MSPT_HIGH));
        this.msptLow = Math.toIntExact(config.getLong("scaling.tick_length.low", (long) DEFAULT_MSPT_LOW));
    }

    @Override
    public void executeStrategy() {
        Collection<RegisteredServer> allServers = plugin
                .getProxy()
                .getAllServers();

        // if all servers are above the threshold, scale up
        boolean scaleUp = allServers
                .stream()
                .map(server -> ServerConnection
                        .getConnection(server.getServerInfo().getName())
                        .getTimer()
                        .averageInMillis() > msptHigh)
                .reduce(Boolean::logicalAnd)
                .orElse(false);
        if (scaleUp)
            plugin.getScalingManager().scaleUp();

        // if all servers are below the threshold, scale down
        boolean scaleDown = allServers
                .stream()
                .map(server -> ServerConnection
                        .getConnection(server.getServerInfo().getName())
                        .getTimer()
                        .averageInMillis() < msptLow)
                .reduce(Boolean::logicalAnd)
                .orElse(false);

        // delete the server with the lowest amount of players
        if (scaleDown) {
            allServers
                    .stream()
                    .min(Comparator.comparingInt(s -> s.getPlayersConnected().size()))
                    .ifPresent(server -> {
                        plugin.getScalingManager().deletePod(server.getServerInfo().getName());
                    });
        }
    }
}
