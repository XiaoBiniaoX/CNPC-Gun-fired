package bincnpc.gun.mixin.compat;

import bincnpc.gun.throwing.ThrowFlights;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=techguns.entities.projectiles.GenericProjectile.class,remap=false)
public abstract class TechgunsProjectileMixin {
    @Inject(method={"func_70071_h_","onUpdate"},at=@At("HEAD"),cancellable=true)
    private void cnpcgun$fuse(CallbackInfo ci){
        Entity projectile=(Entity)(Object)this;
        if(!projectile.world.isRemote&&!projectile.isDead&&ThrowFlights.managed(projectile)
            &&projectile instanceof TechgunsGrenadeImpact&&((TechgunsProjectileAccess)projectile).cnpcgun$life()<=1){
            ((TechgunsGrenadeImpact)projectile).cnpcgun$detonate();ci.cancel();
        }
    }
}
