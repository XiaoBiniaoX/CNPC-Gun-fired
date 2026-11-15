package bincnpc.gun.compat.tacz;

import bincnpc.gun.common.api.*;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.gun.adapter.GunAdapter;
import bincnpc.gun.gun.ammo.*;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.*;
import com.tacz.guns.api.item.*;
import com.tacz.guns.api.item.gun.*;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class TaczAdapter implements GunAdapter {
    @Override public String id() { return "tacz"; }
    @Override public boolean matches(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof AbstractGunItem)) return false;
        var gunId = ((IGun)stack.getItem()).getGunId(stack);
        return gunId != null && TimelessAPI.getCommonGunIndex(gunId).filter(index -> index.getGunData() != null).isPresent();
    }
    private IGun gun(ItemStack stack) { return (IGun)stack.getItem(); }
    private GunData data(ItemStack stack) { return TimelessAPI.getCommonGunIndex(gun(stack).getGunId(stack)).orElseThrow().getGunData(); }
    @Override public String weaponId(ItemStack stack) { return "tacz:" + gun(stack).getGunId(stack); }
    @Override public String ammoKey(ItemStack stack) { return "tacz:" + data(stack).getAmmoId(); }
    @Override public boolean matchesAmmo(ItemStack weapon, ItemStack ammo) {
        return ammo.getItem() instanceof IAmmo item && item.isAmmoOfGun(weapon, ammo);
    }
    @Override public int ammoUnits(ItemStack weapon, ItemStack ammo) {
        if (ammo.getItem() instanceof IAmmoBox box && box.isAmmoBoxOfGun(weapon, ammo))
            return box.isCreative(ammo) ? 0 : (int)Math.min(100001L, (long)Math.max(0, box.getAmmoCount(ammo)) * ammo.getCount());
        return GunAdapter.super.ammoUnits(weapon, ammo);
    }
    @Override public int extractAmmo(ItemStack weapon, ItemStack ammo, int requested) {
        if (ammo.getItem() instanceof IAmmoBox box && box.isAmmoBoxOfGun(weapon, ammo) && !box.isCreative(ammo)) {
            int count = Math.max(0, Math.min(requested, box.getAmmoCount(ammo))); box.setAmmoCount(ammo, box.getAmmoCount(ammo) - count); return count;
        }
        return GunAdapter.super.extractAmmo(weapon, ammo, requested);
    }
    @Override public int loadedRounds(ItemStack stack) { return gun(stack).getCurrentAmmoCount(stack) + (gun(stack).hasBulletInBarrel(stack) ? 1 : 0); }
    @Override public int magazineCapacity(ItemStack stack) { return AttachmentDataUtils.getAmmoCountWithAttachment(stack, data(stack)); }
    @Override public void resetReserve(CombatContext ctx, ItemStack stack) {
        gun(stack).setDummyAmmoAmount(stack, 0); gun(stack).setMaxDummyAmmoAmount(stack, 100000);
    }
    @Override public Views.GunCaps capabilities(ItemStack stack) {
        Views.GunCaps c = new Views.GunCaps();
        IGun gun = gun(stack);
        c.canFire = true; c.canControlFireRate = true; c.hasMagazine = !gun.useInventoryAmmo(stack);
        c.canReload = c.hasMagazine; c.canReadAmmo = true; c.canConsumeAmmo = true; c.hasReserveAmmo = true;
        c.hasAimState = true; c.canAim = true; c.hasSpread = true;
        c.hasRecoil = data(stack).getRecoil() != null;
        c.supportsSingleShot = true;
        c.supportsAutomaticFire = data(stack).getFireModeSet().contains(FireMode.AUTO);
        c.supportsBurst = data(stack).getFireModeSet().contains(FireMode.BURST);
        List<String> modes = new ArrayList<>(List.of("NATIVE", "SINGLE", "SEMI", "CUSTOM"));
        if (c.supportsAutomaticFire) modes.add("AUTO");
        if (c.supportsBurst) modes.add("BURST");
        c.firingModes = modes.toArray(String[]::new);
        c.projectileModel = "INTERNAL"; c.supportsSpecialMechanics = true;
        c.specialMechanics = new String[]{"BOLT", "HEAT", "NATIVE_SCRIPTS"};
        c.supportsPartialReload = true;
        return c;
    }
    @Override public void onEquip(CombatContext ctx) {
        IGun gun = gun(ctx.weapon);
        if (!gun.useDummyAmmo(ctx.weapon)) gun.setDummyAmmoAmount(ctx.weapon, 0);
        gun.setMaxDummyAmmoAmount(ctx.weapon, Math.max(gun.getDummyAmmoAmount(ctx.weapon), ctx.config.integer("ammo.reserveLimit")));
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
        IGun item = gun(stack);
        return ctx.config.bool("ammo.infinite") || item.getCurrentAmmoCount(stack) > 0 || item.hasBulletInBarrel(stack)
            || item.getDummyAmmoAmount(stack) > 0 || itemAmmo(ctx.withWeapon(stack)) > 0
            || ctx.data.ammo.independent(ctx, this, stack) > ctx.config.integer("ammo.minimumReserve");
    }
    private void feedReserve(CombatContext ctx, int requested) {
        IGun gun = gun(ctx.weapon);
        int space = Math.max(0, gun.getMaxDummyAmmoAmount(ctx.weapon) - gun.getDummyAmmoAmount(ctx.weapon));
        int need = Math.min(space, Math.max(0, requested));
        int added = 0;
        if (AmmoController.allowsItems(ctx) && added < need)
            added += ((AbstractGunItem)ctx.weapon.getItem()).findAndExtractInventoryAmmo(new AmmoInventory(ctx.npc), ctx.weapon, need - added);
        if (added < need) added += ctx.data.ammo.takeReserve(ctx, ammoKey(ctx.weapon), need - added);
        if (added > 0) gun.addDummyAmmoAmount(ctx.weapon, added);
    }
    @Override public Views.AmmoState ammo(CombatContext ctx) {
        IGun gun = gun(ctx.weapon);
        int capacity = AttachmentDataUtils.getAmmoCountWithAttachment(ctx.weapon, data(ctx.weapon));
        int count = gun.useInventoryAmmo(ctx.weapon) ? gun.getDummyAmmoAmount(ctx.weapon) : gun.getCurrentAmmoCount(ctx.weapon);
        if (gun.hasBulletInBarrel(ctx.weapon)) count++;
        int reserve = (int)Math.min(100000L, (long)Math.max(0, gun.getDummyAmmoAmount(ctx.weapon)) + itemAmmo(ctx)
            + Math.max(0, ctx.data.ammo.independent(ctx, this, ctx.weapon) - ctx.config.integer("ammo.minimumReserve")));
        boolean reload = IGunOperator.fromLivingEntity(ctx.npc).getSynReloadState().getStateType().isReloading();
        boolean magazine = !gun.useInventoryAmmo(ctx.weapon);
        return new Views.AmmoState(magazine, count, magazine ? capacity : null, true, reserve, reload,
            magazine && !reload && count < capacity && reserve > 0);
    }
    @Override public int baseInterval(ItemStack stack) {
        return Math.max(1, (int)Math.ceil(1200.0 / Math.max(1, data(stack).getRoundsPerMinute(gun(stack).getFireMode(stack)))));
    }
    @Override public Result fire(CombatContext ctx) {
        IGun gun = gun(ctx.weapon);
        if (ctx.config.bool("ammo.infinite")) {
            int capacity = AttachmentDataUtils.getAmmoCountWithAttachment(ctx.weapon, data(ctx.weapon));
            gun.setCurrentAmmoCount(ctx.weapon, Math.max(1, capacity));
            gun.setMaxDummyAmmoAmount(ctx.weapon, 100000); gun.setDummyAmmoAmount(ctx.weapon, 100000);
        }
        if (gun.useInventoryAmmo(ctx.weapon) && gun.getDummyAmmoAmount(ctx.weapon) == 0) feedReserve(ctx, 1);
        String mode = ctx.config.text("gun.firingMode");
        FireMode selected = mode.equals("AUTO") ? FireMode.AUTO : mode.equals("BURST") ? FireMode.BURST : FireMode.SEMI;
        if (!mode.equals("NATIVE") && data(ctx.weapon).getFireModeSet().contains(selected)) gun.setFireMode(ctx.weapon, selected);
        IGunOperator operator = IGunOperator.fromLivingEntity(ctx.npc);
        float pitch = ctx.pitch(), yaw = ctx.yaw();
        ShootResult result = operator.shoot(() -> pitch, () -> yaw);
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
        if (!state.hasMagazine) return Result.fail("UNSUPPORTED");
        if (state.isReloading) return Result.fail("BUSY");
        if (state.magazineAmmo >= state.magazineCapacity) return Result.fail("CONDITION_NOT_MET");
        feedReserve(ctx, Math.max(0, state.magazineCapacity - state.magazineAmmo));
        if (gun(ctx.weapon).getDummyAmmoAmount(ctx.weapon) <= 0) return Result.fail("NO_AMMO");
        if (ctx.runtime.reloadTargetTicks > 0) ctx.runtime.adapterState.put("tacz.reloadNativeTicks", nativeReloadTicks(ctx));
        IGunOperator.fromLivingEntity(ctx.npc).reload();
        return Result.success();
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
                int missing = Math.max(1, Math.min(gun(ctx.weapon).getDummyAmmoAmount(ctx.weapon), Math.min(100000, magazineCapacity(ctx.weapon) - gun(ctx.weapon).getCurrentAmmoCount(ctx.weapon))));
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
    @Override public void cancel(CombatContext ctx) { IGunOperator.fromLivingEntity(ctx.npc).cancelReload(); IGunOperator.fromLivingEntity(ctx.npc).aim(false); }
}
