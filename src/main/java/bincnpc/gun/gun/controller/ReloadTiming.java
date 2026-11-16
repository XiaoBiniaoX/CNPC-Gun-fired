package bincnpc.gun.gun.controller;

/** Numeric timing only. Native adapters still perform every ammo transfer and completion callback. */
public final class ReloadTiming {
    private ReloadTiming() {}
    public static double speed(double nativeTicks, int requestedTicks) {
        if (!Double.isFinite(nativeTicks) || nativeTicks <= 0 || requestedTicks < 1 || requestedTicks > 1200)
            throw new IllegalArgumentException("reload duration");
        return Math.max(1.0 / 1200, Math.min(100000, nativeTicks / requestedTicks));
    }
    public static int steps(int rounds, int amount) {
        if (rounds <= 0 || amount <= 0) return 0;
        return (int)(((long)rounds + amount - 1) / amount);
    }
    public static long stepAt(long start, int duration, int step, int steps) {
        if (duration < 1 || duration > 1200 || steps < 1 || step < 1 || step > steps)
            throw new IllegalArgumentException("reload steps");
        return start + ((long)duration * step + steps - 1) / steps;
    }
    /** Compensates native wall-clock timestamps using server ticks, including native script resets. */
    public static final class Clock {
        private long lastTick, lastWall;
        private double remainder;
        public Clock(long tick, long wall) { lastTick = tick; lastWall = wall; }
        public long adjust(long nativeTimestamp, long tick, long wall, double speed) {
            if (!Double.isFinite(speed) || speed <= 0) throw new IllegalArgumentException("reload speed");
            if (tick < lastTick) return nativeTimestamp;
            if (tick == lastTick) {
                long result = nativeTimestamp + (wall - lastWall); lastWall = wall; return result;
            }
            double advance = Math.min(100000, tick - lastTick) * 50.0 * speed + remainder;
            long whole = (long)Math.floor(advance); remainder = advance - whole;
            long result = nativeTimestamp + (wall - lastWall) - whole;
            lastTick = tick; lastWall = wall;
            return result;
        }
    }
}
