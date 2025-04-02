package puregero.multipaper.server.velocity;

import com.velocitypowered.api.proxy.server.RegisteredServer;

public interface DrainStrategy {
    boolean drain(String serverName, MultiPaperVelocity plugin);

    DrainStrategy defaultDrainStrategy = (serverName, plugin) -> {
            RegisteredServer s = plugin.getProxy().getServer(serverName).orElse(null);
            if (s == null)
                return false;

            plugin.getLogger().info("Draining server {}", s);

            return s.getPlayersConnected().stream()
                    .map(player -> {
                        // delegate to the server selection strategy
                        RegisteredServer to = plugin.getServerSelectionStrategy().selectServer(plugin.getProxy().getAllServers(), player);
                        if (to == null) return false;
                        plugin.transferPlayer(player, to, 5);
                        return true;
                    })
                    .reduce(Boolean::logicalAnd)
                    .orElse(false);
    };
}
