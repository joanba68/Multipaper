package puregero.multipaper.server.velocity.metric;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.velocitypowered.api.proxy.server.RegisteredServer;

import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;
import puregero.multipaper.server.ServerConnection;
import puregero.multipaper.server.velocity.BaseStrategy;
import puregero.multipaper.server.velocity.MultiPaperVelocity;
import puregero.multipaper.server.velocity.ServerWithData;

public class MetricReporter extends BaseStrategy {

    private static final int DEFAULT_MSPT_HIGH = 40;
    private static final int DEFAULT_IDEAL_PLAYERS = 40;
    
    //private static final String sep = "++++++++++++++++++++++++++++METRICS++++++++++++++++++++++++++++";

    private int msptHigh;
    private double timeW;
    private double playerW;
    private double qualityT;

    private Collection<Metrics> metrics;
    private Gauge serverQualityGauge;
    private Gauge serverMSPTGauge;
    private Gauge serverPlayersGauge;
    private Gauge serverChunksGauge;
    private Set<String> previousServers = new HashSet<>();

    public MetricReporter(Long interval, TimeUnit timeUnit) {
        super(interval, timeUnit);
    }

    public Collection<Metrics> getMetrics() {
        return this.metrics;
    }

    public double getQualityT() {
        return qualityT;
    }

    @Override
    public void onStartup(MultiPaperVelocity plugin) {
        super.onStartup(plugin);

        this.msptHigh = Math.toIntExact(config.getLong("performance.tick_length.high", (long) DEFAULT_MSPT_HIGH));
        this.timeW   = config.getDouble("defaults.timeW", 5.0);
        this.playerW = config.getDouble("defaults.playerW", 1.0);
        this.qualityT = msptHigh * this.timeW + DEFAULT_IDEAL_PLAYERS * this.playerW;

        logger.info("Threshold for degraded performance > {}", Math.round(qualityT));

        JvmMetrics.builder().register();

        serverQualityGauge = Gauge.builder()
            .name("velocity_server_quality")
            .help("Quality metric for each server based on MSPT and player count")
            .labelNames("server_name")
            .register();

        serverMSPTGauge = Gauge.builder()
            .name("velocity_server_mspt")
            .help("MSPT metric for each server")
            .labelNames("server_name")
            .register();

        serverPlayersGauge = Gauge.builder()
            .name("velocity_server_players")
            .help("Players count for each server")
            .labelNames("server_name")
            .register();

        serverChunksGauge = Gauge.builder()
            .name("velocity_server_chunks")
            .help("Owned chunks count for each server")
            .labelNames("server_name")
            .register();

        try {
            HTTPServer server = HTTPServer.builder()
                    .port(9400)
                    .buildAndStart();
            logger.info("HTTPServer listening on port http://localhost:{}/metrics", server.getPort());
        } catch (IOException e) {
            logger.error("Failed to start Prometheus HTTP server: {}", e.getMessage());
        }

    }

    @Override
    public void executeStrategy() {
        //logger.info(sep);

        // Obtenir tots els servidors i filtrar els actius
        Collection<RegisteredServer> allServers = plugin
                .getProxy()
                .getAllServers();

        // at startup time there are no registered servers...
        if (allServers.size() == 0) {
            logger.info("Waiting for servers before repoprting metrics");
            //logger.info(sep);
            if (previousServers.size() != 0) {
                for (String serverName : previousServers) {
                    serverQualityGauge.remove(serverName);
                    serverMSPTGauge.remove(serverName);
                    serverPlayersGauge.remove(serverName);
                    serverChunksGauge.remove(serverName);
                }
                previousServers.clear();
            }   
            return;
        }

        Set<String> currentServers = allServers.stream()
            .map(server -> server.getServerInfo().getName())
            .collect(Collectors.toSet());

        // Eliminar mètriques dels servidors que ja no existeixen
        for (String serverName : previousServers) {
            if (!currentServers.contains(serverName)) {
                logger.info("Removing metrics for deleted server: {}", serverName);
                serverQualityGauge.remove(serverName);
                serverMSPTGauge.remove(serverName);
                serverPlayersGauge.remove(serverName);
                serverChunksGauge.remove(serverName);
            }
        }

        // Actualitzar la llista de servidors anteriors
        previousServers.clear();
        previousServers.addAll(currentServers);

        Collection<ServerWithData> serversWD = allServers
            .stream()
            .map(server -> new ServerWithData(
                ServerConnection.getConnection(server.getServerInfo().getName()).getTimer().averageInMillis() * this.timeW + server.getPlayersConnected().size() * this.playerW >= qualityT,
                server,
                server.getPlayersConnected().size(),
                ServerConnection.getConnection(server.getServerInfo().getName()).getTimer().averageInMillis(),
                ServerConnection.getConnection(server.getServerInfo().getName()).getTimer().averageInMillis() * this.timeW + server.getPlayersConnected().size() * this.playerW,
                ServerConnection.getConnection(server.getServerInfo().getName()).getOwnedChunks()))
            .collect(Collectors.toList());

        metrics = serversWD
            .stream()
            .map(serverX -> new Metrics(
                serverX.getServer().getServerInfo().getName(),
                ServerConnection.getConnection(serverX.getServer().getServerInfo().getName()).getTimer().averageInMillis(),
                ServerConnection.getConnection(serverX.getServer().getServerInfo().getName()).getTimer().averageInMillis() * this.timeW + serverX.getPlayers() * this.playerW,
                serverX.getPlayers(), 
                serverX.getChunks()))
            .collect(Collectors.toList());

        for (Metrics wmetrics: metrics){
            serverQualityGauge.labelValues(wmetrics.getName()).set(wmetrics.getQuality());
            serverMSPTGauge.labelValues(wmetrics.getName()).set(wmetrics.getMspt());
            serverPlayersGauge.labelValues(wmetrics.getName()).set(wmetrics.getPlayers());
            serverChunksGauge.labelValues(wmetrics.getName()).set(wmetrics.getChunks());
            logger.info("{} {} mspt {} Q {} QT {} P {} OWNC. degraded perf. is {}",
            wmetrics.getName(),
            Math.round(wmetrics.getMspt()),
            Math.round(wmetrics.getQuality()), 
            Math.round(qualityT), 
            wmetrics.getPlayers(),
            Math.round(wmetrics.getChunks()),
            wmetrics.getQuality() > qualityT);
        }

        //logger.info(sep);
    }
    
}
