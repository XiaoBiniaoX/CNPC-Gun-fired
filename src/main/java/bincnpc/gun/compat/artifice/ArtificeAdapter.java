package bincnpc.gun.compat.artifice;

import bincnpc.gun.common.api.*;
import bincnpc.gun.common.adapter.AdapterRegistry;
import bincnpc.gun.common.combat.*;
import bincnpc.gun.gun.adapter.GunAdapter;
import bincnpc.gun.gun.ammo.*;
import io.redspace.irons_artifice.item.*;
import io.redspace.irons_artifice.data.*;
import io.redspace.irons_artifice.api.ComposeShotEvent;
import io.redspace.irons_artifice.utils.IronsArtificeTags;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import noppes.npcs.entity.EntityNPCInterface;

public final class ArtificeAdapter implements GunAdapter {
    private final java.util.function.Consumer<ComposeShotEvent> composeListener = this::compose;
    public ArtificeAdapter() { MinecraftForge.EVENT_BUS.addListener(composeListener); }
    @Override public void close() { MinecraftForge.EVENT_BUS.unregister(composeListener); }
    @Override public String id() { return "irons_artifice"; }
    @Override public boolean matches(ItemStack stack) { return stack != null && stack.getItem() instanceof GunItem item && item.getGun() != null; }
    private GunItem gun(ItemStack stack) { return (GunItem)stack.getItem(); }
    @Override public String ammoKey(ItemStack stack) { return "irons_artifice:bullets"; }
    @Override public boolean matchesAmmo(ItemStack weapon, ItemStack item) { return item.is(IronsArtificeTags.AMMO); }
    @Override public int ammoUnits(ItemStack weapon, ItemStack item) {
        if (item.getItem() instanceof BulletContainerItem) return (int)Math.min(100001L, (long)Math.max(0, BulletContainerItem.count(item)) * item.getCount());
        return GunAdapter.super.ammoUnits(weapon, item);
    }
    @Override public int extractAmmo(ItemStack weapon, ItemStack item, int requested) {
        if (item.getItem() instanceof BulletContainerItem) return BulletContainerItem.drain(item, Math.max(0, requested));
        return GunAdapter.super.extractAmmo(weapon, item, requested);
    }
    @Override public int loadedRounds(ItemStack stack) { return GunItem.getMagazine(stack).count(); }
    @Override public int magazineCapacity(ItemStack stack) { return gun(stack).magazineCapacity(); }
    @Override public Views.GunCaps capabilities(ItemStack stack) {
        Views.GunCaps c = new Views.GunCaps(); var profile = gun(stack).getGun().baseProfile();
        c.canFire = true; c.canControlFireRate = true; c.canOverrideNativeRate = true;
        c.hasMagazine = true; c.canReload = true; c.canReadAmmo = true; c.canConsumeAmmo = true; c.hasReserveAmmo = true;
        c.supportsPartialReload = true;
        c.hasRecoil = profile.getOrDefault(ShotComponents.CAMERA_RECOIL).magnitude() != 0 || profile.getOrDefault(ShotComponents.CHARACTER_BLOWBACK).base() != 0;
        c.canControlRecoil = c.hasRecoil;
        c.hasSpread = profile.getOrDefault(ShotComponents.SPREAD).base() > 0; c.canControlSpread = c.hasSpread;
        c.supportsSingleShot = true; c.supportsAutomaticFire = true; c.supportsBurst = true;
        c.firingModes = new String[]{"NATIVE", "SINGLE", "SEMI", "AUTO", "BURST", "CUSTOM"};
        c.projectileModel = "INTERNAL";
        return c.withNpcControls();
    }
    private int containers(CombatContext ctx) {
        if (!AmmoController.allowsItems(ctx)) return 0;
        AmmoInventory inv = new AmmoInventory(ctx.npc);
        int count = 0;
        for (int i = 0; i < inv.getSlots(); i++) if (inv.getStackInSlot(i).getItem() instanceof BulletContainerItem)
            count += BulletContainerItem.count(inv.getStackInSlot(i));
        return Math.min(100000, count);
    }
    @Override public Views.AmmoState ammo(CombatContext ctx) {
        int count = GunItem.getMagazine(ctx.weapon).count(), cap = gun(ctx.weapon).magazineCapacity();
        int reserve = ctx.data.ammo.available(ctx, this);
        return new Views.AmmoState(true, count, cap, true, reserve, GunItem.isReloading(ctx.weapon),
            !GunItem.isReloading(ctx.weapon) && count < cap && reserve > 0);
    }
    @Override public boolean canUse(CombatContext ctx, ItemStack stack) {
        return ctx.config.bool("ammo.infinite") || GunItem.getMagazine(stack).count() > 0 || ctx.data.ammo.available(ctx, this, stack) > 0;
    }
    @Override public Result fire(CombatContext ctx) {
        if (ctx.config.bool("ammo.infinite")) GunItem.setMagazine(ctx.weapon, GunItem.getMagazine(ctx.weapon).with(gun(ctx.weapon).magazineCapacity()));
        if (ctx.config.bool("gun.overrideNativeRate") && ctx.runtime.shotInterval > 0 && ctx.now >= ctx.runtime.nextShot
                && ctx.weapon == ctx.npc.getMainHandItem() && FireDelayState.isActive(ctx.npc, ctx.weapon)) FireDelayState.clear(ctx.npc);
        FireOutcome outcome = GunplayManager.tryFire(ctx.npc, ctx.shotDirection());
        return switch (outcome) {
            case FIRED -> Result.success();
            case FIRE_DELAY_ACTIVE -> Result.fail("COOLDOWN");
            case RELOADING -> Result.fail("BUSY");
            case EMPTY_MAGAZINE -> Result.fail("NO_AMMO");
            case EVENT_CANCELLED -> Result.fail("CONDITION_NOT_MET");
            case NO_GUN -> Result.fail("NO_COMPATIBLE_WEAPON");
            default -> Result.fail("INVALID_NPC");
        };
    }
    @Override public double projectileSpeed(CombatContext ctx) {
        // A null shooter composes native item modifiers without posting a second ComposeShotEvent.
        return GunplayManager.compose(null, gun(ctx.weapon).getGun(), ctx.weapon).value(ShotComponents.BULLET_SPEED);
    }
    @Override public Result reload(CombatContext ctx) {
        if (!ammo(ctx).canReload) return Result.fail(GunItem.isReloading(ctx.weapon) ? "BUSY" : "NO_AMMO");
        ReloadResult result = GunplayManager.attemptStartReload(ctx.npc, ctx.weapon);
        return result == ReloadResult.STARTING_RELOAD ? Result.success() : Result.fail("CONDITION_NOT_MET");
    }
    public static void adjustNpcReload(EntityNPCInterface npc, ItemStack stack) {
        if (!CombatService.serverThread(npc) || !CombatService.validNpc(npc) || stack == null) return;
        CombatContext ctx = new CombatContext(npc);
        if (!(ctx.runtime.adapter instanceof ArtificeAdapter) || stack != ctx.weapon || ctx.runtime.reloadTargetTicks <= 0) return;
        ReloadState state = ReloadState.get(stack);
        if (state == null) return;
        double effective = Math.max(0, state.duration() - Math.max(0, state.skipTo() - state.skipAt()));
        if (!Double.isFinite(effective) || effective <= 0) return;
        double speed = effective * 20 / ctx.runtime.reloadTargetTicks;
        ReloadState.set(stack, new ReloadState(state.progress(), state.duration(), speed, state.roundsToLoad(), state.skipAt(), state.skipTo()));
    }
    public static ReloadResult finishNpc(EntityNPCInterface npc, ItemStack stack, int rounds) {
        CombatContext ctx = new CombatContext(npc);
        if (!(ctx.runtime.adapter instanceof ArtificeAdapter adapter) || !AdapterRegistry.loaded(adapter.id()) || stack != ctx.weapon) return ReloadResult.NO_AMMO;
        int count = GunItem.getMagazine(stack).count();
        int need = Math.max(0, ((GunItem)stack.getItem()).magazineCapacity() - count);
        if (rounds > 0) need = Math.min(need, rounds);
        int taken = ctx.data.ammo.take(ctx, adapter, need);
        if (AmmoController.allowsItems(ctx) && taken < need) {
            AmmoInventory inventory = new AmmoInventory(npc);
            for (int i = 0; i < inventory.getSlots() && taken < need; i++)
                if (inventory.getStackInSlot(i).getItem() instanceof BulletContainerItem)
                    taken += BulletContainerItem.drain(inventory.getStackInSlot(i), need - taken);
        }
        if (taken <= 0) return ReloadResult.NO_AMMO;
        GunItem.setMagazine(stack, GunItem.getMagazine(stack).with(count + taken));
        npc.updateClient = true;
        return ReloadResult.FINISHED_RELOAD;
    }
    private void compose(ComposeShotEvent event) {
        if (!(event.getEntity() instanceof EntityNPCInterface npc) || npc.level().isClientSide || !CombatService.serverThread(npc)) return;
        CombatContext ctx = new CombatContext(npc);
        if (ctx.runtime.adapter != this || !AdapterRegistry.loaded(id())) return;
        AdapterRegistry.run(this, () -> composeProfile(event, ctx));
    }
    private void composeProfile(ComposeShotEvent event, CombatContext ctx) {
        var profile = event.getShotProfile();
        double spread = profile.value(ShotComponents.SPREAD);
        if (spread > 0) profile.modifyValue(ShotComponents.SPREAD,
            modifier(Math.min(ctx.config.number("gun.maxSpread"), spread * ctx.spreadMultiplier()) / spread));
        double recoil = ctx.config.bool("gun.applyRecoil") ? ctx.config.number("gun.recoilMultiplier") : 0;
        profile.modifyValue(ShotComponents.CAMERA_RECOIL_MULTIPLIER, modifier(recoil));
        profile.modifyValue(ShotComponents.CHARACTER_BLOWBACK, modifier(recoil));
        if (ctx.config.bool("gun.overrideNativeRate")) {
            double ticks = bincnpc.gun.gun.controller.GunController.configuredNativeInterval(ctx);
            double base = profile.value(ShotComponents.FIRE_DELAY);
            double rate = Math.max(1.0E-6, profile.value(ShotComponents.FIRE_RATE));
            double delay = ticks * rate;
            if (base > 0) profile.modifyValue(ShotComponents.FIRE_DELAY, modifier(delay / base));
            else profile.modifyValue(ShotComponents.FIRE_DELAY, new ValueModifier(delay - base, ValueModifier.Operation.ADD, ValueModifier.Type.NEUTRAL));
        }
    }
    private static ValueModifier modifier(double multiplier) {
        return new ValueModifier(multiplier - 1, ValueModifier.Operation.MULTIPLY_TOTAL, ValueModifier.Type.NEUTRAL);
    }
    @Override public int baseInterval(ItemStack stack) {
        var profile = gun(stack).getGun().baseProfile();
        return Math.max(1, (int)Math.round(profile.getOrDefault(ShotComponents.FIRE_DELAY).base()
            / Math.max(1.0E-6, profile.getOrDefault(ShotComponents.FIRE_RATE).base())));
    }
    @Override public Double spread(ItemStack stack) { return gun(stack).getGun().baseProfile().getOrDefault(ShotComponents.SPREAD).base(); }
    @Override public Double recoil(ItemStack stack) { return (double)gun(stack).getGun().baseProfile().getOrDefault(ShotComponents.CAMERA_RECOIL).magnitude(); }
    @Override public void cancel(CombatContext ctx) {
        if (ctx.weapon.getItem() instanceof GunItem) ReloadState.remove(ctx.weapon);
    }
}
