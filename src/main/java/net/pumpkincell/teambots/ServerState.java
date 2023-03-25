package net.pumpkincell.teambots;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class ServerState extends PersistentState {
    // Map of team name -> leader
    private HashMap<String, UUID> nationLeaders = new HashMap<>();

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        var tag = new NbtCompound();
        this.nationLeaders.forEach((k, v) -> {
            tag.putString(k, v.toString());
        });
        nbt.put("nationLeaders", tag);
        return nbt;
    }

    public static ServerState createFromNbt(NbtCompound tag) {
        ServerState serverState = new ServerState();
        var leaders = tag.getCompound("nationLeaders");
        leaders.getKeys().forEach(key -> {
            serverState.nationLeaders.put(key, UUID.fromString(leaders.getString(key)));
        });
        return serverState;
    }
    public static ServerState load(MinecraftServer server) {
        PersistentStateManager persistentStateManager = Objects.requireNonNull(server
            .getWorld(World.OVERWORLD)).getPersistentStateManager();

        ServerState serverState = persistentStateManager.getOrCreate(
            ServerState::createFromNbt,
            ServerState::new,
            "teambots");

        serverState.markDirty();

        return serverState;
    }

    public void setLeader(String teamName, UUID leader) {
        this.nationLeaders.put(teamName, leader);
        this.markDirty();
    }

    public void clearLeader(String teamName) {
        this.nationLeaders.remove(teamName);
        this.markDirty();
    }

    @Nullable
    public UUID getLeader(MinecraftServer server, String teamName) {
        return this.nationLeaders.get(teamName);
    }

    public boolean isLeader(MinecraftServer server, Team team, UUID maybeLeader) {
        var leader = getLeader(server, team.getName());
        if (leader == null) {
            return false;
        }
        return leader.equals(maybeLeader);
    }

    public Map<String, UUID> leaders() {
        return this.nationLeaders;
    }
}
