package net.pumpkincell.teambots.inbox;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

public class TextMessage extends Message {
    Text text;

    public TextMessage(NbtCompound tag) {
        super(tag);
        this.text = Text.Serializer.fromJson(tag.getString("text"));
    }

    public NbtCompound toNbt() {
        var tag = super.toNbt();
        tag.putString("type", "text");
        tag.putString("text", Text.Serializer.toJson(this.text));
        return tag;
    }

    public TextMessage(Text msg) {
        this.text = msg;
    }

    @Override
    public Text format(MinecraftServer server) {
        return this.text;
    }

    public String toString() {
        return String.format("%s", this.text.getString());
    }
}
