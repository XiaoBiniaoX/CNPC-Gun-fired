package bincnpc.gun.mixin.compat;

import bincnpc.gun.common.combat.FriendlyFire;
import com.atsuishio.superbwarfare.world.phys.EntityResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.atsuishio.superbwarfare.entity.projectile.ProjectileEntity", remap = false)
public abstract class SuperbFriendlyFireMixin {
    @Inject(method = "getHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Lcom/atsuishio/superbwarfare/world/phys/EntityResult;",
        at = @At("HEAD"), cancellable = true)
    private void cnpcgun$skipProtectedHit(Entity victim, Vec3 start, Vec3 end, CallbackInfoReturnable<EntityResult> cir) {
        if (FriendlyFire.isProtected((Entity)(Object)this, victim)) cir.setReturnValue(null);
    }
}
