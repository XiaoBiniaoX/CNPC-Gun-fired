package bincnpc.gun.compat.superb;

import java.util.Objects;
import java.util.UUID;

/** One continuous server-side draw; no world or optional-mod references. */
public final class BocekDrawState {
    // Native bowPower gains 0.018 * 4 per game tick and caps at 1.0.
    public static final int FULL_DRAW_TICKS = 14;
    private final UUID target;
    private final long revision, weaponEpoch;
    private long observedTick;
    private int ticks;
    private boolean cancelled, releasing;

    public BocekDrawState(long now, UUID target, long revision, long weaponEpoch) {
        observedTick = now; this.target = Objects.requireNonNull(target);
        this.revision = revision; this.weaponEpoch = weaponEpoch;
    }

    public boolean matches(UUID target, long revision, long weaponEpoch) {
        return !cancelled && this.target.equals(target) && this.revision == revision && this.weaponEpoch == weaponEpoch;
    }

    public boolean advance(long now, UUID target, long revision, long weaponEpoch, boolean allowed) {
        return observe(now, target, revision, weaponEpoch, allowed, true);
    }

    public boolean pause(long now, UUID target, long revision, long weaponEpoch, boolean allowed) {
        return observe(now, target, revision, weaponEpoch, allowed, false);
    }

    private boolean observe(long now, UUID target, long revision, long weaponEpoch, boolean allowed, boolean charging) {
        if (cancelled || releasing) return false;
        if (!allowed || !matches(target, revision, weaponEpoch) || now < observedTick || now - observedTick > 1) {
            cancel(); return false;
        }
        if (now != observedTick) { if (charging) ticks = Math.min(FULL_DRAW_TICKS, ticks + 1); observedTick = now; }
        return true;
    }

    /** Claim the release once, before invoking native callbacks. */
    public boolean release(long now) {
        if (cancelled || releasing || observedTick != now || ticks < FULL_DRAW_TICKS) return false;
        releasing = true; return true;
    }
    public boolean releasing() { return !cancelled && releasing; }
    public void cancel() { cancelled = true; ticks = 0; }
}
