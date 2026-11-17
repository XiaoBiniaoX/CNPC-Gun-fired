package bincnpc.gun.throwing;

/** Timing decisions only; terrain, native resources and damage remain on the server thread. */
public final class ThrowPolicy {
    public static final int PLAN_TIMEOUT = 28, FRESH_TICKS = 10;
    private ThrowPolicy() {}

    public static boolean requiresSafeDistance(String timing) { return "SAFE".equals(timing); }

    /** Automatic firing includes aiming, charging and the interval between shots. */
    public static boolean shootingTask(long now, long targetSince, long lastShot, boolean automatic, boolean firingAction) {
        if (now < targetSince) return false;
        return automatic || firingAction
            || lastShot >= targetSince && lastShot <= now && now - lastShot <= 20;
    }

    public static boolean planExpired(long now, long started) { return now < started || now - started > PLAN_TIMEOUT; }

    /** A budgeted capture may take longer than the release freshness window. */
    public static boolean predictionFresh(long now, long started, long captured, boolean release) {
        return !planExpired(now, started) && (!release || captured >= started && captured <= now && now - captured <= FRESH_TICKS);
    }
}
