package net.pumpkincell.teambots;

import carpet.patches.EntityPlayerMPFake;
import com.ibm.icu.impl.duration.DurationFormatter;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.SharedConstants;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.pumpkincell.teambots.commands.BotCommand;
import net.pumpkincell.teambots.commands.NationCommand;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.text.DateFormat;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class TeamBotsMod implements ModInitializer {
    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LoggerFactory.getLogger("teambots");

    // FIXME: This should belong to Server or to World.
    public static final Map<AbstractTeam, EntityPlayerMPFake> botsForTeam = new HashMap<>();

    public static final SimpleCommandExceptionType NOT_IN_NATION = new SimpleCommandExceptionType(Text.literal("You " +
        "need to be in a nation to run this command"));
    public static final SimpleCommandExceptionType NOT_NATION_LEADER = new SimpleCommandExceptionType(Text.literal(
        "You need to be a nation's leader to run this command"));
    public static final SimpleCommandExceptionType ALREADY_IN_NATION = new SimpleCommandExceptionType(Text.literal(
        "You are already in a nation"));

    public static Config config;

    static {
        try {
            config = Config.load();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isInNation(ServerCommandSource source) {
        var player = source.getPlayer();
        assert player != null;
        var team = (Team) player.getScoreboardTeam();
        return team != null;
    }

    private static boolean isNationLeader(ServerCommandSource source) {
        var player = source.getPlayer();
        assert player != null;
        var team = (Team) player.getScoreboardTeam();
        if (team == null) {
            return false;
        }
        var state = ServerState.load(Objects.requireNonNull(player.getServer()));
        return state.isLeader(player.getServer(), team, player.getGameProfile().getId());
    }

    public static void requireIsInNation(ServerCommandSource source) throws CommandSyntaxException {
        if (!TeamBotsMod.isInNation(source)) {
            throw NOT_IN_NATION.create();
        }
    }

    public static void requireIsNotInNation(ServerCommandSource source) throws CommandSyntaxException {
        if (TeamBotsMod.isInNation(source)) {
            throw ALREADY_IN_NATION.create();
        }
    }

    public static void requireIsNationLeader(ServerCommandSource source) throws CommandSyntaxException {
        if (!TeamBotsMod.isNationLeader(source)) {
            throw NOT_NATION_LEADER.create();
        }
    }

    private String formatTheEndOpeningTime(boolean longFormat) {
        var timeLeft = config.getTimeLeftToEndOpeningMS();
        if (timeLeft <= 0) {
            return "The End is already opened";
        }
        if (longFormat) {
            DateFormat df = DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault());
            String formattedDate = df.format(config.getEndOpeningTimeMS());
            return String.format("The End is opening %s, in %s", formattedDate, DurationFormatUtils.formatDurationWords(timeLeft, true, true));
        }
        return String.format("The End is opening in %s", DurationFormatUtils.formatDurationHMS(timeLeft));
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
            BotCommand.register(dispatcher);
            NationCommand.register(dispatcher);
            dispatcher.register(CommandManager.literal("endopening").executes((ctx) -> {
                ctx.getSource().sendFeedback(Text.literal(formatTheEndOpeningTime(true)), false);
                return 0;
            }));
        });

        ServerTickEvents.END_SERVER_TICK.register((server) -> {
            if (server.getTicks() % 20 != 0) {
                return;
            }
            var timeLeft = config.getTimeLeftToEndOpeningMS() / 1000 + 1;
            if (timeLeft < 0 || timeLeft > 3600) {
                return;
            }
            var timeLeftFormatted = String.format("%d:%02d", timeLeft / 60, timeLeft % 60);
            server.getPlayerManager()
                .broadcast(Text.literal(formatTheEndOpeningTime(false)), true);
        });


    }
}
