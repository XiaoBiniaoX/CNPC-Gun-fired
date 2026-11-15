package bincnpc.gun.compat.lr;

import bincnpc.gun.throwing.ThrowAdapter;
import me.xjqsh.lrtactical.api.item.IThrowable;
import me.xjqsh.lrtactical.entity.GrenadeEntity;
import me.xjqsh.lrtactical.entity.ThrowableItemEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;

public final class LrThrowAdapter implements ThrowAdapter {
    @Override public boolean matches(ItemStack item) {
        return item != null && item.getItem() instanceof IThrowable throwable
            && throwable.getThrowableIndex(item).filter(index -> index.getData() != null && index.getType() != null).isPresent();
    }
    @Override public java.util.List<ItemStack> catalogue() {
        var indexes = me.xjqsh.lrtactical.api.LrTacticalAPI.getThrowableIndexes();
        if (indexes == null) return java.util.List.of();
        return indexes.stream().filter(index -> index != null && index.getId() != null && index.getData() != null && index.getBaseItem() != null)
            .sorted(java.util.Comparator.comparing(index -> index.getId().toString())).limit(4096)
            .map(index -> index.createItemStack()).filter(this::matches).map(ItemStack::copy).toList();
    }
    @Override public Projectile create(ServerLevel level, EntityNPCInterface owner, ItemStack item) {
        return ((IThrowable)item.getItem()).getThrowableIndex(item).orElseThrow().createEntity(item.copyWithCount(1), owner);
    }
    @Override public Profile profile(Projectile projectile) {
        var item = (ThrowableItemEntity)projectile;
        double speed = item.getItem().getItem() instanceof IThrowable throwable
            ? throwable.getThrowableIndex(item.getItem()).map(index -> index.getData().getInitialSpeed()).orElse(1.5) : 1.5;
        return new Profile(speed, item.getGravity(), 0.99, Math.min(2, item.getBbWidth() * 0.5),
            item instanceof GrenadeEntity grenade ? Math.max(0, grenade.getRadius()) : 4.5, item.getLife() > 0 ? Math.min(1200, item.getLife()) : 600, false);
    }
    @Override public void detonate(Projectile projectile) { ((ThrowableItemEntity)projectile).onDeath(null); }
}
