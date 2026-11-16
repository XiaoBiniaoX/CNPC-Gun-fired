package bincnpc.gun.mixin;

import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.util.math.RayTraceResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Virtual dispatch keeps vanilla snowball, potion and egg effects. */
@Mixin(EntityThrowable.class)
public interface ProjectileHitAccess {
    @Invoker("onImpact") void cnpcgun$impact(RayTraceResult hit);
}
