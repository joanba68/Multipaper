package puregero.multipaper.server.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

public interface Strategy {
    void onStartup(MultiPaperVelocity plugin);

    void onPlayerConnect(Player player);

    void onPlayerDisconnect(Player player);

    void onServerRegister(RegisteredServer server);

    void onServerUnregister(RegisteredServer server);

    void onShutdown();

    void executeStrategy();
}
