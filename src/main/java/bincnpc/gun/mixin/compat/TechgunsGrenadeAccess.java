package bincnpc.gun.mixin.compat;

import techguns.items.guns.IGrenadeProjectileFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value=techguns.items.guns.GenericGrenade.class,remap=false)
public interface TechgunsGrenadeAccess {
    @Accessor("projectile_factory") IGrenadeProjectileFactory<?> cnpcgun$factory();
}
