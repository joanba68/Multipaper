package puregero.multipaper.server.velocity;

import com.google.common.base.CaseFormat;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class StrategyManager {
    private final Logger logger;
    private final Map<String, Strategy> strategies = new HashMap<>();

    public StrategyManager(Logger logger) {
        this.logger = logger;
    }

    public void addStrategy(String name, Strategy strategy) {
        strategies.put(name, strategy);
    }

    public void removeStrategy(String name) {
        strategies.get(name).onShutdown();
        strategies.remove(name);
    }

    public Strategy getStrategy(String name) {
        return strategies.get(name);
    }

    public void onStartup(MultiPaperVelocity plugin) {
        strategies.values().forEach(strategy -> {
            strategy.onStartup(plugin);
        });
    }

    public void onPlayerConnect(Player player) {
        strategies.values().forEach(strategy -> {
            strategy.onPlayerConnect(player);
        });
    }

    public void onPlayerDisconnect(Player player) {
        strategies.values().forEach(strategy -> {
            strategy.onPlayerDisconnect(player);
        });
    }

    public void onServerRegister(RegisteredServer server) {
        strategies.values().forEach(strategy -> {
            strategy.onServerRegister(server);
        });
    }

    public void onServerUnregister(RegisteredServer server) {
        strategies.values().forEach(strategy -> {
            strategy.onServerUnregister(server);
        });
    }

    @SuppressWarnings("unchecked")
    public <T> T loadStrategy(String packagePrefix, String strategyName, Class<T> strategyClass,
                       Object... constructorArgs) {
        if (strategyName.isEmpty())
            logger.warn("Strategy name for {} is not set, using default strategy", strategyClass.getSimpleName());

        try {
            Class<?> clazz = Class.forName(
                    getClass().getPackageName() + "." + packagePrefix + getClassName(strategyName)
            );
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

    public static String getStrategyName(Class<?> clazz) {
        return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, clazz.getSimpleName());
    }

    public static String getClassName(String strategyName) {
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, strategyName);
    }

    private static Class<?>[] getConstructorParameterTypes(Object... args) {
        return Arrays.stream(args).map(Object::getClass).toArray(Class<?>[]::new);
    }
}
