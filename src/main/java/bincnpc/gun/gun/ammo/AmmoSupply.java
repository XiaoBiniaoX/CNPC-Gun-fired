package bincnpc.gun.gun.ammo;

import bincnpc.gun.common.adapter.*;
import bincnpc.gun.common.api.Views;
import bincnpc.gun.common.combat.*;
import bincnpc.gun.gun.adapter.GunAdapter;
import bincnpc.gun.gun.switching.Armory;
import java.util.*;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;

/** All rewards and pickup accounting are computed on the owning server thread. */
public final class AmmoSupply {
    private static final class Weapon  {
private final ItemStack stack;
private final GunAdapter adapter;
public Weapon(ItemStack stack, GunAdapter adapter) {
this.stack = stack; this.adapter = adapter;}
public ItemStack stack() { return stack; }
public GunAdapter adapter() { return adapter; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof Weapon))return false; Weapon that=(Weapon)value; return java.util.Objects.equals(stack, that.stack) && java.util.Objects.equals(adapter, that.adapter); }
@Override public int hashCode() { return java.util.Objects.hash(stack, adapter); }

}
    private AmmoSupply() {}
    private static List<Weapon> guns(CombatContext ctx) {
        List<Weapon> guns = new ArrayList<>();
        for (int slot = 0; slot < Armory.SIZE; slot++) {
Object cnpcgun$value0; GunAdapter cnpcgun$match0;

            ItemStack stack = ctx.data.armory.get(ctx.npc, slot); WeaponAdapter adapter = AdapterRegistry.find(stack);
            if (((cnpcgun$value0 = adapter) instanceof GunAdapter && (cnpcgun$match0 = (GunAdapter)cnpcgun$value0) != null)) {
                Views.GunCaps caps = AdapterRegistry.query(cnpcgun$match0, () -> cnpcgun$match0.capabilities(stack), null);
                if (caps != null && caps.hasReserveAmmo) guns.add(new Weapon(stack, cnpcgun$match0));
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
            Weapon gun = guns.get(ctx.npc.getRNG().nextInt(guns.size())); ctx.data.ammo.credit(ctx, gun.adapter, gun.stack, reward);
        }
        ctx.npc.updateClient = true;
    }
    public static void pickup(CombatContext ctx) {
        if (!CombatService.serverThread(ctx.npc) || !ctx.config.bool("ammo.dropSupply") || ctx.config.bool("ammo.infinite") || ctx.now < ctx.runtime.nextDropScan) return;
        ctx.runtime.nextDropScan = ctx.now + 10;
        List<Weapon> guns = guns(ctx); if (guns.isEmpty()) return;
        int scanned = 0;
        for (EntityItem entity : ctx.npc.world.getEntitiesWithinAABB(EntityItem.class, ctx.npc.getEntityBoundingBox().grow(2))) {
            if (++scanned > 32) break;
            if (!entity.isEntityAlive() || entity.cannotPickup()) continue;
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
            if (ctx.data.ammo.creditPickup(ctx, matching, units)) { entity.setDead(); ctx.npc.updateClient = true; }
        }
    }
}
