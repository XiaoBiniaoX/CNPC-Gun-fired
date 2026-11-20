package bincnpc.gun.gun.switching;

/** A return is due only after combat has been observed and the target has been lost. */
public final class ReturnTimer {
    private boolean fighting;
    private long idleSince = -1;

    public boolean tick(long now, boolean hasTarget) {
        if (hasTarget) { fighting = true; idleSince = -1; }
        else if (fighting) { fighting = false; idleSince = now; }
        return idleSince >= 0 && now - idleSince >= 100;
    }

    public void reset() { fighting = false; idleSince = -1; }
}
