package bincnpc.gun.mixin;

import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.util.math.RayTraceResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Fireballs have a separate native impact hierarchy in 1.12. */
@Mixin(EntityFireball.class)
public interface ProjectileFactoryAccess {
    @Invoker("onImpact") void cnpcgun$impact(RayTraceResult hit);
}
