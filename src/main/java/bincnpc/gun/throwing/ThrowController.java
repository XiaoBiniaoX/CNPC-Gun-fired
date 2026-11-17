package bincnpc.gun.throwing;

import bincnpc.gun.CNPCGun;
import bincnpc.gun.common.adapter.AdapterRegistry;
import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.combat.*;
import bincnpc.gun.common.network.GunNetwork;
import bincnpc.gun.gun.adapter.GunAdapter;
import bincnpc.gun.gun.controller.ReloadPolicy;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** One staged native projectile and one numeric worker job per NPC. No world mutation leaves the main thread. */
public final class ThrowController {
    private static final int PLAN_TIMEOUT = ThrowPolicy.PLAN_TIMEOUT;
    private Pending pending;
    private long nextThrow, nextEvaluation, recoveryUntil, lastBackstep = Long.MIN_VALUE, nextWarning;
    private long nextDiagnostic;
    private final long[] retryAt = new long[3];
    private static final class Pending {
        final int slot;
        final long revision, inventoryEpoch;
        final String dimension, key;
        final ThrowAdapter adapter;
        final ThrowAdapter.Profile profile;
        final Projectile projectile;
        final Ballistics.Vec offset;
        final boolean safeDistance;
        UUID target;
        Ballistics.Vec aim, movement;
        double targetRadius;
        long releaseAt = -1, planStarted, capturedAt = -1;
        boolean finalPlan, failed, reserved, holdingPose;
        String failure = "";
        Ballistics.Request request;
        Ballistics.Candidate candidate;
        Ballistics.World world;
        List<Ballistics.Candidate> candidates = List.of();
        int nextCandidate;
        ThrowPlanner.Ticket<List<Ballistics.Candidate>> solving;
        ThrowPlanner.Ticket<Ballistics.Candidate> assessing;
        CollisionSnapshot capture;
        Pending(CombatContext ctx, int slot, ThrowAdapter adapter, ThrowAdapter.Profile profile, Projectile projectile, Ballistics.Vec offset) {
            this.slot = slot; this.adapter = adapter; this.profile = profile; this.projectile = projectile; this.offset = offset;
            revision = ctx.config.revision; inventoryEpoch = ctx.data.throwItems.epoch;
            dimension = ctx.npc.level().dimension().location().toString(); target = ctx.target.getUUID();
            key = ctx.data.throwItems.get(slot).getItem().toString();
            safeDistance = ThrowPolicy.requiresSafeDistance(ctx.config.text("throw.slot" + slot + ".timing"));
        }
        String prefix() { return "throw.slot" + slot + "."; }
        boolean winding() { return releaseAt >= 0; }
        void clearPlan() {
            if (solving != null) solving.cancel(); if (assessing != null) assessing.cancel();
            solving = null; assessing = null; capture = null; candidate = null; world = null; failed = false; failure = "";
            candidates = List.of(); nextCandidate = 0; capturedAt = -1;
        }
    }
    public boolean blocksAttack(long now) { return pending != null && (pending.reserved || pending.winding()) || now < recoveryUntil; }
    public boolean movementLocked() { return pending != null && (pending.reserved || pending.winding()); }
    public boolean pausesCharge() { return pending != null && pending.reserved && !pending.winding(); }
    public boolean planning() { return pending != null; }
    public void cancel() {
        if (pending != null) {
            if (pending.winding() && pending.projectile.getOwner() instanceof noppes.npcs.entity.EntityNPCInterface npc
                    && CombatService.serverThread(npc) && CombatService.validNpc(npc)) GunNetwork.animation(new CombatContext(npc), "THROW_END", 1);
            pending.clearPlan(); if (!pending.projectile.isRemoved()) pending.projectile.discard(); pending = null;
        }
        recoveryUntil = 0; lastBackstep = Long.MIN_VALUE;
    }
    public void reset() { cancel(); nextThrow = 0; nextEvaluation = 0; java.util.Arrays.fill(retryAt, 0); }
    private void diagnostic(CombatContext ctx, int slot, String reason) {
        if (ctx.now < nextDiagnostic) return;
        nextDiagnostic = ctx.now + 200;
        CNPCGun.LOGGER.debug("NPC {} throwable slot {}: {}", ctx.npc.getUUID(), slot, reason);
    }
    private void abort(CombatContext ctx, String reason) {
        if (pending != null) {
            retryAt[pending.slot] = ctx.now + 40;
            diagnostic(ctx, pending.slot, reason);
        }
        cancel(); nextEvaluation = ctx.now + 2;
    }

    public void tick(CombatContext ctx) {
        if (ctx == null || !CombatService.serverThread(ctx.npc)) return;
        try { tickMain(ctx); }
        catch (RuntimeException | LinkageError error) {
            abort(ctx, "adapter exception"); if (ctx.now >= nextWarning) { nextWarning = ctx.now + 1200; CNPCGun.LOGGER.warn("NPC {} throwable planning cancelled", ctx.npc.getUUID(), error); }
        }
    }
    private void tickMain(CombatContext ctx) {
        if (!CombatService.serverThread(ctx.npc)) return;
        if (!CombatService.validNpc(ctx.npc) || ctx.runtime.editorPaused || !ctx.config.bool("combat.enabled")
                || !ctx.config.bool("throw.enabled") || !ctx.runtime.command.equals("AUTO") || ctx.npc.isPassenger()) { cancel(); return; }
        if (ctx.runtime.movement.ammoFallback && ctx.runtime.movement.pursueSlot >= 0) { cancel(); return; }
        if (pending == null) {
            if (recoveryUntil > 0 && ctx.now >= recoveryUntil) {
                recoveryUntil = 0; if (ctx.runtime.combatState.equals("THROWING")) CombatService.setState(ctx, ctx.validTarget() ? "WAITING" : "IDLE");
            }
            if (ctx.now < nextEvaluation || ctx.now < nextThrow || ctx.now < recoveryUntil || !ctx.validTarget() || !ThrowFlights.hasRoom()) return;
            nextEvaluation = ctx.now + 4; select(ctx); return;
        }
        Pending p = pending;
        if (p.revision != ctx.config.revision || p.inventoryEpoch != ctx.data.throwItems.epoch || ctx.data.throwItems.remaining(ctx, p.slot) == 0
                || !p.dimension.equals(ctx.npc.level().dimension().location().toString()) || !p.key.equals(ctx.data.throwItems.get(p.slot).getItem().toString())) { abort(ctx, "equipment, configuration or quota changed"); return; }
        if (!target(ctx, p)) { abort(ctx, "target lost"); return; }
        if (p.reserved && !p.winding()) hold(ctx);
        if (p.winding()) {
            hold(ctx);
            if (ctx.now > p.releaseAt + PLAN_TIMEOUT) { abort(ctx, "release planning wait expired"); return; }
            if (!p.finalPlan && ctx.now >= p.releaseAt - Math.min(10, ctx.config.integer(p.prefix() + "windup"))) {
                p.finalPlan = true; startPlan(ctx, p, (int)Math.max(0, p.releaseAt - ctx.now));
            }
        }
        advancePlan(ctx, p);
        if (p.failed || ThrowPolicy.planExpired(ctx.now, p.planStarted) && p.candidate == null) { abort(ctx, p.failed ? p.failure : "planning timeout"); return; }
        if (p.candidate == null) {
            if (p.winding() && ctx.now >= p.releaseAt && !p.holdingPose) {
                p.holdingPose = true;
                GunNetwork.animation(ctx, "THROW_HOLD", Math.max(1, PLAN_TIMEOUT - (int)(ctx.now - p.planStarted) + 1));
            }
            return;
        }
        if (!p.winding()) {
            if (ThrowPolicy.planExpired(ctx.now, p.planStarted)) { abort(ctx, "combat action remained locked until prediction expired"); return; }
            if (!fresh(ctx, p, false)) { abort(ctx, "initial prediction became stale"); return; }
            if (!beginWindup(ctx)) return;
            p.reserved = true;
            int windup = ctx.config.integer(p.prefix() + "windup");
            p.releaseAt = ctx.now + windup;
            GunNetwork.animation(ctx, "THROW", Math.max(1, windup));
            hold(ctx);
            if (windup == 0) {
                if (fresh(ctx, p, true)) release(ctx, p); else abort(ctx, "release prediction became stale");
                return;
            }
            if (windup <= 10) {
                p.finalPlan = true;
                if (windup >= 2) startPlan(ctx, p, windup);
            }
            return;
        }
        if (ctx.now < p.releaseAt || !p.finalPlan) return;
        if (!fresh(ctx, p, true)) { abort(ctx, "release prediction became stale"); return; }
        release(ctx, p);
    }
    private static boolean beginWindup(CombatContext ctx) {
        Scheduler scheduler = ctx.runtime.scheduler;
        if (ctx.runtime.adapter instanceof bincnpc.gun.slashblade.adapter.BladeAdapter)
            return ctx.data.blade.suspendForThrow(ctx).ok;
        if (scheduler.busy(ctx.now) && !scheduler.channel.equals("RELOAD")) {
            if (!scheduler.channel.equals("GUN") || !scheduler.interruptible) return false;
            scheduler.release();
        }
        if (ctx.runtime.adapter instanceof GunAdapter gun) AdapterRegistry.run(gun, () -> gun.stopFire(ctx));
        return true;
    }
    private static boolean firingTask(CombatContext ctx) {
        if (!(ctx.runtime.adapter instanceof GunAdapter gun) || ctx.runtime.gunCaps == null || !ctx.runtime.gunCaps.canFire
                || ctx.runtime.reloading || ctx.runtime.fireStopped || !ctx.config.bool("gun.enabled")) return false;
        var ammo = ctx.data.gun.ammo(ctx);
        int cost = AdapterRegistry.query(gun, () -> gun.ammoPerShot(ctx.weapon), 1);
        if (ammo == null ? ctx.runtime.ammoEmpty
                : ammo.isReloading || !ReloadPolicy.hasLoadedShot(ammo.hasMagazine, ammo.magazineAmmo, cost, ctx.config.bool("ammo.infinite"))) return false;
        boolean firingAction = ctx.runtime.scheduler.busy(ctx.now) && ctx.runtime.scheduler.channel.equals("GUN");
        return ThrowPolicy.shootingTask(ctx.now, ctx.runtime.firstTargetTick, ctx.runtime.lastFired,
            ctx.config.bool("gun.autoAttack"), firingAction);
    }
    private void select(CombatContext ctx) {
        String unavailable = "no equipped compatible throwable with remaining quota";
        Integer[] order = {0, 1, 2}; Arrays.sort(order, Comparator.comparingInt((Integer slot) -> ctx.config.integer("throw.slot" + slot + ".priority")).reversed());
        for (int slot : order) {
            String prefix = "throw.slot" + slot + ".";
            if (ctx.now < retryAt[slot] || ctx.data.throwItems.remaining(ctx, slot) == 0) continue;
            if (ctx.distance < ctx.config.number(prefix + "minRange") || ctx.distance > ctx.config.number(prefix + "maxRange")
                    || ctx.distance > ctx.config.number(prefix + "calculationRange")) { unavailable = "target outside throwing or calculation range"; continue; }
            String timing = ctx.config.text(prefix + "timing");
            if (timing.equals("RELOAD") && !ctx.runtime.reloading
                    || timing.equals("SHOOT") && !firingTask(ctx)
                    || timing.equals("RANDOM") && ctx.npc.getRandom().nextInt(12) != 0) { unavailable = "configured timing " + timing + " not active"; continue; }
            ItemStack item = ctx.data.throwItems.get(slot); ThrowAdapter adapter = ThrowAdapters.find(item); if (adapter == null) continue;
            Projectile projectile = null;
            try {
                projectile = adapter.create((ServerLevel)ctx.npc.level(), ctx.npc, item.copyWithCount(1));
                if (projectile == null || projectile.isRemoved() || projectile.level() != ctx.npc.level()) {
                    diagnostic(ctx, slot, "native factory did not return a valid projectile for this world"); retryAt[slot] = ctx.now + 40;
                    if (projectile != null && !projectile.isRemoved()) projectile.discard();
                    continue;
                }
                projectile.setOwner(ctx.npc);
                if (((ServerLevel)ctx.npc.level()).getEntity(projectile.getUUID()) == projectile) {
                    diagnostic(ctx, slot, "native factory spawned the projectile before validation"); retryAt[slot] = ctx.now + 40; projectile.discard(); continue;
                }
                ThrowAdapter.Profile profile = adapter.profile(projectile);
                if (profile == null) { diagnostic(ctx, slot, "native projectile has no physics profile"); projectile.discard(); retryAt[slot] = ctx.now + 40; continue; }
                Ballistics.Vec offset = new Ballistics.Vec(0, 0, 0);
                if (ctx.config.text(prefix + "area").equals("AREA")) {
                    double angle = ctx.npc.getRandom().nextDouble() * Math.PI * 2, distance = Math.sqrt(ctx.npc.getRandom().nextDouble()) * ctx.config.number(prefix + "deviation");
                    offset = new Ballistics.Vec(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
                }
                Pending p = new Pending(ctx, slot, adapter, profile, projectile, offset);
                updateTarget(p, ctx.target);
                if (p.safeDistance && !CollisionSnapshot.safeArea((ServerLevel)ctx.npc.level(), ctx.npc, p.aim, profile.blastRadius())) {
                    diagnostic(ctx, slot, "unsafe landing area"); retryAt[slot] = ctx.now + 40; projectile.discard(); continue;
                }
                if (timing.equals("SHOOT")) {
                    // Freeze the launch point and pause charging; only a valid trajectory can interrupt the gun.
                    Scheduler scheduler = ctx.runtime.scheduler;
                    if (ctx.runtime.shotInterval > 0 || scheduler.busy(ctx.now)
                            && (!scheduler.channel.equals("GUN") || !scheduler.interruptible)) {
                        unavailable = "firing action cannot be interrupted"; projectile.discard(); retryAt[slot] = ctx.now + 2; continue;
                    }
                    p.reserved = true;
                }
                pending = p;
                if (p.reserved) hold(ctx);
                startPlan(ctx, p, ctx.config.integer(prefix + "windup")); return;
            } catch (RuntimeException | LinkageError error) {
                if (projectile != null && !projectile.isRemoved()) projectile.discard();
                retryAt[slot] = ctx.now + 40;
                if (ctx.now >= nextWarning) { nextWarning = ctx.now + 1200; CNPCGun.LOGGER.warn("Unable to prepare NPC {} throwable slot {}", ctx.npc.getUUID(), slot, error); }
            }
        }
        diagnostic(ctx, -1, unavailable);
    }
    private static void updateTarget(Pending p, LivingEntity target) {
        p.aim = CollisionSnapshot.vector(target.getBoundingBox().getCenter()).add(p.offset);
        boolean verticallyStationary = Math.abs(target.getY() - target.yo) < 0.001 && target.getDeltaMovement().y <= 0;
        p.movement = Ballistics.targetMovement(CollisionSnapshot.vector(target.getDeltaMovement()), target.onGround() || verticallyStationary);
        p.targetRadius = Math.min(8, target.getBbWidth() * 0.5);
    }
    private boolean target(CombatContext ctx, Pending p) {
        if (p.target == null) return true;
        Entity entity = ((ServerLevel)ctx.npc.level()).getEntity(p.target);
        if (entity instanceof LivingEntity living && CombatService.validTarget(ctx.npc, living)) { updateTarget(p, living); return true; }
        switch (ctx.config.text(p.prefix() + "lostTarget")) {
            case "CONTINUE" -> { p.target = null; p.movement = new Ballistics.Vec(0, 0, 0); }
            case "RETARGET" -> {
                LivingEntity selected = ctx.targets().stream().filter(target -> ctx.npc.distanceTo(target) <= ctx.config.number(p.prefix() + "maxRange"))
                    .min(Comparator.comparingDouble(target -> ctx.npc.distanceToSqr(target))).orElse(null);
                if (selected == null) return false; p.target = selected.getUUID(); updateTarget(p, selected);
            }
            default -> { return false; }
        }
        startPlan(ctx, p, p.winding() ? (int)Math.max(0, p.releaseAt - ctx.now) : ctx.config.integer(p.prefix() + "windup")); return true;
    }
    private Ballistics.Request request(CombatContext ctx, Pending p, int lead) {
        Vec3 aim = CollisionSnapshot.vector(p.aim), forward = aim.subtract(ctx.npc.getEyePosition()).normalize();
        Vec3 origin = ctx.npc.getEyePosition().add(0, -0.18, 0).add(forward.scale(0.3));
        var profile = p.profile; String prefix = p.prefix();
        var physics = new Ballistics.Physics(profile.speed() * ctx.config.number(prefix + "speed"), profile.gravity(), profile.drag(), profile.radius(), profile.blastRadius(), profile.life());
        return new Ballistics.Request(CollisionSnapshot.vector(origin), p.aim, p.movement, physics, Ballistics.Trajectory.valueOf(ctx.config.text(prefix + "trajectory")),
            ctx.config.number(prefix + "maxElevation"), ctx.config.number(prefix + "maxDepression"), ctx.config.number(prefix + "calculationRange"),
            ctx.config.text(prefix + "area").equals("AREA") ? 0 : ctx.config.number(prefix + "deviation"), p.targetRadius, Math.min(200, lead), ctx.config.bool(prefix + "prediction"));
    }
    private void startPlan(CombatContext ctx, Pending p, int lead) {
        p.clearPlan(); p.planStarted = ctx.now;
        try { p.request = request(ctx, p, lead); p.solving = ThrowPlanner.plan(p.request); p.failed = p.solving == null; p.failure = p.failed ? "planner queue unavailable" : ""; }
        catch (IllegalArgumentException invalid) { p.failed = true; p.failure = "invalid native projectile physics or parameters"; }
    }
    private static void advancePlan(CombatContext ctx, Pending p) {
        if (p.solving != null && p.solving.finished()) {
            List<Ballistics.Candidate> result = p.solving.result(); p.solving = null;
            if (result == null || result.isEmpty()) { p.failed = true; p.failure = result == null ? "numeric planner cancelled" : "no ballistic solution for speed, range and angle"; return; }
            p.candidates = result.stream().sorted(Comparator.comparingDouble(candidate ->
                candidate.ticks() + (candidate.mode() == Ballistics.Trajectory.CURVE ? 0.25 : 0))).toList();
        }
        if (p.capture == null && p.assessing == null && p.candidate == null && !p.candidates.isEmpty()) {
            while (p.capture == null && p.nextCandidate < p.candidates.size()) {
                Ballistics.Candidate candidate = p.candidates.get(p.nextCandidate++);
                try { p.capture = new CollisionSnapshot((ServerLevel)ctx.npc.level(), ctx.npc, p.request, List.of(candidate), p.safeDistance); }
                catch (IllegalArgumentException limit) { p.failure = "trajectory exceeded terrain capture budget"; }
            }
            if (p.capture == null) { p.failed = true; p.failure = "all trajectories blocked, unsafe or beyond terrain capture budget"; return; }
        }
        if (p.capture != null && p.capture.capture()) {
            p.world = p.capture.result(); p.capture = null;
            if (p.world == null) {
                p.failed = p.nextCandidate >= p.candidates.size(); p.failure = "terrain or blast safety capture failed"; return;
            }
            p.capturedAt = ctx.now;
            p.assessing = ThrowPlanner.assess(p.world); if (p.assessing == null) { p.failed = true; p.failure = "collision planner queue unavailable"; return; }
        }
        if (p.assessing != null && p.assessing.finished()) {
            p.candidate = p.assessing.result(); p.assessing = null;
            if (p.candidate == null) {
                p.world = null; p.failed = p.nextCandidate >= p.candidates.size(); p.failure = "all trajectories blocked or unsafe";
            }
        }
    }
    private boolean fresh(CombatContext ctx, Pending p, boolean release) {
        if (!ThrowPolicy.predictionFresh(ctx.now, p.planStarted, p.capturedAt, release)) return false;
        var current = request(ctx, p, 0);
        if (current.origin().distance(p.request.origin()) > (release ? 0.9 : 2)) return false;
        var expected = p.request.target().add(p.request.prediction() ? p.request.movement().scale(ctx.now - p.planStarted) : new Ballistics.Vec(0, 0, 0));
        return current.target().distance(expected) <= Math.max(0.75, p.targetRadius + ctx.config.number(p.prefix() + "deviation"));
    }
    private void release(CombatContext ctx, Pending p) {
        try {
            Ballistics.Request current = request(ctx, p, 0); Ballistics.Candidate candidate = Ballistics.retarget(current, p.candidate);
            if (candidate == null || p.world == null || !ThrowFlights.hasRoom()) { abort(ctx, "release correction failed or flight table full"); return; }
            candidate = Ballistics.choose(new Ballistics.World(current, List.of(candidate), p.world.walls(), p.world.danger()));
            if (candidate == null || p.safeDistance && !CollisionSnapshot.safeArea((ServerLevel)ctx.npc.level(), ctx.npc, candidate.goal(), p.profile.blastRadius())) { abort(ctx, "corrected trajectory blocked or unsafe"); return; }
            if (p.target != null) {
                Entity entity = ((ServerLevel)ctx.npc.level()).getEntity(p.target);
                if (!(entity instanceof LivingEntity living) || !CombatService.validTarget(ctx.npc, living)
                        || ctx.npc.distanceTo(living) < ctx.config.number(p.prefix() + "minRange") || ctx.npc.distanceTo(living) > ctx.config.number(p.prefix() + "maxRange")) { abort(ctx, "target invalid or outside throwing range"); return; }
            } else if (current.origin().distance(current.target()) < ctx.config.number(p.prefix() + "minRange")
                    || current.origin().distance(current.target()) > ctx.config.number(p.prefix() + "maxRange")) { abort(ctx, "frozen landing area outside throwing range"); return; }
            Projectile projectile = p.projectile; projectile.setPos(CollisionSnapshot.vector(candidate.origin()));
            if (!CollisionSnapshot.volumeLoaded((ServerLevel)ctx.npc.level(), projectile.getBoundingBox()) || !ctx.npc.level().noCollision(projectile)) { abort(ctx, "release point obstructed or terrain unloaded"); return; }
            if (candidate.mode() != Ballistics.Trajectory.CURVE) p.adapter.straight(projectile);
            else projectile.setNoGravity(p.profile.customGravity());
            var velocity = candidate.velocity(); p.adapter.launch(projectile, CollisionSnapshot.vector(velocity));
            if (candidate.mode() == Ballistics.Trajectory.TRACKING) projectile.setDeltaMovement(Vec3.ZERO);
            else {
                Vec3 next = projectile.position().add(CollisionSnapshot.vector(candidate.velocity()).scale(Math.min(1, candidate.ticks())));
                if (!CollisionSnapshot.clear((ServerLevel)ctx.npc.level(), projectile, projectile.position(), next)
                        && projectile.position().distanceToSqr(CollisionSnapshot.vector(candidate.goal())) > current.tolerance() * current.tolerance()) { abort(ctx, "first flight segment obstructed"); return; }
            }
            ThrowFlights.mark(ctx, projectile);
            if (!((ServerLevel)ctx.npc.level()).addFreshEntity(projectile)) { abort(ctx, "native projectile spawn rejected"); return; }
            if (pending != p || ctx.config.revision != p.revision || ctx.data.throwItems.epoch != p.inventoryEpoch
                    || !CombatService.validNpc(ctx.npc) || !ThrowFlights.register(ctx, projectile, p.adapter, p.profile, candidate, p.target)) {
                if (!projectile.isRemoved()) projectile.discard(); if (pending == p) cancel(); return;
            }
            ctx.data.throwItems.used(ctx, p.slot); nextThrow = ctx.now + ctx.config.integer("throw.cooldown"); recoveryUntil = ctx.now + ctx.config.integer(p.prefix() + "recovery");
            p.clearPlan(); pending = null; ctx.npc.updateClient = true;
            GunNetwork.animation(ctx, "THROW_RELEASE", Math.max(1, ctx.config.integer(p.prefix() + "recovery")));
            CombatService.setState(ctx, "THROWING");
            bincnpc.gun.common.script.EventBridge.emit(ctx, "throw.released", "slot:" + p.slot, Result.success());
        } catch (RuntimeException | LinkageError error) {
            abort(ctx, "native release exception"); if (ctx.now >= nextWarning) { nextWarning = ctx.now + 1200; CNPCGun.LOGGER.warn("Unable to release NPC {} throwable", ctx.npc.getUUID(), error); }
        }
    }
    private void hold(CombatContext ctx) {
        ctx.npc.getNavigation().stop(); ctx.npc.getMoveControl().strafe(0, 0); ctx.npc.xxa = 0; ctx.npc.zza = 0;
        Vec3 movement = ctx.npc.getDeltaMovement(); ctx.npc.setDeltaMovement(0, movement.y, 0); CombatService.setState(ctx, "THROWING");
    }
    /** Tail of NPC aiStep: navigation cannot replace the fixed, collision-aware 0.02 block backstep. */
    public void afterAi(CombatContext ctx) {
        if (!CombatService.serverThread(ctx.npc) || !movementLocked() || !CombatService.validNpc(ctx.npc) || lastBackstep == ctx.now) return;
        lastBackstep = ctx.now; hold(ctx);
        if (pending == null || !pending.winding()) return;
        Vec3 away = ctx.npc.position().subtract(CollisionSnapshot.vector(pending.aim));
        double horizontal = away.horizontalDistance(); if (horizontal < 1E-6) away = Vec3.directionFromRotation(0, ctx.npc.getYRot()).scale(-1); else away = new Vec3(away.x / horizontal, 0, away.z / horizontal);
        var destination = ctx.npc.getBoundingBox().move(away.scale(0.02));
        var level = (ServerLevel)ctx.npc.level();
        if (!CollisionSnapshot.volumeLoaded(level, destination)) return;
        ctx.npc.move(MoverType.SELF, away.scale(0.02));
    }
}
