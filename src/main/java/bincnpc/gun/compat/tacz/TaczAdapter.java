package bincnpc.gun.compat.tacz;

import bincnpc.gun.common.api.*;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.gun.adapter.GunAdapter;
import bincnpc.gun.gun.ammo.*;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.*;
import com.tacz.guns.api.item.*;
import com.tacz.guns.api.item.gun.*;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;
import java.util.*;

public final class TaczAdapter implements GunAdapter {
    @Override public String id() { return "tacz"; }
    @Override public boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof AbstractGunItem)) return false;
        var gunId = ((IGun)stack.getItem()).getGunId(stack);
        return gunId != null && TimelessAPI.getCommonGunIndex(gunId).filter(index -> index.getGunData() != null).isPresent();
    }
    private IGun gun(ItemStack stack) { return (IGun)stack.getItem(); }
    private GunData data(ItemStack stack) { return TimelessAPI.getCommonGunIndex(gun(stack).getGunId(stack)).orElseThrow().getGunData(); }
    @Override public String weaponId(ItemStack stack) { return "tacz:" + gun(stack).getGunId(stack); }
    @Override public String ammoKey(ItemStack stack) { return "tacz:" + data(stack).getAmmoId(); }
    @Override public boolean matchesAmmo(ItemStack weapon, ItemStack ammo) {
        return weapon != null && !weapon.isEmpty() && ammo != null && !ammo.isEmpty()
            && ammo.getItem() instanceof IAmmo item && item.isAmmoOfGun(weapon, ammo);
    }
    @Override public int ammoUnits(ItemStack weapon, ItemStack ammo) {
        if (weapon == null || weapon.isEmpty() || ammo == null || ammo.isEmpty()) return 0;
        if (ammo.getItem() instanceof IAmmoBox box && box.isAmmoBoxOfGun(weapon, ammo))
            return box.isCreative(ammo) ? 0 : (int)Math.min(100001L, (long)Math.max(0, box.getAmmoCount(ammo)) * ammo.getCount());
        return GunAdapter.super.ammoUnits(weapon, ammo);
    }
    @Override public int extractAmmo(ItemStack weapon, ItemStack ammo, int requested) {
        if (requested <= 0 || weapon == null || weapon.isEmpty() || ammo == null || ammo.isEmpty()) return 0;
        if (ammo.getItem() instanceof IAmmoBox box && box.isAmmoBoxOfGun(weapon, ammo) && !box.isCreative(ammo)) {
            int count = Math.max(0, Math.min(requested, box.getAmmoCount(ammo))); box.setAmmoCount(ammo, box.getAmmoCount(ammo) - count); return count;
        }
        return GunAdapter.super.extractAmmo(weapon, ammo, requested);
    }
    private int chambered(ItemStack stack) { return data(stack).getBolt() != Bolt.OPEN_BOLT && gun(stack).hasBulletInBarrel(stack) ? 1 : 0; }
    private int rawCapacity(ItemStack stack) { return AttachmentDataUtils.getAmmoCountWithAttachment(stack, data(stack)); }
    private boolean freeReload(ItemStack stack) {
        var reload = data(stack).getReloadData();
        return !gun(stack).useInventoryAmmo(stack) && reload != null && reload.isInfinite();
    }
    @Override public int loadedRounds(ItemStack stack) { return Math.max(0, gun(stack).getCurrentAmmoCount(stack)) + chambered(stack); }
    @Override public int magazineCapacity(ItemStack stack) { return rawCapacity(stack) + chambered(stack); }
    @Override public void resetReserve(CombatContext ctx, ItemStack stack) {
        gun(stack).setDummyAmmoAmount(stack, 0); gun(stack).setMaxDummyAmmoAmount(stack, 100000);
    }
    @Override public Views.GunCaps capabilities(ItemStack stack) {
        Views.GunCaps c = new Views.GunCaps();
        if (!matches(stack)) return c;
        IGun gun = gun(stack);
        c.canFire = true; c.canControlFireRate = true; c.canOverrideNativeRate = true; c.hasMagazine = !gun.useInventoryAmmo(stack);
        c.canReload = c.hasMagazine; c.canReadAmmo = true; c.canConsumeAmmo = true; c.hasReserveAmmo = !freeReload(stack);
        c.hasAimState = true; c.canAim = true; c.hasSpread = true;
        c.hasRecoil = data(stack).getRecoil() != null;
        // These patterns are scheduled by CNPC Gun, independently of the gun's native selector.
        c.supportsSingleShot = true; c.supportsAutomaticFire = true; c.supportsBurst = true;
        c.firingModes = new String[]{"NATIVE", "SINGLE", "SEMI", "AUTO", "BURST", "CUSTOM"};
        c.projectileModel = "INTERNAL"; c.supportsSpecialMechanics = true;
        c.specialMechanics = data(stack).getChargeData(gun.getFireMode(stack)) == null
            ? new String[]{"BOLT", "HEAT", "NATIVE_SCRIPTS"} : new String[]{"BOLT", "HEAT", "CHARGE", "NATIVE_SCRIPTS"};
        c.supportsPartialReload = true;
        return c.withNpcControls();
    }
    @Override public void onEquip(CombatContext ctx) {
        IGun gun = gun(ctx.weapon);
        if (!gun.useDummyAmmo(ctx.weapon)) gun.setDummyAmmoAmount(ctx.weapon, 0);
        gun.setMaxDummyAmmoAmount(ctx.weapon, Math.max(gun.getDummyAmmoAmount(ctx.weapon),
            Math.max(1, Math.min(100000, Math.max(rawCapacity(ctx.weapon), ctx.config.integer("ammo.reserveLimit"))))));
        IGunOperator.fromLivingEntity(ctx.npc).draw(() -> ctx.weapon);
    }
    private int itemAmmo(CombatContext ctx) {
        if (!AmmoController.allowsItems(ctx)) return 0;
        AmmoInventory inventory = new AmmoInventory(ctx.npc);
        int amount = 0;
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            amount = (int)Math.min(100000L, (long)amount + Math.max(0, ammoUnits(ctx.weapon, stack)));
        }
        return Math.min(100000, amount);
    }
    @Override public boolean canUse(CombatContext ctx, ItemStack stack) {
        if (!matches(stack)) return false;
        IGun item = gun(stack);
        return ctx.config.bool("ammo.infinite") || loadedRounds(stack) > 0 || freeReload(stack)
            || item.getDummyAmmoAmount(stack) > 0 || itemAmmo(ctx.withWeapon(stack)) > 0
            || AmmoController.allowsIndependent(ctx) && ctx.data.ammo.independent(ctx, this, stack) > ctx.config.integer("ammo.minimumReserve");
    }
    private void feedReserve(CombatContext ctx, int requested) {
        IGun gun = gun(ctx.weapon);
        int need = bincnpc.gun.gun.controller.ReloadPolicy.bufferNeed(requested,
            gun.getDummyAmmoAmount(ctx.weapon), gun.getMaxDummyAmmoAmount(ctx.weapon));
        int added = ctx.data.ammo.take(ctx, this, need);
        if (added > 0) gun.addDummyAmmoAmount(ctx.weapon, added);
    }
    @Override public Views.AmmoState ammo(CombatContext ctx) {
        if (!matches(ctx.weapon)) return null;
        IGun gun = gun(ctx.weapon);
        int capacity = rawCapacity(ctx.weapon), chamber = chambered(ctx.weapon);
        int count = gun.useInventoryAmmo(ctx.weapon) ? gun.getDummyAmmoAmount(ctx.weapon) : gun.getCurrentAmmoCount(ctx.weapon);
        count = Math.max(0, count) + chamber;
        int reserve = (int)Math.min(100000L, (long)Math.max(0, gun.getDummyAmmoAmount(ctx.weapon)) + itemAmmo(ctx)
            + (AmmoController.allowsIndependent(ctx) ? Math.max(0, ctx.data.ammo.independent(ctx, this, ctx.weapon) - ctx.config.integer("ammo.minimumReserve")) : 0));
        var holder = IGunOperator.fromLivingEntity(ctx.npc).getDataHolder();
        boolean reload = holder != null && holder.currentGunItem != null && holder.currentGunItem.get() == ctx.weapon
            && holder.reloadStateType != null && holder.reloadStateType.isReloading();
        boolean magazine = !gun.useInventoryAmmo(ctx.weapon);
        boolean free = freeReload(ctx.weapon);
        return new Views.AmmoState(magazine, count, magazine ? capacity + chamber : null, !free, free ? null : reserve, reload,
            magazine && !reload && gun.getCurrentAmmoCount(ctx.weapon) < capacity && (free || reserve > 0));
    }
    @Override public int baseInterval(ItemStack stack) {
        return Math.max(1, (int)Math.ceil(1200.0 / Math.max(1, data(stack).getRoundsPerMinute(gun(stack).getFireMode(stack)))));
    }
    @Override public int nativeInterval(CombatContext ctx) {
        GunData nativeData = data(ctx.weapon); FireMode mode = gun(ctx.weapon).getFireMode(ctx.weapon);
        double millis = mode == FireMode.BURST && nativeData.getBurstData() != null
            ? nativeData.getBurstData().getMinInterval() * 1000 : nativeData.getShootInterval(ctx.npc, mode, ctx.weapon);
        return Double.isFinite(millis) ? Math.max(1, (int)Math.ceil(millis / 50)) : baseInterval(ctx.weapon);
    }
    public static CombatContext fireContext(LivingEntity shooter, ItemStack stack) {
        if (!(shooter instanceof EntityNPCInterface npc) || !CombatService.serverThread(npc) || !CombatService.validNpc(npc)) return null;
        CombatContext ctx = new CombatContext(npc);
        if (!(ctx.runtime.adapter instanceof TaczAdapter adapter) || ctx.runtime.editorPaused
                || !ctx.config.bool("combat.enabled") || !ctx.config.bool("gun.enabled")
                || ctx.runtime.gunCaps == null || !ctx.runtime.gunCaps.canFire || ctx.runtime.shotInterval <= 0
                || stack == null || stack != ctx.weapon || stack != npc.getMainHandItem() || !adapter.matches(stack)) return null;
        var holder = IGunOperator.fromLivingEntity(npc).getDataHolder();
        return holder != null && holder.currentGunItem != null && holder.currentGunItem.get() == stack ? ctx : null;
    }
    public static CombatContext rateContext(LivingEntity shooter, ItemStack stack) {
        CombatContext ctx = fireContext(shooter, stack);
        return ctx != null && ctx.config.bool("gun.overrideNativeRate") && ctx.runtime.gunCaps.canOverrideNativeRate ? ctx : null;
    }
    @Override public Result prepareFire(CombatContext ctx) {
        if (!matches(ctx.weapon)) return Result.fail("NO_COMPATIBLE_WEAPON");
        IGun item = gun(ctx.weapon); String mode = ctx.config.text("gun.firingMode");
        if (mode.equals("NATIVE")) return Result.success();
        FireMode selected = mode.equals("AUTO") ? FireMode.AUTO : FireMode.SEMI;
        var nativeModes = data(ctx.weapon).getFireModeSet();
        if (nativeModes != null) {
            if (!nativeModes.contains(selected)) selected = nativeModes.contains(FireMode.SEMI) ? FireMode.SEMI : FireMode.AUTO;
            if (nativeModes.contains(selected) && item.getFireMode(ctx.weapon) != selected) {
                item.setFireMode(ctx.weapon, selected);
                AttachmentPropertyManager.postChangeEvent(ctx.npc, ctx.weapon);
            }
        }
        return Result.success();
    }
    @Override public Double recoil(ItemStack stack) {
        var recoil = data(stack).getRecoil();
        var frames = recoil == null ? null : recoil.getPitch();
        double peak = 0;
        if (frames != null) for (int i = 0; i < Math.min(64, frames.length); i++) {
            if (frames[i] == null) continue;
            float[] values = frames[i].getValue();
            if (values != null) for (int n = 0; n < Math.min(2, values.length); n++)
                if (Float.isFinite(values[n])) peak = Math.max(peak, Math.abs(values[n]));
        }
        return peak;
    }
    @Override public Result fire(CombatContext ctx) {
        if (!matches(ctx.weapon)) { TaczCharging.cancel(ctx); return Result.fail("NO_COMPATIBLE_WEAPON"); }
        IGun gun = gun(ctx.weapon);
        if (ctx.config.bool("ammo.infinite")) {
            int capacity = AttachmentDataUtils.getAmmoCountWithAttachment(ctx.weapon, data(ctx.weapon));
            gun.setCurrentAmmoCount(ctx.weapon, Math.max(1, capacity));
            gun.setMaxDummyAmmoAmount(ctx.weapon, 100000); gun.setDummyAmmoAmount(ctx.weapon, 100000);
        }
        if (gun.useInventoryAmmo(ctx.weapon) && gun.getDummyAmmoAmount(ctx.weapon) == 0) feedReserve(ctx, 1);
        IGunOperator operator = IGunOperator.fromLivingEntity(ctx.npc);
        var current = operator.getDataHolder();
        if (current == null || current.currentGunItem == null || current.currentGunItem.get() != ctx.weapon) {
            TaczCharging.cancel(ctx); operator.draw(() -> ctx.weapon); return Result.fail("BUSY");
        }
        float pitch = ctx.pitch(), yaw = ctx.yaw();
        var charge = data(ctx.weapon).getChargeData(gun.getFireMode(ctx.weapon));
        ShootResult result;
        if (charge == null) {
            TaczCharging.cancel(ctx); result = operator.shoot(() -> pitch, () -> yaw);
        } else {
            if (!TaczCharging.valid(charge)) { TaczCharging.cancel(ctx); return Result.fail("CONDITION_NOT_MET"); }
            Float progress = TaczCharging.ready(ctx, gun.getFireMode(ctx.weapon), charge, operator);
            if (progress == null) return Result.fail("BUSY");
            var holder = operator.getDataHolder(); long nativeNow = TaczTime.nowMillis();
            if (holder == null || nativeNow < holder.baseTimestamp) return Result.fail("BUSY");
            result = operator.shoot(() -> pitch, () -> yaw, nativeNow - holder.baseTimestamp, progress);
            if (result == ShootResult.SUCCESS) TaczCharging.fired(ctx, charge);
        }
        if (result == null) return Result.fail("INTERNAL_ERROR");
        return switch (result) {
            case SUCCESS -> Result.success();
            case NO_AMMO -> Result.fail("NO_AMMO");
            case NEED_BOLT -> { operator.bolt(); yield Result.fail("BUSY"); }
            case IS_RELOADING, IS_DRAWING, IS_BOLTING, IS_MELEE, IS_SPRINTING -> Result.fail("BUSY");
            case COOL_DOWN, OVERHEATED, NETWORK_FAIL -> Result.fail("COOLDOWN");
            case NOT_DRAW -> { operator.draw(() -> ctx.weapon); yield Result.fail("BUSY"); }
            case NOT_GUN, ID_NOT_EXIST -> Result.fail("NO_COMPATIBLE_WEAPON");
            case FORGE_EVENT_CANCEL -> Result.fail("CONDITION_NOT_MET");
            default -> Result.fail("INTERNAL_ERROR");
        };
    }
    @Override public Result reload(CombatContext ctx) {
        Views.AmmoState state = ammo(ctx);
        if (state == null) return Result.fail("NO_COMPATIBLE_WEAPON");
        if (!state.hasMagazine) return Result.fail("UNSUPPORTED");
        if (state.magazineAmmo == null || state.magazineCapacity == null) return Result.fail("NO_COMPATIBLE_WEAPON");
        if (state.isReloading) return Result.fail("BUSY");
        if (state.magazineAmmo >= state.magazineCapacity) return Result.fail("CONDITION_NOT_MET");
        IGunOperator operator = IGunOperator.fromLivingEntity(ctx.npc);
        var current = operator.getDataHolder();
        if (current == null || current.currentGunItem == null || current.currentGunItem.get() != ctx.weapon) {
            operator.draw(() -> ctx.weapon); return Result.fail("BUSY");
        }
        if (!freeReload(ctx.weapon)) {
            feedReserve(ctx, Math.max(0, state.magazineCapacity - state.magazineAmmo));
            if (gun(ctx.weapon).getDummyAmmoAmount(ctx.weapon) <= 0) return Result.fail("NO_AMMO");
        }
        if (ctx.runtime.reloadTargetTicks > 0) ctx.runtime.adapterState.put("tacz.reloadNativeTicks", nativeReloadTicks(ctx));
        TaczCharging.cancel(ctx);
        operator.reload();
        var holder = operator.getDataHolder();
        return holder != null && holder.reloadStateType != null && holder.reloadStateType.isReloading()
            ? Result.success() : Result.fail("BUSY");
    }
    private double nativeReloadTicks(CombatContext ctx) {
        GunData nativeData = data(ctx.weapon); var reload = nativeData.getReloadData();
        boolean empty = loadedRounds(ctx.weapon) <= 0;
        double seconds = reload == null || reload.getCooldown() == null ? 1.5
            : empty ? reload.getCooldown().getEmptyTime() : reload.getCooldown().getTacticalTime();
        if (reload != null && reload.getFeed() != null) seconds = Math.max(seconds, empty ? reload.getFeed().getEmptyTime() : reload.getFeed().getTacticalTime());
        Map<String, Object> params = nativeData.getScriptParam();
        if (params != null) {
            double loop = timing(params, "loop"), intro = timing(params, empty ? "intro_empty" : "intro"), ending = timing(params, "ending");
            if (loop > 0 && intro >= 0 && ending >= 0) {
                int needed = Math.max(0, Math.min(100000, rawCapacity(ctx.weapon) - gun(ctx.weapon).getCurrentAmmoCount(ctx.weapon)));
                int missing = Math.max(1, freeReload(ctx.weapon) ? needed : Math.min(gun(ctx.weapon).getDummyAmmoAmount(ctx.weapon), needed));
                double doubleLoop = timing(params, "loop_2");
                seconds = intro + (doubleLoop > 0 ? (missing / 2) * doubleLoop + (missing % 2) * loop : missing * loop) + ending;
                double clip = timing(params, "clip_load");
                if (empty && clip > 0) seconds = clip + ending;
            } else {
                int extent = Math.max(0, Math.min(3, AttachmentDataUtils.getMagExtendLevel(ctx.weapon, nativeData)));
                String key = (empty ? "empty" : "reload") + (extent == 0 ? "" : "_xmag_" + extent) + "_cooldown";
                double scripted = timing(params, key); if (scripted > 0) seconds = scripted;
            }
        }
        return Double.isFinite(seconds) && seconds > 0 ? seconds * 20 : 30;
    }
    private static double timing(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value instanceof Number number && Double.isFinite(number.doubleValue()) && number.doubleValue() >= 0 ? number.doubleValue() : -1;
    }
    @Override public Result aim(CombatContext ctx, boolean aim) { IGunOperator.fromLivingEntity(ctx.npc).aim(aim); return Result.success(); }
    @Override public Object aimState(CombatContext ctx) { return IGunOperator.fromLivingEntity(ctx.npc).getSynIsAiming(); }
    @Override public void tick(CombatContext ctx) { TaczCharging.tick(ctx); }
    @Override public void stopFire(CombatContext ctx) { TaczCharging.cancel(ctx); }
    @Override public void cancel(CombatContext ctx) { TaczCharging.cancel(ctx); IGunOperator.fromLivingEntity(ctx.npc).cancelReload(); IGunOperator.fromLivingEntity(ctx.npc).aim(false); }
}
