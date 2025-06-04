package puregero.multipaper.server.velocity.migration.strategy;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
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

public class EasyStrategy extends BaseStrategy {

    private static final int DEFAULT_MSPT_HIGH = 40;
    private static final int DEFAULT_MSPT_LOW = 10;
    private static final double DEFAULT_RED_RATIO = 0.6;
    private static final long DEFAULT_INTERVAL = 60;
    private static final String DEFAULT_UNIT_TIME = "SECONDS";
    private static final double DEFAULT_PLAYERS_TRANSFER = 0.2;
    private static final int DEFAULT_MIN_SERVERS_MIG = 5;
    
    private int msptHigh;
    private int msptLow;
    private double red;
    private double playersT;
    private int minServers;

    public EasyStrategy(Long interval, TimeUnit timeUnit) {
        super(Long.MAX_VALUE, TimeUnit.DAYS);
    }

    @Override
    public void onStartup(MultiPaperVelocity plugin) {
        super.onStartup(plugin);
        this.msptHigh = Math.toIntExact(config.getLong("scaling.tick_length.high", (long) DEFAULT_MSPT_HIGH));
        this.msptLow  = Math.toIntExact(config.getLong("scaling.tick_length.low", (long) DEFAULT_MSPT_LOW));
        this.red      = config.getDouble("scaling.tick_length.redL", DEFAULT_RED_RATIO);
        
        this.playersT = config.getDouble("migration.playersT", DEFAULT_PLAYERS_TRANSFER);
        this.interval = config.getLong("migration.interval", DEFAULT_INTERVAL);
        this.timeUnit = TimeUnit.valueOf(config.getString("migration.units", DEFAULT_UNIT_TIME));
        this.minServers  = Math.toIntExact(config.getLong("migration.minServers", (long) DEFAULT_MIN_SERVERS_MIG));

        // plugin.getProxy().getScheduler().buildTask(plugin, this::executeStrategy)
        //     .repeat(interval, timeUnit)
        //     .schedule();
    }

    @Override
    public void executeStrategy() {

        long redServers;

        Collection<RegisteredServer> allServers = plugin
                .getProxy()
                .getAllServers();

        // at startup time there are no registered servers...
        if (allServers.size() == 0) {
            logger.info("Waiting for servers before starting migration strategy");
            return;
        }

        for (RegisteredServer serverX : allServers){
            logger.info("Server {} has {} players", serverX.getServerInfo().getName(), serverX.getPlayersConnected().size());
            logger.info("Server {} has {} mseg. response time", serverX.getServerInfo().getName(), String.format("%.2g", ServerConnection.getConnection(serverX.getServerInfo().getName()).getTimer().averageInMillis()));
            logger.info("Server {} has {} address", serverX.getServerInfo().getName(), serverX.getServerInfo().getAddress());
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
        ServerWithData[] serversOk  = partitionedServers.get(false).toArray(new ServerWithData[0]);

        long counterBad = serversBad.length;
        //long counterOk  = serversOk.length;

        // Now to consider migration of players

        logger.info("Servers with degraded tick time: {}", counterBad);

        redServers = (long) Math.round(red * (double) counterBad);
        
        // From here, we have servers with degraded tick time
        // if there is a low nº of servers, ex: 2 servers --> yellow = red = 1
        // we can set a rule: if nº of servers < 5 only scale up is an option
        if (allServers.size() < minServers) {
            logger.info("Not enough servers for player migrations");
            return;
        }

        // From here, we have servers with degraded tick time and enough servers to migrate players
        if (counterBad < redServers) {
            logger.info("Required players transfer");
            
            // how many players to transfer ??
            long playersToTransfer = 0;
            for (ServerWithData serverWD : serversBad){
                playersToTransfer = (long) Math.round(playersT * (double) serverWD.players);           
            
                // to which server ? to the one which is Ok with less players
                ServerWithData candidate = Arrays.stream(serversOk).min(Comparator.comparingInt(serverX -> serverX.players)).orElse(null);

                if (candidate != null) {
                    serverWD.server.getPlayersConnected().stream()
                        .limit(playersToTransfer)
                        .forEach(player -> plugin.transferPlayer(player, candidate.server, 5));
                    logger.info("Transferring {} players to another server", playersToTransfer); 
                } else {
                    logger.info("Not possible to transfer players to {}", candidate.server);
                }
            }
        } 
     
    }

}
