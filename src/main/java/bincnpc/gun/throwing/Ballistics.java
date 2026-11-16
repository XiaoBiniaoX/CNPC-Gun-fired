package bincnpc.gun.throwing;

import java.util.*;

/** Immutable numeric inputs only: neither the solver nor route search can access the game. */
public final class Ballistics {
    public static final int MAX_TICKS = 600, MAX_CANDIDATES = 4, MAX_BOXES = 4096, GRID_SIDE = 9, MAX_NODES = 512;
    public enum Trajectory { STRAIGHT, CURVE, AUTO, TRACKING }
    public static final class Vec  {
private final double x;
private final double y;
private final double z;
public Vec(double x, double y, double z) { if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("vector"); 
this.x = x; this.y = y; this.z = z;}
public double x() { return x; }
public double y() { return y; }
public double z() { return z; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof Vec))return false; Vec that=(Vec)value; return java.util.Objects.equals(x, that.x) && java.util.Objects.equals(y, that.y) && java.util.Objects.equals(z, that.z); }
@Override public int hashCode() { return java.util.Objects.hash(x, y, z); }

        
        public Vec add(Vec v) { return new Vec(x + v.x, y + v.y, z + v.z); }
        public Vec subtract(Vec v) { return new Vec(x - v.x, y - v.y, z - v.z); }
        public Vec scale(double s) { return new Vec(x * s, y * s, z * s); }
        public double length() { return Math.sqrt(x * x + y * y + z * z); }
        public double distance(Vec v) { return subtract(v).length(); }
        public Vec unit() { double n = length(); return n < 1E-10 ? new Vec(0, 0, 0) : scale(1 / n); }
        public Vec lerp(Vec v, double t) { return add(v.subtract(this).scale(t)); }
    
}
    public static final class Bounds  {
private final double x0;
private final double y0;
private final double z0;
private final double x1;
private final double y1;
private final double z1;
public Bounds(double x0, double y0, double z0, double x1, double y1, double z1) {
            if (!Double.isFinite(x0) || !Double.isFinite(y0) || !Double.isFinite(z0) || !Double.isFinite(x1)
                    || !Double.isFinite(y1) || !Double.isFinite(z1) || x0 > x1 || y0 > y1 || z0 > z1) throw new IllegalArgumentException("bounds");
        
this.x0 = x0; this.y0 = y0; this.z0 = z0; this.x1 = x1; this.y1 = y1; this.z1 = z1;}
public double x0() { return x0; }
public double y0() { return y0; }
public double z0() { return z0; }
public double x1() { return x1; }
public double y1() { return y1; }
public double z1() { return z1; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof Bounds))return false; Bounds that=(Bounds)value; return java.util.Objects.equals(x0, that.x0) && java.util.Objects.equals(y0, that.y0) && java.util.Objects.equals(z0, that.z0) && java.util.Objects.equals(x1, that.x1) && java.util.Objects.equals(y1, that.y1) && java.util.Objects.equals(z1, that.z1); }
@Override public int hashCode() { return java.util.Objects.hash(x0, y0, z0, x1, y1, z1); }

        
        public Bounds inflate(double r) { return new Bounds(x0 - r, y0 - r, z0 - r, x1 + r, y1 + r, z1 + r); }
        public boolean contains(Vec p) { return p.x >= x0 && p.x <= x1 && p.y >= y0 && p.y <= y1 && p.z >= z0 && p.z <= z1; }
        /** First intersection with a swept point; +infinity means no intersection. */
        public double hit(Vec a, Vec b) {
            double low = 0, high = 1;
            double[] p = {a.x, a.y, a.z}, d = {b.x - a.x, b.y - a.y, b.z - a.z}, min = {x0, y0, z0}, max = {x1, y1, z1};
            for (int i = 0; i < 3; i++) {
                if (Math.abs(d[i]) < 1E-10) { if (p[i] < min[i] || p[i] > max[i]) return Double.POSITIVE_INFINITY; }
                else {
                    double l = (min[i] - p[i]) / d[i], h = (max[i] - p[i]) / d[i];
                    if (l > h) { double t = l; l = h; h = t; }
                    low = Math.max(low, l); high = Math.min(high, h);
                    if (low > high) return Double.POSITIVE_INFINITY;
                }
            }
            return low;
        }
    
}
    public static final class Physics  {
private final double speed;
private final double gravity;
private final double drag;
private final double radius;
private final double blast;
private final int life;
public Physics(double speed, double gravity, double drag, double radius, double blast, int life) {
            if (!Double.isFinite(speed) || speed < 0.0001 || speed > 800 || !Double.isFinite(gravity) || gravity < 0 || gravity > 2
                    || !Double.isFinite(drag) || drag <= 0 || drag > 1 || !Double.isFinite(radius) || radius < 0 || radius > 2
                    || !Double.isFinite(blast) || blast < 0 || blast > 32 || life < 1 || life > 1200) throw new IllegalArgumentException("physics");
        
this.speed = speed; this.gravity = gravity; this.drag = drag; this.radius = radius; this.blast = blast; this.life = life;}
public double speed() { return speed; }
public double gravity() { return gravity; }
public double drag() { return drag; }
public double radius() { return radius; }
public double blast() { return blast; }
public int life() { return life; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof Physics))return false; Physics that=(Physics)value; return java.util.Objects.equals(speed, that.speed) && java.util.Objects.equals(gravity, that.gravity) && java.util.Objects.equals(drag, that.drag) && java.util.Objects.equals(radius, that.radius) && java.util.Objects.equals(blast, that.blast) && java.util.Objects.equals(life, that.life); }
@Override public int hashCode() { return java.util.Objects.hash(speed, gravity, drag, radius, blast, life); }

        
    
}
    public static final class Request  {
private final Vec origin;
private final Vec target;
private final Vec movement;
private final Physics physics;
private final Trajectory mode;
private final double elevation;
private final double depression;
private final double range;
private final double deviation;
private final double targetRadius;
private final int lead;
private final boolean prediction;
public Request(Vec origin, Vec target, Vec movement, Physics physics, Trajectory mode, double elevation, double depression, double range, double deviation, double targetRadius, int lead, boolean prediction) {
            Objects.requireNonNull(origin); Objects.requireNonNull(target); Objects.requireNonNull(movement); Objects.requireNonNull(physics); Objects.requireNonNull(mode);
            if (!Double.isFinite(elevation) || elevation < 0 || elevation > 89 || !Double.isFinite(depression) || depression < 0 || depression > 89
                    || !Double.isFinite(range) || range < 1 || range > 128 || !Double.isFinite(deviation) || deviation < 0 || deviation > 16
                    || !Double.isFinite(targetRadius) || targetRadius < 0 || targetRadius > 8 || lead < 0 || lead > 200 || movement.length() > 4)
                throw new IllegalArgumentException("throw request");
        
this.origin = origin; this.target = target; this.movement = movement; this.physics = physics; this.mode = mode; this.elevation = elevation; this.depression = depression; this.range = range; this.deviation = deviation; this.targetRadius = targetRadius; this.lead = lead; this.prediction = prediction;}
public Vec origin() { return origin; }
public Vec target() { return target; }
public Vec movement() { return movement; }
public Physics physics() { return physics; }
public Trajectory mode() { return mode; }
public double elevation() { return elevation; }
public double depression() { return depression; }
public double range() { return range; }
public double deviation() { return deviation; }
public double targetRadius() { return targetRadius; }
public int lead() { return lead; }
public boolean prediction() { return prediction; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof Request))return false; Request that=(Request)value; return java.util.Objects.equals(origin, that.origin) && java.util.Objects.equals(target, that.target) && java.util.Objects.equals(movement, that.movement) && java.util.Objects.equals(physics, that.physics) && java.util.Objects.equals(mode, that.mode) && java.util.Objects.equals(elevation, that.elevation) && java.util.Objects.equals(depression, that.depression) && java.util.Objects.equals(range, that.range) && java.util.Objects.equals(deviation, that.deviation) && java.util.Objects.equals(targetRadius, that.targetRadius) && java.util.Objects.equals(lead, that.lead) && java.util.Objects.equals(prediction, that.prediction); }
@Override public int hashCode() { return java.util.Objects.hash(origin, target, movement, physics, mode, elevation, depression, range, deviation, targetRadius, lead, prediction); }

        
        public Vec goal(double ticks) { return prediction ? target.add(movement.scale(lead + ticks)) : target; }
        public double tolerance() { return Math.max(0.35, deviation + targetRadius); }
    
}
    public static final class Candidate  {
private final Trajectory mode;
private final Vec origin;
private final Vec velocity;
private final Vec goal;
private final double ticks;
private final List<Vec> points;
public Candidate(Trajectory mode, Vec origin, Vec velocity, Vec goal, double ticks, List<Vec> points) {
            if (mode == null || mode == Trajectory.AUTO || origin == null || velocity == null || goal == null
                    || !Double.isFinite(ticks) || ticks <= 0 || ticks > MAX_TICKS || points == null || points.size() < 2 || points.size() > MAX_TICKS + 2)
                throw new IllegalArgumentException("candidate");
            points = bincnpc.gun.common.legacy.Legacy.copyList(points);
        
this.mode = mode; this.origin = origin; this.velocity = velocity; this.goal = goal; this.ticks = ticks; this.points = points;}
public Trajectory mode() { return mode; }
public Vec origin() { return origin; }
public Vec velocity() { return velocity; }
public Vec goal() { return goal; }
public double ticks() { return ticks; }
public List<Vec> points() { return points; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof Candidate))return false; Candidate that=(Candidate)value; return java.util.Objects.equals(mode, that.mode) && java.util.Objects.equals(origin, that.origin) && java.util.Objects.equals(velocity, that.velocity) && java.util.Objects.equals(goal, that.goal) && java.util.Objects.equals(ticks, that.ticks) && java.util.Objects.equals(points, that.points); }
@Override public int hashCode() { return java.util.Objects.hash(mode, origin, velocity, goal, ticks, points); }

        
    
}
    public static final class World  {
private final Request request;
private final List<Candidate> candidates;
private final List<Bounds> walls;
private final List<Bounds> danger;
public World(Request request, List<Candidate> candidates, List<Bounds> walls, List<Bounds> danger) {
            Objects.requireNonNull(request);
            if (candidates == null || candidates.size() > MAX_CANDIDATES || walls == null || walls.size() > MAX_BOXES || danger == null || danger.size() > 65)
                throw new IllegalArgumentException("collision snapshot");
            candidates = bincnpc.gun.common.legacy.Legacy.copyList(candidates); walls = bincnpc.gun.common.legacy.Legacy.copyList(walls); danger = bincnpc.gun.common.legacy.Legacy.copyList(danger);
        
this.request = request; this.candidates = candidates; this.walls = walls; this.danger = danger;}
public Request request() { return request; }
public List<Candidate> candidates() { return candidates; }
public List<Bounds> walls() { return walls; }
public List<Bounds> danger() { return danger; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof World))return false; World that=(World)value; return java.util.Objects.equals(request, that.request) && java.util.Objects.equals(candidates, that.candidates) && java.util.Objects.equals(walls, that.walls) && java.util.Objects.equals(danger, that.danger); }
@Override public int hashCode() { return java.util.Objects.hash(request, candidates, walls, danger); }

        
    
}
    private Ballistics() {}
    /** Grounded entities retain a gravity impulse even while their position stays still. */
    public static Vec targetMovement(Vec movement, boolean grounded) {
        Vec result = grounded && movement.y() < 0 ? new Vec(movement.x(), 0, movement.z()) : movement;
        return result.length() > 2 ? result.unit().scale(2) : result;
    }

    public static List<Candidate> candidates(Request r) {
        if (r.origin.distance(r.target) > r.range || r.origin.distance(r.target) < 0.05) return bincnpc.gun.common.legacy.Legacy.list();
        if (r.mode == Trajectory.TRACKING) {
            Vec direction = r.target.subtract(r.origin).unit();
            if (!angle(r, direction)) return bincnpc.gun.common.legacy.Legacy.list();
            double time = Math.max(0.05, Math.min(Math.min(MAX_TICKS, r.physics.life), r.origin.distance(r.target) / r.physics.speed));
            return bincnpc.gun.common.legacy.Legacy.list(new Candidate(Trajectory.TRACKING, r.origin, direction.scale(r.physics.speed), r.target, time,
                bincnpc.gun.common.legacy.Legacy.list(r.origin, r.origin.add(direction.scale(0.2)))));
        }
        List<Candidate> result = new ArrayList<>(MAX_CANDIDATES);
        if (r.mode == Trajectory.STRAIGHT || r.mode == Trajectory.AUTO) solve(r, Trajectory.STRAIGHT, 0, result);
        if (r.mode == Trajectory.CURVE || r.mode == Trajectory.AUTO) solve(r, Trajectory.CURVE, r.physics.gravity, result);
        return bincnpc.gun.common.legacy.Legacy.copyList(result);
    }
    private static void solve(Request r, Trajectory mode, double gravity, List<Candidate> result) {
        if (mode == Trajectory.CURVE && gravity < 1E-9) return;
        double end = Math.min(MAX_TICKS, Math.max(0.05, r.physics.life - 1)), previous = 0.01;
        double prior = required(r, gravity, previous).length() - r.physics.speed;
        for (int i = 1; i <= 256 && result.size() < MAX_CANDIDATES; i++) {
            if (Thread.currentThread().isInterrupted()) return;
            double t = 0.01 * Math.pow(end / 0.01, i / 256.0);
            double error = required(r, gravity, t).length() - r.physics.speed;
            if ((prior <= 0 && error >= 0) || (prior >= 0 && error <= 0)) {
                double lo = previous, hi = t, side = prior;
                for (int j = 0; j < 28; j++) {
                    double mid = (lo + hi) * 0.5, f = required(r, gravity, mid).length() - r.physics.speed;
                    if ((side <= 0 && f <= 0) || (side >= 0 && f >= 0)) { lo = mid; side = f; } else hi = mid;
                }
                double root = (lo + hi) * 0.5;
                Vec velocity = required(r, gravity, root), goal = r.goal(root);
                if (angle(r, velocity) && r.origin.distance(goal) <= r.range
                        && result.stream().noneMatch(c -> c.mode == mode && Math.abs(c.ticks - root) < 0.05)) {
                    List<Vec> path = path(r.origin, velocity, gravity, r.physics.drag, root);
                    double length = 0;
                    for (int p = 1; p < path.size(); p++) length += path.get(p).distance(path.get(p - 1));
                    if (length <= Math.min(512, r.range * 4)) result.add(new Candidate(mode, r.origin, velocity, goal, root, path));
                }
            }
            previous = t; prior = error;
        }
    }
    private static boolean angle(Request r, Vec velocity) {
        double angle = Math.toDegrees(Math.atan2(velocity.y, Math.hypot(velocity.x, velocity.z)));
        return angle <= r.elevation + 1E-6 && angle >= -r.depression - 1E-6;
    }
    /** Exact discrete drag/gravity displacement, with linear movement within the final tick. */
    public static Vec position(Vec origin, Vec velocity, double gravity, double drag, double ticks) {
        double[] terms = terms(drag, ticks);
        return origin.add(velocity.scale(terms[0])).add(new Vec(0, -gravity * terms[1], 0));
    }
    private static double[] terms(double drag, double ticks) {
        int n = (int)Math.floor(ticks); double fraction = ticks - n;
        if (Math.abs(1 - drag) < 1E-9) return new double[]{ticks, n * (n - 1) * 0.5 + fraction * n};
        double dn = Math.pow(drag, n), sum = (1 - dn) / (1 - drag);
        return new double[]{sum + fraction * dn, (n - sum) / (1 - drag) + fraction * sum};
    }
    private static Vec required(Request r, double gravity, double t) {
        double[] terms = terms(r.physics.drag, t);
        return r.goal(t).subtract(r.origin).add(new Vec(0, gravity * terms[1], 0)).scale(1 / terms[0]);
    }
    public static List<Vec> path(Vec origin, Vec velocity, double gravity, double drag, double ticks) {
        if (!Double.isFinite(ticks) || ticks <= 0 || ticks > MAX_TICKS) throw new IllegalArgumentException("flight time");
        List<Vec> result = new ArrayList<>((int)Math.ceil(ticks) + 1); result.add(origin);
        for (int i = 1; i < ticks; i++) result.add(position(origin, velocity, gravity, drag, i));
        result.add(position(origin, velocity, gravity, drag, ticks)); return bincnpc.gun.common.legacy.Legacy.copyList(result);
    }
    /** Corrects the final release pose while preserving the exact configured launch speed. */
    public static Candidate retarget(Request r, Candidate prior) {
        if (r.origin.distance(r.target) > r.range) return null;
        if (prior.mode == Trajectory.TRACKING) return candidates(r).stream().findFirst().orElse(null);
        double gravity = prior.mode == Trajectory.CURVE ? r.physics.gravity : 0;
        Vec velocity = required(r, gravity, prior.ticks).unit().scale(r.physics.speed), goal = r.goal(prior.ticks);
        if (!angle(r, velocity) || r.origin.distance(goal) > r.range) return null;
        List<Vec> path = path(r.origin, velocity, gravity, r.physics.drag, prior.ticks);
        if (path.get(path.size() - 1).distance(goal) > r.tolerance()) return null;
        return new Candidate(prior.mode, r.origin, velocity, goal, prior.ticks, path);
    }

    /** Chooses a clear straight/low/high trajectory; AUTO can never produce a tracking projectile. */
    public static Candidate choose(World world) {
        Spatial walls = new Spatial(world.walls, world.request.physics.radius);
        Candidate best = null; double score = Double.POSITIVE_INFINITY;
        for (Candidate candidate : world.candidates) {
            if (Thread.currentThread().isInterrupted()) return null;
            if (world.request.mode == Trajectory.AUTO && candidate.mode == Trajectory.TRACKING) continue;
            Vec impact = candidate.goal; boolean clear = true;
            for (int i = 1; i < candidate.points.size(); i++) {
                Vec a = candidate.points.get(i - 1), b = candidate.points.get(i);
                double hit = walls.hit(a, b);
                if (Double.isFinite(hit)) {
                    impact = a.lerp(b, hit);
                    clear = candidate.mode != Trajectory.TRACKING && impact.distance(candidate.goal) <= world.request.tolerance();
                    break;
                }
            }
            Vec landing = impact;
            if (!clear || world.danger.stream().anyMatch(box -> box.contains(candidate.goal))
                    || world.danger.stream().anyMatch(box -> box.contains(landing))) continue;
            double rank = candidate.ticks + (candidate.mode == Trajectory.CURVE ? 0.25 : 0);
            if (rank < score) { score = rank; best = candidate; }
        }
        return best;
    }

    public static final class Cell  {
private final int x;
private final int y;
private final int z;
public Cell(int x, int y, int z) {
this.x = x; this.y = y; this.z = z;}
public int x() { return x; }
public int y() { return y; }
public int z() { return z; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof Cell))return false; Cell that=(Cell)value; return java.util.Objects.equals(x, that.x) && java.util.Objects.equals(y, that.y) && java.util.Objects.equals(z, that.z); }
@Override public int hashCode() { return java.util.Objects.hash(x, y, z); }

}
    /** A bounded voxel traversal also used by the main-thread snapshot sampler. */
    public static List<Cell> cells(Vec a, Vec b, double radius, int limit) {
        if (radius < 0 || radius > 2 || limit < 1 || limit > 8192) throw new IllegalArgumentException("cell budget");
        LinkedHashSet<Cell> result = new LinkedHashSet<>();
        double length = a.distance(b); int steps = Math.max(1, (int)Math.ceil(length * 4));
        if (steps > 4096) throw new IllegalArgumentException("segment budget");
        for (int i = 1; i <= steps; i++) {
            Vec p = a.lerp(b, (i - 1) / (double)steps), q = a.lerp(b, i / (double)steps);
            for (int x = (int)Math.floor(Math.min(p.x, q.x) - radius); x <= (int)Math.floor(Math.max(p.x, q.x) + radius); x++)
                for (int y = (int)Math.floor(Math.min(p.y, q.y) - radius); y <= (int)Math.floor(Math.max(p.y, q.y) + radius); y++)
                    for (int z = (int)Math.floor(Math.min(p.z, q.z) - radius); z <= (int)Math.floor(Math.max(p.z, q.z) + radius); z++) {
                        result.add(new Cell(x, y, z)); if (result.size() > limit) throw new IllegalArgumentException("cell budget");
                    }
        }
        return bincnpc.gun.common.legacy.Legacy.copyList(result);
    }
    private static final class Spatial {
        private final Map<Cell, List<Bounds>> boxes = new HashMap<>();
        private final List<Bounds> broad = new ArrayList<>();
        Spatial(List<Bounds> walls, double radius) {
            for (Bounds original : walls) {
                Bounds b = original.inflate(radius);
                int x0 = (int)Math.floor(b.x0), y0 = (int)Math.floor(b.y0), z0 = (int)Math.floor(b.z0);
                int x1 = (int)Math.floor(b.x1), y1 = (int)Math.floor(b.y1), z1 = (int)Math.floor(b.z1);
                if ((long)(x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > 128) { broad.add(b); continue; }
                for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++)
                    boxes.computeIfAbsent(new Cell(x, y, z), ignored -> new ArrayList<>()).add(b);
            }
        }
        double hit(Vec a, Vec b) {
            double first = Double.POSITIVE_INFINITY; Set<Bounds> seen = new HashSet<>(broad);
            for (Cell cell : cells(a, b, 0, 2048)) { List<Bounds> nearby = boxes.get(cell); if (nearby != null) seen.addAll(nearby); }
            for (Bounds box : seen) first = Math.min(first, box.hit(a, b));
            return first;
        }
    }

    /** The array is privately copied, and never exposed to callers or worker threads as mutable state. */
    public static final class Grid {
        private final Vec min, start, target;
        private final boolean[] blocked;
        public Grid(Vec min, Vec start, Vec target, boolean[] blocked) {
            this.min = Objects.requireNonNull(min); this.start = Objects.requireNonNull(start); this.target = Objects.requireNonNull(target);
            if (blocked == null || blocked.length != GRID_SIDE * GRID_SIDE * GRID_SIDE) throw new IllegalArgumentException("grid");
            this.blocked = blocked.clone();
        }
        public Vec min() { return min; }
        public Vec start() { return start; }
        public Vec target() { return target; }
        private int index(int x, int y, int z) { return (y * GRID_SIDE + z) * GRID_SIDE + x; }
        private boolean free(int x, int y, int z) { return x >= 0 && x < GRID_SIDE && y >= 0 && y < GRID_SIDE && z >= 0 && z < GRID_SIDE && !blocked[index(x, y, z)]; }
        private Vec center(int index) { return min.add(new Vec(index % GRID_SIDE + 0.5, index / (GRID_SIDE * GRID_SIDE) + 0.5, index / GRID_SIDE % GRID_SIDE + 0.5)); }
    }
    private static final class Open  {
private final int index;
private final double score;
public Open(int index, double score) {
this.index = index; this.score = score;}
public int index() { return index; }
public double score() { return score; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof Open))return false; Open that=(Open)value; return java.util.Objects.equals(index, that.index) && java.util.Objects.equals(score, that.score); }
@Override public int hashCode() { return java.util.Objects.hash(index, score); }

}
    /** Local 3D A*: diagonal moves also check all crossed orthogonal cells. */
    public static List<Vec> route(Grid grid) {
        int sx = (int)Math.floor(grid.start.x - grid.min.x), sy = (int)Math.floor(grid.start.y - grid.min.y), sz = (int)Math.floor(grid.start.z - grid.min.z);
        if (!grid.free(sx, sy, sz)) return bincnpc.gun.common.legacy.Legacy.list();
        int size = GRID_SIDE * GRID_SIDE * GRID_SIDE, start = grid.index(sx, sy, sz), best = start;
        double[] cost = new double[size]; Arrays.fill(cost, Double.POSITIVE_INFINITY); cost[start] = 0;
        int[] parent = new int[size]; Arrays.fill(parent, -1); boolean[] closed = new boolean[size];
        PriorityQueue<Open> open = new PriorityQueue<>(Comparator.comparingDouble(Open::score));
        double initial = grid.center(start).distance(grid.target), closest = initial; open.add(new Open(start, initial));
        for (int expanded = 0; !open.isEmpty() && expanded < MAX_NODES; ) {
            if (Thread.currentThread().isInterrupted()) return bincnpc.gun.common.legacy.Legacy.list();
            int current = open.remove().index; if (closed[current]) continue; closed[current] = true; expanded++;
            double distance = grid.center(current).distance(grid.target);
            if (distance < closest) { closest = distance; best = current; }
            if (distance < 0.8) { best = current; break; }
            int x = current % GRID_SIDE, y = current / (GRID_SIDE * GRID_SIDE), z = current / GRID_SIDE % GRID_SIDE;
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0 || !grid.free(x + dx, y + dy, z + dz)) continue;
                boolean safe = true;
                for (int ix = 0; ix <= Math.abs(dx); ix++) for (int iy = 0; iy <= Math.abs(dy); iy++) for (int iz = 0; iz <= Math.abs(dz); iz++)
                    if (!grid.free(x + ix * dx, y + iy * dy, z + iz * dz)) safe = false;
                if (!safe) continue;
                int next = grid.index(x + dx, y + dy, z + dz); if (closed[next]) continue;
                double g = cost[current] + Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (g >= cost[next]) continue; cost[next] = g; parent[next] = current;
                open.add(new Open(next, g + grid.center(next).distance(grid.target)));
            }
        }
        if (best == start || initial - closest < 0.5) return bincnpc.gun.common.legacy.Legacy.list();
        ArrayList<Vec> path = new ArrayList<>();
        for (int at = best; at != start && at >= 0 && path.size() < size; at = parent[at]) path.add(grid.center(at));
        Collections.reverse(path); return bincnpc.gun.common.legacy.Legacy.copyList(path);
    }
}
