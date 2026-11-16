package bincnpc.gun.throwing;

import net.minecraft.world.WorldServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IProjectile;
import net.minecraft.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;
import net.minecraft.util.math.Vec3d;
import bincnpc.gun.common.legacy.Platform;

public interface ThrowAdapter {
    default java.util.List<ItemStack> catalogue() { return java.util.Collections.emptyList(); }
    final class Profile {
        private final double speed, gravity, drag, radius, blastRadius;
        private final int life;
        private final boolean customGravity;
        public Profile(double speed, double gravity, double drag, double radius, double blastRadius, int life, boolean customGravity) {
            if (!Double.isFinite(speed) || speed <= 0 || speed > 40 || !Double.isFinite(gravity) || gravity < 0 || gravity > 2
                || !Double.isFinite(drag) || drag <= 0 || drag > 1 || !Double.isFinite(radius) || radius < 0 || radius > 2
                || !Double.isFinite(blastRadius) || blastRadius < 0 || blastRadius > 32 || life < 1 || life > 1200)
                throw new IllegalArgumentException("throw profile");
            this.speed=speed;this.gravity=gravity;this.drag=drag;this.radius=radius;this.blastRadius=blastRadius;this.life=life;this.customGravity=customGravity;
        }
        public double speed(){return speed;} public double gravity(){return gravity;} public double drag(){return drag;}
        public double radius(){return radius;} public double blastRadius(){return blastRadius;} public int life(){return life;}
        public boolean customGravity(){return customGravity;}
    }
    boolean matches(ItemStack item);
    Entity create(WorldServer level, EntityNPCInterface owner, ItemStack item);
    Profile profile(Entity projectile);
    void detonate(Entity projectile);
    default void straight(Entity projectile) { projectile.setNoGravity(true); }
    default void launch(Entity projectile, Vec3d velocity) {
        if (projectile instanceof IProjectile) ((IProjectile)projectile).shoot(velocity.x, velocity.y, velocity.z, (float)velocity.length(), 0);
        else {
            Platform.motion(projectile, velocity);
            projectile.rotationYaw=(float)Math.toDegrees(Math.atan2(velocity.x, velocity.z));
            projectile.rotationPitch=(float)Math.toDegrees(Math.atan2(velocity.y, Math.sqrt(Platform.horizontalSquare(velocity))));
            projectile.prevRotationYaw=projectile.rotationYaw;projectile.prevRotationPitch=projectile.rotationPitch;
        }
    }
    default void guide(Entity projectile, Vec3d velocity) { Platform.motion(projectile, velocity); }
    default void sync(Entity projectile) { projectile.velocityChanged=true; }
    default void restore(Entity projectile, Profile profile) { projectile.setNoGravity(false); }
    default boolean settleAtLanding(Entity projectile) { return false; }
}
