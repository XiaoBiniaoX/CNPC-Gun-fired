package bincnpc.gun.mixin.compat;

import bincnpc.gun.throwing.ThrowFlights;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value=com.flansmod.common.guns.EntityGrenade.class,remap=false)
public abstract class FlanGrenadeMixin {
    @ModifyConstant(method={"func_70071_h_","onUpdate"},constant=@Constant(doubleValue=0.024525))
    private double cnpcgun$gravity(double value){
        Entity projectile=(Entity)(Object)this;
        return ThrowFlights.managed(projectile)&&projectile.hasNoGravity()?0:value;
    }
}
