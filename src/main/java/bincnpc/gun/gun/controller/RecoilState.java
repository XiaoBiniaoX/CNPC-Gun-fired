package bincnpc.gun.gun.controller;

/** Bounded numeric NPC recoil; no world, entity or optional mod references. */
public final class RecoilState {
    private long tick = Long.MIN_VALUE;
    private double pitch;
    public double pitch() { return pitch; }
    public void advance(long now, boolean enabled) {
        if (!enabled || tick == Long.MIN_VALUE || now < tick || now - tick > 200) pitch = 0;
        else if (now > tick) pitch *= Math.pow(0.75, now - tick);
        if (Math.abs(pitch) < 0.0001) pitch = 0;
        tick = now;
    }
    public void kick(long now, double degrees) {
        advance(now, true);
        if (Double.isFinite(degrees) && degrees > 0) pitch = Math.max(-30, pitch - Math.min(30, degrees));
    }
    public void reset() { pitch = 0; tick = Long.MIN_VALUE; }
}
