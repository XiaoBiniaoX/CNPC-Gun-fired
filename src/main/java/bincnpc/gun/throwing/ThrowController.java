package bincnpc.gun.throwing;

import bincnpc.gun.CNPCGun;
import bincnpc.gun.common.adapter.AdapterRegistry;
import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.combat.*;
import bincnpc.gun.common.network.GunNetwork;
import bincnpc.gun.gun.adapter.GunAdapter;
import java.util.*;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.*;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

/** One staged native projectile and one numeric worker job per NPC. No world mutation leaves the main thread. */
public final class ThrowController {
    private static final int PLAN_TIMEOUT = 28;
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
        final Entity projectile;
        final noppes.npcs.entity.EntityNPCInterface owner;
        final Ballistics.Vec offset;
        UUID target;
        Ballistics.Vec aim, movement;
        double targetRadius;
        long releaseAt = -1, planStarted, readyStarted;
        boolean finalPlan, failed;
        String failure = "";
        Ballistics.Request request;
        Ballistics.Candidate candidate;
        Ballistics.World world, capturedWorld;
        ThrowPlanner.Ticket<List<Ballistics.Candidate>> solving;
        ThrowPlanner.Ticket<Ballistics.Candidate> assessing;
        CollisionSnapshot capture, releaseCapture;
        Pending(CombatContext ctx, int slot, ThrowAdapter adapter, ThrowAdapter.Profile profile, Entity projectile, Ballistics.Vec offset) {
            this.owner = ctx.npc; this.slot = slot; this.adapter = adapter; this.profile = profile; this.projectile = projectile; this.offset = offset;
            revision = ctx.config.revision; inventoryEpoch = ctx.data.throwItems.epoch;
            dimension = ctx.npc.world.provider.getDimension() + ""; target = ctx.target.getUniqueID();
            key = ctx.data.throwItems.get(slot).getItem().toString();
        }
        String prefix() { return "throw.slot" + slot + "."; }
        boolean winding() { return releaseAt >= 0; }
        void clearWork() {
            if (solving != null) solving.cancel(); if (assessing != null) assessing.cancel();
            solving = null; assessing = null; capture = null; capturedWorld = null; releaseCapture = null; failed = false; failure = "";
        }
        void clearPlan() { clearWork(); candidate = null; world = null; }
        boolean working() { return solving != null || assessing != null || capture != null; }
    }
    public boolean blocksAttack(long now) { return pending != null && pending.winding() || now < recoveryUntil; }
    public boolean movementLocked() { return pending != null && pending.winding(); }
    public boolean planning() { return pending != null; }
    public void cancel() {
        if (pending != null) {
Object cnpcgun$value0; noppes.npcs.entity.EntityNPCInterface cnpcgun$match0;

            if (pending.winding() && ((cnpcgun$value0 = pending.owner) instanceof noppes.npcs.entity.EntityNPCInterface && (cnpcgun$match0 = (noppes.npcs.entity.EntityNPCInterface)cnpcgun$value0) != null)
                    && CombatService.serverThread(cnpcgun$match0) && CombatService.validNpc(cnpcgun$match0)) GunNetwork.animation(new CombatContext(cnpcgun$match0), "THROW_END", 1);
            pending.clearPlan(); if (!pending.projectile.isDead) pending.projectile.setDead(); pending = null;
        }
        recoveryUntil = 0; lastBackstep = Long.MIN_VALUE;
    }
    public void reset() { cancel(); nextThrow = 0; nextEvaluation = 0; java.util.Arrays.fill(retryAt, 0); }
    private void diagnostic(CombatContext ctx, int slot, String reason) {
        if (ctx.now < nextDiagnostic) return;
        nextDiagnostic = ctx.now + 200;
        CNPCGun.LOGGER.debug("NPC {} throwable slot {}: {}", ctx.npc.getUniqueID(), slot, reason);
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
            abort(ctx, "adapter exception"); if (ctx.now >= nextWarning) { nextWarning = ctx.now + 1200; CNPCGun.LOGGER.warn("NPC {} throwable planning cancelled", ctx.npc.getUniqueID(), error); }
        }
    }
    private void tickMain(CombatContext ctx) {
        if (!CombatService.serverThread(ctx.npc)) return;
        if (!CombatService.validNpc(ctx.npc) || ctx.runtime.editorPaused || !ctx.config.bool("combat.enabled")
                || !ctx.config.bool("throw.enabled") || !ctx.runtime.command.equals("AUTO") || ctx.npc.isRiding()) { cancel(); return; }
        if (ctx.runtime.movement.ammoFallback && ctx.runtime.movement.pursueSlot >= 0) { cancel(); return; }
        if (pending == null) {
            if (recoveryUntil > 0 && ctx.now >= recoveryUntil) {
                recoveryUntil = 0; if (ctx.runtime.combatState.equals("THROWING")) CombatService.setState(ctx, ctx.validTarget() ? "WAITING" : "IDLE");
            }
            if (ctx.now < nextEvaluation || ctx.now < nextThrow || ctx.now < recoveryUntil || !ctx.validTarget() || !ThrowFlights.hasRoom()) return;
            nextEvaluation = ctx.now + 8; select(ctx); return;
        }
        Pending p = pending;
        if (p.revision != ctx.config.revision || p.inventoryEpoch != ctx.data.throwItems.epoch || ctx.data.throwItems.remaining(ctx, p.slot) == 0
                || !p.dimension.equals(ctx.npc.world.provider.getDimension() + "") || !p.key.equals(ctx.data.throwItems.get(p.slot).getItem().toString())) { abort(ctx, "equipment, configuration or quota changed"); return; }
        if (!target(ctx, p)) { abort(ctx, "target lost"); return; }
        if (p.winding()) {
            hold(ctx);
            // Terrain capture is budgeted. Allow one bounded planning window after windup.
            if (ctx.now > p.releaseAt + PLAN_TIMEOUT) { abort(ctx, "release planning deadline missed"); return; }
            if (!p.finalPlan && ctx.now >= p.releaseAt - Math.min(PLAN_TIMEOUT, ctx.config.integer(p.prefix() + "windup"))) {
                p.finalPlan = true; startPlan(ctx, p, (int)Math.max(0, p.releaseAt - ctx.now));
            }
        }
        advancePlan(ctx, p);
        if (p.failed && p.candidate == null || ctx.now - p.planStarted > PLAN_TIMEOUT && p.candidate == null) { abort(ctx, p.failed ? p.failure : "planning timeout"); return; }
        if (p.candidate == null) {
            return;
        }
        if (!p.winding()) {
            if (ctx.now - p.planStarted > PLAN_TIMEOUT) { abort(ctx, "combat action remained locked until prediction expired"); return; }
            if (!fresh(ctx, p, false)) { abort(ctx, "initial prediction became stale"); return; }
            if (!beginWindup(ctx)) return;
            int windup = ctx.config.integer(p.prefix() + "windup");
            p.releaseAt = ctx.now + windup;
            GunNetwork.animation(ctx, "THROW", Math.max(1, windup));
            hold(ctx);
            if (windup == 0) {
                p.finalPlan = true;
                if (fresh(ctx, p, true)) release(ctx, p); else abort(ctx, "release prediction became stale");
                return;
            }
            if (windup <= PLAN_TIMEOUT) {
                p.finalPlan = true;
                if (windup >= 2) startPlan(ctx, p, windup);
            }
            return;
        }
        if (ctx.now < p.releaseAt || !p.finalPlan) return;
        if (!fresh(ctx, p, true)) {
            if (!p.working()) startPlan(ctx, p, 0);
            return;
        }
        release(ctx, p);
    }
    private static boolean beginWindup(CombatContext ctx) {
Object cnpcgun$value1; GunAdapter cnpcgun$match1;

        Scheduler scheduler = ctx.runtime.scheduler;
        if (ctx.runtime.adapter instanceof bincnpc.gun.slashblade.adapter.BladeAdapter)
            return ctx.data.blade.suspendForThrow(ctx).ok;
        if (scheduler.busy(ctx.now) && !scheduler.channel.equals("RELOAD")) {
            if (!scheduler.channel.equals("GUN") || !scheduler.interruptible) return false;
            scheduler.release();
        }
        if (((cnpcgun$value1 = ctx.runtime.adapter) instanceof GunAdapter && (cnpcgun$match1 = (GunAdapter)cnpcgun$value1) != null)) AdapterRegistry.run(cnpcgun$match1, () -> cnpcgun$match1.stopFire(ctx));
        return true;
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
                    || timing.equals("SHOOT") && (!(ctx.runtime.adapter instanceof GunAdapter) || ctx.runtime.reloading || ctx.now - ctx.runtime.lastFired > 4)
                    || timing.equals("RANDOM") && ctx.npc.getRNG().nextInt(12) != 0) { unavailable = "configured timing " + timing + " not active"; continue; }
            ItemStack item = ctx.data.throwItems.get(slot); ThrowAdapter adapter = ThrowAdapters.find(item); if (adapter == null) continue;
            Entity projectile = null;
            try {
                projectile = adapter.create((WorldServer)ctx.npc.world, ctx.npc, item.copy());
                if (projectile == null || projectile.isDead || projectile.world != ctx.npc.world) {
                    diagnostic(ctx, slot, "native factory did not return a valid projectile for this world"); retryAt[slot] = ctx.now + 40;
                    if (projectile != null && !projectile.isDead) projectile.setDead();
                    continue;
                }
                if (((WorldServer)ctx.npc.world).getEntityFromUuid(projectile.getUniqueID()) == projectile) {
                    diagnostic(ctx, slot, "native factory spawned the projectile before validation"); retryAt[slot] = ctx.now + 40; projectile.setDead(); continue;
                }
                ThrowAdapter.Profile profile = adapter.profile(projectile);
                if (profile == null) { diagnostic(ctx, slot, "native projectile has no physics profile"); projectile.setDead(); retryAt[slot] = ctx.now + 40; continue; }
                Ballistics.Vec offset = new Ballistics.Vec(0, 0, 0);
                if (ctx.config.text(prefix + "area").equals("AREA")) {
                    double angle = ctx.npc.getRNG().nextDouble() * Math.PI * 2, distance = Math.sqrt(ctx.npc.getRNG().nextDouble()) * ctx.config.number(prefix + "deviation");
                    offset = new Ballistics.Vec(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
                }
                Pending p = new Pending(ctx, slot, adapter, profile, projectile, offset);
                updateTarget(p, ctx.target);
                if (!CollisionSnapshot.safeArea((WorldServer)ctx.npc.world, ctx.npc, p.aim, profile.blastRadius())) {
                    diagnostic(ctx, slot, "unsafe landing area"); retryAt[slot] = ctx.now + 40; projectile.setDead(); continue;
                }
                pending = p; startPlan(ctx, p, ctx.config.integer(prefix + "windup")); return;
            } catch (RuntimeException | LinkageError error) {
                if (projectile != null && !projectile.isDead) projectile.setDead();
                retryAt[slot] = ctx.now + 40;
                if (ctx.now >= nextWarning) { nextWarning = ctx.now + 1200; CNPCGun.LOGGER.warn("Unable to prepare NPC {} throwable slot {}", ctx.npc.getUniqueID(), slot, error); }
            }
        }
        diagnostic(ctx, -1, unavailable);
    }
    private static void updateTarget(Pending p, EntityLivingBase target) {
        p.aim = CollisionSnapshot.vector(CollisionSnapshot.center(target.getEntityBoundingBox())).add(p.offset);
        boolean verticallyStationary = Math.abs(target.posY - target.prevPosY) < 0.001 && bincnpc.gun.common.legacy.Platform.motion(target).y <= 0;
        p.movement = Ballistics.targetMovement(CollisionSnapshot.vector(bincnpc.gun.common.legacy.Platform.motion(target)), target.onGround || verticallyStationary);
        p.targetRadius = Math.min(8, target.width * 0.5);
    }
    private boolean target(CombatContext ctx, Pending p) {
Object cnpcgun$value2; EntityLivingBase cnpcgun$match2;

        if (p.target == null) return true;
        Entity entity = ((WorldServer)ctx.npc.world).getEntityFromUuid(p.target);
        if (((cnpcgun$value2 = entity) instanceof EntityLivingBase && (cnpcgun$match2 = (EntityLivingBase)cnpcgun$value2) != null) && CombatService.validTarget(ctx.npc, cnpcgun$match2)) { updateTarget(p, cnpcgun$match2); return true; }
        switch (ctx.config.text(p.prefix() + "lostTarget")) {
case "CONTINUE":
{ p.target = null; p.movement = new Ballistics.Vec(0, 0, 0); } break;
case "RETARGET":
{
                EntityLivingBase selected = ctx.targets().stream().filter(target -> ctx.npc.getDistance(target) <= ctx.config.number(p.prefix() + "maxRange"))
                    .min(Comparator.comparingDouble(target -> ctx.npc.getDistanceSq(target))).orElse(null);
                if (selected == null) return false; p.target = selected.getUniqueID(); updateTarget(p, selected);
            } break;
default:
{ return false; }
}
        startPlan(ctx, p, p.winding() ? (int)Math.max(0, p.releaseAt - ctx.now) : ctx.config.integer(p.prefix() + "windup")); return true;
    }
    private Ballistics.Request request(CombatContext ctx, Pending p, int lead) {
        Vec3d aim = CollisionSnapshot.vector(p.aim), forward = aim.subtract(ctx.npc.getPositionEyes(1)).normalize();
        Vec3d origin = ctx.npc.getPositionEyes(1).add(0, -0.18, 0).add(forward.scale(0.3));
        ThrowAdapter.Profile profile = p.profile; String prefix = p.prefix();
        Ballistics.Physics physics = new Ballistics.Physics(profile.speed() * ctx.config.number(prefix + "speed"), profile.gravity(), profile.drag(), profile.radius(), profile.blastRadius(), profile.life());
        return new Ballistics.Request(CollisionSnapshot.vector(origin), p.aim, p.movement, physics, Ballistics.Trajectory.valueOf(ctx.config.text(prefix + "trajectory")),
            ctx.config.number(prefix + "maxElevation"), ctx.config.number(prefix + "maxDepression"), ctx.config.number(prefix + "calculationRange"),
            ctx.config.text(prefix + "area").equals("AREA") ? 0 : ctx.config.number(prefix + "deviation"), p.targetRadius, Math.min(200, lead), ctx.config.bool(prefix + "prediction"));
    }
    private void startPlan(CombatContext ctx, Pending p, int lead) {
        // Keep the last complete candidate and its matching snapshot while refreshing it.
        p.clearWork(); p.planStarted = ctx.now;
        try { p.request = request(ctx, p, lead); p.solving = ThrowPlanner.plan(p.request); p.failed = p.solving == null; p.failure = p.failed ? "planner queue unavailable" : ""; }
        catch (IllegalArgumentException invalid) { p.failed = true; p.failure = "invalid native projectile physics or parameters"; }
    }
    private static void advancePlan(CombatContext ctx, Pending p) {
        if (p.solving != null && p.solving.finished()) {
            List<Ballistics.Candidate> result = p.solving.result(); p.solving = null;
            if (result == null || result.isEmpty()) { p.failed = true; p.failure = result == null ? "numeric planner cancelled" : "no ballistic solution for speed, range and angle"; return; }
            try { p.capture = new CollisionSnapshot((WorldServer)ctx.npc.world, ctx.npc, p.request, result); }
            catch (IllegalArgumentException limit) { p.failed = true; p.failure = "terrain capture limit"; return; }
        }
        if (p.capture != null && p.capture.capture()) {
            p.capturedWorld = p.capture.result(); p.capture = null;
            if (p.capturedWorld == null) { p.failed = true; p.failure = "terrain or blast safety capture failed"; return; }
            p.assessing = ThrowPlanner.assess(p.capturedWorld); if (p.assessing == null) { p.failed = true; p.failure = "collision planner queue unavailable"; return; }
        }
        if (p.assessing != null && p.assessing.finished()) {
            Ballistics.Candidate ready = p.assessing.result(); p.assessing = null;
            p.failed = ready == null; p.failure = p.failed ? "all trajectories blocked or unsafe" : "";
            if (ready != null) { p.candidate = ready; p.world = p.capturedWorld; p.readyStarted = p.planStarted; p.releaseCapture = null; }
            p.capturedWorld = null;
        }
    }
    private boolean fresh(CombatContext ctx, Pending p, boolean release) {
        if (p.world == null || ctx.now < p.readyStarted
                || ctx.now - p.readyStarted > PLAN_TIMEOUT && (!release || p.releaseCapture == null)) return false;
        Ballistics.Request planned = p.world.request();
        Ballistics.Request current = request(ctx, p, 0);
        if (current.origin().distance(planned.origin()) > (release ? 0.9 : 2)) return false;
        Ballistics.Vec expected = planned.target().add(planned.prediction() ? planned.movement().scale(ctx.now - p.readyStarted) : new Ballistics.Vec(0, 0, 0));
        return current.target().distance(expected) <= Math.max(0.75, p.targetRadius + ctx.config.number(p.prefix() + "deviation"));
    }
    private void release(CombatContext ctx, Pending p) {
        try {
            Ballistics.Request current = request(ctx, p, 0); Ballistics.Candidate candidate = Ballistics.retarget(current, p.candidate);
            if (candidate == null || p.world == null || !ThrowFlights.hasRoom()) { abort(ctx, "release correction failed or flight table full"); return; }
            // Capture the entire corrected corridor, including cells outside the old prediction.
            if (p.releaseCapture == null || !p.releaseCapture.covers(candidate))
                p.releaseCapture = new CollisionSnapshot((WorldServer)ctx.npc.world, ctx.npc, current, Collections.singletonList(candidate));
            if (!p.releaseCapture.capture()) return;
            Ballistics.World checked = p.releaseCapture.result();
            if (checked == null) { abort(ctx, "release terrain capture failed"); return; }
            candidate = Ballistics.choose(new Ballistics.World(current, Collections.singletonList(candidate), checked.walls(), checked.danger()));
            if (candidate == null || !CollisionSnapshot.safeArea((WorldServer)ctx.npc.world, ctx.npc, candidate.goal(), p.profile.blastRadius())) { abort(ctx, "corrected trajectory blocked or unsafe"); return; }
            if (p.target != null) {
Object cnpcgun$value3; EntityLivingBase cnpcgun$match3;

                Entity entity = ((WorldServer)ctx.npc.world).getEntityFromUuid(p.target);
                if (!(((cnpcgun$value3 = entity) instanceof EntityLivingBase && (cnpcgun$match3 = (EntityLivingBase)cnpcgun$value3) != null)) || !CombatService.validTarget(ctx.npc, cnpcgun$match3)
                        || ctx.npc.getDistance(cnpcgun$match3) < ctx.config.number(p.prefix() + "minRange") || ctx.npc.getDistance(cnpcgun$match3) > ctx.config.number(p.prefix() + "maxRange")) { abort(ctx, "target invalid or outside throwing range"); return; }
            } else if (current.origin().distance(current.target()) < ctx.config.number(p.prefix() + "minRange")
                    || current.origin().distance(current.target()) > ctx.config.number(p.prefix() + "maxRange")) { abort(ctx, "frozen landing area outside throwing range"); return; }
            Entity projectile = p.projectile; projectile.setPosition(candidate.origin().x(), candidate.origin().y(), candidate.origin().z());
            if (!CollisionSnapshot.volumeLoaded((WorldServer)ctx.npc.world, projectile.getEntityBoundingBox()) || !ctx.npc.world.getCollisionBoxes(projectile, projectile.getEntityBoundingBox()).isEmpty()) { abort(ctx, "release point obstructed or terrain unloaded"); return; }
            if (candidate.mode() != Ballistics.Trajectory.CURVE) p.adapter.straight(projectile);
            else projectile.setNoGravity(p.profile.customGravity());
            Ballistics.Vec velocity = candidate.velocity(); p.adapter.launch(projectile, CollisionSnapshot.vector(velocity));
            if (candidate.mode() == Ballistics.Trajectory.TRACKING) bincnpc.gun.common.legacy.Platform.motion(projectile, Vec3d.ZERO);
            else {
                Vec3d next = projectile.getPositionVector().add(CollisionSnapshot.vector(candidate.velocity()).scale(Math.min(1, candidate.ticks())));
                if (!CollisionSnapshot.clear((WorldServer)ctx.npc.world, projectile, projectile.getPositionVector(), next)
                        && projectile.getPositionVector().squareDistanceTo(CollisionSnapshot.vector(candidate.goal())) > current.tolerance() * current.tolerance()) { abort(ctx, "first flight segment obstructed"); return; }
            }
            ThrowFlights.mark(ctx, projectile);
            if (!((WorldServer)ctx.npc.world).spawnEntity(projectile)) { abort(ctx, "native projectile spawn rejected"); return; }
            if (pending != p || ctx.config.revision != p.revision || ctx.data.throwItems.epoch != p.inventoryEpoch
                    || !CombatService.validNpc(ctx.npc) || !ThrowFlights.register(ctx, projectile, p.adapter, p.profile, candidate, p.target)) {
                if (!projectile.isDead) projectile.setDead(); if (pending == p) cancel(); return;
            }
            ctx.data.throwItems.used(ctx, p.slot); nextThrow = ctx.now + ctx.config.integer("throw.cooldown"); recoveryUntil = ctx.now + ctx.config.integer(p.prefix() + "recovery");
            p.clearPlan(); pending = null; ctx.npc.updateClient = true;
            GunNetwork.animation(ctx, "THROW_RELEASE", Math.max(1, ctx.config.integer(p.prefix() + "recovery")));
            CombatService.setState(ctx, "THROWING");
            bincnpc.gun.common.script.EventBridge.emit(ctx, "throw.released", "slot:" + p.slot, Result.success());
        } catch (RuntimeException | LinkageError error) {
            abort(ctx, "native release exception"); if (ctx.now >= nextWarning) { nextWarning = ctx.now + 1200; CNPCGun.LOGGER.warn("Unable to release NPC {} throwable", ctx.npc.getUniqueID(), error); }
        }
    }
    private void hold(CombatContext ctx) {
        ctx.npc.getNavigator().clearPath(); ctx.npc.getMoveHelper().strafe(0, 0); ctx.npc.moveStrafing = 0; ctx.npc.moveForward = 0;
        Vec3d movement = bincnpc.gun.common.legacy.Platform.motion(ctx.npc); bincnpc.gun.common.legacy.Platform.motion(ctx.npc, 0, movement.y, 0); CombatService.setState(ctx, "THROWING");
    }
    /** Tail of NPC aiStep: navigation cannot replace the fixed, collision-aware 0.02 block backstep. */
    public void afterAi(CombatContext ctx) {
        if (!CombatService.serverThread(ctx.npc) || !movementLocked() || !CombatService.validNpc(ctx.npc) || lastBackstep == ctx.now) return;
        lastBackstep = ctx.now; hold(ctx);
        if (ctx.now >= pending.releaseAt) return;
        Vec3d away = ctx.npc.getPositionVector().subtract(CollisionSnapshot.vector(pending.aim));
        double horizontal = Math.sqrt(bincnpc.gun.common.legacy.Platform.horizontalSquare(away)); if (horizontal < 1E-6) away = Vec3d.fromPitchYaw(0, ctx.npc.rotationYaw).scale(-1); else away = new Vec3d(away.x / horizontal, 0, away.z / horizontal);
        net.minecraft.util.math.AxisAlignedBB destination = ctx.npc.getEntityBoundingBox().offset(away.scale(0.02));
        WorldServer level = (WorldServer)ctx.npc.world;
        if (!CollisionSnapshot.volumeLoaded(level, destination)) return;
        ctx.npc.move(MoverType.SELF, away.x * 0.02, 0, away.z * 0.02);
    }
}
