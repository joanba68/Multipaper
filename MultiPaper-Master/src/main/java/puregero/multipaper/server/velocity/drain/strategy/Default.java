package puregero.multipaper.server.velocity.drain.strategy;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import puregero.multipaper.server.velocity.MultiPaperVelocity;

public class Default implements DrainStrategy {
    @Override
    public boolean drain(RegisteredServer server, MultiPaperVelocity plugin) {
        plugin.getLogger().info("Draining server {}", server.getServerInfo().getName());

        return server.getPlayersConnected()
                .stream()
                .map(player -> {
                    // delegate to the server selection strategy
                    RegisteredServer to = plugin.getServerSelectionStrategy().selectServer(player, plugin);
                    if (to == null) return false;
                    plugin.transferPlayer(player, to, 5);
                    return true;
                })
                .reduce(Boolean::logicalAnd)
                .orElse(false);
    };
}
