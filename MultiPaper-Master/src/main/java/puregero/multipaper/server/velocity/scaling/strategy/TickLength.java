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

    private boolean disableScaling = false;

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
    public void onServerRegister(RegisteredServer server) {
        if (disableScaling) {
            logger.info("New server registered, enabling scaling again");
            disableScaling = false;
        }
    }

    @Override
    public void onServerUnregister(RegisteredServer server) {
        if (disableScaling) {
            logger.info("Server unregistered, enabling scaling again");
            disableScaling = false;
        }
    }

    @Override
    public void executeStrategy() {
        if (disableScaling) {
            logger.info("Scaling is disabled");
            return;
        }

        Collection<RegisteredServer> allServers = plugin
                .getProxy()
                .getAllServers();

        // if all servers are above the threshold, scale up
        boolean scaleUp = allServers
                .stream()
                .map(server -> {
                    double mspt = ServerConnection
                            .getConnection(server.getServerInfo().getName())
                            .getTimer()
                            .averageInMillis();
                    logger.info("Server {} mspt: {}", server.getServerInfo().getName(), mspt);
                    return mspt > msptHigh;
                })
                .reduce(Boolean::logicalAnd)
                .orElse(false);

        if (scaleUp) {
            logger.info("Scaling up, all servers are above the threshold");
            plugin.getScalingManager().scaleUp();
            // disable scaling until the new server is up
            disableScaling = true;
            // do not scale down if we are scaling up
            return;
        }

        // don't scale down if there is only one server
        if(allServers.size() <= 1) {
            logger.info("Not scaling down, only one server available");
            return;
        }

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
            logger.info("Scaling down, all servers are below the threshold");
            allServers
                    .stream()
                    .min(Comparator.comparingInt(s -> s.getPlayersConnected().size()))
                    .ifPresent(server -> {
                        plugin.getScalingManager().deletePod(server.getServerInfo().getName());
                    });
            // disable scaling until the server is deleted
            disableScaling = true;
        }
    }

    public int getMsptHigh() {
        return msptHigh;
    }

    public int getMsptLow() {
        return msptLow;
    }

    public void setMsptHigh(int msptHigh) {
        this.msptHigh = msptHigh;
    }

    public void setMsptLow(int msptLow) {
        this.msptLow = msptLow;
    }
}
