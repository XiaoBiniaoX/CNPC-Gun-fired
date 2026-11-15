package bincnpc.gun.mixin.compat;

import bincnpc.gun.client.animation.NpcAnimations;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The NPC renderer already applied the native body's transform to its whole rig. */
@Mixin(targets = "mods.flammpfeil.slashblade.client.renderer.layers.LayerMainBlade", remap = false)
public abstract class BladeLayerPoseMixin {
    @Inject(method = "setUserPose", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcgun$sharedPose(PoseStack pose, LivingEntity entity, float partialTick, CallbackInfo ci) {
        if (entity instanceof EntityNPCInterface npc && NpcAnimations.nativeRoot(npc)) ci.cancel();
    }
}
