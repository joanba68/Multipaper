package puregero.multipaper.server.velocity.serverselection.strategy;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import puregero.multipaper.server.velocity.MultiPaperVelocity;

import java.util.Collection;

public class Random implements ServerSelectionStrategy {
    private final java.util.Random random = new java.util.Random();

    @Override
    public RegisteredServer selectServer(Player player, MultiPaperVelocity plugin) {
        Collection<RegisteredServer> servers = plugin.getProxy().getAllServers();
        return servers.stream().toList().get(random.nextInt(servers.size()));
    }
}
