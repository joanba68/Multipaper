package puregero.multipaper.server.velocity;

import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.List;
import java.util.Random;

public interface DrainStrategy {
    boolean drain(String serverName, MultiPaperVelocity plugin);

    DrainStrategy defaultDrainStrategy = new DrainStrategy() {
        private final static Random random = new Random();

        @Override
        public boolean drain(String serverName, MultiPaperVelocity plugin) {
                RegisteredServer s = plugin.getProxy().getServer(serverName).orElse(null);
                if (s == null)
                    return false;

                plugin.getLogger().info("Draining server {}", s);

                return s.getPlayersConnected().stream()
                        .map(player -> {
                            List<RegisteredServer> servers = plugin.getProxy().getAllServers().stream()
                                    .filter(s2 -> !s2.equals(s))
                                    .filter(s2 -> !s2.equals(player.getCurrentServer()
                                            .map(ServerConnection::getServer).orElse(null)))
                                    .toList();
                            if (servers.isEmpty()) return false;
                            plugin.transferPlayer(player, servers.get(random.nextInt(servers.size())), 5);
                            return true;
                        })
                        .reduce(Boolean::logicalAnd)
                        .orElse(false);
        }
    };
}
