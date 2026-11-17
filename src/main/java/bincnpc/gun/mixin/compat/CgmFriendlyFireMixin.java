package bincnpc.gun.mixin.compat;

import bincnpc.gun.common.combat.FriendlyFire;
import com.mrcrayfish.guns.entity.ProjectileEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.mrcrayfish.guns.entity.ProjectileEntity", remap = false)
public abstract class CgmFriendlyFireMixin {
    @Inject(method = "getHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Lcom/mrcrayfish/guns/entity/ProjectileEntity$EntityResult;",
        at = @At("HEAD"), cancellable = true)
    private void cnpcgun$skipProtectedHit(Entity victim, Vec3 start, Vec3 end, CallbackInfoReturnable<ProjectileEntity.EntityResult> cir) {
        if (FriendlyFire.isProtected(((ProjectileEntity)(Object)this).getShooter(), victim)) cir.setReturnValue(null);
    }
}
