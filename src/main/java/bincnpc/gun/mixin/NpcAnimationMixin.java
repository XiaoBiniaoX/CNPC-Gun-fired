package bincnpc.gun.mixin;

import bincnpc.gun.client.animation.NpcAnimations;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class NpcAnimationMixin {
    @Shadow protected EntityModel<LivingEntity> model;
    @Unique private boolean cnpcgun$rootPushed;
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void cnpcgun$restoreBefore(LivingEntity living, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        NpcAnimations.restore(model); cnpcgun$rootPushed = false;
    }
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", shift = At.Shift.AFTER))
    private void cnpcgun$pose(LivingEntity living, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (living instanceof EntityNPCInterface npc && model instanceof HumanoidModel<?> humanoid) {
            NpcAnimations.apply(npc, humanoid, partialTick);
            if (NpcAnimations.nativeRoot(npc)) { pose.pushPose(); cnpcgun$rootPushed = true; NpcAnimations.root(npc, pose, partialTick); }
        }
    }
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", ordinal = 0))
    private void cnpcgun$popRoot(LivingEntity living, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (living instanceof EntityNPCInterface npc && net.minecraftforge.fml.ModList.get().isLoaded("slashblade"))
            bincnpc.gun.client.animation.BladePreviewEffects.render(npc, pose, buffers, light, partialTick);
        if (cnpcgun$rootPushed) { pose.popPose(); cnpcgun$rootPushed = false; }
    }
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("RETURN"))
    private void cnpcgun$restoreAfter(LivingEntity living, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) { NpcAnimations.restore(model); }
}
