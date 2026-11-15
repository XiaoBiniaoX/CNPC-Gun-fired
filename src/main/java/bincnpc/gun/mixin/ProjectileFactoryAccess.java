package bincnpc.gun.mixin;

import net.minecraft.core.Position;
import net.minecraft.core.dispenser.AbstractProjectileDispenseBehavior;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Calls only the native factory, without dispensing, consuming items or spawning an entity. */
@Mixin(AbstractProjectileDispenseBehavior.class)
public interface ProjectileFactoryAccess {
    @Invoker("getProjectile") Projectile cnpcgun$projectile(Level level, Position position, ItemStack item);
}
