package puregero.multipaper.server.velocity;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class StrategyCommand {
    private final static Set<String> behaviours = Set.of(
            "server-selection",
            "scaling",
            "migration",
            "drain"
    );

    private final static Map<String, List<String>> strategies = behaviours
            .stream()
            .map(behaviour -> behaviour.replaceAll("-", ""))
            .collect(Collectors.toMap(
                    Function.identity(),
                    StrategyCommand::getStrategies
            ));

    private static List<String> getStrategies(String behaviour) {
        String packageName = StrategyCommand.class.getPackageName() + "." + behaviour + ".strategy";
        Reflections reflections = new Reflections(
                new ConfigurationBuilder()
                        .addUrls(ClasspathHelper.forClassLoader(ClasspathHelper.staticClassLoader()))
                        .setScanners(Scanners.SubTypes.filterResultsBy((s -> true)))
                        .filterInputsBy(
                                new FilterBuilder()
                                        .includePackage(StrategyCommand.class.getPackageName())
                        )
        );
        return reflections
                .getSubTypesOf(Object.class)
                .stream()
                .filter(Predicate.not(Class::isInterface))
                .filter(clazz -> clazz.getPackageName().startsWith(packageName))
                .map(StrategyManager::getStrategyName)
                .collect(Collectors.toList());
    }

    public static BrigadierCommand create(MultiPaperVelocity plugin) {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("strategy")
                //.requires(source -> source.hasPermission("multipaper.command.strategy")) // needs luckperms on velocity
                .executes(context -> {
                    CommandSource source = context.getSource();
                    source.sendMessage(Component.text("You can change the strategies for the following behaviors:"));
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
                                            "You can select the following strategies for "
                                                    + behaviour.replace("-", " ")
                                                    + ":"
                                    )
                            );
                            strategies.get(behaviour.replaceAll("-", "")).forEach(strategy -> {
                                source.sendMessage(Component.text("  " + strategy));
                            });
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(BrigadierCommand.requiredArgumentBuilder("strategy", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    String behaviour = StringArgumentType.getString(context, "behaviour");
                                    for (String strategy : strategies.get(behaviour.replaceAll("-", "")))
                                        builder.suggest(strategy);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    CommandSource source = context.getSource();
                                    String behaviour = StringArgumentType.getString(context, "behaviour");
                                    String strategy = StringArgumentType.getString(context, "strategy");
                                    source.sendMessage(
                                            Component.text(
                                                    "You have selected the "
                                                            + strategy
                                                            + " strategy for "
                                                            + behaviour.replace("-", " ")
                                            )
                                    );
                                    plugin.setStrategy(
                                            behaviour,
                                            strategy
                                    );
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
        );
    }
}
