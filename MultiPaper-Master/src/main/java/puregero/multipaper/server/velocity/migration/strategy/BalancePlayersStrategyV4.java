package puregero.multipaper.server.velocity.migration.strategy;

import java.util.Collection;
import java.util.Comparator;
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

public class BalancePlayersStrategyV4 extends BaseStrategy {

    private static final int DEFAULT_MSPT_HIGH = 40;
    private static final long DEFAULT_INTERVAL = 60;
    private static final String DEFAULT_UNIT_TIME = "SECONDS";
    private static final double DEFAULT_PLAYERS_TRANSFER = 0.2;
    private static final int DEFAULT_MIN_SERVERS_MIG = 5;
    private static final int DEFAULT_MAX_PLAYERS_TO_MOVE = 5;
    private static final double DEFAULT_SCALEUP_RATIO = 0.3;
    private static final double DEFAULT_SCALEDOWN_RATIO = 0.3;

    private int minServers;
    private int maxPlayers;
    private double scaleUpRatio;
    private double scaleDownRatio;

    private MetricReporter metrics;

    private boolean debug;

    public BalancePlayersStrategyV4(Long interval, TimeUnit timeUnit) {
        super(interval, timeUnit);
    }

    @Override
    public void onStartup(MultiPaperVelocity plugin) {
        super.onStartup(plugin);
        this.interval        = config.getLong("migration.interval", DEFAULT_INTERVAL);
        this.timeUnit        = TimeUnit.valueOf(config.getString("migration.units", DEFAULT_UNIT_TIME));
        this.minServers      = Math.toIntExact(config.getLong("migration.minServers", (long) DEFAULT_MIN_SERVERS_MIG));
        this.maxPlayers      = Math.toIntExact(config.getLong("migration.maxPlayers", (long) DEFAULT_MAX_PLAYERS_TO_MOVE));
        this.scaleUpRatio    = config.getDouble("scaling.scaleUpRatio", DEFAULT_SCALEUP_RATIO);
        this.scaleDownRatio  = config.getDouble("scaling.scaleDownRatio", DEFAULT_SCALEDOWN_RATIO);
        this.debug           = config.getBoolean("master.debug", false);
    }

    @Override
    public void executeStrategy() {

        metrics = plugin.getMetricReporter();

        double qualityT = metrics.getQualityT();

        Collection<RegisteredServer> allServers = plugin
                .getProxy()
                .getAllServers();

        // at startup time there are no registered servers...
        if (allServers.size() == 0) {
            logger.info("Waiting for servers before starting migration strategy");
            return;
        }

        if (allServers.size() < minServers) {
            logger.info("Not enough servers for player migrations");
            //return;
        }

        Map<String, Metrics> metricsMap = metrics.getMetrics().stream()
            .collect(Collectors.toMap(Metrics::getName, Function.identity()));

        Collection<ServerWithData> serversWD = allServers
            .stream()
            .map(server -> {
                String serverName = server.getServerInfo().getName();
                Metrics wmetrics = metricsMap.get(serverName);
                if (wmetrics == null) {
                    logger.warn("No s'han trobat mètriques per al servidor: {}", serverName);
                    return new ServerWithData(
                        false, // Comparar qualitat amb el llindar
                        server,
                        0,
                        0,
                        0,
                        0
                    );
                } else {
                    return new ServerWithData(
                        wmetrics.getQuality() >= qualityT, // Comparar qualitat amb el llindar
                        server,
                        wmetrics.getPlayers(),
                        wmetrics.getMspt(),
                        wmetrics.getQuality(),
                        wmetrics.getChunks());
                }
            })
            .collect(Collectors.toList());

        // Calcular el nombre total de jugadors i l'ideal per servidor
        int totalPlayers = serversWD.stream().mapToInt(s -> s.getPlayers()).sum();
        if (totalPlayers == 0) {
            logger.info("Waiting for players...");
            return;
        }

        int idealPlayersPerServer = totalPlayers / Math.max(1, serversWD.size());
        logger.info("Avg number of players per server is {}", idealPlayersPerServer);

        record PlayersCount(ServerWithData server, int playerCount) {}

        Collection<PlayersCount> bestCounts = serversWD.stream()
                .filter(server -> !server.getPerf() && server.getQuality() < qualityT * (1 - scaleDownRatio))
                .map(server -> new PlayersCount(server, (int) Math.abs(idealPlayersPerServer - server.getPlayers())))
                .collect(Collectors.toList());
        
        if (bestCounts.size() == 0) {
            logger.info("No healthy servers to transfer players found");
            return;
        }

        // Identify worst servers and count players to transfer
        Collection<PlayersCount> worstCounts = serversWD.stream()
            .filter(server -> server.getPerf() && server.getQuality() > qualityT * (1 + scaleUpRatio))
            .map(server -> new PlayersCount(server, (int) Math.abs(idealPlayersPerServer - server.getPlayers())))
            .collect(Collectors.toList());

        if (worstCounts.size() == 0) {
            logger.info("No degraded servers found");
            return;
        }

        // Migration loop
        worstCounts.forEach(wcount -> {
            PlayersCount bcount = bestCounts.stream()
                .min(Comparator.comparingDouble(count -> count.server().getPlayers()))
                .orElse(null);
            if (debug) {
                logger.info("trying to move {} players from {}", wcount.playerCount(), wcount.server().getServer().getServerInfo().getName());
                logger.info("space for {} players in {}", bcount.playerCount(), bcount.server().getServer().getServerInfo().getName());
            }
            if (wcount.server().getServer().equals(bcount.server().getServer())){
                logger.info("No transfer possible as best and worst are the same !!");
            } else {
                int playersToMove = Math.min(wcount.playerCount(), bcount.playerCount());
                if (playersToMove > 0) {            
                    wcount.server().getServer().getPlayersConnected().stream()
                        .limit(playersToMove)
                        .forEach(player -> plugin.transferPlayer(player, bcount.server().getServer(), 3));
                    logger.info("Moved {} players from {} to {}", playersToMove, 
                        wcount.server().getServer().getServerInfo().getName(),
                        bcount.server().getServer().getServerInfo().getName());
                } else {
                    logger.info("Not possible to transfer {} players to {}", playersToMove, bcount.server().getServer().getServerInfo().getName());
                }
            }
        });    
    }

}
