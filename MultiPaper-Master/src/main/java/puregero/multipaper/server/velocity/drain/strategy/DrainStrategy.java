package puregero.multipaper.server.velocity.drain.strategy;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import puregero.multipaper.server.velocity.MultiPaperVelocity;

public interface DrainStrategy {
    boolean drain(RegisteredServer server, MultiPaperVelocity plugin);
}
