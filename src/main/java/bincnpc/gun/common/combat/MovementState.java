package bincnpc.gun.common.combat;

/** One NPC's navigation state; no static entity cache. */
public final class MovementState {
    public long nextMove, nextMelee;
    public int side = 1, priorNavigationMode = -1, pursueSlot = -1;
    public boolean meleeFallback, ammoFallback;
}
