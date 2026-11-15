package bincnpc.gun.mixin;

import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import top.cnpcplus.persist.NpcDataExternalStore;

@Mixin(value = NpcDataExternalStore.class, remap = false)
public interface ExternalStoreInvoker {
    @Invoker("<init>")
    static NpcDataExternalStore cnpcgun$create(String group, String idKey, String stateKey,
            Predicate<String> keys, Predicate<CompoundTag> probe) { throw new AssertionError("Mixin constructor invoker"); }
}
