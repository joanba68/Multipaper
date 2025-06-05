package puregero.multipaper.server.velocity.migration.strategy;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;

import puregero.multipaper.server.ServerConnection;
import puregero.multipaper.server.velocity.BaseStrategy;
import puregero.multipaper.server.velocity.MultiPaperVelocity;
import puregero.multipaper.server.velocity.ServerWithData;

public class BalancePlayersStrategy extends BaseStrategy {

    private static final int DEFAULT_MSPT_HIGH = 40;
    private static final long DEFAULT_INTERVAL = 60;
    private static final String DEFAULT_UNIT_TIME = "SECONDS";
    private static final double DEFAULT_PLAYERS_TRANSFER = 0.2;
    private static final int DEFAULT_MIN_SERVERS_MIG = 5;

    private int msptHigh;
    private int minServers;

    public BalancePlayersStrategy(Long interval, TimeUnit timeUnit) {
        super(interval, timeUnit);
    }

    @Override
    public void onStartup(MultiPaperVelocity plugin) {
        super.onStartup(plugin);
        this.msptHigh = Math.toIntExact(config.getLong("scaling.tick_length.high", (long) DEFAULT_MSPT_HIGH));
        this.interval = config.getLong("migration.interval", DEFAULT_INTERVAL);
        this.timeUnit = TimeUnit.valueOf(config.getString("migration.units", DEFAULT_UNIT_TIME));
        this.minServers  = Math.toIntExact(config.getLong("migration.minServers", (long) DEFAULT_MIN_SERVERS_MIG));

    }

    @Override
    public void executeStrategy() {

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
            return;
        }

        for (RegisteredServer serverX : allServers){
            logger.info("Server {} has {} players", serverX.getServerInfo().getName(), serverX.getPlayersConnected().size());
            logger.info("Server {} has {} mseg. response time", serverX.getServerInfo().getName(), String.format("%.2g", ServerConnection.getConnection(serverX.getServerInfo().getName()).getTimer().averageInMillis()));
        }

        Collection<ServerWithData> serversWD = allServers
            .stream()
            .map(server -> new ServerWithData(
                ServerConnection.getConnection(server.getServerInfo().getName()).getTimer().averageInMillis() >= msptHigh,
                server,
                server.getPlayersConnected().size(),
                ServerConnection.getConnection(server.getServerInfo().getName()).getTimer().averageInMillis()))
            .collect(Collectors.toList());

        // Calcular el nombre total de jugadors i l'ideal per servidor
        int totalPlayers = serversWD.stream().mapToInt(s -> s.getPlayers()).sum();
        int idealPlayersPerServer = totalPlayers / Math.max(1, serversWD.size());

        Optional<ServerWithData> bestServer = serversWD.stream()
                .filter(s -> s.getPerf() && s.getPlayers() <= idealPlayersPerServer * DEFAULT_PLAYERS_TRANSFER)
                .min(Comparator.comparingDouble(s -> s.getMspt()));
        
        // if (!bestServer.isPresent()) {
        //     logger.info("No healthy servers to transfer players found");
        //     return;
        // }
        
        // Identificar servidor amb perf degradada i mes jugadors
        Optional<ServerWithData> worstServer = serversWD.stream()
                .filter(server -> !server.getPerf())
                .max(Comparator.comparingInt(server -> server.getPlayers()));

        // if (!worstServer.isPresent()) {
        //     logger.info("No degraded servers found");
        //     return;
        // }
        
        //logger.info("Server {} has {} players", serverX.getServerInfo().getName(), serverX.getPlayersConnected().size());

        ServerWithData worst = worstServer.get();
        ServerWithData best = bestServer.get();

        logger.info("Best server is {}", best.getServer().getServerInfo().getName());
        logger.info("Worst server is {}", worst.getServer().getServerInfo().getName());

        long playersToMove = worst.getPlayers() - idealPlayersPerServer;
        long maxPlayersToMove = Math.round(idealPlayersPerServer * (1 + DEFAULT_PLAYERS_TRANSFER) - best.getPlayers());
        playersToMove = Math.min(playersToMove, maxPlayersToMove);

        if (playersToMove > 0) {            
            worst.getServer().getPlayersConnected().stream()
                .limit(playersToMove)
                .forEach(player -> plugin.transferPlayer(player, best.getServer(), 5));
            logger.info("Transferring {} players to another server", playersToMove); 
        } else {
            logger.info("Not possible to transfer players to {}", best.getServer());
        }

        logger.info("Moved {} players from {} to {}",
                playersToMove,
                worst.getServer().getServerInfo().getName(),
                best.getServer().getServerInfo().getName());
     
    }

}
