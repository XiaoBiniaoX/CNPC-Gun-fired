package bincnpc.gun.throwing;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraftforge.fml.ModList;
import noppes.npcs.entity.EntityNPCInterface;

/** Optional implementations are linked only when their owning mod is loaded. */
public final class ThrowAdapters {
    private static final ThrowAdapter VANILLA = new ThrowAdapter() {
        @Override public boolean matches(ItemStack item) { return item.is(Items.SNOWBALL) || item.is(Items.FIRE_CHARGE) || item.is(Items.EGG) || item.getItem() instanceof ThrowablePotionItem; }
        @Override public Projectile create(ServerLevel level, EntityNPCInterface owner, ItemStack item) {
            if (item.is(Items.FIRE_CHARGE)) {
                SmallFireball fire = new SmallFireball(level, owner, 0, 0, 0); fire.setItem(item.copyWithCount(1)); return fire;
            }
            ThrowableItemProjectile projectile = item.is(Items.SNOWBALL) ? new Snowball(level, owner)
                : item.is(Items.EGG) ? new ThrownEgg(level, owner) : new ThrownPotion(level, owner);
            projectile.setItem(item.copyWithCount(1)); return projectile;
        }
        @Override public Profile profile(Projectile projectile) {
            return new Profile(1.5, projectile instanceof ThrownPotion ? 0.05 : 0.03, projectile instanceof SmallFireball ? 0.95 : 0.99,
                Math.min(2, projectile.getBbWidth() * 0.5), projectile instanceof SmallFireball ? 1 : projectile instanceof ThrownPotion ? 4 : 0, 600, projectile instanceof SmallFireball);
        }
        @Override public void detonate(Projectile projectile) {
            if (!projectile.isRemoved() && projectile.level() instanceof ServerLevel) {
                ((bincnpc.gun.mixin.ProjectileHitAccess)projectile).cnpcgun$impact(new net.minecraft.world.phys.BlockHitResult(projectile.position(),
                    net.minecraft.core.Direction.UP, projectile.blockPosition().below(), false));
                if (!projectile.isRemoved()) projectile.discard();
            }
        }
    };
    private static final java.util.Map<String, ThrowAdapter> optional = new java.util.HashMap<>();
    private static final java.util.Set<String> reported = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private ThrowAdapters() {}
    public static ThrowAdapter find(ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        if (VANILLA.matches(item)) return VANILLA;
        for (String mod : new String[]{"lrtactical", "cgm", "superbwarfare"}) {
            try { ThrowAdapter adapter = optional(mod); if (adapter != null && adapter.matches(item)) return adapter; }
            catch (RuntimeException | LinkageError error) { report(mod, error); }
        }
        return null;
    }
    private static synchronized ThrowAdapter optional(String mod) {
        if (ModList.get() == null || !ModList.get().isLoaded(mod)) return null;
        return optional.computeIfAbsent(mod, id -> switch (id) {
            case "lrtactical" -> new bincnpc.gun.compat.lr.LrThrowAdapter();
            case "cgm" -> new bincnpc.gun.compat.cgm.CgmThrowAdapter();
            case "superbwarfare" -> new bincnpc.gun.compat.superb.SuperbThrowAdapter();
            default -> throw new IllegalArgumentException("throwable adapter");
        });
    }
    private static void report(String mod, Throwable error) {
        String key = mod + ":" + error.getClass().getName();
        if (reported.size() < 8 && reported.add(key)) bincnpc.gun.CNPCGun.LOGGER.warn("Optional throwable adapter {} unavailable", mod, error);
    }
    public static java.util.List<ItemStack> catalogue() {
        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        for (net.minecraft.world.item.Item item : net.minecraftforge.registries.ForgeRegistries.ITEMS) {
            ItemStack stack = new ItemStack(item);
            if (find(stack) != null && items.size() < 4096) items.add(stack);
        }
        for (String mod : new String[]{"lrtactical", "cgm", "superbwarfare"}) {
            try {
                ThrowAdapter adapter = optional(mod); if (adapter == null) continue;
                for (ItemStack stack : adapter.catalogue()) if (stack != null && !stack.isEmpty() && items.size() < 4096) items.add(stack.copyWithCount(1));
            } catch (RuntimeException | LinkageError error) { report(mod, error); }
        }
        return java.util.List.copyOf(items);
    }
}
