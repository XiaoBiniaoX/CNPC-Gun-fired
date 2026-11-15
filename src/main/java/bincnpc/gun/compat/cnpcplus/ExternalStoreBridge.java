package bincnpc.gun.compat.cnpcplus;

import bincnpc.gun.mixin.ExternalStoreInvoker;
import java.lang.reflect.*;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcplus.persist.NpcDataExternalStore;

/** Fixed, private-service adapter for the two verified CNPCplus store signatures. */
public final class ExternalStoreBridge {
    private final NpcDataExternalStore store;
    private final boolean modern;
    public ExternalStoreBridge(String group, String idKey, String stateKey, String dataKey) {
        try {
            modern = java.util.Arrays.stream(NpcDataExternalStore.class.getDeclaredConstructors())
                .anyMatch(ctor -> ctor.getParameterCount() == 5 && ctor.getParameterTypes()[3] == Predicate.class);
            if (modern) store = ExternalStoreInvoker.cnpcgun$create(group, idKey, stateKey, dataKey::equals,
                tag -> tag.contains(dataKey, net.minecraft.nbt.Tag.TAG_COMPOUND));
            else {
                Constructor<NpcDataExternalStore> ctor = NpcDataExternalStore.class.getDeclaredConstructor(String.class,
                    String.class, String.class, String[].class, Predicate.class);
                if (!ctor.trySetAccessible()) throw new IllegalStateException("CNPCplus legacy store constructor inaccessible");
                store = ctor.newInstance(group, idKey, stateKey, new String[]{dataKey},
                    (Predicate<CompoundTag>)tag -> tag.contains(dataKey, net.minecraft.nbt.Tag.TAG_COMPOUND));
            }
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Unsupported CNPCplus external store signature", e); }
    }
    public void load(EntityNPCInterface npc, CompoundTag tag) { store.prepareLoad(npc, tag); }
    public void save(EntityNPCInterface npc, CompoundTag tag) { store.saveNpc(npc, tag); }
    public boolean portable(CompoundTag tag, String idKey, String stateKey, String dataKey) {
        if (modern) return store.makePortable(tag);
        try {
            if (!tag.contains(dataKey, net.minecraft.nbt.Tag.TAG_COMPOUND) && tag.contains(idKey)) {
                UUID id = UUID.fromString(tag.getString(idKey));
                Method read = NpcDataExternalStore.class.getDeclaredMethod("readState", UUID.class);
                if (!read.trySetAccessible()) return false;
                Object state = read.invoke(store, id);
                if (state == null) return false;
                Method verify = NpcDataExternalStore.class.getDeclaredMethod("verifyData", state.getClass());
                if (!verify.trySetAccessible() || !Boolean.TRUE.equals(verify.invoke(store, state))) return false;
                Field data = state.getClass().getDeclaredField("data");
                if (!data.trySetAccessible()) return false;
                CompoundTag nativeData = (CompoundTag)data.get(state);
                if (!nativeData.contains(dataKey, net.minecraft.nbt.Tag.TAG_COMPOUND)) return false;
                tag.put(dataKey, nativeData.getCompound(dataKey).copy());
            }
            if (tag.contains(dataKey, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                tag.remove(idKey); tag.remove(stateKey);
            }
            return true;
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            bincnpc.gun.CNPCGun.LOGGER.warn("CNPCplus legacy portable materialization failed; original data retained", e); return false;
        }
    }
}
