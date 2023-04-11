package net.pumpkincell.teambots.inbox;

import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.SimpleRegistry;
import net.pumpkincell.teambots.TeamBotsMod;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/// Class for managing messages sent to an offline player.
public class Inbox {
    private static final Logger LOGGER = TeamBotsMod.LOGGER;

    private final List<Message> messages = new LinkedList<>();
    private final Map<Long, Message> messagesById = new HashMap<>();
    private long lastId = 0;

    private ServerPlayerEntity player;

    public Inbox(@Nullable ServerPlayerEntity entity, NbtCompound tag) {
        var messages = tag.getList("messages", NbtElement.COMPOUND_TYPE);
        for (var message : messages) {
            loadMessage((NbtCompound) message);
        }
        this.lastId = tag.getLong("lastId");
    }

    private void loadMessage(NbtCompound tag) {
        var type = tag.getString("type");
        switch (type) {
            case "text" -> pushMessage(new TextMessage(tag));
        }
    }

    public NbtCompound toNbt() {
        var tag = new NbtCompound();

        var messages = new NbtList();
        for (var message : this.messages) {
            messages.add(message.toNbt());
        }
        tag.put("messages", messages);
        tag.putLong("lastId", this.lastId);

        return tag;
    }

    public void setPlayer(@Nullable ServerPlayerEntity player) {
        this.player = player;
        if (this.player != null) {
            this.flush();
        }
    }

    public void pushMessage(Message message) {
        LOGGER.info("{}", message);
        if (this.player != null) {
            this.sendMessage(message);
            return;
        }

        if (message.id == 0) {
            message.id = ++this.lastId;
        }
        this.messages.add(message);
        this.messagesById.put(message.id, message);
    }

    private void sendMessage(Message message) {
        assert this.player != null;
        this.player.sendMessage(Text.literal("∙ ").formatted(Formatting.AQUA).append(message.format()));
    }

    // Send the Inbox as a chat messages.
    private void flush() {
        assert this.player != null;
        for (var message : this.messages) {
            this.sendMessage(message);
        }
        this.messages.clear();
        this.messagesById.clear();
    }

    public void handleClickEventCommand(long msgid, String action) {
        var msg = this.messagesById.get(msgid);
        if (msg == null) {
            throw new IllegalArgumentException("Invalid message id");
        }
        msg.handleClickEvent(action);
    }
}
