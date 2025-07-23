package puregero.multipaper.server.velocity.scaling.strategy;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.velocitypowered.api.proxy.server.RegisteredServer;

import puregero.multipaper.server.velocity.BaseStrategy;
import puregero.multipaper.server.velocity.MultiPaperVelocity;
import puregero.multipaper.server.velocity.ServerWithData;
import puregero.multipaper.server.velocity.metric.MetricReporter;
import puregero.multipaper.server.velocity.metric.Metrics;

public class TickLengthV4 extends BaseStrategy {

    private static final double DEFAULT_RED_RATIO = 0.6;
    private static final long DEFAULT_INTERVAL = 2;
    private static final String DEFAULT_UNIT_TIME = "MINUTES";
    private static final double DEFAULT_SCALEUP_RATIO = 0.3;
    private static final double DEFAULT_SCALEDOWN_RATIO = 0.3;
    private static final int DEFAULT_MIN_SERVERS_DOWN = 2;
    private static final int DEFAULT_MAX_SERVERS_UP = 10;
    private static final int DEFAULT_MIN_STEP = 2;
    private static final int DEFAULT_MAX_STEP = 5;

    private double scaleUpRatio;
    private boolean scalingUp;
    private double scaleDownRatio;
    private boolean scalingDown;
    private double red;
    private int minServers;
    private int maxServers;
    private boolean dynamic;
    private int minStep;
    private int maxStep;

    private MetricReporter metrics;

    public TickLengthV4(Long interval, TimeUnit timeUnit) {
        super(interval, timeUnit);
    }

    @Override
    public void onStartup(MultiPaperVelocity plugin) {
        super.onStartup(plugin);

        this.red             = config.getDouble("scaling.redS", DEFAULT_RED_RATIO);
        this.scaleUpRatio    = config.getDouble("scaling.scaleUpRatio", DEFAULT_SCALEUP_RATIO);
        this.scaleDownRatio  = config.getDouble("scaling.scaleDownRatio", DEFAULT_SCALEDOWN_RATIO);
        this.interval        = config.getLong("scaling.interval", DEFAULT_INTERVAL);
        this.timeUnit        = TimeUnit.valueOf(config.getString("scaling.units", DEFAULT_UNIT_TIME));
        this.minServers      = Math.toIntExact(config.getLong("scaling.minServers", (long) DEFAULT_MIN_SERVERS_DOWN));
        this.maxServers      = Math.toIntExact(config.getLong("scaling.maxServers", (long) DEFAULT_MAX_SERVERS_UP));
        this.dynamic         = config.getBoolean("scaling.dynamic", false);
        this.minStep        = Math.toIntExact(config.getLong("scaling.minStep", (long) DEFAULT_MIN_STEP));
        this.maxStep        = Math.toIntExact(config.getLong("scaling.maxStep", (long) DEFAULT_MAX_STEP));
        this.scalingUp   = false;
        this.scalingDown = false;

    }

    @Override
    public void onServerRegister(RegisteredServer server) {
        if (scalingUp) {
            logger.info("New server registered, enabling scaling again");
            scalingUp = false;
            plugin.setScalingUp(scalingUp);
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

        metrics = plugin.getMetricReporter();

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

        // Scaling criteria
        // scale down server if all servers with quality < qualityT * (1 - scaleDownRatio)
        // scale up server if enough servers with quality > qualityT * (1 + scaleUpRatio)

        Collection<ServerWithData> serversWD = allServers
            .stream()
            .map(server -> {
                String serverName = server.getServerInfo().getName();
                Metrics wmetrics = metricsMap.get(serverName);
                if (wmetrics == null) {
                    logger.warn("Metrics not found for server {}", serverName);
                    return new ServerWithData(
                        false, // Comparar qualitat amb el llindar
                        server,
                        0,
                        0,
                        0,
                        0);
                } else {
                    return new ServerWithData(
                        wmetrics.getQuality() >= qualityT * (1 + scaleUpRatio), // Comparar qualitat amb el llindar i donar un marge
                        server,
                        wmetrics.getPlayers(),
                        wmetrics.getMspt(),
                        wmetrics.getQuality(),
                        wmetrics.getChunks());
                }
            })
            .collect(Collectors.toList());

        Map<Boolean, List<ServerWithData>> partitionedServers = serversWD
            .stream()
            .collect(Collectors.partitioningBy(server -> server.getPerf()));
        ServerWithData[] serversBad = partitionedServers.get(true).toArray(new ServerWithData[0]);
        long counterBad = serversBad.length;

        // Now to consider scale up servers
        logger.info("Servers with degraded tick time: {}", counterBad);
        redServers      = (long) Math.round(red * (double) allServers.size());
        logger.info("Required servers for scale up: {}", redServers);
        logger.info("Scaling up if quality > {}", (int) Math.round(qualityT * (1 + scaleUpRatio)));

        // From here, we have servers with degraded tick time and enough servers to migrate players
        // if too many servers are degraded, no need to migrate, should be scale up from scaling manager

        int count = allServers.size();
        logger.info("Total servers active: {}", count);
        if (counterBad < redServers) {
            logger.info("No scale up needed now");
        } else if (scalingUp == false  && counterBad != 0) {
            // scaling only if there are not previous operations in place
            if (count < maxServers) {
                scalingUp = true;
                plugin.setScalingUp(scalingUp);
                if (dynamic && redServers > 0) {
                    // To speed up scaling
                    if (redServers < minStep) redServers = minStep;
                    // To limit the nº of servers to be created
                    if (redServers > maxStep) redServers = maxStep;
                    logger.info("Scaling up {} server/s", redServers);
                    plugin.getScalingManager().scaleUp((int) redServers);
                } else {
                    logger.info("Scaling up 1 server");
                    plugin.getScalingManager().scaleUp();
                }
                return;
            }
        }

        // don't scale down if there is an scale up operation in progress
        if (scalingUp) {
            logger.info("Scale up in progress, scale down not possible !!");
            return;
        }

        // don't scale down if there is only a minimal number of servers or scale up in progress
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
