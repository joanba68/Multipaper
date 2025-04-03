package puregero.multipaper.server.velocity.scaling.strategy;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import puregero.multipaper.server.ServerConnection;

import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

public class TickLength extends Base {
    public static final int MSPT_HIGH = 40;
    public static final int MSPT_LOW = 10;

    public TickLength(Long interval, TimeUnit timeUnit) {
        super(interval, timeUnit);
    }

    @Override
    public void performScaling() {
        Collection<RegisteredServer> allServers = plugin
                .getProxy()
                .getAllServers();

        // if all servers are above the threshold, scale up
        boolean scaleUp = allServers
                .stream()
                .map(server -> ServerConnection
                        .getConnection(server.getServerInfo().getName())
                        .getTimer()
                        .averageInMillis() > MSPT_HIGH)
                .reduce(Boolean::logicalAnd)
                .orElse(false);
        if (scaleUp)
            plugin.getScalingManager().scaleUp();

        // if all servers are below the threshold, scale down
        boolean scaleDown = allServers
                .stream()
                .map(server -> ServerConnection
                        .getConnection(server.getServerInfo().getName())
                        .getTimer()
                        .averageInMillis() < MSPT_LOW)
                .reduce(Boolean::logicalAnd)
                .orElse(false);

        // delete the server with the lowest amount of players
        if (scaleDown) {
            allServers
                    .stream()
                    .min(Comparator.comparingInt(s -> s.getPlayersConnected().size()))
                    .ifPresent(server -> {
                        plugin.getScalingManager().deletePod(server.getServerInfo().getName());
                    });
        }
    }
}
