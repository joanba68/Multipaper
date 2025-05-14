package puregero.multipaper.server.velocity;

import com.google.common.base.CaseFormat;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StrategyManager {
    private final Logger logger;
    private final ArrayList<Strategy> strategies = new ArrayList<>();

    public StrategyManager(Logger logger) {
        this.logger = logger;
    }

    public void addStrategy(Strategy strategy) {
        strategies.add(strategy);
    }

    public void addStrategy(Strategy... strategies) {
        this.strategies.addAll(List.of(strategies));
    }

    public void removeStrategy(Strategy strategy) {
        strategies.remove(strategy);
    }

    public void onStartup(MultiPaperVelocity plugin) {
        strategies.forEach(strategy -> {
            strategy.onStartup(plugin);
        });
    }

    public void onPlayerConnect(Player player) {
        strategies.forEach(strategy -> {
            strategy.onPlayerConnect(player);
        });
    }

    public void onPlayerDisconnect(Player player) {
        strategies.forEach(strategy -> {
            strategy.onPlayerDisconnect(player);
        });
    }

    public void onServerRegister(RegisteredServer server) {
        strategies.forEach(strategy -> {
            strategy.onServerRegister(server);
        });
    }

    public void onServerUnregister(RegisteredServer server) {
        strategies.forEach(strategy -> {
            strategy.onServerUnregister(server);
        });
    }

    @SuppressWarnings("unchecked")
    public <T> T loadStrategy(String packagePrefix, String strategyName, Class<T> strategyClass,
                       Object... constructorArgs) {
        if (strategyName.isEmpty())
            logger.warn("Strategy name for {} is not set, using default strategy", strategyClass.getSimpleName());

        strategyName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, strategyName);
        try {
            Class<?> clazz = Class.forName(getClass().getPackageName() + "." + packagePrefix + strategyName);
            if (strategyClass.isAssignableFrom(clazz)) {
                T strategy = (T) clazz.getConstructor(getConstructorParameterTypes(constructorArgs))
                        .newInstance(constructorArgs);
                logger.info("Loaded {} strategy: {}", packagePrefix.split("\\.")[0], strategyName);
                return strategy;
            }
            else
                logger.warn("Invalid strategy: {}", strategyName);
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException |
                 IllegalAccessException | InvocationTargetException e) {
            logger.warn("Failed to load strategy: {}", strategyName, e);
        }
        return null;
    }

    private static Class<?>[] getConstructorParameterTypes(Object... args) {
        return Arrays.stream(args).map(Object::getClass).toArray(Class<?>[]::new);
    }
}
