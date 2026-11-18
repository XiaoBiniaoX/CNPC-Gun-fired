package bincnpc.gun.mixin;

import bincnpc.gun.common.combat.SightHooks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityLivingBase.class, priority = 1100)
public abstract class NpcLongRangeSightMixin {
    @Inject(method = "canEntityBeSeen", at = @At("HEAD"), cancellable = true)
    private void cnpcgun$sight(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (SightHooks.handler != null) {
            Boolean visible = SightHooks.handler.apply((EntityLivingBase)(Object)this, target);
            if (visible != null) cir.setReturnValue(visible);
        }
    }
}
