package bincnpc.gun.compat.superb;

import bincnpc.gun.common.api.*;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.gun.adapter.GunAdapter;
import bincnpc.gun.gun.ammo.*;
import com.atsuishio.superbwarfare.data.gun.*;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.atsuishio.superbwarfare.event.GunEventHandler;
import bincnpc.gun.gun.controller.ReloadTiming;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class SuperbAdapter implements GunAdapter {
    private final Set<String> invalidItems = java.util.concurrent.ConcurrentHashMap.newKeySet();
    @Override public String id() { return "superbwarfare"; }
    private GunData gun(ItemStack stack) { return GunData.from(stack); }
    @Override public boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof GunItem)) return false;
        try {
            GunData data = gun(stack);
            if (data == null || data.meleeOnly()) return false;
            SuperbAmmo.selected(data);
            return true;
        } catch (RuntimeException error) {
            String item = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
            if (invalidItems.size() < 16 && invalidItems.add(item)) bincnpc.gun.CNPCGun.LOGGER.warn("Cannot read native Superbwarfare gun {}", item, error);
            return false;
        }
    }
    @Override public String weaponId(ItemStack stack) { return gun(stack).id; }
    @Override public String ammoKey(ItemStack stack) { return SuperbAmmo.reserveKey(gun(stack)); }
    @Override public boolean matchesAmmo(ItemStack weapon, ItemStack ammo) { return ammoUnits(weapon, ammo) > 0; }
    @Override public int ammoUnits(ItemStack weapon, ItemStack ammo) {
        return weapon == null || weapon.isEmpty() || !(weapon.getItem() instanceof GunItem) ? 0 : SuperbAmmo.itemUnits(gun(weapon), ammo);
    }
    @Override public int extractAmmo(ItemStack weapon, ItemStack ammo, int requested) { return SuperbAmmo.extractUnits(gun(weapon), ammo, requested); }
    @Override public int loadedRounds(ItemStack stack) { return gun(stack).ammo.get(); }
    @Override public int magazineCapacity(ItemStack stack) { return gun(stack).get(GunProp.MAGAZINE); }
    @Override public void resetReserve(CombatContext ctx, ItemStack stack) { GunData data = gun(stack); data.virtualAmmo.set(0); data.cachedBackupAmmo = -1; data.save(); }
    private boolean finite(GunData gun) { return SuperbAmmo.selected(gun).getType() != AmmoConsumer.AmmoConsumeType.INFINITE; }
    private int nativeReserve(CombatContext ctx, GunData data) {
        if (data.get(GunProp.AMMO_COST_PER_SHOOT) <= 0) return 0;
        if (ctx.config.bool("ammo.infinite")) return Integer.MAX_VALUE;
        // Candidate queries also run while a regular melee weapon owns the NPC's hand.
        return Math.max(0, SuperbAmmo.inventory(data)
            ? data.countBackupAmmo(new AmmoInventory(ctx.npc, AmmoController.allowsItems(ctx))) : data.countBackupAmmo(ctx.npc));
    }
    @Override public Views.GunCaps capabilities(ItemStack stack) {
        GunData gun = gun(stack); Views.GunCaps c = new Views.GunCaps();
        boolean needsAmmo = gun.get(GunProp.AMMO_COST_PER_SHOOT) > 0;
        c.canFire = !needsAmmo || SuperbAmmo.selected(gun).getType() != AmmoConsumer.AmmoConsumeType.INVALID;
        c.canControlFireRate = true; c.canOverrideNativeRate = true;
        c.hasMagazine = needsAmmo && gun.get(GunProp.MAGAZINE) > 0;
        c.canReload = c.canFire && c.hasMagazine; c.canReadAmmo = needsAmmo && (c.hasMagazine || finite(gun)); c.canConsumeAmmo = c.canReadAmmo;
        c.hasReserveAmmo = needsAmmo && SuperbAmmo.inventory(gun); c.supportsPartialReload = c.canReload;
        c.hasAimState = true; c.canAim = true;
        c.hasRecoil = gun.get(GunProp.RECOIL) != 0 || gun.get(GunProp.RECOIL_FORCE) != 0;
        c.canControlRecoil = c.hasRecoil;
        c.hasSpread = gun.get(GunProp.SPREAD) > 0; c.canControlSpread = c.hasSpread; c.canControlSpreadLimit = c.hasSpread;
        c.canControlRange = true;
        c.supportsSingleShot = true; c.supportsAutomaticFire = true; c.supportsBurst = true;
        c.firingModes = new String[]{"NATIVE", "SINGLE", "SEMI", "AUTO", "BURST", "CUSTOM"};
        c.projectileModel = "INTERNAL";
        c.supportsSpecialMechanics = true; c.specialMechanics = new String[]{"CHARGE", "HEAT", "BOLT", "NATIVE_PROJECTILE", "ENERGY"};
        return c;
    }
    @Override public Views.AmmoState ammo(CombatContext ctx) {
        GunData gun = gun(ctx.weapon); boolean needsAmmo = gun.get(GunProp.AMMO_COST_PER_SHOOT) > 0;
        boolean finite = needsAmmo && finite(gun);
        int nativeReserve = nativeReserve(ctx, gun);
        int own = needsAmmo && SuperbAmmo.inventory(gun) ? Math.max(0, ctx.data.ammo.independent(ctx, this, ctx.weapon) - ctx.config.integer("ammo.minimumReserve")) : 0;
        int capacity = needsAmmo ? gun.get(GunProp.MAGAZINE) : 0;
        return new Views.AmmoState(capacity > 0, capacity > 0 ? gun.ammo.get() : null, capacity > 0 ? capacity : null,
            finite, finite && nativeReserve < Integer.MAX_VALUE ? (int)Math.min(100000L, (long)nativeReserve + own) : null,
            gun.reloading(), capacity > 0 && !gun.reloading() && gun.ammo.get() < capacity && (long)nativeReserve + own > 0);
    }
    @Override public boolean canUse(CombatContext ctx, ItemStack stack) {
        GunData data = gun(stack);
        int cost = data.get(GunProp.AMMO_COST_PER_SHOOT);
        if (cost <= 0) return true;
        if (SuperbAmmo.selected(data).getType() == AmmoConsumer.AmmoConsumeType.INVALID) return false;
        if (ctx.config.bool("ammo.infinite") || !finite(data)) return true;
        int loaded = Math.max(0, data.ammo.get()), capacity = data.get(GunProp.MAGAZINE);
        if (capacity > 0 && loaded >= cost) return true;
        long reserve = nativeReserve(ctx, data);
        if (SuperbAmmo.inventory(data)) reserve += Math.max(0, ctx.data.ammo.independent(ctx, this, stack) - ctx.config.integer("ammo.minimumReserve"));
        if (data.useBackpackAmmo()) return reserve >= cost;
        int missing = Math.max(0, capacity - loaded);
        return loaded + Math.min(reserve, missing) >= cost && (ctx.config.bool("ammo.partialReload") || reserve >= missing);
    }
    private void feedReserve(CombatContext ctx, int amount) {
        GunData gun = gun(ctx.weapon);
        if (!SuperbAmmo.inventory(gun)) return;
        // Native backup counts already include the consumer's loadAmount and virtual remainder.
        int available = gun.countBackupAmmo(new AmmoInventory(ctx.npc, AmmoController.allowsItems(ctx)));
        int count = ctx.data.ammo.takeReserve(ctx, ammoKey(ctx.weapon), (int)Math.max(0L, (long)amount - available));
        if (count > 0) { gun.virtualAmmo.add(count); gun.cachedBackupAmmo = -1; gun.save(); }
    }
    @Override public Result fire(CombatContext ctx) {
        GunData gun = gun(ctx.weapon);
        if (gun.reloading()) { SuperbBow.cancel(ctx); return Result.fail("BUSY"); }
        if (ctx.config.bool("ammo.infinite") && gun.get(GunProp.MAGAZINE) > 0) {
            gun.ammo.set(Math.max(1, gun.get(GunProp.MAGAZINE))); gun.cachedBackupAmmo = -1; gun.save();
        }
        if (gun.useBackpackAmmo()) feedReserve(ctx, Math.max(1, gun.get(GunProp.AMMO_COST_PER_SHOOT)));
        if (!gun.hasEnoughAmmoToShoot(ctx.npc)) { SuperbBow.cancel(ctx); return Result.fail("NO_AMMO"); }
        if (!gun.canShoot(ctx.npc)) {
            SuperbBow.cancel(ctx);
            return Result.fail(gun.charging() ? "BUSY" : "COOLDOWN");
        }
        String requested = ctx.config.text("gun.firingMode");
        if (!requested.equals("NATIVE")) {
            String nativeMode = requested.equals("AUTO") ? "AUTO" : requested.equals("BURST") ? "BURST" : "SEMI";
            List<FireModeInfo> modes = gun.get(GunProp.AVAILABLE_FIRE_MODES);
            for (int i = 0; i < modes.size(); i++) if (modes.get(i).mode.name().equals(nativeMode)) gun.selectedFireMode.set(i);
        }
        if (SuperbBow.matches(gun)) return SuperbBow.fire(ctx, gun);
        gun.shoot(ctx.npc, Math.min(ctx.config.number("gun.maxSpread"), gun.get(GunProp.SPREAD) * ctx.spreadMultiplier()),
            gun.zooming.get(), ctx.target.getUUID());
        return Result.success();
    }
    @Override public Result reload(CombatContext ctx) {
        SuperbBow.cancel(ctx);
        GunData gun = gun(ctx.weapon);
        if (!capabilities(ctx.weapon).canReload) return Result.fail("UNSUPPORTED");
        if (gun.reloading()) return Result.fail("BUSY");
        feedReserve(ctx, Math.max(0, gun.get(GunProp.MAGAZINE) - gun.ammo.get()));
        gun.cachedBackupAmmo = -1;
        int available = gun.countBackupAmmo(ctx.npc);
        if (available <= 0) return Result.fail("NO_AMMO");
        ctx.runtime.adapterState.put("superb.reloadRounds", Math.max(0, Math.min(available, Math.min(100000, gun.get(GunProp.MAGAZINE) - gun.ammo.get()))));
        applyParameters(ctx);
        GunEventHandler.INSTANCE.tryStartReload(ctx.npc, gun, true);
        return gun.reloading() || gun.reload.reloadStarter.shouldStart() || gun.reload.singleReloadStarter.shouldStart()
            ? Result.success() : Result.fail("BUSY");
    }
    @Override public int baseInterval(ItemStack stack) { return Math.max(1, (int)Math.ceil(1200.0 / Math.max(1, gun(stack).get(GunProp.RPM)))); }
    @Override public Double spread(ItemStack stack) { return gun(stack).get(GunProp.SPREAD); }
    @Override public Double recoil(ItemStack stack) { return gun(stack).get(GunProp.RECOIL); }
    @Override public Result aim(CombatContext ctx, boolean aim) { gun(ctx.weapon).zooming.set(aim); return Result.success(); }
    @Override public Object aimState(CombatContext ctx) { return gun(ctx.weapon).zooming.get(); }
    @Override public void onEquip(CombatContext ctx) {
        SuperbBow.cancel(ctx); SuperbBow.clearWrongCharge(gun(ctx.weapon)); applyParameters(ctx);
    }
    @Override public void stopFire(CombatContext ctx) { SuperbBow.cancel(ctx); }
    private void applyParameters(CombatContext ctx) {
        GunData gun = gun(ctx.weapon);
        double recoil = ctx.config.bool("gun.applyRecoil") ? ctx.config.number("gun.recoilMultiplier") : 0;
        boolean override = ctx.config.bool("gun.overrideNativeRate");
        int rpm = Math.max(1, (int)Math.round(1200.0 * ctx.config.number("gun.fireRateMultiplier") / ctx.config.integer("gun.fireInterval")));
        int range = (int)ctx.config.number("gun.range");
        int reloadTicks = ctx.runtime.reloadTargetTicks;
        Object roundState = ctx.runtime.adapterState.get("superb.reloadRounds");
        int rounds = roundState instanceof Integer count ? count : Math.max(0, gun.get(GunProp.MAGAZINE) - gun.ammo.get());
        boolean empty = !gun.hasEnoughAmmoToShoot(ctx.npc), openBolt = gun.item.isOpenBolt(gun);
        gun.setTempModifications(original -> {
            DefaultGunData changed = (DefaultGunData)original.copy();
            changed.setRecoil(original.getRecoil() * recoil);
            changed.setRecoilForce((float)(original.getRecoilForce() * recoil));
            if (override) changed.setRpm(rpm);
            changed.setRange(range);
            changed.setAutoReload(false);
            if (reloadTicks > 0) {
                changed.setNormalReloadTime(reloadTicks);
                changed.setEmptyReloadTime(Math.max(0, reloadTicks - (openBolt ? 0 : 1)));
                int prepare = original.getPrepareLoadTime() > 0 && !empty ? original.getPrepareLoadTime()
                    : empty && original.getPrepareEmptyTime() > 0 ? original.getPrepareEmptyTime() : original.getPrepareTime();
                int loops = ReloadTiming.steps(Math.max(0, rounds - (original.getPrepareLoadTime() > 0 && !empty ? 1 : 0)), Math.max(1, original.getIterativeLoadAmount()));
                double total = Math.max(1, prepare + (double)loops * Math.max(1, original.getIterativeTime()) + original.getFinishTime() + 2);
                double scale = reloadTicks / total;
                changed.setPrepareTime(phaseTime(original.getPrepareTime(), scale));
                changed.setPrepareEmptyTime(phaseTime(original.getPrepareEmptyTime(), scale));
                changed.setPrepareLoadTime(phaseTime(original.getPrepareLoadTime(), scale));
                changed.setPrepareAmmoLoadTime(Math.min(changed.getPrepareLoadTime(), phaseTime(original.getPrepareAmmoLoadTime(), scale)));
                changed.setIterativeTime(Math.max(1, phaseTime(original.getIterativeTime(), scale)));
                changed.setIterativeAmmoLoadTime(Math.max(1, Math.min(changed.getIterativeTime(), phaseTime(original.getIterativeAmmoLoadTime(), scale))));
                changed.setFinishTime(phaseTime(original.getFinishTime(), scale));
            }
            return changed;
        });
        ctx.runtime.adapterState.put("superb.reloadTicks", reloadTicks);
    }
    private static int phaseTime(int nativeTime, double scale) {
        return nativeTime <= 0 ? 0 : Math.max(1, Math.min(1200, (int)Math.round(nativeTime * scale)));
    }
    @Override public void tick(CombatContext ctx) {
        GunData gun = gun(ctx.weapon);
        Object prior = ctx.runtime.adapterState.get("revision");
        if (!(prior instanceof Long revision) || revision != ctx.config.revision
                || !Objects.equals(ctx.runtime.adapterState.get("superb.reloadTicks"), ctx.runtime.reloadTargetTicks)) {
            applyParameters(ctx); ctx.runtime.adapterState.put("revision", ctx.config.revision);
        }
        gun.tick(ctx.npc, true);
        if (gun.shouldStartBolt()) gun.startBolt();
        SuperbBow.tick(ctx, gun);
    }
    @Override public void cancel(CombatContext ctx) {
        SuperbBow.cancel(ctx);
        // Temporary property changes are scoped to the NPC's gun and never mutate global definitions.
        if (ctx.weapon.getItem() instanceof GunItem) {
            GunData data = gun(ctx.weapon);
            SuperbBow.clearWrongCharge(data);
            data.reload.reloadStarter.finish(); data.reload.singleReloadStarter.finish(); data.reload.stage3Starter.finish();
            data.reload.setTime(0); data.reload.setStage(0);
            data.reload.prepareTimer.set(0); data.reload.prepareLoadTimer.set(0); data.reload.iterativeLoadTimer.set(0); data.reload.finishTimer.set(0);
            data.reload.setState(com.atsuishio.superbwarfare.data.gun.value.ReloadState.NOT_RELOADING);
            data.save(); data.clearTempModifications();
        }
    }
}
