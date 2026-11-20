package bincnpc.gun.gun.controller;

import java.util.UUID;

/** Per-NPC numeric samples, updated only by the server thread. Speeds are blocks per tick. */
public final class AimPrediction {
    private UUID target;
    private long tick = Long.MIN_VALUE;
    private double x, y, z, vx, vy, vz;

    public void clear() { target = null; tick = Long.MIN_VALUE; vx = vy = vz = 0; }

    public void sample(UUID id, long now, double x, double y, double z) {
        if (id == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) { clear(); return; }
        if (id.equals(target) && now == tick) return;
        double dx = x - this.x, dy = y - this.y, dz = z - this.z;
        boolean continuous = id.equals(target) && now - tick == 1 && dx * dx + dy * dy + dz * dz <= 256;
        vx = continuous ? dx : 0; vy = continuous ? dy : 0; vz = continuous ? dz : 0;
        target = id; tick = now; this.x = x; this.y = y; this.z = z;
    }

    public double vx() { return vx; }
    public double vy() { return vy; }
    public double vz() { return vz; }

    /** Earliest positive interception, capped at five seconds of constant-velocity extrapolation. */
    public double time(UUID id, long now, double dx, double dy, double dz, double speed) {
        if (!id.equals(target) || now != tick || !Double.isFinite(speed) || speed <= 0) return 0;
        double a = vx * vx + vy * vy + vz * vz - speed * speed;
        double b = 2 * (dx * vx + dy * vy + dz * vz), c = dx * dx + dy * dy + dz * dz;
        if (!Double.isFinite(a) || !Double.isFinite(b) || !Double.isFinite(c) || c < 1E-10) return 0;
        double time;
        if (Math.abs(a) < 1E-9) time = b < -1E-9 ? -c / b : 0;
        else {
            double discriminant = b * b - 4 * a * c;
            if (discriminant < 0) return 0;
            double q = -0.5 * (b + Math.copySign(Math.sqrt(discriminant), b));
            double first = q / a, second = q == 0 ? 0 : c / q;
            time = Math.min(first > 0 ? first : Double.POSITIVE_INFINITY, second > 0 ? second : Double.POSITIVE_INFINITY);
        }
        return Double.isFinite(time) && time > 0 ? Math.min(100, time) : 0;
    }
}
