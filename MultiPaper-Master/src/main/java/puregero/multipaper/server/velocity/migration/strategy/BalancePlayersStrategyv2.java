package puregero.multipaper.server.velocity.migration.strategy;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.velocitypowered.api.proxy.server.RegisteredServer;

import puregero.multipaper.server.ServerConnection;
import puregero.multipaper.server.velocity.BaseStrategy;
import puregero.multipaper.server.velocity.MultiPaperVelocity;
import puregero.multipaper.server.velocity.ServerWithData;

public class BalancePlayersStrategyv2 extends BaseStrategy {

    private static final int DEFAULT_MSPT_HIGH = 40;
    private static final long DEFAULT_INTERVAL = 60;
    private static final String DEFAULT_UNIT_TIME = "SECONDS";
    private static final double DEFAULT_PLAYERS_TRANSFER = 0.2;
    private static final int DEFAULT_MIN_SERVERS_MIG = 5;
    private static final int DEFAULT_MAX_PLAYERS_TO_MOVE = 5;
    private static final int DEFAULT_IDEAL_PLAYERS = 40;

    private int msptHigh;
    private int minServers;
    private int maxPlayers;
    private double timeW;
    private double playerW;

    public BalancePlayersStrategyv2(Long interval, TimeUnit timeUnit) {
        super(interval, timeUnit);
    }

    @Override
    public void onStartup(MultiPaperVelocity plugin) {
        super.onStartup(plugin);
        this.msptHigh = Math.toIntExact(config.getLong("scaling.tick_length.high", (long) DEFAULT_MSPT_HIGH));
        this.interval = config.getLong("migration.interval", DEFAULT_INTERVAL);
        this.timeUnit = TimeUnit.valueOf(config.getString("migration.units", DEFAULT_UNIT_TIME));
        this.minServers  = Math.toIntExact(config.getLong("migration.minServers", (long) DEFAULT_MIN_SERVERS_MIG));
        this.maxPlayers  = Math.toIntExact(config.getLong("migration.maxPlayers", (long) DEFAULT_MAX_PLAYERS_TO_MOVE));
        this.timeW   = config.getDouble("migration.timeW", 5.0);
        this.playerW = config.getDouble("migration.playerW", 1.0);
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
            //return;
        }

        double qualityT = msptHigh * this.timeW + DEFAULT_IDEAL_PLAYERS * this.playerW;
        logger.info("Quality threshold is {}", Math.round(qualityT));

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

        for (ServerWithData serverX : serversWD){
            double mspt = ServerConnection.getConnection(serverX.getServer().getServerInfo().getName()).getTimer().averageInMillis();
            double quality = ServerConnection.getConnection(serverX.getServer().getServerInfo().getName()).getTimer().averageInMillis() * this.timeW + serverX.getPlayers() * this.playerW;
            logger.info("Server {} has {} players and {} mspt, degraded performance is {}, quality is {}", 
                serverX.getServer().getServerInfo().getName(), 
                serverX.getPlayers(), 
                Math.round(mspt),
                serverX.getPerf(),
                Math.round(quality));
        }

        // Calcular el nombre total de jugadors i l'ideal per servidor
        int totalPlayers = serversWD.stream().mapToInt(s -> s.getPlayers()).sum();
        if (totalPlayers == 0) {
            logger.info("Waiting for players...");
            return;
        }

        int idealPlayersPerServer = totalPlayers / Math.max(1, serversWD.size());
        logger.info("Ideal number of players per server is {}", idealPlayersPerServer);

        Optional<ServerWithData> bestServer = serversWD.stream()
                .filter(s -> !s.getPerf() && s.getPlayers() <= idealPlayersPerServer * (1 + DEFAULT_PLAYERS_TRANSFER))
                .min(Comparator.comparingDouble(s -> s.getMspt()));
        
        if (!bestServer.isPresent()) {
            logger.info("No healthy servers to transfer players found");
            return;
        }
        
        // Identificar servidor amb perf degradada i mes jugadors
        Optional<ServerWithData> worstServer = serversWD.stream()
                .filter(server -> server.getPerf())
                .max(Comparator.comparingInt(server -> server.getPlayers()));

        if (!worstServer.isPresent()) {
            logger.info("No degraded servers found");
            return;
        }
 
        ServerWithData worst = worstServer.get();
        ServerWithData best = bestServer.get();

        logger.info("Best server is {}", best.getServer().getServerInfo().getName());
        logger.info("Worst server is {}", worst.getServer().getServerInfo().getName());

        if (best.getServer().getServerInfo().getName() == worst.getServer().getServerInfo().getName()) {
            logger.info("No transfer possible as best and worst are the same !!");
            return;
        }
        
        // playersToMove can be negative = worst server has less players than ideal number
        // playersToMove can be zero but still very degraded, so tick time should drive the migration
        long playersToMove = Math.abs(worst.getPlayers() - idealPlayersPerServer);
        long maxPlayersToMove = Math.abs(Math.round(idealPlayersPerServer * (1 + DEFAULT_PLAYERS_TRANSFER) - best.getPlayers()));
        // Too much players being moved can lead to connection issues
        if (playersToMove == 0) playersToMove = Long.MAX_VALUE;
        if (maxPlayersToMove == 0) maxPlayersToMove = Long.MAX_VALUE;
        playersToMove = Math.min(Math.min(playersToMove, maxPlayersToMove), maxPlayers);

        logger.info("Trying to move {} players...", playersToMove);

        if (playersToMove > 0) {            
            worst.getServer().getPlayersConnected().stream()
                .limit(playersToMove)
                .forEach(player -> plugin.transferPlayer(player, best.getServer(), 3));
            logger.info("Transferring {} players to another server", playersToMove); 
        } else {
            logger.info("Not possible to transfer players to {}", best.getServer().getServerInfo().getName());
        }

        logger.info("Moved {} players from {} to {}",
                playersToMove,
                worst.getServer().getServerInfo().getName(),
                best.getServer().getServerInfo().getName());
     
    }

}
