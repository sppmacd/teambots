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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.pumpkincell.teambots.ServerState;
import net.pumpkincell.teambots.TeamBotsMod;
import net.pumpkincell.teambots.inbox.NationInviteMessage;
import net.pumpkincell.teambots.inbox.TextMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class NationCommand {
    public static final Map<UUID, Long> lastNationCommandRunTimes = new HashMap<>();

    private static void handleCooldown(ServerPlayerEntity player) {
        var currentTime = System.currentTimeMillis();
        var lastRunTime = lastNationCommandRunTimes.getOrDefault(player.getUuid(), 0L);
        final int COMMAND_COOLDOWN = TeamBotsMod.config.getNationCommandCooldownMS();
        if (currentTime < lastRunTime + COMMAND_COOLDOWN) {
            throw new CommandException(Text.literal(String.format("You can use this command next time in %d second(s)",
                ((lastRunTime + COMMAND_COOLDOWN) - currentTime) / 1000 + 1
            )));
        }
        lastNationCommandRunTimes.put(player.getUuid(), currentTime);
    }

    private static int handleNationLeave(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();
        var player = source.getPlayer();
        assert player != null;
        handleCooldown(player);
        TeamBotsMod.requireIsInNation(source);

        var state = ServerState.load(source.getServer());
        if (state.isLeader(source.getServer(), (Team) Objects.requireNonNull(player.getScoreboardTeam()),
            player.getGameProfile()
                .getId()
        )) {
            throw new CommandException(Text.literal("You can't leave nation if you are its leader"));
        }

        player.getWorld().getScoreboard().clearPlayerTeam(player.getEntityName());
        source.sendFeedback(Text.literal("Successfully left the nation"), false);
        return 0;
    }

    private static int handleNationRemove(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();
        var player = source.getPlayer();
        assert player != null;
        handleCooldown(player);

        TeamBotsMod.requireIsNationLeader(source);

        var team = (Team) player.getScoreboardTeam();
        assert team != null;

        if (team.getPlayerList().size() > 1) {
            throw new CommandException(Text.literal("Kick all other members first"));
        }

        var state = ServerState.load(Objects.requireNonNull(player.getServer()));
        state.clearLeader(team.getName());
        player.getScoreboard().removeTeam(team);
        source.sendFeedback(Text.literal("Successfully removed the nation"), false);
        return 0;
    }

    private static Text formatPrefix(String name) {
        return Text.literal(String.format("%s: ", name));
    }

    private static int handleNationCreate(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var name = StringArgumentType.getString(context, "name");
        var color = ColorArgumentType.getColor(context, "color");
        var source = context.getSource();
        var player = source.getPlayer();
        assert player != null;
        handleCooldown(player);

        TeamBotsMod.requireIsNotInNation(source);

        if (player.getScoreboardTeam() != null) {
            throw new CommandException(Text.literal("You are already in a nation! If you want to leave, use /nation " +
                "leave first"));
        }

        if (player.getScoreboard().getTeam(name) != null) {
            throw new CommandException(Text.literal("Nation of this name already exists"));
        }

        var team = player.getScoreboard().addTeam(name);
        team.setColor(color);
        team.setPrefix(formatPrefix(name));
        var state = ServerState.load(source.getServer());
        state.setLeader(team.getName(), player.getGameProfile().getId());
        player.getScoreboard().addPlayerToTeam(player.getEntityName(), team);
        source.sendFeedback(Text.literal("Successfully created nation ").append(team.getFormattedName()).append(". " +
            "You become its leader now."), false);
        return 0;
    }

    private static int handleNationInvite(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var playersToAdd = GameProfileArgumentType.getProfileArgument(context, "player");
        var source = context.getSource();
        var player = source.getPlayer();
        assert player != null;
        handleCooldown(player);

        TeamBotsMod.requireIsNationLeader(source);

        var state = ServerState.load(source.getServer());
        var team = (Team) player.getScoreboardTeam();
        assert team != null;

        for (var player1 : playersToAdd) {
            if (source.getServer().getScoreboard().getPlayerTeam(player1.getName()) != null) {
                throw new CommandException(Text.literal("Player you want to invite is already in a nation"));
            }
        }

        if (playersToAdd.size() == 1) {
            source.sendFeedback(Text.literal("Sent invite to ")
                .append(playersToAdd.stream().findFirst().get().getName()), false);
        } else {
            source.sendFeedback(Text.literal(String.format("Sent nation invite to %d players",
                playersToAdd.size()
            )), false);
        }
        for (var addedPlayer : playersToAdd) {
            state.getInbox(addedPlayer.getId()).pushMessage(new NationInviteMessage(team.getName()));
        }
        return 0;
    }

    private static int handleNationKick(CommandContext<ServerCommandSource> context, String reason)
        throws CommandSyntaxException {
        var playersToKick = GameProfileArgumentType.getProfileArgument(context, "player");
        var source = context.getSource();
        var player = source.getPlayer();
        assert player != null;
        handleCooldown(player);

        TeamBotsMod.requireIsNationLeader(source);

        var state = ServerState.load(source.getServer());
        var team = (Team) player.getScoreboardTeam();

        assert team != null;
        for (var playerToKick : playersToKick) {
            if (playerToKick.getId().equals(player.getGameProfile().getId())) {
                throw new CommandException(Text.literal("Refusing to kick yourself."));
            }
            if (source.getServer().getScoreboard().getPlayerTeam(playerToKick.getName()) != team) {
                throw new CommandException(Text.literal("Can't kick player from another nation"));
            }
        }
        for (var playerToKick : playersToKick) {
            player.getScoreboard().removePlayerFromTeam(playerToKick.getName(), team);
        }

        if (playersToKick.size() == 1) {
            source.sendFeedback(Text.literal("Successfully kicked ")
                .append(Text.literal(playersToKick.stream().findFirst().get().getName())
                    .formatted(Formatting.BOLD)).append(" from your nation"), false);
        } else {
            source.sendFeedback(Text.literal(String.format("Successfully kicked %d players from your nation",
                playersToKick.size()
            )), false);
        }
        for (var kickedPlayer : playersToKick) {
            var kickedPlayerEntity = source.getServer().getPlayerManager().getPlayer(kickedPlayer.getId());
            for (var teammate : team.getPlayerList()) {
                var teammateEntity = source.getServer().getPlayerManager().getPlayer(teammate);
                var kickedPlayerName = kickedPlayerEntity == null ? Text.literal(kickedPlayer.getName()) :
                    kickedPlayerEntity.getDisplayName();
                if (teammateEntity != null) {
                    state.getInbox(teammateEntity.getGameProfile().getId())
                        .pushMessage(new TextMessage(kickedPlayerName.copy()
                            .append(Text.literal(String.format(" has been kicked from our nation. Reason: %s",
                                reason
                            )))));
                }
            }
            state.getInbox(kickedPlayer.getId())
                .pushMessage(new TextMessage(Text.literal(String.format("You have been kicked from %s. " +
                        "Reason: %s",
                    team.getName(), reason
                ))));
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
            }
            var playersInTeam = team.getPlayerList().stream().toList();
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

    private static int handleNationSetName(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var name = StringArgumentType.getString(context, "name");
        var source = context.getSource();
        var player = source.getPlayer();
        TeamBotsMod.requireIsNationLeader(source);

        assert player != null;
        var team = (Team)player.getScoreboardTeam();
        assert team != null;
        team.setDisplayName(Text.literal(name));
        team.setPrefix(formatPrefix(name));
        return 0;
    }

    private static int handleNationSetColor(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var color = ColorArgumentType.getColor(context,"color");
        var source = context.getSource();
        var player = source.getPlayer();
        TeamBotsMod.requireIsNationLeader(source);

        assert player != null;
        var team = (Team)player.getScoreboardTeam();
        assert team != null;
        team.setColor(color);
        return 0;
    }

    private static int handleNationAdminSetLeader(CommandContext<ServerCommandSource> context)
        throws CommandSyntaxException {
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
            newLeader.getName(), team
        )), true);
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
            .then(CommandManager.literal("create")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .then(CommandManager.argument("color", ColorArgumentType.color())
                        .executes(NationCommand::handleNationCreate)
                    )
                )
            )
            .then(CommandManager.literal("invite")
                .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                    .executes(NationCommand::handleNationInvite)
                )
            )
            .then(CommandManager.literal("kick")
                .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                    .executes((context) -> handleNationKick(context, "Just because"))
                    .then(CommandManager.argument("reason", StringArgumentType.greedyString())
                        .executes((context) -> handleNationKick(context, StringArgumentType.getString(context,
                            "reason"
                        )))
                    )
                )
            )
            .then(CommandManager.literal("leave")
                .executes(NationCommand::handleNationLeave))
            .then(CommandManager.literal("list").executes(NationCommand::handleNationList))
            .then(CommandManager.literal("remove")
                .executes(NationCommand::handleNationRemove))
            .then(CommandManager.literal("set")
                .then(CommandManager.literal("name")
                    .then(CommandManager.argument("name", StringArgumentType.string())
                        .executes(NationCommand::handleNationSetName)
                    )
                )
                .then(CommandManager.literal("color")
                    .then(CommandManager.argument("color", ColorArgumentType.color())
                        .executes(NationCommand::handleNationSetColor)
                    )
                )
            )
        );
    }
}
