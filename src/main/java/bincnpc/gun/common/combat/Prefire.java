package bincnpc.gun.common.combat;

/** Prefire reacts to cover while retaining a target; it never acquires one. */
public final class Prefire {
    private Prefire() {}
    public static boolean active(String mode, boolean locked, boolean visible) {
        return locked && !visible && ("TRACK".equals(mode) || "SWEEP".equals(mode));
    }
}
