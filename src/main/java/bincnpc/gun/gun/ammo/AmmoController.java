package bincnpc.gun.gun.ammo;

import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.adapter.AdapterRegistry;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.gun.adapter.GunAdapter;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;

public final class AmmoController {
    private final Map<String, Integer> reserve = new LinkedHashMap<>();
    public int independent(String key) { return reserve.getOrDefault(key, 0); }
    public String key(CombatContext ctx, String ammoKey, ItemStack weapon) {
        return "slot:" + ctx.data.armory.slotOf(ctx.npc, weapon) + ":" + ammoKey;
    }
    public String key(CombatContext ctx, GunAdapter adapter, ItemStack weapon) {
        String ammoKey = AdapterRegistry.query(adapter, () -> adapter.ammoKey(weapon), null);
        if (ammoKey == null || ammoKey.isEmpty() || ctx.data.armory.slotOf(ctx.npc, weapon) < 0) return null;
        String key = key(ctx, ammoKey, weapon);
        return key.length() <= 256 ? key : null;
    }
    public int independent(CombatContext ctx, GunAdapter adapter, ItemStack weapon) {
        String key = key(ctx, adapter, weapon);
        return key == null ? 0 : independent(key);
    }
    public void clearDefaultReserve() { reserve.clear(); }
    public int credit(CombatContext ctx, GunAdapter adapter, ItemStack weapon, int amount) {
        if (!CombatService.serverThread(ctx.npc)) return 0;
        String key = key(ctx, adapter, weapon);
        if (key == null || reserve.size() >= 64 && !reserve.containsKey(key)) return 0;
        int credited = Math.max(0, Math.min(amount, ctx.config.integer("ammo.reserveLimit") - independent(key)));
        if (credited > 0) { reserve.put(key, independent(key) + credited); resumeAfterSupply(ctx); }
        return credited;
    }
    /** Plan every matching deposit before consuming the ground item. */
    public boolean creditPickup(CombatContext ctx, Collection<String> matching, int units) {
        if (!CombatService.serverThread(ctx.npc) || units <= 0 || units > 100000 || matching.isEmpty() || matching.size() > 6) return false;
        List<String> keys = new ArrayList<>(new LinkedHashSet<>(matching));
        Map<String, Integer> credits = new LinkedHashMap<>();
        int left = units, freeKeys = Math.max(0, 64 - reserve.size()), limit = ctx.config.integer("ammo.reserveLimit");
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            if (key == null || key.length() > 256 || !key.startsWith("slot:")) return false;
            boolean fresh = !reserve.containsKey(key);
            int capacity = fresh && freeKeys == 0 ? 0 : Math.max(0, limit - independent(key));
            int portion = Math.min(capacity, (left + keys.size() - i - 1) / (keys.size() - i));
            if (portion > 0) { credits.put(key, portion); left -= portion; if (fresh) freeKeys--; }
        }
        for (String key : keys) {
            if (left == 0) break;
            int planned = credits.getOrDefault(key, 0);
            boolean fresh = !reserve.containsKey(key) && planned == 0;
            if (fresh && freeKeys == 0) continue;
            int portion = Math.min(left, Math.max(0, limit - independent(key) - planned));
            if (portion > 0) { credits.put(key, planned + portion); left -= portion; if (fresh) freeKeys--; }
        }
        if (left != 0) return false;
        credits.forEach((key, amount) -> reserve.put(key, independent(key) + amount));
        resumeAfterSupply(ctx);
        return true;
    }
    private void resumeAfterSupply(CombatContext ctx) {
        ctx.runtime.noAmmoRetry = 0; ctx.runtime.noAmmoEvent = false;
        if (!ctx.runtime.noAmmoCommand.isEmpty()) {
            if (ctx.runtime.noAmmoCommand.equals(ctx.runtime.command)) {
                ctx.runtime.command = "AUTO"; ctx.runtime.fireStopped = false; ctx.runtime.nextDecision = ctx.now;
            }
            ctx.runtime.noAmmoCommand = "";
        }
    }
    public int available(CombatContext ctx, GunAdapter adapter) {
        return available(ctx, adapter, ctx.weapon);
    }
    public int available(CombatContext ctx, GunAdapter adapter, ItemStack weapon) {
        if (!CombatService.serverThread(ctx.npc)) return 0;
        if (ctx.config.bool("ammo.infinite")) return 100000;
        int count = allowsIndependent(ctx) ? Math.max(0, independent(ctx, adapter, weapon) - ctx.config.integer("ammo.minimumReserve")) : 0;
        if (allowsItems(ctx)) {
            AmmoInventory inventory = new AmmoInventory(ctx.npc);
            for (int i = 0; i < inventory.getSlots(); i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                count = (int)Math.min(100000L, (long)count + Math.max(0, adapter.ammoUnits(weapon, stack)));
            }
        }
        return count;
    }
    public static boolean allowsIndependent(CombatContext ctx) { return AmmoPolicy.allowsReserve(ctx.config.text("ammo.source")); }
    public static boolean allowsItems(CombatContext ctx) { return AmmoPolicy.allowsItems(ctx.config.text("ammo.source"), ctx.config.bool("ammo.infinite")); }
    public static boolean reserveFirst(CombatContext ctx) { return AmmoPolicy.reserveFirst(ctx.config.text("ammo.priority")); }

    public int take(CombatContext ctx, GunAdapter adapter, int requested) {
        if (!CombatService.serverThread(ctx.npc)) return 0;
        int count = Math.max(0, Math.min(100000, requested));
        if (ctx.config.bool("ammo.infinite")) return count;
        String key = adapter.ammoKey(ctx.weapon);
        int taken = reserveFirst(ctx) ? takeReserve(ctx, key, count) : 0;
        if (allowsItems(ctx)) {
            AmmoInventory inventory = new AmmoInventory(ctx.npc);
            for (int i = 0; i < inventory.getSlots() && taken < count; i++) {
                ItemStack item = inventory.getStackInSlot(i);
                int extracted = adapter.extractAmmo(ctx.weapon, item, count - taken);
                if (extracted > 0) { taken += extracted; inventory.changed(i); }
            }
        }
        if (!reserveFirst(ctx)) taken += takeReserve(ctx, key, count - taken);
        return taken;
    }

    public int takeReserve(CombatContext ctx, String key, int requested) {
        if (!CombatService.serverThread(ctx.npc)) return 0;
        if (ctx.config.bool("ammo.infinite")) return Math.max(0, Math.min(100000, requested));
        if (!allowsIndependent(ctx)) return 0;
        key = key(ctx, key, ctx.weapon);
        int old = independent(key);
        int count = Math.max(0, Math.min(requested, old - ctx.config.integer("ammo.minimumReserve")));
        if (old == count) reserve.remove(key); else if (count > 0) reserve.put(key, old - count);
        return count;
    }

    public Result supply(CombatContext ctx, GunAdapter adapter, int count, boolean replace, boolean script) {
        if (count < 0 || count > 100000) return Result.fail("INVALID_ARGUMENT");
        if (!replace && ctx.now < ctx.runtime.lastSupply + ctx.config.integer("ammo.supplyCooldown")) return Result.fail("COOLDOWN");
        if (!CombatService.serverThread(ctx.npc)) return Result.fail("PERMISSION_DENIED");
        String key = key(ctx, adapter, ctx.weapon);
        if (key == null) return Result.fail("ADAPTER_UNAVAILABLE");
        long next = replace ? count : (long)independent(key) + count;
        if (next > ctx.config.integer("ammo.reserveLimit")) return Result.fail("INVALID_ARGUMENT");
        if (reserve.size() >= 64 && !reserve.containsKey(key)) return Result.fail("CONFLICT");
        if (next == 0) reserve.remove(key); else reserve.put(key, (int)next);
        ctx.runtime.lastSupply = ctx.now; ctx.runtime.noAmmoRetry = 0;
        if (next > 0) resumeAfterSupply(ctx);
        return Result.success((int)next);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        reserve.forEach(tag::putInt);
        return tag;
    }
    public void load(CompoundTag tag) {
        reserve.clear();
        for (String key : tag.getAllKeys()) if (reserve.size() < 64 && key.length() <= 256) {
            reserve.put(key, Math.max(0, Math.min(100000, tag.getInt(key))));
        }
    }
}
