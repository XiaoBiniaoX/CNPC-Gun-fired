package bincnpc.gun.mixin;

import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.entity.data.DataStats;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DataStats.class, remap = false)
public abstract class NpcAggroRangeMixin {
    @Shadow public int aggroRange;
    @Inject(method = "setAggroRange", at = @At("HEAD"), cancellable = true)
    private void cnpcgun$set(int range, CallbackInfo ci) { aggroRange = Math.max(0, Math.min(2048, range)); ci.cancel(); }
    @Inject(method = "readToNBT", at = @At("TAIL"))
    private void cnpcgun$load(NBTTagCompound tag, CallbackInfo ci) { aggroRange = Math.max(0, Math.min(2048, aggroRange)); }
}
