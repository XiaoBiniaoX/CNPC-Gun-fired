package bincnpc.gun.mixin.client;

import bincnpc.gun.client.animation.BipedAnimationHooks;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBiped.class)
public abstract class BipedAnimationMixin {
    @Inject(method="setRotationAngles",at=@At("HEAD"))
    private void cnpcgun$restore(float limb,float amount,float age,float yaw,float pitch,float scale,Entity entity,CallbackInfo ci){
        BipedAnimationHooks.before(this);
    }
    @Inject(method="setRotationAngles",at=@At("TAIL"))
    private void cnpcgun$pose(float limb,float amount,float age,float yaw,float pitch,float scale,Entity entity,CallbackInfo ci){
        // Armor item renderers legitimately call ModelBiped with no entity.
        if(entity==null)return;
        BipedAnimationHooks.after(this,entity,age-entity.ticksExisted);
    }
}
