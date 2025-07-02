package puregero.multipaper.server.velocity.metric;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.velocitypowered.api.proxy.server.RegisteredServer;

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
            return;
        }

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
