package net.pumpkincell.teambots.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.command.CommandException;
import net.minecraft.command.argument.ColorArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.command.argument.TeamArgumentType;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.pumpkincell.teambots.ServerState;
import net.pumpkincell.teambots.TeamBotsMod;

import java.util.Objects;

public class NationCommand {
    private static int handleNationLeave(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();
        var player = source.getPlayer();

        TeamBotsMod.requireIsInNation(source);

        assert player != null;
        var state = ServerState.load(source.getServer());
        if (state.isLeader(source.getServer(), (Team) Objects.requireNonNull(player.getScoreboardTeam()), player.getGameProfile().getId())) {
            throw new CommandException(Text.literal("You can't leave nation if you are its leader"));
        }

        player.getWorld().getScoreboard().clearPlayerTeam(player.getEntityName());
        source.sendFeedback(Text.literal("Successfully left the nation"), false);
        return 0;
    }

    private static int handleNationCreate(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var name = StringArgumentType.getString(context, "name");
        var color = ColorArgumentType.getColor(context, "color");
        var source = context.getSource();
        var player = source.getPlayer();

        TeamBotsMod.requireIsNotInNation(source);

        assert player != null;
        if (player.getScoreboardTeam() != null) {
            throw new CommandException(Text.literal("You are already in a nation! If you want to leave, use /nation " +
                "leave first"));
        }

        if (player.getScoreboard().getTeam(name) != null) {
            throw new CommandException(Text.literal("Nation of this name already exists"));
        }

        var team = player.getScoreboard().addTeam(name);
        team.setColor(color);
        team.setPrefix(Text.literal(String.format("(%s) ", name)));
        var state = ServerState.load(source.getServer());
        state.setLeader(team.getName(), player.getGameProfile().getId());
        player.getScoreboard().addPlayerToTeam(player.getEntityName(), team);
        source.sendFeedback(Text.literal("Successfully created nation ").append(team.getFormattedName()).append(". " +
            "You become its leader now."), false);
        return 0;
    }

    private static int handleNationAdd(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var playersToAdd = GameProfileArgumentType.getProfileArgument(context, "player");
        var source = context.getSource();
        var player = source.getPlayer();
        assert player != null;

        TeamBotsMod.requireIsNationLeader(source);

        var team = (Team)player.getScoreboardTeam();
        assert team != null;

        var playerCountInTeamAfterAdding = team.getPlayerList().size() + playersToAdd.size();
        if (playerCountInTeamAfterAdding > 5) {
            throw new CommandException(Text.literal(String.format("A nation may contain a maximum of 5 players, after" +
                " adding players it would be %d", playerCountInTeamAfterAdding)));
        }

        for (var player1 : playersToAdd) {
            if (source.getServer().getScoreboard().getPlayerTeam(player1.getName()) != null) {
                throw new CommandException(Text.literal("Player you want to add is already in a nation"));
            }
        }

        for (var player1 : playersToAdd) {
            player.getScoreboard().addPlayerToTeam(player1.getName(), team);
        }

        if (playersToAdd.size() == 1) {
            source.sendFeedback(Text.literal("Successfully added ")
                .append(Text.literal(playersToAdd.stream().findFirst().get().getName())
                    .formatted(Formatting.BOLD)).append(" to your nation"), false);
        } else {
            source.sendFeedback(Text.literal(String.format("Successfully added %d players to your nation",
                playersToAdd.size())), false);
        }
        for (var teammate: team.getPlayerList()) {
            for (var addedPlayer : playersToAdd) {
                var teammateEntity = source.getServer().getPlayerManager().getPlayer(teammate);
                var addedPlayerEntity = source.getServer().getPlayerManager().getPlayer(addedPlayer.getId());
                var addedPlayerName = addedPlayerEntity == null ? Text.literal(addedPlayer.getName()) : addedPlayerEntity.getDisplayName();
                if (teammateEntity != null) {
                    teammateEntity.sendMessageToClient(addedPlayerName.copy()
                        .append(", joined our nation. Welcome!"), true);
                }
            }
        }
        return 0;
    }

    private static int handleNationList(CommandContext<ServerCommandSource> context) {
        var source = context.getSource();
        var player = source.getPlayer();
        assert player != null;
        var server = player.getServer();

        assert server != null;
        var state = ServerState.load(server);
        if (player.getScoreboard().getTeams().isEmpty()) {
            source.sendFeedback(Text.literal("There are no nations created"), false);
            return 0;
        }
        player.getScoreboard().getTeams().forEach(team -> {
            MutableText text = Text.empty();
            text.append(team.getFormattedName());
            text.append(" -> ");
            var leaderUUID = state.getLeader(server, team.getName());
            if (leaderUUID == null) {
                text.append(Text.literal("(No leader)").formatted(Formatting.BOLD, Formatting.RED));
            } else {
                var leaderProfile = server.getUserCache().getByUuid(leaderUUID);
                if (leaderProfile.isPresent()) {
                    text.append(Text.literal(leaderProfile.get().getName())
                        .formatted(Formatting.BOLD));
                } else {
                    text.append(Text.literal("(Invalid leader)").formatted(Formatting.BOLD, Formatting.RED));
                }
            } var playersInTeam = team.getPlayerList().stream().toList();
            for (String playerInTeam : playersInTeam) {
                var profile = server.getUserCache().findByName(playerInTeam);
                if (profile.isEmpty()) {
                    text.append(Text.literal(", ").append(Text.literal(playerInTeam).formatted(Formatting.RED)));
                    continue;
                }
                var profileO = profile.get();
                if (profileO.getId().equals(leaderUUID)) {
                    continue;
                }
                text.append(Text.literal(", " + profileO.getName()));
            }
            source.sendFeedback(text, false);
        });
        return 0;
    }

    private static int handleNationAdminSetLeader(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var team = StringArgumentType.getString(context, "team");
        var leaders = GameProfileArgumentType.getProfileArgument(context, "leader");
        if (leaders.size() != 1) {
            // FIXME: Maybe we want to allow multiple leaders, who knows?
            throw new SimpleCommandExceptionType(Text.literal("Only one leader per nation is allowed")).create();
        }
        var source = context.getSource();
        var player = source.getPlayer();

        assert player != null;
        var state = ServerState.load(Objects.requireNonNull(player.getServer()));
        var newLeaderO = leaders.stream().findFirst();
        assert newLeaderO.isPresent();
        var newLeader = newLeaderO.get();
        state.setLeader(team, newLeader.getId());
        source.sendFeedback(Text.literal(String.format("Successfully set player %s as leader of %s",
            newLeader.getName(), team)), true);
        return 0;
    }

    private static int handleNationAdminClearLeader(CommandContext<ServerCommandSource> context)
        throws CommandSyntaxException {
        var team = StringArgumentType.getString(context, "team");
        var source = context.getSource();
        var player = source.getPlayer();

        assert player != null;
        var state = ServerState.load(Objects.requireNonNull(player.getServer()));
        if (state.getLeader(player.getServer(), team) == null) {
            throw new SimpleCommandExceptionType(Text.literal("This nation doesn't have a leader already")).create();
        }
        state.clearLeader(team);
        source.sendFeedback(Text.literal(String.format("Successfully cleared leader of %s", team)), true);
        return 0;
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("nation")
            .requires(ServerCommandSource::isExecutedByPlayer)
            .then(CommandManager.literal("leave")
                .executes(NationCommand::handleNationLeave))
            .then(CommandManager.literal("create")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .then(CommandManager.argument("color", ColorArgumentType.color())
                        .executes(NationCommand::handleNationCreate)
                    )
                )
            )
            .then(CommandManager.literal("add")
                .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                    .executes(NationCommand::handleNationAdd)
                )
            )
            .then(CommandManager.literal("list").executes(NationCommand::handleNationList))
            .then(CommandManager.literal("admin")
                .requires(source -> source.hasPermissionLevel(3))
                .then(CommandManager.literal("setleader")
                    .then(CommandManager.argument("team", TeamArgumentType.team())
                        .then(CommandManager.argument("leader", GameProfileArgumentType.gameProfile())
                            .executes(NationCommand::handleNationAdminSetLeader)
                        )
                    )
                )
                .then(CommandManager.literal("clearleader")
                    .then(CommandManager.argument("team", TeamArgumentType.team())
                        .executes(NationCommand::handleNationAdminClearLeader)
                    )
                )
            )
            // TODO: /nation kick
        );
    }
}
