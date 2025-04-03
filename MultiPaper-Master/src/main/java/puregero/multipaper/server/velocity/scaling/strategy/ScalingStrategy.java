package puregero.multipaper.server.velocity.scaling.strategy;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import puregero.multipaper.server.velocity.MultiPaperVelocity;

public interface ScalingStrategy {
    void onStartup(MultiPaperVelocity plugin);

    void onPlayerConnect(Player player);

    void onPlayerDisconnect(Player player);

    void onServerRegister(RegisteredServer server);

    void onServerUnregister(RegisteredServer server);

    void performScaling();
}
