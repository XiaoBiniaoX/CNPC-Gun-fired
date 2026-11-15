package bincnpc.gun.compat.superb;

import bincnpc.gun.throwing.ThrowAdapter;
import com.atsuishio.superbwarfare.item.HandGrenade;
import com.atsuishio.superbwarfare.item.DispenserLaunchable;
import bincnpc.gun.mixin.ProjectileFactoryAccess;
import com.atsuishio.superbwarfare.item.RgoGrenade;
import com.atsuishio.superbwarfare.item.projectile.M18SmokeGrenadeItem;
import com.atsuishio.superbwarfare.entity.projectile.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.core.dispenser.AbstractProjectileDispenseBehavior;
import net.minecraft.core.PositionImpl;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;
import net.minecraft.world.phys.Vec3;

public final class SuperbThrowAdapter implements ThrowAdapter {
    @Override public boolean matches(ItemStack item) { return item != null && (item.getItem() instanceof HandGrenade || item.getItem() instanceof RgoGrenade || item.getItem() instanceof M18SmokeGrenadeItem); }
    @Override public Projectile create(ServerLevel level, EntityNPCInterface owner, ItemStack item) {
        if (level == null || owner == null || !matches(item) || !(item.getItem() instanceof DispenserLaunchable launchable)) return null;
        var behavior = launchable.getLaunchBehavior();
        if (!(behavior instanceof AbstractProjectileDispenseBehavior)) return null;
        Projectile projectile = ((ProjectileFactoryAccess)behavior).cnpcgun$projectile(level,
            new PositionImpl(owner.getX(), owner.getEyeY() - 0.18, owner.getZ()), item.copyWithCount(1));
        if (!(projectile instanceof FastThrowableProjectile)) { if (projectile != null) projectile.discard(); return null; }
        projectile.setOwner(owner);
        if (projectile instanceof ThrowableItemProjectile thrown) thrown.setItem(item.copyWithCount(1));
        return projectile;
    }
    @Override public Profile profile(Projectile projectile) {
        var nativeData = (FastThrowableProjectile)projectile; boolean bounce = projectile instanceof BounceProjectile;
        return new Profile(1.5, Math.max(0, Math.min(2, nativeData.getCustomGravity() * (bounce ? 0.98 : 1))), bounce ? 0.98 : 1,
            Math.min(2, projectile.getBbWidth() * 0.5), Math.max(0, nativeData.getExplosionRadius()), Math.max(1, Math.min(1200, nativeData.getLife())), false);
    }
    @Override public void straight(Projectile projectile) { projectile.setNoGravity(true); ((FastThrowableProjectile)projectile).setCustomGravity(0); }
    @Override public void launch(Projectile projectile, Vec3 velocity) {
        ThrowAdapter.super.launch(projectile, velocity);
        if (projectile instanceof BounceProjectile) projectile.setDeltaMovement(velocity.scale(1 / 0.98).add(0, ((FastThrowableProjectile)projectile).getCustomGravity(), 0));
    }
    @Override public void guide(Projectile projectile, Vec3 velocity) { projectile.setDeltaMovement(projectile instanceof BounceProjectile ? velocity.scale(1 / 0.98) : velocity); }
    @Override public void sync(Projectile projectile) { ThrowAdapter.super.sync(projectile); ((FastThrowableProjectile)projectile).syncMotion(); }
    @Override public void restore(Projectile projectile, Profile profile) {
        projectile.setNoGravity(false); ((FastThrowableProjectile)projectile).setCustomGravity((float)(profile.gravity() / (projectile instanceof BounceProjectile ? 0.98 : 1)));
    }
    @Override public boolean settleAtLanding(Projectile projectile) { return projectile instanceof M18SmokeGrenadeEntity; }
    @Override public void detonate(Projectile projectile) {
        if (projectile instanceof M18SmokeGrenadeEntity smoke) smoke.releaseSmoke();
        else ((FastThrowableProjectile)projectile).causeExplode(projectile.position());
        projectile.discard();
    }
}
