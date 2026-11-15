package bincnpc.gun.common.persistence;

import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.compat.cnpcplus.ExternalStoreBridge;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.EntityNPCInterface;

/** Uses CNPCplus's verified external store, including its atomic IO and owner fork. */
public final class GunPersistence {
    public static final String KEY = "CNPCGunData";
    private static final String ID_KEY = "CNPCGunStoreId", STATE_KEY = "CNPCGunStoreState", SOURCE_KEY = "CNPCGunSource";
    private static ExternalStoreBridge store;
    private GunPersistence() {}
    private static ExternalStoreBridge store() {
        if (store == null) store = new ExternalStoreBridge("cnpcgun", ID_KEY, STATE_KEY, KEY);
        return store;
    }
    private static void requireData(EntityNPCInterface npc, CompoundTag tag) {
        if (npc == null || tag == null) throw new IllegalArgumentException("NPC persistence requires an NPC and an NBT compound");
    }
    public static void load(EntityNPCInterface npc, CompoundTag tag) {
        requireData(npc, tag);
        if (npc.level().isClientSide) return;
        store().load(npc, tag);
        if (tag.contains(KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) CombatService.data(npc).read(tag.getCompound(KEY));
    }
    public static void save(EntityNPCInterface npc, CompoundTag tag) {
        requireData(npc, tag);
        if (npc.level().isClientSide) { tag.putUUID(SOURCE_KEY, npc.getUUID()); return; }
        tag.put(KEY, CombatService.data(npc).write());
        store().save(npc, tag);
    }
    public static void memoryWrite(EntityNPCInterface npc, CompoundTag tag) {
        if (tag == null) return; // A missing linked template has no compound to augment.
        requireData(npc, tag); tag.put(KEY, CombatService.data(npc).write());
    }
    public static void memoryRead(EntityNPCInterface npc, CompoundTag tag) {
        if (tag == null) return;
        requireData(npc, tag);
        if (tag.contains(KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) CombatService.data(npc).read(tag.getCompound(KEY));
    }
    public static boolean portable(CompoundTag tag) {
        if (tag == null) return false;
        // Native client clone capture contains a source reference, not an empty authoritative blob.
        if (tag.hasUUID(SOURCE_KEY)) {
            var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            boolean restored = false;
            if (server != null && server.isSameThread()) for (var level : server.getAllLevels()) {
                var entity = level.getEntity(tag.getUUID(SOURCE_KEY));
                if (entity instanceof EntityNPCInterface npc && !npc.isRemoved()) { memoryWrite(npc, tag); restored = true; break; }
            }
            if (!restored) {
                bincnpc.gun.CNPCGun.LOGGER.warn("CNPC Gun clone source {} is unavailable; source reference retained", tag.getUUID(SOURCE_KEY));
                return false;
            }
        }
        if (!store().portable(tag, ID_KEY, STATE_KEY, KEY)) return false;
        tag.remove(SOURCE_KEY);
        return true;
    }
}
