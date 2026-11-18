package bincnpc.gun.throwing;

import bincnpc.gun.common.combat.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import noppes.npcs.entity.EntityNPCInterface;

/** All reads happen on the server thread, are budgeted, and never request an unloaded chunk. */
public final class CollisionSnapshot {
    public static final int MAX_CELLS = 4096, PER_TICK = 192, SERVER_BUDGET = 2048;
    private static int remaining = SERVER_BUDGET;
    private final ServerLevel level;
    private final EntityNPCInterface owner;
    private final Ballistics.Request request;
    private final List<Ballistics.Candidate> candidates;
    private final List<Ballistics.Cell> cells;
    private final boolean safeDistance;
    private final List<Ballistics.Bounds> walls = new ArrayList<>();
    private int cursor;
    private boolean failed;
    public CollisionSnapshot(ServerLevel level, EntityNPCInterface owner, Ballistics.Request request, List<Ballistics.Candidate> candidates, boolean safeDistance) {
        this.level = level; this.owner = owner; this.request = request; this.candidates = List.copyOf(candidates);
        this.safeDistance = safeDistance;
        LinkedHashSet<Ballistics.Cell> cells = new LinkedHashSet<>();
        for (var candidate : candidates) for (int i = 1; i < candidate.points().size(); i++) {
            for (var cell : Ballistics.cells(candidate.points().get(i - 1), candidate.points().get(i), request.physics().radius(), MAX_CELLS)) {
                cells.add(cell); cells.add(new Ballistics.Cell(cell.x(), cell.y() - 1, cell.z()));
                if (cells.size() > MAX_CELLS) throw new IllegalArgumentException("terrain budget");
            }
        }
        this.cells = List.copyOf(cells);
    }
    public static void beginTick() { remaining = SERVER_BUDGET; }
    private static boolean budget() { if (remaining <= 0) return false; remaining--; return true; }
    public boolean capture() {
        requireThread(level);
        for (int count = 0; !failed && cursor < cells.size() && count < PER_TICK && budget(); count++, cursor++) {
            var cell = cells.get(cursor); BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            if (!loadedShape(level, pos)) { walls.add(new Ballistics.Bounds(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1)); }
            else {
                var state = level.getBlockState(pos);
                if (!state.getFluidState().isEmpty()) walls.add(new Ballistics.Bounds(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1));
                for (AABB box : state.getCollisionShape(level, pos, CollisionContext.of(owner)).toAabbs()) {
                    walls.add(bounds(box.move(pos)));
                    if (walls.size() > Ballistics.MAX_BOXES) { failed = true; break; }
                }
            }
            if (walls.size() > Ballistics.MAX_BOXES) failed = true;
        }
        return failed || cursor >= cells.size();
    }
    public Ballistics.World result() {
        if (failed || cursor < cells.size()) return null;
        List<Ballistics.Bounds> danger = safeDistance ? danger(level, owner, request.target(), request.physics().blast()) : List.of();
        return danger == null ? null : new Ballistics.World(request, candidates, walls, danger);
    }
    public static Ballistics.Vec vector(Vec3 v) { return new Ballistics.Vec(v.x, v.y, v.z); }
    public static Vec3 vector(Ballistics.Vec v) { return new Vec3(v.x(), v.y(), v.z()); }
    public static Ballistics.Bounds bounds(AABB b) { return new Ballistics.Bounds(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ); }
    public static boolean loaded(ServerLevel level, BlockPos pos) { return !level.isOutsideBuildHeight(pos) && level.hasChunkAt(pos); }
    public static boolean loadedShape(ServerLevel level, BlockPos pos) {
        if (!loaded(level, pos)) return false;
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) if (!level.hasChunkAt(pos.offset(x, 0, z))) return false;
        return true;
    }
    public static boolean volumeLoaded(ServerLevel level, AABB box) {
        if (box.minY < level.getMinBuildHeight() || box.maxY > level.getMaxBuildHeight()) return false;
        int x0 = (int)Math.floor((box.minX - 1) / 16), x1 = (int)Math.floor((box.maxX + 1) / 16);
        int z0 = (int)Math.floor((box.minZ - 1) / 16), z1 = (int)Math.floor((box.maxZ + 1) / 16);
        if ((long)(x1 - x0 + 1) * (z1 - z0 + 1) > 64) return false;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++)
            if (!level.hasChunkAt(new BlockPos(x * 16, (int)Math.floor(box.minY), z * 16))) return false;
        return true;
    }
    private static void requireThread(ServerLevel level) { if (!level.getServer().isSameThread()) throw new IllegalStateException("main-thread terrain capture"); }

    /** SAFE timing checks blast clearance in addition to the configured throwing range. */
    private static List<Ballistics.Bounds> danger(ServerLevel level, EntityNPCInterface owner, Ballistics.Vec goal, double blast) {
        requireThread(level); if (blast <= 0) return List.of();
        List<Ballistics.Bounds> result = new ArrayList<>(); result.add(bounds(owner.getBoundingBox().inflate(blast + 0.35)));
        AABB area = new AABB(goal.x() - blast - 2, goal.y() - blast - 2, goal.z() - blast - 2, goal.x() + blast + 2, goal.y() + blast + 2, goal.z() + blast + 2);
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, area, entity -> entity != owner && entity.isAlive() && !entity.isSpectator() && !CombatService.hostile(owner, entity))) {
            if (result.size() >= 65) return null;
            result.add(bounds(nearby.getBoundingBox().inflate(blast + 0.35)));
        }
        return List.copyOf(result);
    }
    public static boolean safeArea(ServerLevel level, EntityNPCInterface owner, Ballistics.Vec goal, double blast) {
        var danger = danger(level, owner, goal, blast);
        return danger != null && danger.stream().noneMatch(box -> box.contains(goal));
    }
    /** A ray is allowed only through loaded cells; the copied flight path provides the longer-term check. */
    public static boolean clear(ServerLevel level, Entity entity, Vec3 start, Vec3 end) {
        requireThread(level);
        if (!Double.isFinite(start.x) || !Double.isFinite(start.y) || !Double.isFinite(start.z)
                || !Double.isFinite(end.x) || !Double.isFinite(end.y) || !Double.isFinite(end.z) || start.distanceToSqr(end) > 4096.0 * 4096.0) return false;
        BlockGetter view = new BlockGetter() {
            @Override public BlockState getBlockState(BlockPos pos) {
                if (level.isOutsideBuildHeight(pos)) return Blocks.AIR.defaultBlockState();
                var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                return chunk == null ? Blocks.BARRIER.defaultBlockState() : chunk.getBlockState(pos);
            }
            @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
            @Override public BlockEntity getBlockEntity(BlockPos pos) {
                var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                return chunk == null ? null : chunk.getBlockEntity(pos);
            }
            @Override public int getHeight() { return level.getHeight(); }
            @Override public int getMinBuildHeight() { return level.getMinBuildHeight(); }
        };
        // Shared by NPC sight and projectile flight. Neighbor shape reads also stay in the non-loading view.
        return view.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getType() == HitResult.Type.MISS;
    }
    public static boolean stepClear(ServerLevel level, Entity projectile, Vec3 from, Vec3 to) {
        if (!clear(level, projectile, from, to)) return false;
        double radius = Math.min(2, projectile.getBbWidth() * 0.5);
        try {
            for (var cell : Ballistics.cells(vector(from), vector(to), radius, 512)) {
                if (!loaded(level, new BlockPos(cell.x(), cell.y(), cell.z()))) return false;
            }
        } catch (IllegalArgumentException tooLarge) { return false; }
        AABB body = projectile.getBoundingBox().move(to.subtract(from));
        return volumeLoaded(level, body) && level.noCollision(projectile, body);
    }

    public static final class RouteCapture {
        private final ServerLevel level;
        private final Entity projectile;
        private final Ballistics.Vec min, start, target;
        private final boolean[] blocked = new boolean[Ballistics.GRID_SIDE * Ballistics.GRID_SIDE * Ballistics.GRID_SIDE];
        private int cursor;
        public RouteCapture(ServerLevel level, Entity projectile, Vec3 target) {
            this.level = level; this.projectile = projectile; this.start = vector(projectile.position()); this.target = vector(target);
            min = new Ballistics.Vec(Math.floor(start.x()) - 4, Math.floor(start.y()) - 4, Math.floor(start.z()) - 4);
        }
        public Ballistics.Vec start() { return start; }
        public Ballistics.Vec target() { return target; }
        public boolean capture() {
            requireThread(level); int side = Ballistics.GRID_SIDE;
            for (int count = 0; cursor < blocked.length && count < PER_TICK / 2 && remaining >= 2; count++, cursor++) {
                remaining -= 2;
                int x = (int)min.x() + cursor % side, y = (int)min.y() + cursor / (side * side), z = (int)min.z() + cursor / side % side;
                BlockPos pos = new BlockPos(x, y, z); double r = Math.max(0.08, Math.min(0.49, projectile.getBbWidth() * 0.5));
                AABB body = new AABB(x + 0.5 - r, y + 0.5 - r, z + 0.5 - r, x + 0.5 + r, y + 0.5 + r, z + 0.5 + r);
                if (!loadedShape(level, pos) || !loadedShape(level, pos.below())) { blocked[cursor] = true; continue; }
                for (BlockPos sample : new BlockPos[]{pos, pos.below()}) {
                    var state = level.getBlockState(sample);
                    if (!state.getFluidState().isEmpty()) { blocked[cursor] = true; break; }
                    for (AABB box : state.getCollisionShape(level, sample, CollisionContext.of(projectile)).toAabbs())
                        if (box.move(sample).intersects(body)) { blocked[cursor] = true; break; }
                }
            }
            return cursor >= blocked.length;
        }
        public Ballistics.Grid result() { return cursor >= blocked.length ? new Ballistics.Grid(min, start, target, blocked) : null; }
    }
}
