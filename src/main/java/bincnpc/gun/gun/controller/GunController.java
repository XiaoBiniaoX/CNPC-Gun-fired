package bincnpc.gun.gun.controller;

import bincnpc.gun.common.adapter.AdapterRegistry;
import bincnpc.gun.common.api.*;
import bincnpc.gun.common.combat.*;
import bincnpc.gun.common.script.EventBridge;
import bincnpc.gun.gun.adapter.GunAdapter;
import bincnpc.gun.gun.ammo.AmmoInventory;

public final class GunController {
    public Views.AmmoState ammo(CombatContext ctx) {
        Views.AmmoState state = ctx.runtime.adapter instanceof GunAdapter adapter
            ? AdapterRegistry.query(adapter, () -> adapter.ammo(ctx), null) : null;
        if (state != null && ctx.config.bool("ammo.infinite")) return new Views.AmmoState(state.hasMagazine,
            state.hasMagazine ? Math.max(1, state.magazineCapacity == null ? 1 : state.magazineCapacity) : null,
            state.magazineCapacity, false, null, state.isReloading, false);
        return state;
    }
    public void tick(CombatContext ctx) {
        if (!CombatService.serverThread(ctx.npc) || !CombatService.validNpc(ctx.npc)) return;
        if (!(ctx.runtime.adapter instanceof GunAdapter adapter) || ctx.runtime.gunCaps == null || !ctx.config.bool("gun.enabled")) return;
        syncTiming(ctx, adapter);
        Views.AmmoState ammo = ammo(ctx);
        if (ammo != null) {
            boolean wasReloading = ctx.runtime.reloading;
            if (ammo.isReloading) ctx.runtime.reloadObserved = true;
            boolean awaitingNativeStart = wasReloading && !ctx.runtime.reloadObserved && ctx.now - ctx.runtime.reloadStarted < 3;
            ctx.runtime.reloading = ammo.isReloading || awaitingNativeStart;
            if (wasReloading && !ctx.runtime.reloading) {
                ctx.runtime.reloadTargetTicks = 0; ctx.runtime.reloadObserved = false;
                if (ctx.runtime.scheduler.channel.equals("RELOAD")) ctx.runtime.scheduler.release();
                EventBridge.emit(ctx, "gun.reload_finished", "", Result.success());
                bincnpc.gun.common.network.GunNetwork.animation(ctx, "RELOAD_END", 1);
                ctx.runtime.noAmmoRetry = 0;
            }
            if (ctx.runtime.reloading) {
                ctx.runtime.scheduler.channel = "RELOAD";
                ctx.runtime.scheduler.until = ctx.now + 2;
                ctx.runtime.scheduler.interruptible = false;
                CombatService.setState(ctx, "RELOADING");
                return;
            }
            int cost = AdapterRegistry.query(adapter, () -> adapter.ammoPerShot(ctx.weapon), 1);
            boolean empty = !ReloadPolicy.hasLoadedShot(ammo.hasMagazine, ammo.magazineAmmo, cost, ctx.config.bool("ammo.infinite"));
            if (empty && !ctx.runtime.ammoEmpty) EventBridge.emit(ctx, "gun.ammo_empty", "", Result.success());
            ctx.runtime.ammoEmpty = empty;
            if (ctx.runtime.movement.ammoFallback) return;
            if (ammo.canReload && ctx.config.bool("ammo.autoReload") && (!ctx.validTarget() || ctx.config.bool("ammo.combatReload"))
                    && ReloadPolicy.requested(ctx.config.text("ammo.reloadTiming"), ctx.validTarget(), !empty,
                        ammo.magazineAmmo, ctx.config.integer("ammo.reloadThreshold"))
                    ) {
                boolean danger = reloadDanger(ctx);
                if (empty && ctx.validTarget() && danger && switchLoaded(ctx)) return;
                Result result = reload(ctx); if (result.ok) return;
                if (empty && result.code.equals("NO_AMMO")) { noAmmo(ctx); return; }
            }
            if (empty) {
                if (ammo.reserveAmmo == null || ammo.reserveAmmo > 0) CombatService.setState(ctx, "WAITING");
                else noAmmo(ctx);
                return;
            }
        }
        if (ctx.data.throwing.blocksAttack(ctx.now)) return;
        if (!ctx.validTarget()) {
            if (ctx.runtime.aiming) AdapterRegistry.call(adapter, () -> adapter.aim(ctx, false));
            ctx.runtime.aiming = false; ctx.runtime.shotStreak = 0;
            return;
        }
        if (!ctx.runtime.command.equals("AUTO") || !ctx.config.bool("gun.autoAttack") || ctx.runtime.fireStopped) return;
        double range = ctx.gunRange();
        if (ctx.distance > range) {
            if (ctx.now >= ctx.runtime.nextDecision) ctx.npc.getNavigation().moveTo(ctx.target, 1);
            CombatService.setState(ctx, "APPROACHING"); return;
        }
        if (ctx.distance < ctx.config.number("gun.minRange")) return;
        boolean visible = ctx.npc.getSensing().hasLineOfSight(ctx.target);
        String prefire = ctx.config.text("gun.prefire");
        if (!visible && !ctx.canFireWithoutSight()) {
            switch (ctx.config.text("gun.lostSight")) {
                case "APPROACH" -> {
                    if (ctx.now >= ctx.runtime.nextDecision) ctx.npc.getNavigation().moveTo(ctx.target, 1);
                }
                case "STOP" -> ctx.runtime.fireStopped = true;
                default -> {}
            }
            return;
        }
        boolean trackingPrefire = ctx.isPrefiring() && prefire.equals("TRACK");
        if (trackingPrefire && ctx.now >= ctx.runtime.nextDecision) ctx.npc.getNavigation().moveTo(ctx.target, 1);
        if (!ctx.config.bool("gun.moveFire") && !trackingPrefire) ctx.npc.getNavigation().stop();
        if (!ctx.config.bool("gun.jumpFire") && !ctx.npc.onGround()) return;
        if (ctx.now >= ctx.runtime.nextShot && ctx.now >= ctx.runtime.nextFireAttempt) fire(ctx);
    }

    public Result fire(CombatContext ctx) {
        if (!CombatService.serverThread(ctx.npc) || !CombatService.validNpc(ctx.npc) || ctx.runtime.editorPaused) return Result.fail("PERMISSION_DENIED");
        if (ctx.data.throwing.blocksAttack(ctx.now)) return Result.fail("BUSY");
        if (!(ctx.runtime.adapter instanceof GunAdapter adapter)) return Result.fail("NO_COMPATIBLE_WEAPON");
        if (!ctx.config.bool("combat.enabled") || !ctx.config.bool("gun.enabled")) return Result.fail("PERMISSION_DENIED");
        if (ctx.runtime.gunCaps == null || !ctx.runtime.gunCaps.canFire) return Result.fail("UNSUPPORTED");
        if (!ctx.validTarget()) return Result.fail("INVALID_TARGET");
        syncTiming(ctx, adapter);
        if (ctx.distance > ctx.gunRange()
                || ctx.distance < ctx.config.number("gun.minRange")) return Result.fail("OUT_OF_RANGE");
        if (!ctx.npc.getSensing().hasLineOfSight(ctx.target) && !ctx.canFireWithoutSight())
            return Result.fail("CONDITION_NOT_MET");
        if (!ctx.config.bool("gun.moveFire") && ctx.npc.getDeltaMovement().horizontalDistanceSqr() > 0.01
                && (!ctx.isPrefiring() || !ctx.config.text("gun.prefire").equals("TRACK")))
            ctx.npc.getNavigation().stop();
        if (!ctx.npc.onGround() && (!ctx.config.bool("gun.jumpFire") || !ctx.runtime.gunCaps.canJumpFire)) return Result.fail("UNSUPPORTED");
        if (ctx.now < ctx.runtime.nextShot || ctx.now < ctx.runtime.nextFireAttempt) return Result.fail("COOLDOWN");
        Result lock = ctx.runtime.scheduler.acquire("GUN", "fire", ctx.now,
            Math.max(1, ctx.config.integer("gun.recovery")), true, false);
        if (!lock.ok) return lock;
        Result prepared = AdapterRegistry.call(adapter, () -> adapter.prepareFire(ctx));
        if (!prepared.ok) {
            ctx.runtime.scheduler.release(); ctx.runtime.nextFireAttempt = ctx.now + 1;
            ctx.runtime.lastResult = prepared; return prepared;
        }
        boolean aiming = ctx.config.bool("gun.requireAim");
        if (ctx.runtime.gunCaps.canAim && ctx.runtime.aiming != aiming) {
            Result aim = AdapterRegistry.call(adapter, () -> adapter.aim(ctx, aiming));
            if (aim.ok) ctx.runtime.aiming = aiming;
        }
        double error = ctx.isPrefiring() && ctx.config.text("gun.prefire").equals("SWEEP")
            ? 10 : (100 - ctx.config.number("gun.accuracy")) * 0.06;
        ctx.runtime.shotYawOffset = (float)(ctx.npc.getRandom().nextGaussian() * error);
        ctx.runtime.shotPitchOffset = (float)(ctx.npc.getRandom().nextGaussian() * error * 0.6);
        EventBridge.emit(ctx, "gun.fire_requested", "", Result.success());
        ctx.npc.setYRot(ctx.yaw()); ctx.npc.setXRot(ctx.pitch()); ctx.npc.yHeadRot = ctx.yaw();
        ctx.runtime.fireStopped = false;
        FireTiming.Plan timing = ctx.runtime.pendingShotTiming;
        if (timing == null) ctx.runtime.pendingShotTiming = timing = interval(ctx, adapter);
        Result result;
        ctx.runtime.shotInterval = timing.ticks();
        try { result = AdapterRegistry.call(adapter, () -> adapter.fire(ctx)); }
        finally { ctx.runtime.shotInterval = 0; ctx.runtime.shotYawOffset = 0; ctx.runtime.shotPitchOffset = 0; }
        ctx.runtime.lastResult = result;
        if (result.ok) {
            ctx.runtime.movement.pursueSlot = -1;
            ctx.runtime.shotStreak++;
            ctx.runtime.noAmmoEvent = false;
            ctx.runtime.nextShot = ctx.now + timing.ticks(); ctx.runtime.burstRemaining = timing.burstRemaining();
            ctx.runtime.nextFireAttempt = 0; ctx.runtime.pendingShotTiming = null;
            CombatService.setState(ctx, "ATTACKING");
            if (!"PENDING".equals(result.data)) {
                ctx.runtime.lastFired = ctx.now;
                ctx.runtime.lastShotInterval = timing.ticks();
                ctx.runtime.recoil.kick(ctx.now, AdapterRegistry.query(adapter, () -> adapter.configuredRecoil(ctx), 0.0));
                EventBridge.emit(ctx, "gun.fired", "", result);
                bincnpc.gun.common.network.GunNetwork.animation(ctx, "FIRE", Math.max(2, adapter.baseInterval(ctx.weapon)));
            }
            ctx.npc.updateClient = true;
        } else {
            ctx.runtime.scheduler.release();
            // Native cooldown queries must not observe retry backoff as if another shot had happened.
            ctx.runtime.nextFireAttempt = ctx.now + (result.code.equals("NO_AMMO") ? 20 : 1);
            if (result.code.equals("NO_AMMO")) ctx.runtime.pendingShotTiming = null;
            if (result.code.equals("NO_AMMO")) noAmmo(ctx);
        }
        return result;
    }

    /** Native cooldown overrides use the same interval as the server scheduler, including bursts and randomness. */
    public static int configuredNativeInterval(CombatContext ctx) {
        return ctx.runtime.shotInterval > 0 ? ctx.runtime.shotInterval
            : ctx.runtime.lastShotInterval > 0 ? ctx.runtime.lastShotInterval
            : Math.max(FireTiming.scaledTicks(ctx.config.integer("gun.fireInterval"), ctx.config.number("gun.fireRateMultiplier")), ctx.config.integer("gun.recovery"));
    }
    private FireTiming.Plan interval(CombatContext ctx, GunAdapter adapter) {
        FireTiming.Settings settings = ctx.runtime.firingSettings;
        if (settings == null) settings = FireTiming.Settings.read(ctx.config);
        int random = settings.interval();
        if (settings.random() && ctx.runtime.gunCaps.canControlFireRate
                && (!settings.mode().equals("NATIVE") || settings.overrideNative() && ctx.runtime.gunCaps.canOverrideNativeRate))
            random = ctx.npc.getRandom().nextInt(settings.maxInterval() - settings.minInterval() + 1) + settings.minInterval();
        int nativeInterval = settings.overrideNative() && ctx.runtime.gunCaps.canControlFireRate && ctx.runtime.gunCaps.canOverrideNativeRate
            ? 1 : AdapterRegistry.query(adapter, () -> adapter.nativeInterval(ctx), 4);
        return FireTiming.plan(settings, nativeInterval,
            ctx.runtime.gunCaps.canControlFireRate, ctx.runtime.gunCaps.canOverrideNativeRate, ctx.runtime.burstRemaining, random);
    }
    private void syncTiming(CombatContext ctx, GunAdapter adapter) {
        if (ctx.runtime.firingSettings != null && ctx.runtime.firingRevision == ctx.config.revision) return;
        FireTiming.Settings next = FireTiming.Settings.read(ctx.config);
        FireTiming.Settings previous = ctx.runtime.firingSettings;
        boolean changed = previous != null && !next.equals(previous);
        ctx.runtime.firingSettings = next; ctx.runtime.firingRevision = ctx.config.revision;
        if (!changed) return;
        ctx.runtime.burstRemaining = 0; ctx.runtime.pendingShotTiming = null;
        ctx.runtime.nextFireAttempt = 0; ctx.runtime.lastShotInterval = 0;
        if (ctx.runtime.scheduler.channel.equals("GUN") && ctx.runtime.scheduler.interruptible) ctx.runtime.scheduler.release();
        if (ctx.validTarget()) {
            if (!next.mode().equals(previous.mode())) AdapterRegistry.call(adapter, () -> adapter.prepareFire(ctx));
            if (ctx.runtime.lastFired >= ctx.runtime.firstTargetTick && ctx.runtime.lastFired <= ctx.now) {
                FireTiming.Plan timing = interval(ctx, adapter);
                ctx.runtime.pendingShotTiming = timing;
                ctx.runtime.nextShot = FireTiming.reschedule(ctx.now, ctx.runtime.lastFired, timing.ticks());
                ctx.runtime.lastShotInterval = timing.ticks();
            } else ctx.runtime.nextShot = Math.max(ctx.now, ctx.runtime.firstTargetTick + next.firstDelay() + next.windup());
        }
    }

    public Result reload(CombatContext ctx) {
        if (!CombatService.serverThread(ctx.npc) || !CombatService.validNpc(ctx.npc) || ctx.runtime.editorPaused) return Result.fail("PERMISSION_DENIED");
        if (!(ctx.runtime.adapter instanceof GunAdapter adapter)) return Result.fail("NO_COMPATIBLE_WEAPON");
        if (!ctx.config.bool("combat.enabled") || !ctx.config.bool("gun.enabled")) return Result.fail("PERMISSION_DENIED");
        if (ctx.config.bool("ammo.infinite")) return Result.fail("UNSUPPORTED");
        if (ctx.runtime.gunCaps == null || !ctx.runtime.gunCaps.canReload) return Result.fail("UNSUPPORTED");
        if (ctx.runtime.reloading || ctx.runtime.shotInterval > 0) return Result.fail("BUSY");
        Views.AmmoState state = ammo(ctx);
        if (state != null && state.isReloading) return Result.fail("BUSY");
        if (state != null && !ctx.config.bool("ammo.partialReload") && state.magazineCapacity != null
                && state.magazineAmmo != null && state.reserveAmmo != null
                && state.reserveAmmo < state.magazineCapacity - state.magazineAmmo) return Result.fail("NO_AMMO");
        if (ctx.runtime.scheduler.busy(ctx.now) && ctx.runtime.scheduler.channel.equals("GUN") && ctx.runtime.scheduler.interruptible) {
            AdapterRegistry.run(adapter, () -> adapter.stopFire(ctx));
            ctx.runtime.scheduler.release();
        }
        Result lock = ctx.runtime.scheduler.acquire("RELOAD", "reload", ctx.now, 200, false, false);
        if (!lock.ok) return lock;
        int fastest = ctx.config.integer("gun.reloadMinTicks"), slowest = ctx.config.integer("gun.reloadMaxTicks");
        ctx.runtime.reloadTargetTicks = ctx.config.bool("gun.reloadSpeedEnabled") ? fastest + ctx.npc.getRandom().nextInt(slowest - fastest + 1) : 0;
        ctx.runtime.reloadStarted = ctx.now; ctx.runtime.reloadObserved = false;
        Result result = AdapterRegistry.call(adapter, () -> adapter.reload(ctx));
        if (result.ok) {
            ctx.runtime.reloading = true; CombatService.setState(ctx, "RELOADING");
            EventBridge.emit(ctx, "gun.reload_started", "", result);
            bincnpc.gun.common.network.GunNetwork.animation(ctx, "RELOAD", ctx.runtime.reloadTargetTicks > 0 ? ctx.runtime.reloadTargetTicks : 30,
                ctx.runtime.reloadTargetTicks > 0 ? "reload_override" : "");
        } else { ctx.runtime.scheduler.release(); ctx.runtime.reloadTargetTicks = 0; ctx.runtime.reloadObserved = false; }
        return result;
    }
    private boolean reloadDanger(CombatContext ctx) {
        if (!ctx.validTarget()) return false;
        return ctx.npc.getHealth() / Math.max(1, ctx.npc.getMaxHealth()) <= ctx.config.number("weapon.lowHealth")
            || ctx.distance <= Math.max(6, ctx.config.number("weapon.meleeDistance")) || ctx.targets().size() >= 3;
    }
    private boolean switchLoaded(CombatContext ctx) {
        if (!ctx.runtime.command.equals("AUTO") || !ctx.config.bool("weapon.autoSwitch")) return false;
        int old = ctx.data.armory.active;
        Result result = CombatService.switchBest(ctx, false, true);
        return result.ok && old != ctx.data.armory.active;
    }

    private void noAmmo(CombatContext ctx) {
        if (ctx.now < ctx.runtime.noAmmoRetry) return;
        ctx.runtime.noAmmoRetry = ctx.now + 40;
        if (!ctx.runtime.noAmmoEvent) {
            ctx.runtime.noAmmoEvent = true; EventBridge.emit(ctx, "gun.no_ammo", "", Result.fail("NO_AMMO"));
        }
        if (!ctx.runtime.command.equals("AUTO") && ctx.runtime.noAmmoCommand.isEmpty()) return;
        switch (ctx.config.text("ammo.noAmmoBehavior")) {
            case "SWITCH_WEAPON", "DELEGATE" -> {
                int old = ctx.data.armory.active;
                Result switched = CombatService.switchBest(ctx, false, true);
                if (switched.ok && old != ctx.data.armory.active) return;
                CombatService.pursueMelee(ctx);
            }
            case "SWITCH_TO_MELEE" -> {
                int old = ctx.data.armory.active;
                Result switched = CombatService.switchBest(ctx, true, true);
                if (switched.ok && old != ctx.data.armory.active) return;
                CombatService.pursueMelee(ctx);
            }
            case "RETREAT" -> noAmmoCommand(ctx, "RETREAT");
            case "HOLD_POSITION" -> noAmmoCommand(ctx, "HOLD");
            default -> {}
        }
        if (ctx.runtime.scheduler.channel.isEmpty()) CombatService.setState(ctx, "NO_AMMO");
    }
    private void noAmmoCommand(CombatContext ctx, String command) {
        if (CombatService.command(ctx, command).ok) ctx.runtime.noAmmoCommand = command;
    }

    public Result stop(CombatContext ctx) {
        ctx.runtime.fireStopped = true;
        ctx.runtime.recoil.reset(); ctx.runtime.burstRemaining = 0;
        ctx.runtime.pendingShotTiming = null; ctx.runtime.nextFireAttempt = 0; ctx.runtime.lastShotInterval = 0;
        if (ctx.runtime.adapter instanceof GunAdapter adapter) AdapterRegistry.run(adapter, () -> adapter.stopFire(ctx));
        if (ctx.runtime.scheduler.channel.equals("GUN")) ctx.runtime.scheduler.release();
        return Result.success();
    }
}
