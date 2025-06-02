package puregero.multipaper.server.velocity;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Map;

public class StrategyCommand {

    private final static Map<String, List<String>> strategies = Map.of(
            "server-selection", List.of("random", "lowest_players", "lowest_tick_time", "weighted_tick_players"),
            "scaling", List.of("none", "tick_length", "tick_length_v3"),
            "migration", List.of("none", "tick_player_ratio", "easy_strategy"),
            "drain", List.of("default")
    );

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
                            for (String behaviour : strategies.keySet())
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
                            strategies.get(behaviour).forEach(strategy -> {
                                source.sendMessage(Component.text("  " + strategy));
                            });
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(BrigadierCommand.requiredArgumentBuilder("strategy", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    String behaviour = StringArgumentType.getString(context, "behaviour");
                                    for (String strategy : strategies.get(behaviour))
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
