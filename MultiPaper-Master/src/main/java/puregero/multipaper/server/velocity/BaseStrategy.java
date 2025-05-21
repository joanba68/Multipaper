package puregero.multipaper.server.velocity;

import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class BaseStrategy implements Strategy {
    protected MultiPaperVelocity plugin;
    protected Toml config;
    protected long interval;
    protected TimeUnit timeUnit;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private ScheduledTask task;

    public BaseStrategy(Long interval, TimeUnit timeUnit) {
        this.interval = interval;
        this.timeUnit = timeUnit;
    }

    @Override
    public void onStartup(MultiPaperVelocity plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.task = plugin.getProxy().getScheduler().buildTask(plugin, this::executeStrategy)
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
    public void onShutdown() {
        this.task.cancel();
    }

    @Override
    public void executeStrategy() {
    }

    public long getInterval() {
        return interval;
    }

    public void setInterval(long interval) {
        this.interval = interval;
        this.task.cancel();
        this.task = plugin.getProxy().getScheduler().buildTask(plugin, this::executeStrategy)
                .repeat(interval, timeUnit)
                .schedule();
    }
}
