package puregero.multipaper.server.velocity.serverselection.strategy;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.Collection;

public interface ServerSelectionStrategy {
    RegisteredServer selectServer(Collection<RegisteredServer> servers, Player player);
}
