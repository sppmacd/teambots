package net.pumpkincell.teambots.inbox;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.pumpkincell.teambots.TeamBotsMod;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/// Class for managing messages sent to an offline player.
public class Inbox {
    private static final Logger LOGGER = TeamBotsMod.LOGGER;

    private final List<Message> storedMessages = new LinkedList<>();
    private final Map<Long, Message> messagesWithPendingAction = new HashMap<>();
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
            case "nation_invite" -> pushMessage(new NationInviteMessage(tag));
        }
    }

    public NbtCompound toNbt() {
        var tag = new NbtCompound();

        var messages = new NbtList();
        for (var message : this.storedMessages) {
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

        if (message.id == 0) {
            message.id = ++this.lastId;
        }
        if (this.player != null) {
            this.sendMessage(message);
        } else {
            this.storedMessages.add(message);
        }
    }

    private void sendMessage(Message message) {
        assert this.player != null;
        var chatMsg = Text.literal("∙ ").formatted(Formatting.GOLD).append(message.format(this.player.server));

        var actions = message.getActions();
        if (!actions.isEmpty()) {
            for (var action : actions) {
                var command = String.format("/~inbox clickevent %s %s", message.id, action);
                chatMsg.append(Text.literal(" "));
                chatMsg.append(Text.literal(action).formatted(Formatting.AQUA, Formatting.UNDERLINE)
                    .styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))));
            }
        }

        this.messagesWithPendingAction.put(message.id, message);
        this.player.sendMessage(chatMsg);
    }

    // Send the Inbox as a chat messages.
    private void flush() {
        assert this.player != null;
        for (var message : this.storedMessages) {
            this.sendMessage(message);
        }
        this.storedMessages.clear();
    }

    public void handleClickEventCommand(ServerPlayerEntity entity, long msgid, String action) {
        var msg = this.messagesWithPendingAction.get(msgid);
        if (msg == null) {
            return;
        }
        msg.handleAction(entity, action);
        this.messagesWithPendingAction.remove(msgid);
    }
}
