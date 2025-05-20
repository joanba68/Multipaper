package puregero.multipaper.server.velocity.migration.strategy;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import puregero.multipaper.server.ServerConnection;
import puregero.multipaper.server.velocity.BaseStrategy;
import puregero.multipaper.server.velocity.MultiPaperVelocity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TickPlayerRatio extends BaseStrategy {

    private static final int DEFAULT_MSPT_THRESHOLD = 30;
    private static final int DEFAULT_EMPTY_CAPACITY = 20;

    private int threshold;

    public TickPlayerRatio(Long interval, TimeUnit timeUnit) {
        super(interval, timeUnit);
    }

    @Override
    public void onStartup(MultiPaperVelocity plugin) {
        super.onStartup(plugin);
        this.threshold = Math.toIntExact(config.getLong("migration.tick_player_ratio.threshold", (long) DEFAULT_MSPT_THRESHOLD));
    }

    @Override
    public void executeStrategy() {
        Collection<RegisteredServer> allServers = plugin
                .getProxy()
                .getAllServers();

        // get servers mspt
        Map<RegisteredServer, Double> serverMspt = allServers
                .stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        server -> ServerConnection
                                .getConnection(server.getServerInfo().getName())
                                .getTimer()
                                .averageInMillis()
                ));

        // find overloaded servers
        List<RegisteredServer> overloadedServers = serverMspt
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > threshold)
                .map(Map.Entry::getKey)
                .toList();

        // if there are no overloaded servers, do nothing
        if (overloadedServers.isEmpty()) {
            logger.info("No overloaded servers");
            return;
        }

        // find non-overloaded servers
        List<RegisteredServer> nonOverloadedServers = allServers
                .stream()
                .filter(server -> !overloadedServers.contains(server))
                .toList();

        // if there are no non-overloaded servers, do nothing
        if (nonOverloadedServers.isEmpty()) {
            logger.info("No non-overloaded servers");
            return;
        }

        // find ratio MSPT / player count for each server
        Map<RegisteredServer, Double> serverRatios = serverMspt
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            RegisteredServer server = entry.getKey();
                            int playerCount = server.getPlayersConnected().size();
                            return entry.getValue() / playerCount;
                        }
                ));

        // find how many players to move from each overloaded server
        Map<RegisteredServer, Integer> playersToMove = overloadedServers
                .stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        server -> {
                            double mspt = serverMspt.get(server);
                            double ratio = serverRatios.get(server);
                            return (int) Math.ceil((mspt - threshold) / ratio);
                        }
                ));

        // find how many players can be moved to each non-overloaded server
        Map<RegisteredServer, Integer> capacity = nonOverloadedServers
                .stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        server -> {
                            double mspt = serverMspt.get(server);
                            double ratio = serverRatios.get(server);
                            return ratio == Double.POSITIVE_INFINITY ?
                                    DEFAULT_EMPTY_CAPACITY : (int) Math.max(0, (threshold - mspt) / ratio);
                        }
                ));

        int totalCapacity = capacity
                .values()
                .stream()
                .reduce(0, Integer::sum);

        logger.info("Total server capacity: {}", totalCapacity);

        if (totalCapacity == 0) {
            logger.info("No capacity to move players");
            return;
        }

        // now we need to equally distribute the players from the overloaded servers to the non-overloaded servers
        overloadedServers.forEach(srcServer -> {
            int needed = playersToMove.get(srcServer);
            for (RegisteredServer dstServer : nonOverloadedServers) {
                int cap = capacity.get(dstServer);
                int move = (int) Math.floor((double) needed * cap / totalCapacity);
                if (move > 0) {
                    // move the players
                    logger.info(
                            "Moving {} players from {} to {}",
                            move,
                            srcServer.getServerInfo().getName(),
                            dstServer.getServerInfo().getName()
                    );
                    List<Player> players = srcServer.getPlayersConnected()
                            .stream()
                            .limit(move)
                            .toList();
                    players.forEach(player -> plugin.transferPlayer(player, dstServer, 3));
                    needed -= move;
                }
            }
            // if there are still players left, move them to the first non-overloaded server
            if (needed > 0) {
                List<Player> players = srcServer.getPlayersConnected()
                        .stream()
                        .limit(needed)
                        .toList();
                players.forEach(player -> plugin
                        .transferPlayer(
                                player,
                                plugin.getServerSelectionStrategy().selectServer(player, plugin),
                                3
                        ));
            }
        });
    }

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }
}
