package bincnpc.gun.throwing;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;
import net.minecraft.world.phys.Vec3;

public interface ThrowAdapter {
    /** Additional server-authoritative item variants, including native data-pack indexes. */
    default java.util.List<ItemStack> catalogue() { return java.util.List.of(); }
    record Profile(double speed, double gravity, double drag, double radius, double blastRadius, int life, boolean customGravity) {
        public Profile {
            if (!Double.isFinite(speed) || speed <= 0 || speed > 40 || !Double.isFinite(gravity) || gravity < 0 || gravity > 2
                    || !Double.isFinite(drag) || drag <= 0 || drag > 1 || !Double.isFinite(radius) || radius < 0 || radius > 2
                    || !Double.isFinite(blastRadius) || blastRadius < 0 || blastRadius > 32 || life < 1 || life > 1200) throw new IllegalArgumentException("throw profile");
        }
    }
    boolean matches(ItemStack item);
    Projectile create(ServerLevel level, EntityNPCInterface owner, ItemStack item);
    Profile profile(Projectile projectile);
    void detonate(Projectile projectile);
    default void straight(Projectile projectile) { projectile.setNoGravity(true); }
    default void launch(Projectile projectile, Vec3 velocity) { projectile.shoot(velocity.x, velocity.y, velocity.z, (float)velocity.length(), 0); }
    default void guide(Projectile projectile, Vec3 velocity) { projectile.setDeltaMovement(velocity); }
    default void sync(Projectile projectile) { projectile.hasImpulse = true; projectile.hurtMarked = true; }
    default void restore(Projectile projectile, Profile profile) { projectile.setNoGravity(false); }
    default boolean settleAtLanding(Projectile projectile) { return false; }
}
