package bincnpc.gun.common.combat;

import java.util.*;
import java.util.stream.Collectors;

/** Immutable coordinates only: the shared worker never reads Minecraft objects. */
public final class TargetSearch {
    public static final int MAX_ENTITIES = 65536, MAX_AGE = 80;
    public static final class Candidate {
        private final UUID id;
        private final double x, y, z;
        public Candidate(UUID id, double x, double y, double z) {
            if (id == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("target coordinates");
            this.id = id; this.x = x; this.y = y; this.z = z;
        }
        public UUID id() { return id; }
        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
    }
    public static final class Snapshot {
        private final UUID npc;
        private final String dimension;
        private final long tick;
        private final double x, y, z;
        private final int range;
        private final List<Candidate> entities;
        public Snapshot(UUID npc, String dimension, long tick, double x, double y, double z, int range, List<Candidate> entities) {
            if (npc == null || dimension == null || range < 0 || range > 2048 || !Double.isFinite(x) || !Double.isFinite(y)
                    || !Double.isFinite(z) || entities == null || entities.size() > MAX_ENTITIES) throw new IllegalArgumentException("target snapshot");
            this.npc = npc; this.dimension = dimension; this.tick = tick; this.x = x; this.y = y; this.z = z; this.range = range;
            this.entities = Collections.unmodifiableList(new ArrayList<>(entities));
        }
        public UUID npc() { return npc; }
        public String dimension() { return dimension; }
        public long tick() { return tick; }
        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
        public int range() { return range; }
        public List<Candidate> entities() { return entities; }
        public boolean matches(UUID npc, String dimension, long now, int range, double x, double y, double z) {
            double dx = this.x - x, dy = this.y - y, dz = this.z - z;
            return this.npc.equals(npc) && this.dimension.equals(dimension) && this.range == range
                && now >= tick && now - tick <= MAX_AGE && dx * dx + dy * dy + dz * dz <= 256;
        }
    }
    private TargetSearch() {}
    public static List<UUID> rank(Snapshot snapshot) {
        if (snapshot.range() == 0) return Collections.emptyList();
        double limit = (double)snapshot.range() * snapshot.range();
        return Collections.unmodifiableList(snapshot.entities().stream()
            .filter(entity -> !entity.id().equals(snapshot.npc()) && distance(snapshot, entity) <= limit)
            .sorted(Comparator.comparingDouble((Candidate entity) -> distance(snapshot, entity)).thenComparing(Candidate::id))
            .map(Candidate::id).collect(Collectors.toList()));
    }
    private static double distance(Snapshot snapshot, Candidate entity) {
        double x = snapshot.x() - entity.x(), y = snapshot.y() - entity.y(), z = snapshot.z() - entity.z();
        return x * x + y * y + z * z;
    }
}
