package net.pumpkincell.teambots.inbox;

import com.mojang.authlib.GameProfile;
import net.minecraft.command.CommandException;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.pumpkincell.teambots.ServerState;

import java.util.Optional;
import java.util.Set;

public class NationInviteMessage extends Message {
    String nation;

    public NationInviteMessage(NbtCompound tag) {
        super(tag);
        this.nation = tag.getString("nation");
    }

    @Override
    public NbtCompound toNbt() {
        var tag = super.toNbt();
        tag.putString("type", "nation_invite");
        tag.putString("nation", this.nation);
        return tag;
    }

    public NationInviteMessage(String nation) {
        this.nation = nation;
    }

    public Optional<GameProfile> getLeader(MinecraftServer server) {
        var state = ServerState.load(server);
        var leader = state.getLeader(server, this.nation);
        return server.getUserCache().getByUuid(leader);
    }

    @Override
    Text format(MinecraftServer server) {
        return this.getLeader(server)
            .map((v) -> Text.literal(String.format("%s invites you to their nation, %s.", v.getName(), nation)))
            .orElse(Text.literal(String.format("You have been invited to %s", nation)));
    }

    @Override
    Set<String> getActions() {
        return Set.of("Accept", "Decline");
    }

    @Override
    void handleAction(ServerPlayerEntity player, String action) {
        var server = player.server;
        var state = ServerState.load(server);
        switch (action) {
            case "Accept" -> {
                var team = server.getScoreboard().getTeam(nation);
                if (team == null) {
                    throw new IllegalStateException("The nation doesn't exist, maybe it was removed.");
                }

                if (team.getPlayerList().size() >= 5) {
                    player.sendMessage(Text.literal("A nation may contain a maximum of 5 players."));
                    return;
                }

                player.getScoreboard().addPlayerToTeam(player.getEntityName(), team);

                for (var teammate : team.getPlayerList()) {
                    var profile = server.getUserCache().findByName(teammate);
                    profile.ifPresent((v) -> {
                        if (v.getId().equals(player.getGameProfile().getId())) {
                            player.sendMessage(Text.literal(String.format("You have just joined %s!", team.getName())));
                        } else {
                            state.getInbox(profile.get().getId())
                                .pushMessage(new TextMessage(Text.literal(String.format("%s has joined our nation. " +
                                    "Welcome!", player.getEntityName()))));
                        }
                    });
                }
            }
            case "Decline" -> {
                player.sendMessage(Text.literal("Invite declined"));
                getLeader(server).ifPresent(leader -> {
                    state.getInbox(leader.getId())
                        .pushMessage(new TextMessage(Text.literal(String.format("%s declined your invite",
                            player.getEntityName()))));
                });
            }
        }
    }
}
