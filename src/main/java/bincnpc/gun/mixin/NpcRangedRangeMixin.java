package bincnpc.gun.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.data.DataRanged;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DataRanged.class, remap = false)
public abstract class NpcRangedRangeMixin {
    @Shadow private int rangedRange;
    @Inject(method = "setRange", at = @At("HEAD"), cancellable = true)
    private void cnpcgun$set(int range, CallbackInfo ci) { rangedRange = Math.max(0, Math.min(2048, range)); ci.cancel(); }
    @Inject(method = "load", at = @At("TAIL"))
    private void cnpcgun$load(CompoundTag tag, CallbackInfo ci) { rangedRange = Math.max(0, Math.min(2048, rangedRange)); }
}
