package bincnpc.gun.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value=techguns.entities.projectiles.GenericProjectile.class,remap=false)
public interface TechgunsProjectileAccess {
    @Accessor("gravity") double cnpcgun$gravity();
    @Accessor("gravity") void cnpcgun$gravity(double value);
    @Accessor("lifetime") int cnpcgun$life();
    @Accessor("damageDropEnd") float cnpcgun$blastRadius();
}
