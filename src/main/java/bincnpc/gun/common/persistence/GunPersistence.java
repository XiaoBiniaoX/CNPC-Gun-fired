package bincnpc.gun.common.persistence;

import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.compat.cnpcplus.ExternalStoreBridge;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.entity.EntityNPCInterface;

/** Uses CNPCplus's verified external store, including its atomic IO and owner fork. */
public final class GunPersistence {
    public static final String KEY = "CNPCGunData";
    private static final String ID_KEY = "CNPCGunStoreId", STATE_KEY = "CNPCGunStoreState", SOURCE_KEY = "CNPCGunSource";
    private static ExternalStoreBridge store;
    private GunPersistence() {}
    public static void clear() { store = null; }
    private static ExternalStoreBridge store() {
        if (store == null) store = new ExternalStoreBridge("cnpcgun", ID_KEY, STATE_KEY, KEY);
        return store;
    }
    private static void requireData(EntityNPCInterface npc, NBTTagCompound tag) {
        if (npc == null || tag == null) throw new IllegalArgumentException("NPC persistence requires an NPC and an NBT compound");
    }
    public static void load(EntityNPCInterface npc, NBTTagCompound tag) {
        requireData(npc, tag);
        if (npc.world.isRemote) return;
        store().load(npc, tag);
        bincnpc.gun.common.npc.NpcData data = CombatService.data(npc);
        data.persistenceAvailable = tag.hasKey(KEY, 10) || !tag.hasKey(ID_KEY, 8);
        data.unrestoredReference = null;
        if (!data.persistenceAvailable) {
            data.unrestoredReference = new NBTTagCompound();
            data.unrestoredReference.setString(ID_KEY, tag.getString(ID_KEY));
            if (tag.hasKey(STATE_KEY)) data.unrestoredReference.setTag(STATE_KEY, tag.getTag(STATE_KEY).copy());
        } else if (tag.hasKey(KEY, 10)) data.read(tag.getCompoundTag(KEY));
    }
    public static void save(EntityNPCInterface npc, NBTTagCompound tag) {
        requireData(npc, tag);
        if (npc.world.isRemote) { tag.setUniqueId(SOURCE_KEY, npc.getUniqueID()); return; }
        if (!CombatService.data(npc).persistenceAvailable) { retainReference(npc, tag); return; }
        tag.setTag(KEY, CombatService.data(npc).write());
        store().save(npc, tag);
    }
    public static void memoryWrite(EntityNPCInterface npc, NBTTagCompound tag) {
        if (tag == null) return; // A missing linked template has no compound to augment.
        requireData(npc, tag);
        if (npc.world.isRemote) { tag.removeTag(KEY); tag.setUniqueId(SOURCE_KEY, npc.getUniqueID()); return; }
        if (!CombatService.data(npc).persistenceAvailable) { retainReference(npc, tag); return; }
        tag.setTag(KEY, CombatService.data(npc).write());
    }
    private static void retainReference(EntityNPCInterface npc, NBTTagCompound tag) {
        tag.removeTag(KEY);
        NBTTagCompound reference = CombatService.data(npc).unrestoredReference;
        if (reference != null) for (String key : reference.getKeySet()) tag.setTag(key, reference.getTag(key).copy());
    }
    public static void memoryRead(EntityNPCInterface npc, NBTTagCompound tag) {
        if (tag == null) return;
        requireData(npc, tag);
        if (tag.hasKey(KEY, 10)) {
            CombatService.data(npc).read(tag.getCompoundTag(KEY));
            CombatService.data(npc).persistenceAvailable = true; CombatService.data(npc).unrestoredReference = null;
        }
    }
    public static boolean portable(NBTTagCompound tag) {
        if (tag == null) return false;
        // Native client clone capture contains a source reference, not an empty authoritative blob.
        if (tag.hasUniqueId(SOURCE_KEY)) {
            net.minecraft.server.MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
            boolean restored = false;
            if (server != null && server.isCallingFromMinecraftThread()) for (net.minecraft.world.WorldServer level : server.worlds) {
Object cnpcgun$value0; EntityNPCInterface cnpcgun$match0;

                net.minecraft.entity.Entity entity = level.getEntityFromUuid(tag.getUniqueId(SOURCE_KEY));
                if (((cnpcgun$value0 = entity) instanceof EntityNPCInterface && (cnpcgun$match0 = (EntityNPCInterface)cnpcgun$value0) != null) && !cnpcgun$match0.isDead && CombatService.data(cnpcgun$match0).persistenceAvailable) { memoryWrite(cnpcgun$match0, tag); restored = true; break; }
            }
            if (!restored) {
                bincnpc.gun.CNPCGun.LOGGER.warn("CNPC Gun clone source {} is unavailable; source reference retained", tag.getUniqueId(SOURCE_KEY));
                return false;
            }
        }
        if (!store().portable(tag, ID_KEY, STATE_KEY, KEY)) return false;
        tag.removeTag(SOURCE_KEY);
        return true;
    }
}
