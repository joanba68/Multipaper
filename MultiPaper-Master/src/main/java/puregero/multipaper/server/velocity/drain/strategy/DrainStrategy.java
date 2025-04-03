package puregero.multipaper.server.velocity.drain.strategy;

import puregero.multipaper.server.velocity.MultiPaperVelocity;

public interface DrainStrategy {
    boolean drain(String serverName, MultiPaperVelocity plugin);
}
