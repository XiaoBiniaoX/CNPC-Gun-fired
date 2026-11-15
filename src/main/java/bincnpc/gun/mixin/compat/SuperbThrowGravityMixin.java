package bincnpc.gun.mixin.compat;

import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Superb's M18 overrides its gravity getter with a constant; honor the synced vanilla no-gravity flag. */
@Mixin(targets = {"com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile", "com.atsuishio.superbwarfare.entity.projectile.M18SmokeGrenadeEntity"}, remap = false)
public abstract class SuperbThrowGravityMixin {
    @Inject(method = "getCustomGravity", at = @At("HEAD"), cancellable = true)
    private void cnpcgun$guidedGravity(CallbackInfoReturnable<Float> cir) {
        if (((Projectile)(Object)this).isNoGravity()) cir.setReturnValue(0F);
    }
}
