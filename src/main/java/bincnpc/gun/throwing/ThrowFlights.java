package bincnpc.gun.throwing;

import bincnpc.gun.CNPCGun;
import bincnpc.gun.common.combat.*;
import java.util.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Only server-created projectiles enter this bounded table; network packets cannot register entities. */
public final class ThrowFlights {
    public static final int MAX_FLIGHTS = 192, FAILURE_TICKS = 40;
    private static final Map<UUID, Flight> flights = new LinkedHashMap<>();
    private static final String OWNER_TAG = "CNPCGunThrowOwner";
    private static final class Flight {
        final UUID id, owner, target;
        final ResourceKey<Level> dimension;
        final ThrowAdapter adapter;
        final ThrowAdapter.Profile profile;
        final Ballistics.Trajectory mode;
        final Vec3 goal;
        final double speed, travel;
        final long started;
        int failures, waypoint;
        long nextRay, nextRoute, nextSync, routeStarted;
        boolean direct;
        CollisionSnapshot.RouteCapture capture;
        ThrowPlanner.Ticket<List<Ballistics.Vec>> ticket;
        Ballistics.Vec routeOrigin, routeTarget;
        List<Ballistics.Vec> path = List.of();
        Flight(CombatContext ctx, Projectile projectile, ThrowAdapter adapter, ThrowAdapter.Profile profile, Ballistics.Candidate candidate, UUID target) {
            id = projectile.getUUID(); owner = ctx.npc.getUUID(); this.target = target; dimension = ctx.npc.level().dimension();
            this.adapter = adapter; this.profile = profile; mode = candidate.mode(); goal = CollisionSnapshot.vector(candidate.goal());
            speed = candidate.velocity().length(); travel = candidate.ticks(); started = ctx.now;
        }
        void cancelRoute() { if (ticket != null) ticket.cancel(); ticket = null; capture = null; path = List.of(); waypoint = 0; }
    }
    private ThrowFlights() {}
    public static boolean hasRoom() { return flights.size() < MAX_FLIGHTS; }
    public static void mark(CombatContext ctx, Projectile projectile) {
        if (CombatService.serverThread(ctx.npc) && projectile.level() == ctx.npc.level()) projectile.getPersistentData().putUUID(OWNER_TAG, ctx.npc.getUUID());
    }
    public static boolean register(CombatContext ctx, Projectile projectile, ThrowAdapter adapter, ThrowAdapter.Profile profile, Ballistics.Candidate candidate, UUID target) {
        if (!CombatService.serverThread(ctx.npc) || projectile.isRemoved() || projectile.level() != ctx.npc.level() || !hasRoom()) return false;
        projectile.getPersistentData().putUUID(OWNER_TAG, ctx.npc.getUUID());
        flights.put(projectile.getUUID(), new Flight(ctx, projectile, adapter, profile, candidate, target)); return true;
    }
    public static boolean owned(Entity projectile, Entity owner) {
        return projectile instanceof Projectile && owner != null && projectile.getPersistentData().hasUUID(OWNER_TAG)
            && owner.getUUID().equals(projectile.getPersistentData().getUUID(OWNER_TAG));
    }
    public static void tick(MinecraftServer server) {
        if (!server.isSameThread()) return;
        for (Flight flight : new ArrayList<>(flights.values())) {
            ServerLevel level = server.getLevel(flight.dimension);
            Entity entity = level == null ? null : level.getEntity(flight.id);
            if (!(entity instanceof Projectile projectile) || entity.isRemoved()) { remove(flight); continue; }
            long now = level.getGameTime();
            try {
                if (now < flight.started || now - flight.started > Math.min(1200, flight.profile.life() + FAILURE_TICKS)) { detonate(flight, projectile); continue; }
                if (flight.mode == Ballistics.Trajectory.TRACKING) tracking(level, flight, projectile, now);
                else {
                    if (flight.mode == Ballistics.Trajectory.CURVE && flight.profile.customGravity())
                        projectile.setDeltaMovement(projectile.getDeltaMovement().add(0, -flight.profile.gravity(), 0));
                    // Bouncing explosives retain their native fuse/effect after arriving at the calculated landing area.
                    if ((flight.profile.blastRadius() > 0 || flight.adapter.settleAtLanding(projectile)) && projectile.tickCount >= Math.ceil(flight.travel)
                            && projectile.position().distanceToSqr(flight.goal) < Math.max(4, flight.profile.blastRadius() * flight.profile.blastRadius()))
                        flight.adapter.guide(projectile, Vec3.ZERO);
                    // Do not allow a missed native projectile to force terrain loading beyond its captured corridor.
                    try {
                        for (var cell : Ballistics.cells(CollisionSnapshot.vector(projectile.position()), CollisionSnapshot.vector(projectile.position().add(projectile.getDeltaMovement())), flight.profile.radius(), 1024))
                            if (!CollisionSnapshot.loadedShape(level, new net.minecraft.core.BlockPos(cell.x(), cell.y(), cell.z()))) { flight.adapter.guide(projectile, Vec3.ZERO); break; }
                    } catch (IllegalArgumentException tooLarge) { flight.adapter.guide(projectile, Vec3.ZERO); }
                }
                if (!projectile.isRemoved() && now >= flight.nextSync) { flight.nextSync = now + 5; flight.adapter.sync(projectile); }
            } catch (RuntimeException | LinkageError error) {
                CNPCGun.LOGGER.warn("NPC throwable flight ended after adapter failure: {}", error.toString()); projectile.discard(); remove(flight);
            }
        }
    }
    private static void tracking(ServerLevel level, Flight f, Projectile projectile, long now) {
        Entity owner = level.getEntity(f.owner), target = f.target == null ? null : level.getEntity(f.target);
        if (!(owner instanceof noppes.npcs.entity.EntityNPCInterface npc) || !(target instanceof LivingEntity living)
                || !CombatService.validTarget(npc, living) || projectile.distanceToSqr(living) > 128 * 128) {
            f.cancelRoute(); f.adapter.guide(projectile, Vec3.ZERO);
            if (++f.failures >= FAILURE_TICKS) detonate(f, projectile); return;
        }
        Vec3 goal = living.getBoundingBox().getCenter();
        if (now >= f.nextRay) {
            f.nextRay = now + 4; f.direct = CollisionSnapshot.clear(level, projectile, projectile.position(), goal);
            if (f.direct) f.cancelRoute();
        }
        if (f.direct) {
            Vec3 movement = goal.subtract(projectile.position());
            double speed = Math.min(f.speed, Math.max(0.01, movement.length())); Vec3 next = projectile.position().add(movement.normalize().scale(speed));
            if (CollisionSnapshot.stepClear(level, projectile, projectile.position(), next)) {
                f.failures = 0; f.adapter.guide(projectile, movement.normalize().scale(speed)); return;
            }
            f.direct = false;
        }
        if (f.ticket != null && f.ticket.finished()) {
            List<Ballistics.Vec> path = f.ticket.result(); f.ticket = null;
            if (path != null && now - f.routeStarted <= 28 && f.routeOrigin != null && f.routeTarget != null
                    && f.routeOrigin.distance(CollisionSnapshot.vector(projectile.position())) <= 0.8 && f.routeTarget.distance(CollisionSnapshot.vector(goal)) <= 3) { f.path = path; f.waypoint = 0; }
            else f.path = List.of();
        }
        while (f.waypoint < f.path.size() && projectile.position().distanceToSqr(CollisionSnapshot.vector(f.path.get(f.waypoint))) < 0.2 * 0.2) f.waypoint++;
        if (f.waypoint < f.path.size()) {
            Vec3 movement = CollisionSnapshot.vector(f.path.get(f.waypoint)).subtract(projectile.position());
            Vec3 step = movement.normalize().scale(Math.min(f.speed, movement.length()));
            if (CollisionSnapshot.stepClear(level, projectile, projectile.position(), projectile.position().add(step))) {
                f.failures = 0; f.adapter.guide(projectile, step); return;
            }
            f.path = List.of(); f.waypoint = 0;
        }
        f.adapter.guide(projectile, Vec3.ZERO);
        if (++f.failures >= FAILURE_TICKS) { detonate(f, projectile); return; }
        if (f.capture != null && f.capture.capture()) {
            Ballistics.Grid grid = f.capture.result();
            boolean fresh = f.capture.start().distance(CollisionSnapshot.vector(projectile.position())) <= 0.8 && now - f.routeStarted <= 24;
            f.routeOrigin = f.capture.start(); f.routeTarget = f.capture.target();
            f.capture = null;
            if (fresh && grid != null) f.ticket = ThrowPlanner.route(grid);
            if (f.ticket == null) f.nextRoute = now + 4;
        }
        if (f.capture == null && f.ticket == null && now >= f.nextRoute) {
            f.routeStarted = now; f.nextRoute = now + 12; f.capture = new CollisionSnapshot.RouteCapture(level, projectile, goal);
        }
    }
    private static void remove(Flight flight) { flights.remove(flight.id); flight.cancelRoute(); }
    private static void detonate(Flight flight, Projectile projectile) {
        remove(flight);
        if (!projectile.isRemoved()) { flight.adapter.detonate(projectile); if (!projectile.isRemoved()) projectile.discard(); }
    }
    public static void stop(MinecraftServer server) {
        for (Flight flight : new ArrayList<>(flights.values())) {
            flight.cancelRoute(); ServerLevel level = server.getLevel(flight.dimension); Entity entity = level == null ? null : level.getEntity(flight.id);
            if (entity instanceof Projectile projectile && !entity.isRemoved()) {
                try { flight.adapter.restore(projectile, flight.profile); } catch (RuntimeException | LinkageError ignored) { projectile.setNoGravity(false); }
            }
        }
        flights.clear();
    }
}
