package net.pumpkincell.teambots.inbox;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import java.util.HashSet;
import java.util.Set;

public abstract class Message {
    long id = 0;

    public Message() {
    }

    public Message(NbtCompound tag) {
        this.id = tag.getLong("id");
    }

    public NbtCompound toNbt() {
        var tag = new NbtCompound();
        tag.putLong("id", this.id);
        return tag;
    }

    abstract Text format();

    // Get a list of possible actions
    Set<String> getActions() { return new HashSet<>(); }

    void handleClickEvent(String action) {
        // TODO
    }
}
