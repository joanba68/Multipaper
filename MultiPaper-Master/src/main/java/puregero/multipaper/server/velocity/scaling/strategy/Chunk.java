package puregero.multipaper.server.velocity.scaling.strategy;

import puregero.multipaper.server.ServerConnection;
import puregero.multipaper.server.velocity.BaseStrategy;

import java.util.concurrent.TimeUnit;

public class Chunk extends BaseStrategy {
    public Chunk(Long interval, TimeUnit timeUnit) {
        super(interval, timeUnit);
    }

    @Override
    public void executeStrategy() {
        plugin.getProxy().getAllServers().forEach(server -> {
            ServerConnection connection = ServerConnection.getConnection(server.getServerInfo().getName());
            logger.info("Server {} owns {} chunks", server.getServerInfo().getName(), connection.getOwnedChunks());
        });
    }
}
