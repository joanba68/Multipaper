package puregero.multipaper.server.velocity;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
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
import puregero.multipaper.server.velocity.drain.DrainServer;
import puregero.multipaper.server.velocity.drain.strategy.DrainStrategy;
import puregero.multipaper.server.velocity.scaling.ScalingManager;
import puregero.multipaper.server.velocity.serverselection.strategy.ServerSelectionStrategy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private DrainStrategy drainStrategy;
    private ServerSelectionStrategy serverSelectionStrategy;

    private final StrategyManager strategyManager;
    private final ScalingManager scalingManager;

    private Toml config;

    @Inject
    public MultiPaperVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataFolder) {
        this.server = server;
        this.logger = logger;
        this.dataFolder = dataFolder;

        this.scalingManager = new ScalingManager(logger);
        this.strategyManager = new StrategyManager(logger);
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        config = this.readConfig();

        this.port = Math.toIntExact(config.getLong("master.port", (long) MultiPaperServer.DEFAULT_PORT));
        new MultiPaperServer(this.port);

        server.getAllServers().forEach(s -> server.unregisterServer(s.getServerInfo()));

        this.balanceNodes = config.getBoolean("server-selection.enabled", true);
        serverSelectionStrategy = strategyManager.loadStrategy(
                "serverselection.strategy.",
                config.getString("server-selection.strategy", "lowest_tick_time"),
                ServerSelectionStrategy.class
        );

        boolean drainServerEnabled = config.getBoolean("drain-server.enabled", true);
        int drainServerPort = Math.toIntExact(config.getLong("drain-server.port", (long) DrainServer.DEFAULT_PORT));
        if (drainServerEnabled)
            new DrainServer(logger, drainServerPort, this::executeDrainStrategy);

        drainStrategy = strategyManager.loadStrategy(
                "drain.strategy.",
                config.getString("drain-server.strategy", "default"),
                DrainStrategy.class
        );

        Strategy scalingStrategy = strategyManager.loadStrategy(
                "scaling.strategy.",
                config.getString("scaling.strategy", "none"),
                Strategy.class,
                config.getLong("scaling.interval", 60L),
                TimeUnit.SECONDS
        );

        Strategy migrationStrategy = strategyManager.loadStrategy(
                "migration.strategy.",
                config.getString("migration.strategy", "none"),
                Strategy.class,
                config.getLong("migration.interval", 60L),
                TimeUnit.SECONDS
        );

        strategyManager.addStrategy(scalingStrategy, migrationStrategy);

        strategyManager.onStartup(this);

        server.getEventManager().register(
                this,
                ServerConnectedEvent.class,
                e -> strategyManager.onPlayerConnect(e.getPlayer())
        );

        server.getEventManager().register(
                this,
                DisconnectEvent.class,
                e -> strategyManager.onPlayerDisconnect(e.getPlayer())
        );

        ServerConnection.addListener(new ServerConnection.Listener() {
            @Override
            public void onConnect(ServerConnection.ServerConnectionInfo connection) {
                RegisteredServer s = server.registerServer(
                        new ServerInfo(connection.name(), new InetSocketAddress(connection.host(), connection.port()))
                );
                logger.info("Registered server {}", connection.name());
                strategyManager.onServerRegister(s);
            }

            @Override
            public void onDisconnect(ServerConnection.ServerConnectionInfo connection) {
                RegisteredServer s = server.getServer(connection.name()).orElse(null);
                server.unregisterServer(
                        new ServerInfo(connection.name(), new InetSocketAddress(connection.host(), connection.port()))
                );
                logger.info("Unregistered server {}", connection.name());
                strategyManager.onServerUnregister(s);
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
        player.createConnectionRequest(to)
                .connect()
                .whenComplete((result, throwable) ->
                        handleTransfer(player, to, result, throwable, maxRetries, 1));
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
                .buildTask(this, () ->
                        player.createConnectionRequest(server)
                        .connect()
                        .whenComplete((r, t) ->
                                handleTransfer(player, server, r, t, retries - 1, retryDelay * 2)))
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
            bestServer = serverSelectionStrategy.selectServer(event.getPlayer(), this);
            if (bestServer != null)
                event.setResult(ServerPreConnectEvent.ServerResult.allowed(bestServer));
        }

        logger.info("Found best server. Best server: {}, player: {}",
                bestServer != null ? bestServer.getServerInfo().getName() : "null",
                event.getPlayer().getUsername()
        );
    }

    private boolean isMultiPaperServer(String name) {
        return ServerConnection.getConnection(name) != null;
    }

    private Toml readConfig() {
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

    public Toml getConfig() {
        return config;
    }

    public ProxyServer getProxy() {
        return server;
    }

    public Logger getLogger() {
        return logger;
    }

    public ServerSelectionStrategy getServerSelectionStrategy() {
        return serverSelectionStrategy;
    }

    public ScalingManager getScalingManager() {
        return scalingManager;
    }

    public boolean executeDrainStrategy(String serverName) {
        Preconditions.checkNotNull(this.drainStrategy, "Drain strategy is not set");
        return this.drainStrategy.drain(serverName, this);
    }
}
