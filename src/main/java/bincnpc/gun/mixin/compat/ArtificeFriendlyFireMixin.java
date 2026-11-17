package bincnpc.gun.mixin.compat;

import bincnpc.gun.common.combat.FriendlyFire;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shared native predicate also covers blackpowder splash, soul damage and chain lightning. */
@Mixin(targets = "io.redspace.irons_artifice.utils.Utils", remap = false)
public abstract class ArtificeFriendlyFireMixin {
    @Inject(method = "canHarm(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)Z",
        at = @At("HEAD"), cancellable = true)
    private static void cnpcgun$skipProtectedTarget(Entity shooter, Entity victim, CallbackInfoReturnable<Boolean> cir) {
        if (FriendlyFire.isProtected(shooter, victim)) cir.setReturnValue(false);
    }
}
