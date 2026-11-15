package bincnpc.gun.mixin;

import bincnpc.gun.common.persistence.GunPersistence;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.persist.NpcDataExternalStore;

@Mixin(value = NpcDataExternalStore.class, remap = false)
public abstract class ExternalPortableMixin {
    @Inject(method = "makeAllPortable", at = @At("RETURN"), cancellable = true)
    private static void cnpcgun$portable(CompoundTag tag, CallbackInfoReturnable<Boolean> cir) {
        if (!GunPersistence.portable(tag)) cir.setReturnValue(false);
    }
}
