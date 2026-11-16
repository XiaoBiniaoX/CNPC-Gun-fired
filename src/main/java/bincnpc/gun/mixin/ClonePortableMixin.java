package bincnpc.gun.mixin;

import bincnpc.gun.common.persistence.GunPersistence;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.ServerCloneController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerCloneController.class, remap = false)
public abstract class ClonePortableMixin {
    @Inject(method = "saveClone", at = @At("HEAD"), cancellable = true)
    private void cnpcgun$portableClone(int tab, String name, NBTTagCompound data, CallbackInfo ci) {
        if (!GunPersistence.portable(data)) {
            bincnpc.gun.CNPCGun.LOGGER.warn("CNPC Gun prevented incomplete clone export: tab {}, name {}", tab, name);
            ci.cancel();
        }
    }
}
