package puregero.multipaper.server.velocity.metric;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.velocitypowered.api.proxy.server.RegisteredServer;

import io.prometheus.metrics.core.metrics.Gauge;
//import io.prometheus.metrics.exporter.httpserver.HTTPServer;
//import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;
import puregero.multipaper.server.ServerConnection;
import puregero.multipaper.server.velocity.BaseStrategy;
import puregero.multipaper.server.velocity.MultiPaperVelocity;
import puregero.multipaper.server.velocity.ServerWithData;

public class MetricReporter extends BaseStrategy {

    private static final int DEFAULT_MSPT_HIGH = 40;
    private static final int DEFAULT_IDEAL_PLAYERS = 40;
    private static final int DEFAULT_CHUNKS_PLAYER = 256;

    private static final String sep = "--------------------------------------------";

    private int msptHigh;
    private double timeW;
    private double playerW;
    private long chunksxPlayer;
    private double chunksW;
    private long idealPlayers;
    private double qualityT;
    private int level;

    private int logFreq;
    private int logCount;

    private Collection<Metrics> metrics;
    // private Gauge serverQualityGauge;
    // private Gauge serverMSPTGauge;
    // private Gauge serverPlayersGauge;
    // private Gauge serverChunksGauge;
    // private Set<String> previousServers = new HashSet<>();
    // private Set<String> toRemoveServers = new HashSet<>();

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

        this.msptHigh      = Math.toIntExact(config.getLong("performance.tick_length.high", (long) DEFAULT_MSPT_HIGH));
        this.timeW         = config.getDouble("quality.timeW", 5.0);
        this.playerW       = config.getDouble("quality.playerW", 1.0);
        this.chunksxPlayer = config.getLong("quality.chunksxPlayer", (long) DEFAULT_CHUNKS_PLAYER);
        this.chunksW       = config.getDouble("quality.chunksW", 2.0);
        this.idealPlayers  = config.getLong("quality.idealPlayers", (long) DEFAULT_IDEAL_PLAYERS);

        this.level         = Math.toIntExact(config.getLong("metric.level", (long) 1));
        long wint          = config.getLong("metric.interval", (long) 0);

        if (wint < 5) {
            logFreq = 3;
        } else {
            logFreq = 1;
        }
        logCount = 0;

        // Quality threshold basement: mspt, players
        //this.qualityT = msptHigh * this.timeW + idealPlayers * this.playerW;
        // Quality threshold basement: mspt, players
        this.qualityT = msptHigh * timeW + idealPlayers * playerW + idealPlayers * chunksxPlayer * chunksW;
        logger.info("Threshold for degraded performance > {}", Math.round(qualityT));

        logger.info("Read params {} {} {} {} {}", msptHigh, timeW, playerW, chunksW, idealPlayers);

        // JvmMetrics.builder().register();
        // if (level >= 1) {
        //     serverQualityGauge = Gauge.builder()
        //         .name("velocity_server_quality")
        //         .help("Quality metric for each server based on MSPT and player count")
        //         .labelNames("server_name")
        //         .register();

        //     if ( level >= 2) {
        //         serverMSPTGauge = Gauge.builder()
        //             .name("velocity_server_mspt")
        //             .help("MSPT metric for each server")
        //             .labelNames("server_name")
        //             .register();

        //         serverPlayersGauge = Gauge.builder()
        //             .name("velocity_server_players")
        //             .help("Players count for each server")
        //             .labelNames("server_name")
        //             .register();

        //         serverChunksGauge = Gauge.builder()
        //             .name("velocity_server_chunks")
        //             .help("Owned chunks count for each server")
        //             .labelNames("server_name")
        //             .register();
        //     }

        //     try {
        //         HTTPServer server = HTTPServer.builder()
        //                 .port(9400)
        //                 .buildAndStart();
        //         logger.info("HTTPServer listening on port http://localhost:{}/metrics", server.getPort());
        //     } catch (IOException e) {
        //         logger.error("Failed to start Prometheus HTTP server: {}", e.getMessage());
        //     }
        // }
    }

    @Override
    public void executeStrategy() {

        // to not overload log...
        boolean logShow = false;
        logCount += 1;
        if (logCount == logFreq) {
            logCount = 0;
            logShow  = true;
        }

        //logger.info(sep);

        // Obtenir tots els servidors i filtrar els actius
        Collection<RegisteredServer> allServers = plugin
                .getProxy()
                .getAllServers();

        // Prometheus i grafana veuen el darrer valor si un server s'ha eliminat però no s'han
        // eliminat els gauges, per tant mostren línies planes per servers que ja no hi son
        // Primer es posa a zero el darrer valor i a la següent crida s'elimina el gauge

        // Si el servidor s'ha posat a zero, ha d'estar a la llista per eliminar els gauges
        // if (level >= 1) {
        //     for (String serverName : toRemoveServers) {
        //         serverQualityGauge.remove(serverName);
        //         if (level >= 2) {
        //             serverMSPTGauge.remove(serverName);
        //             serverPlayersGauge.remove(serverName);
        //             serverChunksGauge.remove(serverName);
        //         }
        //         logger.info("Removed metric gauges for server: {}", serverName);
        //     }
        //     toRemoveServers.clear();
        // }

        // at startup time there are no registered servers...
        if (allServers.size() == 0) {
            if (logShow) logger.info("Waiting for servers before repoprting metrics");
            return;
        }

        Set<String> currentServers = allServers.stream()
            .map(server -> server.getServerInfo().getName())
            .collect(Collectors.toSet());

        // Posar a zero mètriques dels servidors que ja no existeixen
        // if (level >= 1) {
        //     for (String serverName : previousServers) {
        //         if (!currentServers.contains(serverName)) {
        //             logger.info("Set to zero metrics for server: {}", serverName);
        //             serverQualityGauge.labelValues(serverName).set(0);
        //             if (level >= 2) {
        //                 serverMSPTGauge.labelValues(serverName).set(0);
        //                 serverPlayersGauge.labelValues(serverName).set(0);
        //                 serverChunksGauge.labelValues(serverName).set(0);
        //             }
        //             toRemoveServers.add(serverName);
        //         }
        //     }
        // }

        // Actualitzar la llista de servidors anteriors
        // previousServers.clear();
        // previousServers.addAll(currentServers);

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

        // double avgChunks = serversWD.stream()
        //     .mapToLong(ServerWithData::getChunks)
        //     .average()
        //     .orElse(0.0);

        metrics = serversWD
            .stream()
            .map(serverX -> new Metrics(
                serverX.getServer().getServerInfo().getName(),
                ServerConnection.getConnection(serverX.getServer().getServerInfo().getName()).getTimer().averageInMillis(),
                ServerConnection.getConnection(serverX.getServer().getServerInfo().getName()).getTimer().averageInMillis() * this.timeW + serverX.getPlayers() * this.playerW + this.chunksW * serverX.getChunks(),
                serverX.getPlayers(), 
                serverX.getChunks()))
            .collect(Collectors.toList());

        if (logShow) logger.info("{} active servers, metrics level {}", metrics.size(), level);
        int pcount = 0;
        for (Metrics wmetrics: metrics){
            // if (level >= 1) {
            // serverQualityGauge.labelValues(wmetrics.getName()).set(wmetrics.getQuality());
            //     if (level >= 2) {
            //         serverMSPTGauge.labelValues(wmetrics.getName()).set(wmetrics.getMspt());
            //         serverPlayersGauge.labelValues(wmetrics.getName()).set(wmetrics.getPlayers());
            //         serverChunksGauge.labelValues(wmetrics.getName()).set(wmetrics.getChunks());
            //     }
            // }

            if (logShow) logger.info("{} {} mspt {} Q {} QT {} P {} OWNC",
            wmetrics.getName(),
            Math.round(wmetrics.getMspt()),
            Math.round(wmetrics.getQuality()), 
            Math.round(qualityT), 
            wmetrics.getPlayers(),
            Math.round(wmetrics.getChunks()));

            pcount += wmetrics.getPlayers();
        }
        if (logShow) logger.info("{} total players", pcount);

        if (logShow) logger.info(sep);
    }
    
}
