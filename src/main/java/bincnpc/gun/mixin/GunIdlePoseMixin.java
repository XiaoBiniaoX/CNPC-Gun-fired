package bincnpc.gun.mixin;

import bincnpc.gun.client.animation.NpcAnimations;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forge arm-pose extensions share this entry point; idle NPCs retain their CNPC puppet pose. */
@Mixin(value = HumanoidModel.ArmPose.class, remap = false)
public abstract class GunIdlePoseMixin {
    @Inject(method = "applyTransform(Lnet/minecraft/client/model/HumanoidModel;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/HumanoidArm;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcgun$idleGunPose(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm, CallbackInfo ci) {
        if (entity instanceof EntityNPCInterface npc && NpcAnimations.idleGun(npc)) ci.cancel();
    }
}
