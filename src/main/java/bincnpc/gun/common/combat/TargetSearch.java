package bincnpc.gun.common.combat;

import java.util.*;

/** Immutable loaded-entity coordinates; ranking never touches Minecraft objects. */
public final class TargetSearch {
    public static final int MAX_ENTITIES = 65536, MAX_AGE = 80;
    public record Candidate(UUID id, double x, double y, double z) {
        public Candidate {
            if (id == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("target coordinates");
        }
    }
    public record Snapshot(UUID npc, String dimension, long tick, double x, double y, double z, int range, List<Candidate> entities) {
        public Snapshot {
            if (npc == null || dimension == null || range < 0 || range > 2048 || !Double.isFinite(x) || !Double.isFinite(y)
                    || !Double.isFinite(z) || entities == null || entities.size() > MAX_ENTITIES) throw new IllegalArgumentException("target snapshot");
            entities = List.copyOf(entities);
        }
        public boolean matches(UUID npc, String dimension, long now, int range, double x, double y, double z) {
            double dx = this.x - x, dy = this.y - y, dz = this.z - z;
            return this.npc.equals(npc) && this.dimension.equals(dimension) && this.range == range
                && now >= tick && now - tick <= MAX_AGE && dx * dx + dy * dy + dz * dz <= 256;
        }
    }
    private TargetSearch() {}
    public static List<UUID> rank(Snapshot snapshot) {
        if (snapshot.range() == 0) return List.of();
        double limit = (double)snapshot.range() * snapshot.range();
        return snapshot.entities().stream().filter(entity -> !entity.id().equals(snapshot.npc()) && distance(snapshot, entity) <= limit)
            .sorted(Comparator.comparingDouble((Candidate entity) -> distance(snapshot, entity)).thenComparing(Candidate::id))
            .map(Candidate::id).toList();
    }
    private static double distance(Snapshot snapshot, Candidate entity) {
        double x = snapshot.x() - entity.x(), y = snapshot.y() - entity.y(), z = snapshot.z() - entity.z();
        return x * x + y * y + z * z;
    }
}
