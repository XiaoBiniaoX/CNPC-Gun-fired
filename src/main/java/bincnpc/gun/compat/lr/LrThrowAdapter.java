package bincnpc.gun.compat.lr;

import bincnpc.gun.throwing.ThrowAdapter;
import bincnpc.gun.common.combat.CombatService;
import me.xjqsh.lrtactical.api.item.IThrowable;
import me.xjqsh.lrtactical.entity.GrenadeEntity;
import me.xjqsh.lrtactical.entity.ThrowableItemEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;

public final class LrThrowAdapter implements ThrowAdapter {
    @Override public boolean matches(ItemStack item) {
        return item != null && !item.isEmpty() && item.getItem() instanceof IThrowable throwable
            && throwable.getThrowableIndex(item).filter(index -> index.getData() != null && index.getData().getEntityData() != null
                && index.getType() != null && index.getType().factory() != null && index.getId() != null).isPresent();
    }
    @Override public java.util.List<ItemStack> catalogue() {
        var indexes = me.xjqsh.lrtactical.api.LrTacticalAPI.getThrowableIndexes();
        if (indexes == null) return java.util.List.of();
        return indexes.stream().filter(index -> index != null && index.getId() != null && index.getData() != null && index.getBaseItem() != null)
            .limit(4096).sorted(java.util.Comparator.comparing(index -> index.getId().toString()))
            .map(index -> index.createItemStack()).filter(this::matches).map(ItemStack::copy).toList();
    }
    @Override public Projectile create(ServerLevel level, EntityNPCInterface owner, ItemStack item) {
        if (level == null || owner == null || owner.level() != level || !CombatService.serverThread(owner)
                || !CombatService.validNpc(owner) || !matches(item)) return null;
        var index = ((IThrowable)item.getItem()).getThrowableIndex(item).orElse(null);
        return index == null ? null : index.createEntity(item.copyWithCount(1), owner);
    }
    @Override public Profile profile(Projectile projectile) {
        if (!(projectile instanceof ThrowableItemEntity item) || !matches(item.getItem())) return null;
        var index = ((IThrowable)item.getItem().getItem()).getThrowableIndex(item.getItem()).orElse(null);
        if (index == null || index.getData() == null) return null;
        double speed = index.getData().getInitialSpeed();
        return new Profile(speed, item.getGravity(), 0.99, Math.min(2, item.getBbWidth() * 0.5),
            item instanceof GrenadeEntity grenade ? Math.max(0, grenade.getRadius()) : 4.5,
            item.getLife() >= 0 ? Math.max(1, Math.min(1200, item.getLife())) : 600, false);
    }
    @Override public void detonate(Projectile projectile) {
        if (projectile instanceof ThrowableItemEntity item && !item.isRemoved()
                && item.level() instanceof ServerLevel level && level.getServer().isSameThread()) item.onDeath(null);
    }
}
