package bincnpc.gun.mixin.compat;

import bincnpc.gun.compat.superb.SuperbFireRate;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Apply the NPC override after native fire-mode, custom-property and perk RPM modifiers. */
@Mixin(value = GunData.class, remap = false)
public abstract class SuperbFireRateMixin {
    @Inject(method = "get(Lcom/atsuishio/superbwarfare/data/gun/GunProp;)Ljava/lang/Object;", at = @At("RETURN"), cancellable = true)
    private void cnpcgun$effectiveRpm(GunProp<?, ?> property, CallbackInfoReturnable<Object> cir) {
        if (property != GunProp.RPM) return;
        Integer rpm = SuperbFireRate.configuredRpm(((GunData)(Object)this).stack);
        if (rpm != null) cir.setReturnValue(rpm);
    }
}
