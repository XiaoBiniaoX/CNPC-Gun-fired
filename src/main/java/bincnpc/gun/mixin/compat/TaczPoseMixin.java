package bincnpc.gun.mixin.compat;

import bincnpc.gun.client.animation.NpcAnimations;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat", remap = false)
public abstract class TaczPoseMixin {
    @Inject(method = "hasPlayerAnimator3rd", at = @At("HEAD"), cancellable = true)
    private static void cnpcgun$nativeNpcClip(LivingEntity entity, GunDisplayInstance display, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof EntityNPCInterface npc && NpcAnimations.nativeGun(npc)) cir.setReturnValue(true);
    }
}
