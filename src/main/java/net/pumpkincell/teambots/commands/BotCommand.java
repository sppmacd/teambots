package net.pumpkincell.teambots.commands;

import carpet.patches.EntityPlayerMPFake;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandException;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import net.pumpkincell.teambots.TeamBotsMod;

public class BotCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("bot")
            .requires(ServerCommandSource::isExecutedByPlayer)
            .then(CommandManager.literal("set")
                .then(CommandManager.argument("coords", Vec3ArgumentType.vec3())
                    .executes(BotCommand::handleBotSet)
                )
                .executes(BotCommand::handleBotSet)
            )
            .then(CommandManager.literal("remove").executes(BotCommand::handleBotRemove))
        );
    }

    private static int handleBotSet(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var coords = Vec3ArgumentType.getVec3(context, "coords");
        var source = context.getSource();

        TeamBotsMod.requireIsInNation(source);

        var player = source.getPlayer();

        assert player != null;
        if (coords.squaredDistanceTo(player.getPos()) > 25) {
            throw new CommandException(Text.literal("You can place a bot max 5 blocks away from you"));
        }

        var team = (Team) player.getScoreboardTeam();

        var bot = TeamBotsMod.botsForTeam.get(team);
        if (bot == null) {
            assert team != null;
            var newBot =
                EntityPlayerMPFake.createFake("bot_" + Integer.toHexString((team.getName() + System.currentTimeMillis()).hashCode()), player.server, coords.getX(), coords.getY(), coords.getZ(), 0, 0, player.getWorld()
                    .getRegistryKey(), GameMode.SURVIVAL, false);
            TeamBotsMod.botsForTeam.put(team, newBot);
        } else {
            bot.setPos(coords.getX(), coords.getY(), coords.getZ());
        }
        return 0;
    }

    private static int handleBotRemove(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();
        var player = source.getPlayer();

        TeamBotsMod.requireIsInNation(source);

        assert player != null;
        var team = player.getScoreboardTeam();

        var bot = TeamBotsMod.botsForTeam.get(team);
        if (bot == null) {
            throw new CommandException(Text.literal("No bot to remove"));
        }

        TeamBotsMod.botsForTeam.remove(team);
        bot.kill();

        return 0;
    }
}
