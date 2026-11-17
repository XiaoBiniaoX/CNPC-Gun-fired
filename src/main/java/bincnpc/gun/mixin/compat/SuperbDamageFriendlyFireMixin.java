package bincnpc.gun.mixin.compat;

import bincnpc.gun.common.combat.FriendlyFire;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Superb's optional force-damage fallback can bypass a canceled native hurt call. */
@Mixin(targets = "com.atsuishio.superbwarfare.tools.DamageHandler", remap = false)
public abstract class SuperbDamageFriendlyFireMixin {
    @Inject(method = "doDamage(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
        at = @At("HEAD"), cancellable = true)
    private static void cnpcgun$protectFromForcedDamage(Entity victim, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
        if (FriendlyFire.blocksDamage(source, victim)) cir.setReturnValue(false);
    }
}
