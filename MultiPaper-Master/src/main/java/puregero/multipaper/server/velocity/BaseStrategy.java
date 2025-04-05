package puregero.multipaper.server.velocity;

import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;

public class BaseStrategy implements Strategy {
    protected MultiPaperVelocity plugin;
    protected Toml config;
    protected Logger logger;
    protected long interval;
    protected TimeUnit timeUnit;

    public BaseStrategy(Long interval, TimeUnit timeUnit) {
        this.interval = interval;
        this.timeUnit = timeUnit;
    }

    @Override
    public void onStartup(MultiPaperVelocity plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.logger = plugin.getLogger();
        plugin.getProxy().getScheduler().buildTask(plugin, this::executeStrategy)
                .repeat(interval, timeUnit)
                .schedule();
    }

    @Override
    public void onPlayerConnect(Player player) {
    }

    @Override
    public void onPlayerDisconnect(Player player) {
    }

    @Override
    public void onServerRegister(RegisteredServer server) {
    }

    @Override
    public void onServerUnregister(RegisteredServer server) {
    }

    @Override
    public void executeStrategy() {
    }
}
