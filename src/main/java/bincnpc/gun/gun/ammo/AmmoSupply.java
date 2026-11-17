package bincnpc.gun.gun.ammo;

import bincnpc.gun.common.adapter.*;
import bincnpc.gun.common.api.Views;
import bincnpc.gun.common.combat.*;
import bincnpc.gun.gun.adapter.GunAdapter;
import bincnpc.gun.gun.switching.Armory;
import java.util.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** All rewards and pickup accounting are computed on the owning server thread. */
public final class AmmoSupply {
    private record Weapon(ItemStack stack, GunAdapter adapter) {}
    private AmmoSupply() {}
    private static List<Weapon> guns(CombatContext ctx) {
        List<Weapon> guns = new ArrayList<>();
        for (int slot = 0; slot < Armory.SIZE; slot++) {
            ItemStack stack = ctx.data.armory.get(ctx.npc, slot); WeaponAdapter adapter = AdapterRegistry.find(stack);
            if (adapter instanceof GunAdapter gun) {
                Views.GunCaps caps = AdapterRegistry.query(gun, () -> gun.capabilities(stack), null);
                if (caps != null && caps.hasReserveAmmo) guns.add(new Weapon(stack, gun));
            }
        }
        return guns;
    }
    public static void defaults(CombatContext ctx) {
        if (!CombatService.serverThread(ctx.npc)) return;
        ctx.data.ammo.clearDefaultReserve();
        for (Weapon gun : guns(ctx)) {
            AdapterRegistry.run(gun.adapter, () -> gun.adapter.resetReserve(ctx, gun.stack));
            ctx.data.ammo.credit(ctx, gun.adapter, gun.stack, ctx.config.integer("ammo.defaultReserve"));
        }
        ctx.runtime.restoreSupplies = false; ctx.npc.updateClient = true;
    }
    public static void kill(CombatContext ctx) {
        if (!CombatService.serverThread(ctx.npc) || !ctx.config.bool("ammo.killSupply") || ctx.config.bool("ammo.infinite")) return;
        List<Weapon> guns = guns(ctx); int reward = ctx.config.integer("ammo.killReward");
        if (guns.isEmpty() || reward <= 0) return;
        if (reward % guns.size() == 0) {
            for (Weapon gun : guns) ctx.data.ammo.credit(ctx, gun.adapter, gun.stack, reward / guns.size());
        } else {
            Weapon gun = guns.get(ctx.npc.getRandom().nextInt(guns.size())); ctx.data.ammo.credit(ctx, gun.adapter, gun.stack, reward);
        }
        ctx.npc.updateClient = true;
    }
    public static void pickup(CombatContext ctx) {
        if (!CombatService.serverThread(ctx.npc) || !ctx.config.bool("ammo.dropSupply") || ctx.config.bool("ammo.infinite")
                || !AmmoController.allowsIndependent(ctx) || ctx.now < ctx.runtime.nextDropScan) return;
        ctx.runtime.nextDropScan = ctx.now + 10;
        List<Weapon> guns = guns(ctx); if (guns.isEmpty()) return;
        int scanned = 0;
        for (ItemEntity entity : ctx.npc.level().getEntitiesOfClass(ItemEntity.class, ctx.npc.getBoundingBox().inflate(2))) {
            if (++scanned > 32) break;
            if (!entity.isAlive() || entity.hasPickUpDelay()) continue;
            ItemStack item = entity.getItem(); List<String> matching = new ArrayList<>(); int units = 0;
            for (Weapon gun : guns) {
                int count = AdapterRegistry.query(gun.adapter, () -> gun.adapter.ammoUnits(gun.stack, item), 0);
                // A stack must have one unambiguous denomination for all matching weapons.
                if (count > 0 && count <= 100000 && (units == 0 || count == units)) {
                    String key = ctx.data.ammo.key(ctx, gun.adapter, gun.stack);
                    if (key != null) { units = count; matching.add(key); }
                }
            }
            if (matching.isEmpty()) continue;
            if (ctx.data.ammo.creditPickup(ctx, matching, units)) { entity.discard(); ctx.npc.updateClient = true; }
        }
    }
}
