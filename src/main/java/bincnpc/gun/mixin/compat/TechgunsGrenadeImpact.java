package bincnpc.gun.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value=techguns.entities.projectiles.GrenadeProjectile.class,remap=false)
public interface TechgunsGrenadeImpact {
    @Invoker("explode") void cnpcgun$detonate();
}
