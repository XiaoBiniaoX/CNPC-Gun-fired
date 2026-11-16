package bincnpc.gun.compat.tacz;

import bincnpc.gun.common.api.*;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.gun.adapter.GunAdapter;
import com.tacz.legacy.api.entity.*;
import com.tacz.legacy.api.item.*;
import com.tacz.legacy.api.item.gun.FireMode;
import com.tacz.legacy.common.entity.shooter.ShooterDataHolder;
import com.tacz.legacy.common.resource.*;
import com.tacz.legacy.common.application.refit.LegacyGunRefitRuntime;
import net.minecraft.item.ItemStack;
import java.util.*;

/** Native TaCZ Legacy operator, scripts, heat, bolt and actual barrel/magazine state. */
public final class TaczAdapter implements GunAdapter {
    private static final class Timing { long lastTick, lastTime; double scale = 1; }
    private Timing timing(CombatContext ctx) { return (Timing)ctx.runtime.adapterState.computeIfAbsent(id(), key -> new Timing()); }
    private IGun gun(ItemStack stack) { return (IGun)stack.getItem(); }
    private GunCombatData data(ItemStack stack) { return GunDataAccessor.getGunData(gun(stack).getGunId(stack)); }
    @Override public String id() { return "tacz"; }
    @Override public Object catalogueRevision() { return TACZGunPackRuntimeRegistry.currentSnapshotForJava(); }
    @Override public List<ItemStack> catalogueItems() { return TaczCreativeItems.items(); }
    @Override public boolean matches(ItemStack stack) { return stack != null && !stack.isEmpty() && stack.getItem() instanceof IGun && data(stack) != null; }
    @Override public String weaponId(ItemStack stack) { return "tacz:" + gun(stack).getGunId(stack); }
    @Override public String ammoKey(ItemStack stack) { return "tacz:" + data(stack).getAmmoId(); }
    @Override public boolean matchesAmmo(ItemStack weapon, ItemStack ammo) {
        return !ammo.isEmpty() && ammo.getItem() instanceof IAmmo && ((IAmmo)ammo.getItem()).isAmmoOfGun(weapon, ammo);
    }
    @Override public int ammoUnits(ItemStack weapon, ItemStack ammo) {
        if (!ammo.isEmpty() && ammo.getItem() instanceof IAmmoBox) {
            IAmmoBox box = (IAmmoBox)ammo.getItem();
            if (box.isAmmoBoxOfGun(weapon, ammo) && !box.isCreative(ammo))
                return (int)Math.min(100001L, (long)Math.max(0, box.getAmmoCount(ammo)) * ammo.getCount());
        }
        return GunAdapter.super.ammoUnits(weapon, ammo);
    }
    @Override public int extractAmmo(ItemStack weapon, ItemStack ammo, int requested) {
        if (!ammo.isEmpty() && ammo.getItem() instanceof IAmmoBox) {
            IAmmoBox box = (IAmmoBox)ammo.getItem();
            if (box.isAmmoBoxOfGun(weapon, ammo) && !box.isCreative(ammo) && ammo.getCount() == 1) {
                int count = Math.max(0, Math.min(requested, box.getAmmoCount(ammo)));
                box.setAmmoCount(ammo, box.getAmmoCount(ammo) - count); return count;
            }
            return 0;
        }
        return GunAdapter.super.extractAmmo(weapon, ammo, requested);
    }
    @Override public int extractAmmo(CombatContext ctx, bincnpc.gun.gun.ammo.AmmoInventory inventory, int slot, int requested) {
        ItemStack stack = inventory.getStackInSlot(slot);
        if (!(stack.getItem() instanceof IAmmoBox)) return GunAdapter.super.extractAmmo(ctx, inventory, slot, requested);
        IAmmoBox box = (IAmmoBox)stack.getItem();
        if (!box.isAmmoBoxOfGun(ctx.weapon, stack) || box.isCreative(stack)) return 0;
        int taken = Math.max(0, Math.min(requested, box.getAmmoCount(stack)));
        if (taken == 0) return 0;
        if (stack.getCount() == 1) {
            box.setAmmoCount(stack, box.getAmmoCount(stack) - taken); inventory.changed(slot);
        } else {
            // Split one reusable box so a partial box never changes every box in the stack.
            ItemStack partial = bincnpc.gun.common.legacy.Platform.copy(stack, 1);
            box.setAmmoCount(partial, box.getAmmoCount(partial) - taken);
            stack.shrink(1); inventory.changed(slot);
            for (int i = 0; i < inventory.getSlots() && !partial.isEmpty(); i++)
                if (i != slot) partial = inventory.insertItem(i, partial, false);
            if (!partial.isEmpty()) ctx.npc.entityDropItem(partial, 0.5F);
        }
        return taken;
    }
    @Override public int loadedRounds(ItemStack stack) {
        IGun gun = gun(stack);
        return (gun.useInventoryAmmo(stack) ? gun.getDummyAmmoAmount(stack) : gun.getCurrentAmmoCount(stack))
            + (gun.hasBulletInBarrel(stack) ? 1 : 0);
    }
    @Override public int magazineCapacity(ItemStack stack) { return Math.max(1, LegacyGunRefitRuntime.INSTANCE.computeAmmoCapacity(stack)); }
    @Override public void resetReserve(CombatContext ctx, ItemStack weapon) { gun(weapon).setDummyAmmoAmount(weapon, 0); }
    @Override public void onEquip(CombatContext ctx) {
        IGun gun = gun(ctx.weapon);
        if (!gun.useDummyAmmo(ctx.weapon)) gun.setDummyAmmoAmount(ctx.weapon, 0);
        IGunOperator.fromLivingEntity(ctx.npc).draw(() -> ctx.npc.getHeldItemMainhand());
    }
    @Override public boolean canUse(CombatContext ctx, ItemStack weapon) {
        return ctx.config.bool("ammo.infinite") || data(weapon).isReloadInfinite() || loadedRounds(weapon) > 0
            || gun(weapon).getDummyAmmoAmount(weapon) > 0 || ctx.data.ammo.available(ctx, this, weapon) > 0;
    }
    private void feedReserve(CombatContext ctx, int need) {
        IGun gun = gun(ctx.weapon);
        int added = ctx.data.ammo.take(ctx, this, Math.max(0, Math.min(100000 - gun.getDummyAmmoAmount(ctx.weapon), need)));
        if (added > 0) { gun.addDummyAmmoAmount(ctx.weapon, added); ctx.npc.updateClient = true; }
    }
    @Override public Views.AmmoState ammo(CombatContext ctx) {
        IGun gun = gun(ctx.weapon); boolean magazine = !gun.useInventoryAmmo(ctx.weapon);
        boolean unlimited = ctx.config.bool("ammo.infinite") || data(ctx.weapon).isReloadInfinite();
        int dummy = gun.getDummyAmmoAmount(ctx.weapon);
        int reserve = (int)Math.min(100000L, (long)dummy + ctx.data.ammo.available(ctx, this));
        int count = loadedRounds(ctx.weapon), capacity = magazineCapacity(ctx.weapon);
        boolean reload = IGunOperator.fromLivingEntity(ctx.npc).getSynReloadState().getStateType().isReloading();
        return new Views.AmmoState(magazine, unlimited ? null : count, magazine ? capacity : null,
            !unlimited, unlimited ? null : reserve, reload, magazine && !unlimited && !reload && count < capacity && reserve > 0);
    }
    @Override public Views.GunCaps capabilities(ItemStack stack) {
        Views.GunCaps c = new Views.GunCaps(); GunCombatData data = data(stack);
        c.canFire = c.canControlFireRate = true; c.hasMagazine = !gun(stack).useInventoryAmmo(stack);
        c.canReadAmmo = c.canConsumeAmmo = c.hasReserveAmmo = !data.isReloadInfinite(); c.canReload = c.hasMagazine && c.hasReserveAmmo;
        c.supportsPartialReload = true; c.hasAimState = c.canAim = c.hasSpread = true;
        c.supportsSingleShot = true;
        c.supportsAutomaticFire = data.getFireModesSet().contains("auto"); c.supportsBurst = data.getFireModesSet().contains("burst");
        List<String> modes = new ArrayList<>(Arrays.asList("NATIVE", "SINGLE", "SEMI", "CUSTOM"));
        if (c.supportsAutomaticFire) modes.add("AUTO"); if (c.supportsBurst) modes.add("BURST");
        c.firingModes = modes.toArray(new String[0]); c.projectileModel = "INTERNAL";
        c.supportsSpecialMechanics = true; c.specialMechanics = new String[]{"BOLT", "HEAT", "NATIVE_SCRIPTS"}; return c;
    }
    @Override public int baseInterval(ItemStack stack) { return Math.max(1, (int)Math.ceil(data(stack).getShootIntervalMs() / 50.0)); }
    @Override public Result fire(CombatContext ctx) {
        IGun gun = gun(ctx.weapon); GunCombatData data = data(ctx.weapon);
        if (ctx.config.bool("ammo.infinite") || data.isReloadInfinite()) {
            gun.setCurrentAmmoCount(ctx.weapon, magazineCapacity(ctx.weapon)); gun.setDummyAmmoAmount(ctx.weapon, 100000);
        }
        if (gun.useInventoryAmmo(ctx.weapon) && gun.getDummyAmmoAmount(ctx.weapon) <= 0)
            feedReserve(ctx, Math.max(1, data.getBurstCount()));
        String mode = ctx.config.text("gun.firingMode");
        String nativeMode = mode.equals("AUTO") ? "auto" : mode.equals("BURST") ? "burst" : "semi";
        if (!mode.equals("NATIVE") && data.getFireModesSet().contains(nativeMode))
            gun.setFireMode(ctx.weapon, nativeMode.equals("auto") ? FireMode.AUTO : nativeMode.equals("burst") ? FireMode.BURST : FireMode.SEMI);
        IGunOperator operator = IGunOperator.fromLivingEntity(ctx.npc);
        final float pitch = ctx.pitch(), yaw = ctx.yaw();
        ShootResult result = operator.shoot(() -> pitch, () -> yaw, System.currentTimeMillis() - operator.getDataHolder().baseTimestamp);
        switch (result) {
            case SUCCESS: return Result.success();
            case NO_AMMO: return Result.fail("NO_AMMO");
            case NEED_BOLT: operator.bolt(); return Result.fail("BUSY");
            case NOT_DRAW: operator.draw(() -> ctx.npc.getHeldItemMainhand()); return Result.fail("BUSY");
            case IS_RELOADING: case IS_DRAWING: case IS_BOLTING: case IS_MELEE: case IS_SPRINTING: return Result.fail("BUSY");
            case COOL_DOWN: case OVERHEATED: case NETWORK_FAIL: return Result.fail("COOLDOWN");
            case NOT_GUN: case ID_NOT_EXIST: return Result.fail("NO_COMPATIBLE_WEAPON");
            case FORGE_EVENT_CANCEL: return Result.fail("CONDITION_NOT_MET");
            default: return Result.fail("INTERNAL_ERROR");
        }
    }
    @Override public Result reload(CombatContext ctx) {
        Views.AmmoState state = ammo(ctx);
        if (!state.hasMagazine || ctx.config.bool("ammo.infinite")) return Result.fail("UNSUPPORTED");
        if (state.isReloading) return Result.fail("BUSY");
        if (state.magazineAmmo >= state.magazineCapacity) return Result.fail("CONDITION_NOT_MET");
        feedReserve(ctx, Math.max(0, state.magazineCapacity - state.magazineAmmo));
        if (gun(ctx.weapon).getDummyAmmoAmount(ctx.weapon) <= 0 && !data(ctx.weapon).isReloadInfinite()) return Result.fail("NO_AMMO");
        IGunOperator operator = IGunOperator.fromLivingEntity(ctx.npc); operator.reload();
        if (!operator.getDataHolder().reloadStateType.isReloading()) return Result.fail("BUSY");
        Timing t = timing(ctx); t.lastTick = ctx.now; t.lastTime = System.currentTimeMillis();
        t.scale = ctx.runtime.reloadTargetTicks > 0 ? nativeReloadTicks(ctx) / ctx.runtime.reloadTargetTicks : 1;
        return Result.success();
    }
    private double nativeReloadTicks(CombatContext ctx) {
        GunCombatData d = data(ctx.weapon); boolean empty = loadedRounds(ctx.weapon) <= 0;
        double seconds = empty ? d.getEmptyReloadFeedingTimeS() + d.getEmptyReloadFinishingTimeS()
            : d.getReloadFeedingTimeS() + d.getReloadFinishingTimeS();
        Map<String, Object> params = d.getScriptParams();
        if (params != null) {
            double loop = number(params, "loop"), intro = number(params, empty ? "intro_empty" : "intro"), ending = number(params, "ending");
            if (loop > 0 && intro >= 0 && ending >= 0)
                seconds = intro + Math.max(1, magazineCapacity(ctx.weapon) - gun(ctx.weapon).getCurrentAmmoCount(ctx.weapon)) * loop + ending;
            else {
                double scripted = number(params, empty ? "empty_cooldown" : "reload_cooldown");
                if (scripted > 0) seconds = scripted;
            }
        }
        return Double.isFinite(seconds) && seconds > 0 ? seconds * 20 : 30;
    }
    private static double number(Map<String, Object> params, String key) {
        Object value = params.get(key); return value instanceof Number && Double.isFinite(((Number)value).doubleValue()) ? ((Number)value).doubleValue() : -1;
    }
    @Override public void tick(CombatContext ctx) {
        // TaCZ's ShooterTickHandler already ticks every server living entity once.
        ShooterDataHolder holder = IGunOperator.fromLivingEntity(ctx.npc).getDataHolder(); Timing t = timing(ctx);
        if (ctx.runtime.reloadTargetTicks > 0 && holder.reloadTimestamp >= 0 && holder.reloadStateType.isReloading() && t.lastTick < ctx.now) {
            long now = System.currentTimeMillis();
            holder.reloadTimestamp += (now - t.lastTime) - Math.round((ctx.now - t.lastTick) * 50 * t.scale);
            t.lastTick = ctx.now; t.lastTime = now;
        }
    }
    @Override public Result aim(CombatContext ctx, boolean aim) { IGunOperator.fromLivingEntity(ctx.npc).aim(aim); return Result.success(); }
    @Override public Object aimState(CombatContext ctx) { return IGunOperator.fromLivingEntity(ctx.npc).getSynIsAiming(); }
    @Override public void cancel(CombatContext ctx) {
        IGunOperator operator = IGunOperator.fromLivingEntity(ctx.npc); operator.cancelReload(); operator.aim(false);
    }
}
