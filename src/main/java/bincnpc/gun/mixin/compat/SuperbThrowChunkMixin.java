package bincnpc.gun.mixin.compat;

import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile", remap = false)
public abstract class SuperbThrowChunkMixin {
    @Inject(method = "forceLoadChunk", at = @At("HEAD"), cancellable = true)
    private void cnpcgun$keepLoadedTerrain(CallbackInfoReturnable<Boolean> cir) {
        if (((Projectile)(Object)this).getPersistentData().hasUUID("CNPCGunThrowOwner")) cir.setReturnValue(false);
    }
}
