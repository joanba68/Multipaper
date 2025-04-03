package puregero.multipaper.server.velocity.scaling;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import puregero.multipaper.server.velocity.MultiPaperVelocity;
import puregero.multipaper.server.velocity.ScalingStrategy;

import java.util.concurrent.TimeUnit;

public class BaseScalingStrategy implements ScalingStrategy {
    protected MultiPaperVelocity plugin;
    protected long interval;
    protected TimeUnit timeUnit;

    public BaseScalingStrategy(long interval, TimeUnit timeUnit) {
        this.interval = interval;
        this.timeUnit = timeUnit;
    }

    @Override
    public void onStartup(MultiPaperVelocity plugin) {
        this.plugin = plugin;
        plugin.getProxy().getScheduler().buildTask(plugin, this::performScaling)
                .repeat(interval, timeUnit)
                .schedule();
    }

    @Override
    public void onShutdown() {
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
    public void performScaling() {
    }
}
