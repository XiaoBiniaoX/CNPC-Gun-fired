package bincnpc.gun.gun.adapter;

import bincnpc.gun.common.api.*;
import bincnpc.gun.common.combat.*;
import bincnpc.gun.common.network.GunNetwork;
import bincnpc.gun.common.script.EventBridge;
import bincnpc.gun.gun.controller.ReloadTiming;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import java.util.UUID;

/** Shared scheduling only. Modules retain their native ammo and projectile format. */
public abstract class NativeMagazineAdapter implements GunAdapter {
    protected static final class State {
        public long nextShot, reloadStart, nextLoad, chargeEnd;
        public int reloadDuration, steps, loadedSteps;
        public boolean reloading, aiming;
        public double recoil;
        public ItemStack charging = ItemStack.EMPTY;
        public UUID target;
    }
    protected final State state(CombatContext ctx) {
        return (State)ctx.runtime.adapterState.computeIfAbsent(id(), key -> new State());
    }
    protected boolean infinite(ItemStack stack) { return false; }
    protected final boolean infinite(CombatContext ctx) { return ctx.config.bool("ammo.infinite") || infinite(ctx.weapon); }
    protected int reserveAvailable(CombatContext ctx) { return ctx.data.ammo.available(ctx, this); }
    protected int loadRounds(CombatContext ctx, int amount) { return ctx.data.ammo.take(ctx, this, amount); }
    protected abstract void addLoaded(ItemStack stack, int rounds);
    protected abstract void consumeLoaded(ItemStack stack);
    protected abstract Result shoot(CombatContext ctx);
    protected abstract int nativeReloadTicks(ItemStack stack);
    protected int loadBatch(ItemStack stack) { return magazineCapacity(stack); }
    protected int chargeTicks(ItemStack stack) { return 0; }
    protected void reloadSound(CombatContext ctx) {}
    @Override public boolean canUse(CombatContext ctx, ItemStack candidate) {
        return ctx.config.bool("ammo.infinite") || infinite(candidate) || loadedRounds(candidate) > 0
            || reserveAvailable(ctx.withWeapon(candidate)) > 0;
    }
    @Override public Views.AmmoState ammo(CombatContext ctx) {
        boolean unlimited = infinite(ctx);
        State s = state(ctx);
        int count = Math.max(0, loadedRounds(ctx.weapon)), capacity = Math.max(1, magazineCapacity(ctx.weapon));
        int reserve = reserveAvailable(ctx);
        return new Views.AmmoState(true, unlimited ? null : count, capacity, !unlimited,
            unlimited ? null : reserve, s.reloading, !unlimited && !s.reloading && count < capacity && reserve > 0);
    }
    @Override public Result aim(CombatContext ctx, boolean aiming) { state(ctx).aiming = aiming; return Result.success(); }
    @Override public Object aimState(CombatContext ctx) { return state(ctx).aiming; }
    @Override public Result fire(CombatContext ctx) {
        State s = state(ctx);
        if (s.reloading || !s.charging.isEmpty()) return Result.fail("BUSY");
        if (ctx.now < s.nextShot) return Result.fail("COOLDOWN");
        if (!infinite(ctx) && loadedRounds(ctx.weapon) <= 0) return Result.fail("NO_AMMO");
        int charge = chargeTicks(ctx.weapon);
        if (charge > 0) {
            s.charging = ctx.weapon; s.target = ctx.target.getUniqueID(); s.chargeEnd = ctx.now + charge;
            ctx.npc.setActiveHand(EnumHand.MAIN_HAND);
            ctx.runtime.scheduler.channel = "GUN"; ctx.runtime.scheduler.until = s.chargeEnd + 1;
            GunNetwork.animation(ctx, "CHARGE", charge);
            return Result.success("PENDING");
        }
        return releaseShot(ctx, s);
    }
    private Result releaseShot(CombatContext ctx, State s) {
        float pitch = ctx.npc.rotationPitch;
        ctx.npc.rotationPitch = (float)Math.max(-90, Math.min(90, ctx.pitch() + s.recoil));
        Result result;
        try { result = shoot(ctx); } finally { ctx.npc.rotationPitch = pitch; }
        if (result.ok) {
            if (!infinite(ctx)) consumeLoaded(ctx.weapon);
            s.nextShot = ctx.now + (ctx.config.bool("gun.overrideNativeRate")
                ? Math.max(1, (int)Math.ceil(ctx.config.integer("gun.fireInterval") / ctx.config.number("gun.fireRateMultiplier")))
                : baseInterval(ctx.weapon));
            Double nativeRecoil = recoil(ctx.weapon);
            if (ctx.config.bool("gun.applyRecoil") && nativeRecoil != null)
                s.recoil += nativeRecoil * ctx.config.number("gun.recoilMultiplier");
            ctx.npc.updateClient = true;
        }
        return result;
    }
    @Override public Result reload(CombatContext ctx) {
        State s = state(ctx);
        if (infinite(ctx)) return Result.fail("UNSUPPORTED");
        if (s.reloading || !s.charging.isEmpty()) return Result.fail("BUSY");
        int missing = Math.max(0, magazineCapacity(ctx.weapon) - loadedRounds(ctx.weapon));
        if (missing == 0) return Result.fail("CONDITION_NOT_MET");
        int reserve = reserveAvailable(ctx);
        if (reserve <= 0) return Result.fail("NO_AMMO");
        s.steps = ReloadTiming.steps(Math.min(missing, reserve), Math.max(1, loadBatch(ctx.weapon)));
        s.reloadStart = ctx.now; s.loadedSteps = 0;
        s.reloadDuration = ctx.runtime.reloadTargetTicks > 0 ? ctx.runtime.reloadTargetTicks : Math.max(1, nativeReloadTicks(ctx.weapon));
        s.nextLoad = ReloadTiming.stepAt(s.reloadStart, s.reloadDuration, 1, s.steps);
        s.reloading = true; reloadSound(ctx);
        return Result.success();
    }
    @Override public void tick(CombatContext ctx) {
        State s = state(ctx); s.recoil *= 0.75;
        if (!s.charging.isEmpty()) {
            boolean allowed = s.charging == ctx.weapon && !ctx.runtime.fireStopped && ctx.validTarget()
                && ctx.target.getUniqueID().equals(s.target) && !ctx.runtime.editorPaused
                && ctx.config.bool("combat.enabled") && ctx.config.bool("gun.enabled")
                && !ctx.data.throwing.blocksAttack(ctx.now)
                && ctx.distance <= ctx.gunRange();
            if (!allowed) stopFire(ctx);
            else if (ctx.now >= s.chargeEnd) {
                s.charging = ItemStack.EMPTY; s.target = null; ctx.npc.resetActiveHand();
                ctx.npc.rotationYaw = ctx.yaw(); ctx.npc.rotationYawHead = ctx.yaw();
                Result result = releaseShot(ctx, s);
                ctx.runtime.lastResult = result;
                if (result.ok) {
                    ctx.runtime.lastFired = ctx.now;
                    EventBridge.emit(ctx, "gun.fired", "", result);
                    GunNetwork.animation(ctx, "FIRE", Math.max(2, baseInterval(ctx.weapon)));
                }
                ctx.runtime.scheduler.release();
            }
        }
        if (!s.reloading || ctx.now < s.nextLoad) return;
        int due = (int)Math.min(s.steps, Math.max(0, ctx.now - s.reloadStart) * s.steps / s.reloadDuration);
        int batches = Math.max(1, due - s.loadedSteps);
        int missing = Math.max(0, magazineCapacity(ctx.weapon) - loadedRounds(ctx.weapon));
        int amount = (int)Math.min(missing, (long)Math.max(1, loadBatch(ctx.weapon)) * batches);
        int loaded = loadRounds(ctx, amount);
        if (loaded > 0) { addLoaded(ctx.weapon, loaded); ctx.npc.updateClient = true; }
        s.loadedSteps += batches;
        if (loaded == 0 || loadedRounds(ctx.weapon) >= magazineCapacity(ctx.weapon)
                || reserveAvailable(ctx) <= 0 || s.loadedSteps >= s.steps) s.reloading = false;
        else s.nextLoad = ReloadTiming.stepAt(s.reloadStart, s.reloadDuration, s.loadedSteps + 1, s.steps);
    }
    @Override public void stopFire(CombatContext ctx) {
        State s = state(ctx);
        if (!s.charging.isEmpty()) { s.charging = ItemStack.EMPTY; s.target = null; ctx.npc.resetActiveHand(); }
    }
    @Override public void cancel(CombatContext ctx) { stopFire(ctx); state(ctx).reloading = false; state(ctx).aiming = false; }
}
