package bincnpc.gun.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.data.DataStats;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DataStats.class, remap = false, priority = 1100)
public abstract class NpcAggroRangeMixin {
    @Shadow public int aggroRange;
    @ModifyVariable(method = "setAggroRange", at = @At("HEAD"), argsOnly = true)
    private int cnpcgun$set(int range) { return Math.max(0, Math.min(2048, range)); }
    @Inject(method = "readToNBT", at = @At("TAIL"))
    private void cnpcgun$load(CompoundTag tag, CallbackInfo ci) { aggroRange = Math.max(0, Math.min(2048, aggroRange)); }
}
