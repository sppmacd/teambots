package net.pumpkincell.teambots;

import carpet.patches.EntityPlayerMPFake;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.SharedConstants;
import net.minecraft.command.CommandException;
import net.minecraft.command.argument.ColorArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.command.argument.TeamArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class TeamBotsMod implements ModInitializer {
    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LoggerFactory.getLogger("teambots");

    private final Map<AbstractTeam, EntityPlayerMPFake> botsForTeam = new HashMap<>();

    private final SimpleCommandExceptionType NOT_IN_NATION = new SimpleCommandExceptionType(Text.literal("You need to be in a nation to run this command"));
    private final SimpleCommandExceptionType NOT_NATION_LEADER = new SimpleCommandExceptionType(Text.literal("You need to be a nation's leader to run this command"));
    private final SimpleCommandExceptionType ALREADY_IN_NATION = new SimpleCommandExceptionType(Text.literal("You are already in a nation"));

    private int handleBotSet(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var coords = Vec3ArgumentType.getVec3(context, "coords");
        var source = context.getSource();

        requireIsInNation(source);

        var player = source.getPlayer();

        assert player != null;
        if (coords.squaredDistanceTo(player.getPos()) > 25) {
            throw new CommandException(Text.literal("You can place a bot max 5 blocks away from you"));
        }

        var team = (Team) player.getScoreboardTeam();

        var bot = botsForTeam.get(team);
        if (bot == null) {
            assert team != null;
            var newBot =
                EntityPlayerMPFake.createFake("bot_" + Integer.toHexString((team.getName() + System.currentTimeMillis()).hashCode()), player.server, coords.getX(), coords.getY(), coords.getZ(), 0, 0, player.getWorld()
                                                                                                                                                                                                              .getRegistryKey(), GameMode.SURVIVAL, false);
            botsForTeam.put(team, newBot);
        } else {
            bot.setPos(coords.getX(), coords.getY(), coords.getZ());
        }
        return 0;
    }

    private int handleBotRemove(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();
        var player = source.getPlayer();

        requireIsInNation(source);

        assert player != null;
        var team = player.getScoreboardTeam();

        var bot = botsForTeam.get(team);
        if (bot == null) {
            throw new CommandException(Text.literal("No bot to remove"));
        }

        botsForTeam.remove(team);
        bot.kill();

        return 0;
    }

    private int handleNationLeave(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();
        var player = source.getPlayer();

        requireIsInNation(source);

        assert player != null;
        var state = ServerState.load(source.getServer());
        if (state.isLeader(source.getServer(), (Team) Objects.requireNonNull(player.getScoreboardTeam()), player.getGameProfile().getId())) {
            throw new CommandException(Text.literal("You can't leave nation if you are its leader"));
        }

        player.getWorld().getScoreboard().clearPlayerTeam(player.getEntityName());
        source.sendFeedback(Text.literal("Successfully left the nation"), false);
        return 0;
    }

    private int handleNationCreate(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var name = StringArgumentType.getString(context, "name");
        var color = ColorArgumentType.getColor(context, "color");
        var source = context.getSource();
        var player = source.getPlayer();

        requireIsNotInNation(source);

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

    private int handleNationAdd(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var playersToAdd = GameProfileArgumentType.getProfileArgument(context, "player");
        var source = context.getSource();
        var player = source.getPlayer();
        assert player != null;

        requireIsNationLeader(source);

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

    private int handleNationList(CommandContext<ServerCommandSource> context) {
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

    private int handleNationAdminSetLeader(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
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

    private int handleNationAdminClearLeader(CommandContext<ServerCommandSource> context)
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

    private boolean isInNation(ServerCommandSource source) {
        var player = source.getPlayer();
        assert player != null;
        var team = (Team) player.getScoreboardTeam();
        return team != null;
    }

    private boolean isNationLeader(ServerCommandSource source) {
        var player = source.getPlayer();
        assert player != null;
        var team = (Team) player.getScoreboardTeam();
        if (team == null) {
            return false;
        }
        var state = ServerState.load(Objects.requireNonNull(player.getServer()));
        return state.isLeader(player.getServer(), team, player.getGameProfile().getId());
    }

    private void requireIsInNation(ServerCommandSource source) throws CommandSyntaxException {
        if (!this.isInNation(source)) {
            throw NOT_IN_NATION.create();
        }
    }

    private void requireIsNotInNation(ServerCommandSource source) throws CommandSyntaxException {
        if (this.isInNation(source)) {
            throw ALREADY_IN_NATION.create();
        }
    }

    private void requireIsNationLeader(ServerCommandSource source) throws CommandSyntaxException {
        if (!this.isNationLeader(source)) {
            throw NOT_NATION_LEADER.create();
        }
    }

    @Override
    public void onInitialize() {
        SharedConstants.isDevelopment = true;

        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (entity instanceof EntityPlayerMPFake) {
                if (botsForTeam.containsValue(entity)) {
                    LOGGER.info("Bot {} was killed, removing", entity.getEntityName());
                    botsForTeam.values().remove(entity);
                }
            }
            return true;
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("bot")
                .requires(ServerCommandSource::isExecutedByPlayer)
                .then(CommandManager.literal("set")
                    .then(CommandManager.argument("coords", Vec3ArgumentType.vec3())
                        .executes(this::handleBotSet)
                    )
                    .executes(this::handleBotSet)
                )
                .then(CommandManager.literal("remove").executes(this::handleBotRemove))
            );
            dispatcher.register(CommandManager.literal("nation")
                .requires(ServerCommandSource::isExecutedByPlayer)
                .then(CommandManager.literal("leave")
                    .executes(this::handleNationLeave))
                .then(CommandManager.literal("create")
                    .then(CommandManager.argument("name", StringArgumentType.string())
                        .then(CommandManager.argument("color", ColorArgumentType.color())
                            .executes(this::handleNationCreate)
                        )
                    )
                )
                .then(CommandManager.literal("add")
                    .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                        .executes(this::handleNationAdd)
                    )
                )
                .then(CommandManager.literal("list").executes(this::handleNationList))
                .then(CommandManager.literal("admin")
                    .requires(source -> source.hasPermissionLevel(3))
                    .then(CommandManager.literal("setleader")
                        .then(CommandManager.argument("team", TeamArgumentType.team())
                            .then(CommandManager.argument("leader", GameProfileArgumentType.gameProfile())
                                .executes(this::handleNationAdminSetLeader)
                            )
                        )
                    )
                    .then(CommandManager.literal("clearleader")
                        .then(CommandManager.argument("team", TeamArgumentType.team())
                            .executes(this::handleNationAdminClearLeader)
                        )
                    )
                )
                // TODO: /nation kick
            );
        });
    }
}
