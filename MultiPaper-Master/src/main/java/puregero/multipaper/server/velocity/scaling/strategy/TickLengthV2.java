package puregero.multipaper.server.velocity.scaling.strategy;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import com.velocitypowered.api.proxy.server.RegisteredServer;

import puregero.multipaper.server.ServerConnection;
import puregero.multipaper.server.velocity.BaseStrategy;
import puregero.multipaper.server.velocity.MultiPaperVelocity;


public class TickLengthV2 extends BaseStrategy {
    private static final int DEFAULT_MSPT_HIGH = 40;
    private static final int DEFAULT_MSPT_LOW = 10;
    private static final double DEFAULT_SCALEUP_RATIO = 0.3;


    private int msptHigh;
    private int msptLow;
    private double scaleUpRatio;

    public TickLengthV2(Long interval, TimeUnit timeUnit) {
        super(interval, timeUnit);
    }

    @Override
    public void onStartup(MultiPaperVelocity plugin) {
        super.onStartup(plugin);
        this.msptHigh = Math.toIntExact(config.getLong("scaling.tick_length.high", (long) DEFAULT_MSPT_HIGH));
        this.msptLow = Math.toIntExact(config.getLong("scaling.tick_length.low", (long) DEFAULT_MSPT_LOW));
        this.scaleUpRatio = config.getDouble("scaling.scaleUpRatio", DEFAULT_SCALEUP_RATIO);
    }

    @Override
    public void executeStrategy() {
        Collection<RegisteredServer> allServers = plugin
                .getProxy()
                .getAllServers();

        // if more than scaleUpRatio servers are behind threshold we should scale up
        long counter = allServers
                        .stream()
                        .filter(server -> ServerConnection
                            .getConnection(server.getServerInfo().getName())
                            .getTimer()
                            .averageInMillis() >= msptHigh)
                        .count();

        boolean scaleUp = counter >= scaleUpRatio * (double) allServers.size();

        logger.info("Servers with degraded tick time: {}, required {} servers for scale up", counter, (int) Math.round(scaleUpRatio * (double) allServers.size()));
        logger.info("Scale up servers needed: {}", scaleUp);
        
        //if (scaleUp)
            //plugin.getScalingManager().scaleUp();

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
        // if (scaleDown) {
        //     allServers
        //             .stream()
        //             .min(Comparator.comparingInt(s -> s.getPlayersConnected().size()))
        //             .ifPresent(server -> {
        //                 plugin.getScalingManager().deletePod(server.getServerInfo().getName());
        //             });
        // }
    }
}
