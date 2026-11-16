package bincnpc.gun.compat.cnpcplus;

import bin.cnpcplus.data.NbtFiles;
import bincnpc.gun.CNPCGun;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import noppes.npcs.entity.EntityNPCInterface;

/** Content-addressed NPC blobs, using CNPCplus's atomic IO. Inline NBT is kept on every failure. */
public final class ExternalStoreBridge {
    private final String idKey, stateKey, dataKey;
    public ExternalStoreBridge(String group, String idKey, String stateKey, String dataKey) {
        if (!"cnpcgun".equals(group)) throw new IllegalArgumentException("private store namespace");
        this.idKey = idKey; this.stateKey = stateKey; this.dataKey = dataKey;
    }
    private static Path root() throws IOException {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || !server.isCallingFromMinecraftThread() || server.getWorld(0) == null) throw new IOException("NPC store requires the server thread");
        return server.getWorld(0).getSaveHandler().getWorldDirectory().toPath().resolve("cnpcgun").resolve("npcs").toAbsolutePath().normalize();
    }
    private static String hash(NBTTagCompound tag) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            CompressedStreamTools.writeCompressed(tag, out);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(out.toByteArray());
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new IOException(impossible); }
    }
    private NBTTagCompound read(String id) throws IOException {
        if (id == null || !id.matches("[0-9a-f]{64}")) throw new IOException("invalid NPC blob reference");
        Path folder = root(), path = folder.resolve(id + ".dat").normalize();
        if (!path.getParent().equals(folder)) throw new IOException("NPC blob path escaped namespace");
        NBTTagCompound envelope = NbtFiles.read(path);
        if (envelope == null || !envelope.hasKey("Data", 10) || !id.equals(envelope.getString("Hash"))) throw new IOException("missing NPC blob");
        NBTTagCompound data = envelope.getCompoundTag("Data");
        if (!id.equals(hash(data))) throw new IOException("NPC blob integrity mismatch");
        return data.copy();
    }
    public void load(EntityNPCInterface npc, NBTTagCompound tag) {
        if (tag.hasKey(dataKey, 10) || !tag.hasKey(idKey, 8)) return;
        try { tag.setTag(dataKey, read(tag.getString(idKey))); }
        catch (IOException | RuntimeException error) { CNPCGun.LOGGER.warn("NPC {} external weapons could not be restored; reference retained", npc.getUniqueID(), error); }
    }
    public void save(EntityNPCInterface npc, NBTTagCompound tag) {
        if (!tag.hasKey(dataKey, 10)) return;
        NBTTagCompound data = tag.getCompoundTag(dataKey).copy();
        try {
            String id = hash(data);
            Path path = root().resolve(id + ".dat");
            boolean present = false;
            if (Files.isRegularFile(path)) try { present = data.equals(read(id)); } catch (IOException invalid) { present = false; }
            if (!present) {
                NBTTagCompound envelope = new NBTTagCompound();
                envelope.setInteger("Version", 1); envelope.setString("Hash", id); envelope.setTag("Data", data);
                NbtFiles.writeNow(path, envelope);
            }
            if (!data.equals(read(id))) throw new IOException("NPC blob read-back mismatch");
            tag.setString(idKey, id); tag.setInteger(stateKey, 1); tag.removeTag(dataKey);
        } catch (IOException | RuntimeException error) {
            tag.setTag(dataKey, data);
            CNPCGun.LOGGER.warn("NPC {} weapons retained inline after external IO failure", npc.getUniqueID(), error);
        }
    }
    public boolean portable(NBTTagCompound tag, String idKey, String stateKey, String dataKey) {
        if (!this.idKey.equals(idKey) || !this.dataKey.equals(dataKey)) return false;
        try {
            if (!tag.hasKey(dataKey, 10) && tag.hasKey(idKey, 8)) tag.setTag(dataKey, read(tag.getString(idKey)));
            if (tag.hasKey(dataKey, 10)) { tag.removeTag(idKey); tag.removeTag(stateKey); }
            return true;
        } catch (IOException | RuntimeException error) { CNPCGun.LOGGER.warn("NPC clone weapons could not be materialized; source reference retained", error); return false; }
    }
}
