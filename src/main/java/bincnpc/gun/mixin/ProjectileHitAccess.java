package bincnpc.gun.mixin;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Virtual dispatch preserves the snowball, potion, egg and fireball's native impact effect. */
@Mixin(Projectile.class)
public interface ProjectileHitAccess {
    @Invoker("onHit") void cnpcgun$impact(HitResult hit);
}
