package bincnpc.gun.compat.cgm;

import bincnpc.gun.throwing.ThrowAdapter;
import com.mrcrayfish.guns.item.GrenadeItem;
import com.mrcrayfish.guns.entity.ThrowableGrenadeEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;

public final class CgmThrowAdapter implements ThrowAdapter {
    @Override public boolean matches(ItemStack item) { return item.getItem() instanceof GrenadeItem; }
    @Override public Projectile create(ServerLevel level, EntityNPCInterface owner, ItemStack item) {
        var grenade = ((GrenadeItem)item.getItem()).create(level, owner, Math.max(1, Math.min(1200, item.getUseDuration())));
        grenade.setItem(item.copyWithCount(1)); return grenade;
    }
    @Override public Profile profile(Projectile projectile) {
        var nativeData = (bincnpc.gun.mixin.compat.CgmThrowAccess)projectile;
        return new Profile(1, nativeData.cnpcgun$gravity(), 0.99, Math.min(2, projectile.getBbWidth() * 0.5),
            Math.max(0, com.mrcrayfish.guns.Config.COMMON.grenades.explosionRadius.get()), Math.max(1, Math.min(1200, nativeData.cnpcgun$life())), false);
    }
    @Override public void detonate(Projectile projectile) { projectile.discard(); ((ThrowableGrenadeEntity)projectile).onDeath(); }
}
