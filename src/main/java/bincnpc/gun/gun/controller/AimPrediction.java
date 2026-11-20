package bincnpc.gun.gun.controller;

import java.util.UUID;

/** Server-tick target samples. Coordinates are in blocks; speeds are in blocks per tick. */
public final class AimPrediction {
    private UUID target;
    private long tick;
    private double x, y, z, vx, vy, vz;

    public void clear() { target = null; vx = vy = vz = 0; }

    public void sample(UUID id, long now, double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) { clear(); return; }
        if (id.equals(target) && now == tick) return;
        double dx = x - this.x, dy = y - this.y, dz = z - this.z;
        boolean continuous = id.equals(target) && now - tick == 1 && dx * dx + dy * dy + dz * dz <= 256;
        vx = continuous ? dx : 0; vy = continuous ? dy : 0; vz = continuous ? dz : 0;
        target = id; tick = now; this.x = x; this.y = y; this.z = z;
    }

    public boolean moving(UUID id, long now) {
        return now == tick && id.equals(target) && (vx != 0 || vy != 0 || vz != 0);
    }

    public double vx() { return vx; }
    public double vy() { return vy; }
    public double vz() { return vz; }

    /** Earliest positive interception for the current sample, capped at 100 ticks. */
    public double time(double dx, double dy, double dz, double speed) {
        if (!Double.isFinite(speed) || speed <= 0) return 0;
        double a = vx * vx + vy * vy + vz * vz - speed * speed;
        double b = 2 * (dx * vx + dy * vy + dz * vz), c = dx * dx + dy * dy + dz * dz;
        if (c < 1E-10) return 0;
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
