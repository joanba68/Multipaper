package puregero.multipaper.server.velocity;

import com.google.common.base.CaseFormat;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

public class StrategyConfigCommand {

    private final static Set<String> behaviours = Set.of(
            "server-selection",
            "scaling",
            "migration",
            "drain"
    );

    private static Object getStrategy(String type, MultiPaperVelocity plugin) {
        switch (type) {
            case "server-selection" -> {
                return plugin.getServerSelectionStrategy();
            }
            case "drain" -> {
                return plugin.getDrainStrategy();
            }
            case "scaling" -> {
                return plugin.getStrategyManager().getStrategy("scaling");
            }
            case "migration" -> {
                return plugin.getStrategyManager().getStrategy("migration");
            }
            default -> {
                return null;
            }
        }
    }

    public static List<Method> getAllMethods(List<Method> methods, Class<?> clazz) {
        if (clazz == null)
            return methods;
        methods.addAll(List.of(clazz.getDeclaredMethods()));
        getAllMethods(methods, clazz.getSuperclass());
        return methods;
    }

    public static List<Method> getAllMethods(Class<?> clazz) {
        return getAllMethods(new ArrayList<>(), clazz);
    }

    public static List<Field> getAllFields(List<Field> fields, Class<?> clazz) {
        if (clazz == null)
            return fields;
        fields.addAll(List.of(clazz.getDeclaredFields()));
        getAllFields(fields, clazz.getSuperclass());
        return fields;
    }

    public static List<Field> getAllFields(Class<?> clazz) {
        return getAllFields(new ArrayList<>(), clazz);
    }

    public static Method getMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        return getAllMethods(clazz)
                .stream()
                .filter(method -> method.getName().equals(name))
                .filter(method -> Arrays.equals(method.getParameterTypes(), parameterTypes))
                .findFirst()
                .orElseThrow();
    }

    public static Field getField(Class<?> clazz, String name) {
        return getAllFields(clazz)
                .stream()
                .filter(field -> field.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    public static Set<String> getConfigurableValues(Object strategy) {
        if (strategy == null)
            return Collections.emptySet();
        return getAllMethods(strategy.getClass())
                .stream()
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getName().startsWith("set"))
                .filter(method -> method.getParameterCount() == 1)
                .filter(name -> {
                    try {
                        String variableName = getVariableName(name);
                        if (variableName == null)
                            return false;
                        return isAllowableType(getField(strategy.getClass(), variableName));
                    } catch (NoSuchElementException e) {
                        return false;
                    }
                })
                .map(StrategyConfigCommand::getMinecraftName)
                .collect(Collectors.toSet());
    }

    public static String getFieldValue(Object strategy, String minecraftName)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (strategy == null)
            return null;
        String javaName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, minecraftName);
        Method method = getMethod(strategy.getClass(), "get" + javaName);
        if (method.getParameterCount() != 0)
            throw new IllegalArgumentException("Method has wrong number of parameters");
        if (method.getReturnType() == void.class || method.getReturnType() == Void.class)
            throw new IllegalArgumentException("Method has void return type");
        if (!Modifier.isPublic(method.getModifiers()))
            throw new IllegalArgumentException("Method is not public");
        Object value = method.invoke(strategy);
        if (value == null)
            throw new IllegalArgumentException("Value is null");
        return String.valueOf(value);
    }

    public static void setFieldValue(Object strategy, String minecraftName, String value)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (strategy == null)
            throw new IllegalArgumentException("Strategy is null");
        String javaName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, minecraftName);
        Class<?> fieldType = getMethod(strategy.getClass(), "get" + javaName).getReturnType();
        Method method = getMethod(strategy.getClass(), "set" + javaName, fieldType);
        if (method.getParameterCount() != 1)
            throw new IllegalArgumentException("Method has wrong number of parameters");
        if (!Modifier.isPublic(method.getModifiers()))
            throw new IllegalArgumentException("Method is not public");
        Object convertedValue = convertValue(method.getParameterTypes()[0], value);
        if (convertedValue == null)
            throw new IllegalArgumentException("Value is not convertible");
        method.invoke(strategy, convertedValue);
    }

    private static Object convertValue(Class<?> type, String value) {
        if (type == String.class)
            return value;
        if (type == int.class || type == Integer.class)
            return Integer.parseInt(value);
        if (type == long.class || type == Long.class)
            return Long.parseLong(value);
        if (type == double.class || type == Double.class)
            return Double.parseDouble(value);
        if (type == float.class || type == Float.class)
            return Float.parseFloat(value);
        if (type == boolean.class || type == Boolean.class)
            return Boolean.parseBoolean(value);
        return null;
    }

    public static String getVariableName(Method method) {
        String name = method.getName();
        if (method.getName().startsWith("set") || method.getName().startsWith("get"))
            name = name.substring(3);
        else
            return null;
        name = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, name);
        return name;
    }

    public static String getMinecraftName(Method method) {
        String name = method.getName();
        if (method.getName().startsWith("set") || method.getName().startsWith("get"))
            name = name.substring(3);
        else
            return null;
        name = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name);
        return name;
    }

    public static boolean isAllowableType(Field field) {
        Class<?> type = field.getType();
        return type.isPrimitive()
                || type == String.class || type == Integer.class
                || type == Long.class || type == Double.class
                || type == Float.class || type == Boolean.class;
    }

    public static BrigadierCommand create(MultiPaperVelocity plugin) {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("strategyconfig")
                //.requires(source -> source.hasPermission("multipaper.command.strategyconfig")) // needs luckperms on velocity
                .executes(context -> {
                    CommandSource source = context.getSource();
                    source.sendMessage(
                            Component.text(
                                    "You can change the configurable values for the following behaviors:"
                            )
                    );
                    source.sendMessage(Component.text("  Server selection"));
                    source.sendMessage(Component.text("  Server scaling"));
                    source.sendMessage(Component.text("  Player migration"));
                    source.sendMessage(Component.text("  Drain process"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand.requiredArgumentBuilder("behaviour", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (String behaviour : behaviours)
                                builder.suggest(behaviour);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            CommandSource source = context.getSource();
                            String behaviour = StringArgumentType.getString(context, "behaviour");
                            source.sendMessage(
                                    Component.text(
                                            "You can change the following values for "
                                                    + behaviour.replace("-", " ")
                                                    + ":"
                                    )
                            );
                            Object strategy = getStrategy(behaviour, plugin);
                            Set<String> values = getConfigurableValues(strategy);
                            for (String value : values)
                                source.sendMessage(Component.text("  " + value));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(BrigadierCommand.requiredArgumentBuilder("variable", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    String behaviour = StringArgumentType.getString(context, "behaviour");
                                    Object strategy = getStrategy(behaviour, plugin);
                                    Set<String> values = getConfigurableValues(strategy);
                                    for (String value : values)
                                        builder.suggest(value);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    CommandSource source = context.getSource();
                                    String behaviour = StringArgumentType.getString(context, "behaviour");
                                    String variable = StringArgumentType.getString(context, "variable");
                                    Object strategy = getStrategy(behaviour, plugin);
                                    try {
                                        String value = getFieldValue(strategy, variable);
                                        if (value == null) {
                                            source.sendMessage(Component.text("No value found for " + variable));
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        source.sendMessage(Component.text(variable + ": " + value));
                                        return Command.SINGLE_SUCCESS;
                                    } catch (NoSuchMethodException | InvocationTargetException |
                                             IllegalAccessException e) {
                                        source.sendMessage(Component.text("Failed to get value for " + variable));
                                        source.sendMessage(Component.text("Error: " + e.getMessage()));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                })
                                .then(BrigadierCommand.requiredArgumentBuilder("value", StringArgumentType.word())
                                        .executes(context -> {
                                            CommandSource source = context.getSource();
                                            String behaviour = StringArgumentType.getString(context, "behaviour");
                                            String variable = StringArgumentType.getString(context, "variable");
                                            String value = StringArgumentType.getString(context, "value");
                                            Object strategy = getStrategy(behaviour, plugin);
                                            try {
                                                setFieldValue(strategy, variable, value);
                                                source.sendMessage(Component.text("Set " + variable + " to " + value));
                                                return Command.SINGLE_SUCCESS;
                                            } catch (NoSuchMethodException | InvocationTargetException |
                                                     IllegalAccessException e) {
                                                source.sendMessage(Component.text("Failed to set " + variable + " to " + value));
                                                source.sendMessage(Component.text("Error: " + e.getMessage()));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                        })
                                )
                        )
                )
        );
    }
}
