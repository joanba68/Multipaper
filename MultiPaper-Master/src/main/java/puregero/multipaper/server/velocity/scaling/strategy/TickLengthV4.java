package puregero.multipaper.server.velocity.scaling.strategy;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.function.Function;

import com.velocitypowered.api.proxy.server.RegisteredServer;

import puregero.multipaper.server.ServerConnection;
import puregero.multipaper.server.velocity.BaseStrategy;
import puregero.multipaper.server.velocity.MultiPaperVelocity;
import puregero.multipaper.server.velocity.ServerWithData;
import puregero.multipaper.server.velocity.metric.MetricReporter;
import puregero.multipaper.server.velocity.metric.Metrics;

public class TickLengthV4 extends BaseStrategy {

    private static final int DEFAULT_MSPT_HIGH = 40;
    private static final int DEFAULT_MSPT_LOW = 10;
    private static final double DEFAULT_RED_RATIO = 0.6;
    private static final long DEFAULT_INTERVAL = 2;
    private static final String DEFAULT_UNIT_TIME = "MINUTES";
    //private static final long DEFAULT_WAITT = 10;
    private static final double DEFAULT_SCALEUP_RATIO = 0.3;
    private static final double DEFAULT_SCALEDOWN_RATIO = 0.3;
    private static final int DEFAULT_MIN_SERVERS_DOWN = 2;
    private static final int DEFAULT_MAX_SERVERS_UP = 10;
    private static final int DEFAULT_IDEAL_PLAYERS = 40;

    private int msptHigh;
    private int msptLow;
    private double scaleUpRatio;
    private boolean scalingUp;
    private double scaleDownRatio;
    private boolean scalingDown;
    //private int waitT;
    //private int waitScaling;
    private double red;
    private int minServers;
    private int maxServers;
    private double timeW;
    private double playerW;

    private Collection<Metrics> metrics;

    public TickLengthV4(Long interval, TimeUnit timeUnit) {
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
        //this.waitT           = Math.toIntExact(config.getLong("scaling.waitT", (long) DEFAULT_WAITT));
        this.minServers      = Math.toIntExact(config.getLong("scaling.minServers", (long) DEFAULT_MIN_SERVERS_DOWN));
        this.maxServers      = Math.toIntExact(config.getLong("scaling.maxServers", (long) DEFAULT_MAX_SERVERS_UP));

        this.timeW   = config.getDouble("defaults.timeW", 5.0);
        this.playerW = config.getDouble("defaults.playerW", 1.0);

        this.scalingUp   = false;
        this.scalingDown = false;
        //this.waitScaling = 0;

    }

    @Override
    public void onServerRegister(RegisteredServer server) {
        if (scalingUp) {
            logger.info("New server registered, enabling scaling again");
            scalingUp = false;
        }
    }

    @Override
    public void onServerUnregister(RegisteredServer server) {
        if (scalingDown) {
            logger.info("Server unregistered, enabling scaling again");
            scalingDown = false;
        }
    }

    @Override
    public void executeStrategy() {

        long redServers;
        long scaleUpServers;

        MetricReporter metrics = plugin.getMetricReporter();

        double qualityT = metrics.getQualityT();

        if (metrics.getMetrics() == null) {
            logger.info("Waiting for servers before starting scaling strategy");
            return;
        }
                      
        // // Obtenir tots els servidors i filtrar els actius
        Collection<RegisteredServer> allServers = plugin
                .getProxy()
                .getAllServers();

        // at startup time there are no registered servers...
        if (allServers.size() == 0) {
            logger.info("Waiting for servers before starting scaling strategy");
            return;
        }

        Map<String, Metrics> metricsMap = metrics.getMetrics().stream()
            .collect(Collectors.toMap(Metrics::getName, Function.identity()));

        Collection<ServerWithData> serversWD = allServers
            .stream()
            .map(server -> {
                String serverName = server.getServerInfo().getName();
                Metrics wmetrics = metricsMap.get(serverName);
                if (wmetrics == null) {
                    logger.warn("Metrics not found for server: {}", serverName);
                    new ServerWithData(
                        false, // Comparar qualitat amb el llindar
                        server,
                        0,
                        0,
                        0,
                        0
                    );
                }
                return new ServerWithData(
                    wmetrics.getQuality() >= qualityT, // Comparar qualitat amb el llindar
                    server,
                    wmetrics.getPlayers(),
                    wmetrics.getMspt(),
                    wmetrics.getQuality(),
                    wmetrics.getChunks()
                );
            })
            .collect(Collectors.toList());

        // for (ServerWithData serverX : serversWD){
        //     double mspt = ServerConnection.getConnection(serverX.getServer().getServerInfo().getName()).getTimer().averageInMillis();
        //     double quality = ServerConnection.getConnection(serverX.getServer().getServerInfo().getName()).getTimer().averageInMillis() * this.timeW + serverX.getPlayers() * this.playerW;
        //     logger.info("{} {} P {} mspt, {} OWNC {} Q, degraded perf. {}", 
        //         serverX.getServer().getServerInfo().getName(), 
        //         serverX.getPlayers(), 
        //         Math.round(mspt),
        //         Math.round(serverX.getChunks()),
        //         Math.round(quality),
        //         serverX.getPerf());
        // }

        Map<Boolean, List<ServerWithData>> partitionedServers = serversWD
            .stream()
            .collect(Collectors.partitioningBy(server -> server.getPerf()));

        ServerWithData[] serversBad = partitionedServers.get(true).toArray(new ServerWithData[0]);
        //ServerWithData[] serversOk  = partitionedServers.get(false).toArray(new ServerWithData[0]);

        long counterBad = serversBad.length;
        //long counterOk  = serversOk.length;

        // Now to consider scale up servers
        logger.info("Servers with degraded tick time: {}", counterBad);

        redServers      = (long) Math.round(red * (double) allServers.size());
        scaleUpServers  = (long) Math.round(scaleUpRatio * (double) counterBad);
        if (scaleUpServers != 0) logger.info("Required {} degraded servers for scale up", scaleUpServers);
        
        // From here, we have servers with degraded tick time and enough servers to migrate players
        // if too many servers are degraded, no need to migrate, should be scale up from scaling manager

        if (counterBad < redServers) {
            logger.info("No scale up needed");
        } else if (scalingUp == false) {
            // scaling only if there are not previous operations in place
        
            int count = allServers.size();
            if (count < maxServers) {
                logger.info("Scaling up 1 server now there are {} servers...", count);
                scalingUp = true;
                plugin.getScalingManager().scaleUp();
                return;
            }
        }

        // don't scale down if there is only a minimal number of servers
        if(allServers.size() < minServers) {
            logger.info("Now {} active servers, at least {} for scale down !!", allServers.size(), minServers);
            return;
        }

        // if all servers are below the threshold, scale down
        // need some kind of hysteresis here...
        boolean scaleDown = serversWD
            .stream()
            .map(server -> server.getQuality() < qualityT * (1 - scaleDownRatio))
            .reduce(Boolean::logicalAnd)
            .orElse(false);

        // delete the server with the lowest amount of players
        if (scaleDown) {
            int serversDown = (int) Math.round((double) scaleDownRatio * (long) allServers.size());
            logger.info("Scaling down {} servers", serversDown);
            logger.info("Now {} active servers, at least {} for scale down !!", allServers.size(), minServers);

            // when there are 2 servers, scaling down cannot occur
            if (serversDown == 0) serversDown = 1;

            scalingDown = true;
            allServers
                    .stream()
                    .limit(serversDown)
                    .min(Comparator.comparingInt(s -> s.getPlayersConnected().size()))
                    .ifPresent(server -> {
                        plugin.getScalingManager().deletePod(server.getServerInfo().getName());
                    });
        } else {
            logger.info("No scale down needed");
        }
    }
}
