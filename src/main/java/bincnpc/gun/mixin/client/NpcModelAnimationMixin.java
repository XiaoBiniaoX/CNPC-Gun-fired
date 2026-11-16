package bincnpc.gun.mixin.client;

import bincnpc.gun.client.animation.NpcAnimations;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={noppes.npcs.client.model.ModelBipedAlt.class,noppes.npcs.client.model.ModelPlayerAlt.class},remap=false)
public abstract class NpcModelAnimationMixin {
    @Inject(method={"func_78087_a","setRotationAngles"},at=@At("HEAD"))
    private void cnpcgun$restore(float limb,float amount,float age,float yaw,float pitch,float scale,Entity entity,CallbackInfo ci){NpcAnimations.restore((ModelBiped)(Object)this);}
    @Inject(method={"func_78087_a","setRotationAngles"},at=@At("TAIL"))
    private void cnpcgun$pose(float limb,float amount,float age,float yaw,float pitch,float scale,Entity entity,CallbackInfo ci){
        if(entity instanceof EntityNPCInterface)NpcAnimations.apply((EntityNPCInterface)entity,(ModelBiped)(Object)this,age-entity.ticksExisted);
    }
}
