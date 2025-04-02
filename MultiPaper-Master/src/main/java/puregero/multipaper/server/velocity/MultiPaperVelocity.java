package puregero.multipaper.server.velocity;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.slf4j.Logger;
import puregero.multipaper.server.MultiPaperServer;
import puregero.multipaper.server.ServerConnection;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

@Plugin(id = "multipaper-velocity",
    name = "MultiPaper Velocity",
    version = "1.0.0",
    authors = { "PureGero" }
)
public class MultiPaperVelocity {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataFolder;

    private int port;
    private boolean balanceNodes;

    private DrainStrategy drainStrategy = DrainStrategy.defaultDrainStrategy;
    private ServerSelectionStrategy serverSelectionStrategy = ServerSelectionStrategy.lowestTickTime;
    private final ScalingManager scalingManager;

    @Inject
    public MultiPaperVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataFolder) {
        this.server = server;
        this.logger = logger;
        this.dataFolder = dataFolder;

        this.scalingManager = new ScalingManager(logger);
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        Toml config = this.getConfig();

        this.port = Math.toIntExact(config.getLong("port", Long.valueOf(MultiPaperServer.DEFAULT_PORT)));
        this.balanceNodes = config.getBoolean("balance-nodes", true);

        new MultiPaperServer(this.port);

        server.getAllServers().forEach(s -> {
            server.unregisterServer(s.getServerInfo());
        });

//       server.getScheduler().buildTask(this, () -> {
//            scalingManager.deletePod(server.getAllServers().stream().findAny().get().getServerInfo().getName());
//       }).repeat(10, TimeUnit.SECONDS).schedule();

        new DrainServer(logger, 8080, this::executeDrainStrategy);

        ServerConnection.addListener(new ServerConnection.Listener() {
            @Override
            public void onConnect(ServerConnection.ServerConnectionInfo connection) {
                server.registerServer(
                        new ServerInfo(connection.name(), new InetSocketAddress(connection.host(), connection.port()))
                );
                logger.info("Registered server {}", connection.name());
            }

            @Override
            public void onDisconnect(ServerConnection.ServerConnectionInfo connection) {
                server.unregisterServer(
                        new ServerInfo(connection.name(), new InetSocketAddress(connection.host(), connection.port()))
                );
                logger.info("Unregistered server {}", connection.name());
            }
        });
    }

    public void transferPlayer(Player player, RegisteredServer to, int maxRetries) {
        logger.info("Transferring player {} from server {} to server {}",
                player.getUsername(),
                player.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse("null"),
                to.getServerInfo().getName()
        );
        disconnect(player);
        player.createConnectionRequest(to).connect().whenComplete((result, throwable) -> {
            handleTransfer(player, to, result, throwable, maxRetries, 1);
        });
    }

    private void handleTransfer(Player player, RegisteredServer server, ConnectionRequestBuilder.Result result,
                                Throwable throwable, int retries, long retryDelay) {
        if (result.isSuccessful()) {
            logger.info("Transferred player {} to server {} successfully",
                    player.getUsername(),
                    server.getServerInfo().getName()
            );
            return;
        }

        logger.error("Failed to transfer player {} to server {}. Remaining retries: {}. " +
                        "Status: {}. Reason: {}, Exception: {}.",
                player.getUsername(),
                server.getServerInfo().getName(),
                retries,
                result.getStatus(),
                result.getReasonComponent().map(Object::toString).orElse("null"),
                throwable
        );

        if (retries <= 0) {
            logger.error("Failed to transfer player {} to server {} after {} retries",
                    player.getUsername(),
                    server.getServerInfo().getName(),
                    retries
            );
            return;
        }

        logger.info("Retrying transfer of player {} to server {} in {} seconds",
                player.getUsername(),
                server.getServerInfo().getName(),
                retryDelay * 2
        );

        this.server.getScheduler()
                .buildTask(this, () -> player
                        .createConnectionRequest(server)
                        .connect()
                        .whenComplete((r, t) -> handleTransfer(player, server, r, t, retries - 1, retryDelay * 2)))
                .delay(retryDelay * 2, TimeUnit.SECONDS)
                .schedule();
    }

    private void disconnect(Player player) {
        try {
            Object connectedServer = player.getClass().getMethod("getConnectedServer").invoke(player);
            if (connectedServer != null)
                connectedServer.getClass().getMethod("disconnect").invoke(connectedServer);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @Subscribe
    public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent event) {
        event.setInitialServer(server.getAllServers().stream().findAny().orElse(null));
    }

    @Subscribe
    public void onServerConnect(ServerPreConnectEvent event) {
        if (event.getPlayer().getCurrentServer().isPresent()) {
            logger.info("Player is transferring from server {} to server {}, not interfering",
                    event.getPlayer().getCurrentServer().get().getServerInfo().getName(),
                    event.getResult().getServer().get().getServerInfo().getName()
            );
            return;
        }

        RegisteredServer targetServer = event.getResult().getServer().get();
        RegisteredServer bestServer = null;

        if (this.balanceNodes && isMultiPaperServer(targetServer.getServerInfo().getName())) {
            Collection<RegisteredServer> servers = this.server.getAllServers();

            bestServer = serverSelectionStrategy.selectServer(servers, event.getPlayer());

            if (bestServer != null) {
                event.setResult(ServerPreConnectEvent.ServerResult.allowed(bestServer));
            }
        }

        logger.info("Found best server. Best server: {}, player: {}",
                bestServer != null ? bestServer.getServerInfo().getName() : "null",
                event.getPlayer().getUsername()
        );
    }

    private boolean isMultiPaperServer(String name) {
        return ServerConnection.getConnection(name) != null;
    }

    private Toml getConfig() {
        File dataFolder = this.dataFolder.toFile();
        if (!dataFolder.exists()) {
            dataFolder.mkdir();
        }

        File file = new File(dataFolder, "config.toml");

        if (!file.exists()) {
            try (InputStream in = getClass().getClassLoader()
                    .getResourceAsStream("config.toml")) {
                Files.copy(in, file.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return new Toml().read(file);
    }

    public ProxyServer getProxy() {
        return server;
    }

    public Logger getLogger() {
        return logger;
    }

    public void setDrainStrategy(DrainStrategy drainStrategy) {
        this.drainStrategy = drainStrategy;
    }

    public void setServerSelectionStrategy(ServerSelectionStrategy serverSelectionStrategy) {
        this.serverSelectionStrategy = serverSelectionStrategy;
    }

    public ServerSelectionStrategy getServerSelectionStrategy() {
        return serverSelectionStrategy;
    }

    public boolean executeDrainStrategy(String serverName) {
        Preconditions.checkNotNull(this.drainStrategy, "Drain strategy is not set");
        return this.drainStrategy.drain(serverName, this);
    }
}
