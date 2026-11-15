package bincnpc.gun.slashblade.strategy;

/** Bounded, transient state owned by one NPC, never a static entity/UUID cache. */
public final class MaidCombatState {
    public long nextPhantom, specialCooldown, superCooldown, guardStart, guardEnd, guardCooldown, escapeAt;
    public long lastSlash = -1000, lastSpecial = -1000, lastTick = Long.MIN_VALUE;
    public int specialBurst, specialCursor, comboCycle;
    public long spiralReleaseAt, guardJustEnd, escapeUntil;
    public double pressure, accumulatedDamage, guardBudget;
    public String specialAction = "";
    public void reset() {
        nextPhantom = specialCooldown = superCooldown = guardStart = guardEnd = guardCooldown = escapeAt = 0;
        lastSlash = lastSpecial = -1000; lastTick = Long.MIN_VALUE; specialBurst = specialCursor = comboCycle = 0; spiralReleaseAt = guardJustEnd = escapeUntil = 0;
        pressure = accumulatedDamage = guardBudget = 0; specialAction = "";
    }
}
