package bincnpc.gun.common.network;

import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

/** Bounded strings and enum decoding for the unchanged editor protocol. */
public final class LegacyBuffer extends PacketBuffer {
    public LegacyBuffer(ByteBuf buffer) { super(buffer); }
    public UUID readUUID() { return readUniqueId(); }
    public void writeUUID(UUID id) { writeUniqueId(id); }
    public String readUtf(int maximum) {
        int bytes = readVarInt();
        if (bytes < 0 || bytes > (long)maximum * 4 || bytes > readableBytes()) throw new IllegalArgumentException("packet string limit");
        byte[] value = new byte[bytes]; readBytes(value);
        String text = new String(value, StandardCharsets.UTF_8);
        if (text.length() > maximum) throw new IllegalArgumentException("packet character limit");
        return text;
    }
    public void writeUtf(String text, int maximum) {
        if (text == null || text.length() > maximum) throw new IllegalArgumentException("packet string limit");
        byte[] value = text.getBytes(StandardCharsets.UTF_8);
        if (value.length > (long)maximum * 4) throw new IllegalArgumentException("packet byte limit");
        writeVarInt(value.length); writeBytes(value);
    }
    public <T extends Enum<T>> T readEnum(Class<T> type) {
        int index = readVarInt(); T[] values = type.getEnumConstants();
        if (index < 0 || index >= values.length) throw new IllegalArgumentException("packet operation");
        return values[index];
    }
    public void writeEnum(Enum<?> value) { writeVarInt(value.ordinal()); }
    public ItemStack readItem() { try { return readItemStack(); } catch (IOException error) { throw new IllegalArgumentException("packet item", error); } }
    public void writeItem(ItemStack item) { writeItemStack(item); }
}
