package bincnpc.gun.gun.adapter;

import bincnpc.gun.common.adapter.WeaponAdapter;
import bincnpc.gun.common.api.*;
import bincnpc.gun.common.combat.CombatContext;
import net.minecraft.item.ItemStack;

public interface GunAdapter extends WeaponAdapter {
    @Override default String domain() { return "GUN"; }
    Views.GunCaps capabilities(ItemStack stack);
    Views.AmmoState ammo(CombatContext context);
    Result fire(CombatContext context);
    /** Cancel an unreleased firing action without cancelling a native reload. */
    default void stopFire(CombatContext context) {}
    Result reload(CombatContext context);
    /** Read-only candidate query; never equips or starts a native action. */
    default boolean canUse(CombatContext context, ItemStack candidate) { return !candidate.isEmpty(); }
    default String ammoKey(ItemStack stack) { return weaponId(stack); }
    default boolean matchesAmmo(ItemStack weapon, ItemStack item) { return false; }
    default int ammoUnits(ItemStack weapon, ItemStack item) { return matchesAmmo(weapon, item) ? item.getCount() : 0; }
    default int extractAmmo(ItemStack weapon, ItemStack item, int requested) {
        int taken = Math.max(0, Math.min(requested, ammoUnits(weapon, item))); item.shrink(taken); return taken;
    }
    default int extractAmmo(CombatContext context, bincnpc.gun.gun.ammo.AmmoInventory inventory, int slot, int requested) {
        int taken = extractAmmo(context.weapon, inventory.getStackInSlot(slot), requested);
        if (taken > 0) inventory.changed(slot);
        return taken;
    }
    default int loadedRounds(ItemStack stack) { return -1; }
    default int magazineCapacity(ItemStack stack) { return -1; }
    default void resetReserve(CombatContext context, ItemStack weapon) {}
    default int baseInterval(ItemStack stack) { return 4; }
    /** Blocks per tick; zero for unknown or instantaneous projectiles. */
    default double projectileSpeed(CombatContext context) { return 0; }
    default Double spread(ItemStack stack) { return null; }
    default Double recoil(ItemStack stack) { return null; }
    default Object aimState(CombatContext context) { return null; }
    default Result aim(CombatContext context, boolean aiming) { return Result.fail("UNSUPPORTED"); }
}
