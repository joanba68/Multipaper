package puregero.multipaper.server.velocity.scaling.strategy;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.velocitypowered.api.proxy.server.RegisteredServer;

import puregero.multipaper.server.ServerConnection;
import puregero.multipaper.server.velocity.BaseStrategy;
import puregero.multipaper.server.velocity.MultiPaperVelocity;

class ServerWithData {

    protected Boolean perf;
    protected RegisteredServer server;
    protected int players;

    protected ServerWithData(Boolean perfDeg, RegisteredServer server, int players){
        this.perf = perfDeg;
        this.server = server;
        this.players = players;

    }

}

public class TickLengthV3 extends BaseStrategy {

    private static final int DEFAULT_MSPT_HIGH = 40;
    private static final int DEFAULT_MSPT_LOW = 10;
    private static final double DEFAULT_RED_RATIO = 0.6;
    private static final long DEFAULT_INTERVAL = 2;
    private static final String DEFAULT_UNIT_TIME = "MINUTES";
    private static final long DEFAULT_WAITT = 10;
    private static final double DEFAULT_SCALEUP_RATIO = 0.3;
    private static final double DEFAULT_SCALEDOWN_RATIO = 0.3;
    private static final int DEFAULT_MIN_SERVERS_DOWN = 5;

    private int msptHigh;
    private int msptLow;
    private double scaleUpRatio;
    private boolean scalingUp;
    private double scaleDownRatio;
    private boolean scalingDown;
    private int waitT;
    private int waitScaling;
    private double red;
    private int minServers;

    public TickLengthV3(Long interval, TimeUnit timeUnit) {
        super(interval, timeUnit);
    }

    @Override
    public void onStartup(MultiPaperVelocity plugin) {
        super.onStartup(plugin);
        this.msptHigh     = Math.toIntExact(config.getLong("performance.tick_length.high", (long) DEFAULT_MSPT_HIGH));
        this.msptLow      = Math.toIntExact(config.getLong("performance.tick_length.low", (long) DEFAULT_MSPT_LOW));
        this.red          = config.getDouble("performance.tick_length.redL", DEFAULT_RED_RATIO);

        this.scaleUpRatio    = config.getDouble("scaling.scaleUpRatio", DEFAULT_SCALEUP_RATIO);
        this.scaleDownRatio  = config.getDouble("scaling.scaleDownRatio", DEFAULT_SCALEDOWN_RATIO);
        this.interval        = config.getLong("scaling.interval", DEFAULT_INTERVAL);
        this.timeUnit        = TimeUnit.valueOf(config.getString("scaling.units", DEFAULT_UNIT_TIME));
        this.waitT           = Math.toIntExact(config.getLong("scaling.waitT", (long) DEFAULT_WAITT));
        this.minServers      = Math.toIntExact(config.getLong("scaling.minServers", (long) DEFAULT_MIN_SERVERS_DOWN));

        this.scalingUp   = false;
        this.scalingDown = false;
        this.waitScaling = 0;

        plugin.getProxy().getScheduler().buildTask(plugin, this::executeStrategy)
            .repeat(interval, timeUnit)
            .schedule();

        plugin.getProxy().getScheduler().buildTask(plugin, this::checkScaling)
            .repeat(interval, timeUnit)
            .schedule();
    }

    public void checkScaling() {
        if(waitScaling < waitT) {
            logger.info("TickLengthv3: No scale up/down operations in progress");
            waitScaling += 1;
        } else if (scalingUp) {
            logger.info("TickLengthv3: Cleaning scale up lock");
            scalingUp = false;
            waitScaling = 0;
        } else if (scalingDown) {
            logger.info("TickLengthv3: Cleaning scale down lock");
            scalingDown = false;
            waitScaling = 0;
        }

    }

    @Override
    public void executeStrategy() {

        long redServers;
        long scaleUpServers;
        long scaleDownServers;

        Collection<RegisteredServer> allServers = plugin
                .getProxy()
                .getAllServers();

        // at startup time there are no registered servers...
        if (allServers.size() == 0) {
            logger.info("TickLengthv3: Waiting for servers before starting scaling strategy");
            return;
        }

        Collection<ServerWithData> serversWD = allServers
            .stream()
            .map(server -> new ServerWithData(
                ServerConnection.getConnection(server.getServerInfo().getName()).getTimer().averageInMillis() >= msptHigh,
                server,
                server.getPlayersConnected().size()))
            .collect(Collectors.toList());

        Map<Boolean, List<ServerWithData>> partitionedServers = serversWD
            .stream()
            .collect(Collectors.partitioningBy(server -> server.perf));

        ServerWithData[] serversBad = partitionedServers.get(true).toArray(new ServerWithData[0]);
        //ServerWithData[] serversOk  = partitionedServers.get(false).toArray(new ServerWithData[0]);

        long counterBad = serversBad.length;
        //long counterOk  = serversOk.length;

        // Now to consider scale up servers

        logger.info("TickLengthv3: Servers with degraded tick time: {}", counterBad);

        redServers      = (long) Math.round(red * (double) allServers.size());
        scaleUpServers  = (long) Math.round(scaleUpRatio * (double) counterBad);
        
        // From here, we have servers with degraded tick time and enough servers to migrate players
        // if too many servers are degraded, no need to migrate, should be scale up from scaling manager

        if (counterBad < redServers) {
            logger.info("TickLengthv3: No scale up needed");
        } else if (scalingUp == false) {
            // scaling only if there are not previous operations in place
            logger.info("TickLengthv3: Servers with degraded tick time: {}, required {} servers for scale up", counterBad, scaleUpServers);
           
            scalingUp = true;
            //plugin.getScalingManager().scaleUp();
        }

        // don't scale down if there is only one server
        if(allServers.size() <= minServers) {
            logger.info("TickLengthv3: There are no servers to scale down");
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
            int serversDown = (int) Math.round((double) scaleDownRatio * (long) allServers.size());
            logger.info("TickLengthv3: Scaling down {} servers", serversDown);

            scalingDown = true;
        //     allServers
        //             .stream()
        //             .limit(serversDown)
        //             .min(Comparator.comparingInt(s -> s.getPlayersConnected().size()))
        //             .ifPresent(server -> {
        //                 plugin.getScalingManager().deletePod(server.getServerInfo().getName());
        //             });
        }
    }
}
