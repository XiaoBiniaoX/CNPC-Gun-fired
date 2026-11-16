package bincnpc.gun.common.combat;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Pure snapshot calculations. This class deliberately has no game, entity, item or adapter imports. */
public final class CombatDecisions {
    public static final int MAX_AGE = 8, MAX_QUEUE = 128;
    public static final class Weapon  {
private final int slot;
private final String key;
private final boolean melee;
private final boolean gun;
private final int loaded;
private final int capacity;
private final int priority;
public Weapon(int slot, String key, boolean melee, boolean gun, int loaded, int capacity, int priority) {
            if (slot < 0 || slot >= 6 || key == null || key.isEmpty() || key.length() > 256 || melee == gun
                || loaded < -1 || loaded > 100000 || capacity < -1 || capacity > 100000 || priority < 0 || priority > 1000)
                throw new IllegalArgumentException("weapon snapshot");
        
this.slot = slot; this.key = key; this.melee = melee; this.gun = gun; this.loaded = loaded; this.capacity = capacity; this.priority = priority;}
public int slot() { return slot; }
public String key() { return key; }
public boolean melee() { return melee; }
public boolean gun() { return gun; }
public int loaded() { return loaded; }
public int capacity() { return capacity; }
public int priority() { return priority; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof Weapon))return false; Weapon that=(Weapon)value; return java.util.Objects.equals(slot, that.slot) && java.util.Objects.equals(key, that.key) && java.util.Objects.equals(melee, that.melee) && java.util.Objects.equals(gun, that.gun) && java.util.Objects.equals(loaded, that.loaded) && java.util.Objects.equals(capacity, that.capacity) && java.util.Objects.equals(priority, that.priority); }
@Override public int hashCode() { return java.util.Objects.hash(slot, key, melee, gun, loaded, capacity, priority); }

        
    
}
    public static final class Snapshot  {
private final UUID npc;
private final UUID target;
private final String dimension;
private final long tick;
private final long revision;
private final long weaponEpoch;
private final int activeSlot;
private final String activeWeapon;
private final double distance;
private final double health;
private final double targetHealth;
private final int enemies;
private final double meleeDistance;
private final double lowHealth;
private final boolean recentHurt;
private final boolean infinite;
private final boolean onlyMelee;
private final boolean requireLoaded;
private final List<Weapon> weapons;
public Snapshot(UUID npc, UUID target, String dimension, long tick, long revision, long weaponEpoch, int activeSlot, String activeWeapon, double distance, double health, double targetHealth, int enemies, double meleeDistance, double lowHealth, boolean recentHurt, boolean infinite, boolean onlyMelee, boolean requireLoaded, List<Weapon> weapons) {
            if (npc == null || target == null || dimension == null || dimension.length() > 256 || activeWeapon == null
                || activeSlot < 0 || activeSlot >= 6 || !Double.isFinite(distance) || distance < 0
                || !unit(health) || !unit(targetHealth) || !unit(lowHealth) || !Double.isFinite(meleeDistance) || meleeDistance < 0
                || enemies < 0 || enemies > 16 || weapons == null || weapons.size() > 6) throw new IllegalArgumentException("combat snapshot");
            weapons = bincnpc.gun.common.legacy.Legacy.copyList(weapons);
            int mask = 0;
            for (Weapon weapon : weapons) { int bit = 1 << weapon.slot(); if ((mask & bit) != 0) throw new IllegalArgumentException("duplicate slot"); mask |= bit; }
        
this.npc = npc; this.target = target; this.dimension = dimension; this.tick = tick; this.revision = revision; this.weaponEpoch = weaponEpoch; this.activeSlot = activeSlot; this.activeWeapon = activeWeapon; this.distance = distance; this.health = health; this.targetHealth = targetHealth; this.enemies = enemies; this.meleeDistance = meleeDistance; this.lowHealth = lowHealth; this.recentHurt = recentHurt; this.infinite = infinite; this.onlyMelee = onlyMelee; this.requireLoaded = requireLoaded; this.weapons = weapons;}
public UUID npc() { return npc; }
public UUID target() { return target; }
public String dimension() { return dimension; }
public long tick() { return tick; }
public long revision() { return revision; }
public long weaponEpoch() { return weaponEpoch; }
public int activeSlot() { return activeSlot; }
public String activeWeapon() { return activeWeapon; }
public double distance() { return distance; }
public double health() { return health; }
public double targetHealth() { return targetHealth; }
public int enemies() { return enemies; }
public double meleeDistance() { return meleeDistance; }
public double lowHealth() { return lowHealth; }
public boolean recentHurt() { return recentHurt; }
public boolean infinite() { return infinite; }
public boolean onlyMelee() { return onlyMelee; }
public boolean requireLoaded() { return requireLoaded; }
public List<Weapon> weapons() { return weapons; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof Snapshot))return false; Snapshot that=(Snapshot)value; return java.util.Objects.equals(npc, that.npc) && java.util.Objects.equals(target, that.target) && java.util.Objects.equals(dimension, that.dimension) && java.util.Objects.equals(tick, that.tick) && java.util.Objects.equals(revision, that.revision) && java.util.Objects.equals(weaponEpoch, that.weaponEpoch) && java.util.Objects.equals(activeSlot, that.activeSlot) && java.util.Objects.equals(activeWeapon, that.activeWeapon) && java.util.Objects.equals(distance, that.distance) && java.util.Objects.equals(health, that.health) && java.util.Objects.equals(targetHealth, that.targetHealth) && java.util.Objects.equals(enemies, that.enemies) && java.util.Objects.equals(meleeDistance, that.meleeDistance) && java.util.Objects.equals(lowHealth, that.lowHealth) && java.util.Objects.equals(recentHurt, that.recentHurt) && java.util.Objects.equals(infinite, that.infinite) && java.util.Objects.equals(onlyMelee, that.onlyMelee) && java.util.Objects.equals(requireLoaded, that.requireLoaded) && java.util.Objects.equals(weapons, that.weapons); }
@Override public int hashCode() { return java.util.Objects.hash(npc, target, dimension, tick, revision, weaponEpoch, activeSlot, activeWeapon, distance, health, targetHealth, enemies, meleeDistance, lowHealth, recentHurt, infinite, onlyMelee, requireLoaded, weapons); }

        
        public boolean matches(UUID npc, UUID target, String dimension, long now, long revision, long weaponEpoch, int activeSlot, String activeWeapon) {
            return this.npc.equals(npc) && this.target.equals(target) && this.dimension.equals(dimension)
                && now >= tick && now - tick <= MAX_AGE && this.revision == revision && this.weaponEpoch == weaponEpoch
                && this.activeSlot == activeSlot && this.activeWeapon.equals(activeWeapon);
        }
    
}
    public static final class Advice  {
private final Snapshot snapshot;
private final int slot;
private final String weapon;
private final double score;
private final double pressure;
private final boolean finish;
private final boolean pursue;
private final long generation;
public Advice(Snapshot snapshot, int slot, String weapon, double score, double pressure, boolean finish, boolean pursue, long generation) {
this.snapshot = snapshot; this.slot = slot; this.weapon = weapon; this.score = score; this.pressure = pressure; this.finish = finish; this.pursue = pursue; this.generation = generation;}
public Snapshot snapshot() { return snapshot; }
public int slot() { return slot; }
public String weapon() { return weapon; }
public double score() { return score; }
public double pressure() { return pressure; }
public boolean finish() { return finish; }
public boolean pursue() { return pursue; }
public long generation() { return generation; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof Advice))return false; Advice that=(Advice)value; return java.util.Objects.equals(snapshot, that.snapshot) && java.util.Objects.equals(slot, that.slot) && java.util.Objects.equals(weapon, that.weapon) && java.util.Objects.equals(score, that.score) && java.util.Objects.equals(pressure, that.pressure) && java.util.Objects.equals(finish, that.finish) && java.util.Objects.equals(pursue, that.pursue) && java.util.Objects.equals(generation, that.generation); }
@Override public int hashCode() { return java.util.Objects.hash(snapshot, slot, weapon, score, pressure, finish, pursue, generation); }

        public boolean currentGeneration() { return generation < 0 || generation == CombatDecisions.generation; }
    
}
    private static final Object LIFECYCLE = new Object();
    private static volatile ThreadPoolExecutor executor;
    private static volatile long generation;
    private CombatDecisions() {}
    private static boolean unit(double value) { return Double.isFinite(value) && value >= 0 && value <= 1; }
    /** Exhausted guns cannot compete with melee; distance affects chasing, not equipping. */
    public static int meleeFallback(List<Weapon> weapons, int activeSlot) {
        if (weapons == null || weapons.size() > 6 || activeSlot < 0 || activeSlot >= 6) throw new IllegalArgumentException("melee fallback");
        Weapon best = null;
        for (Weapon weapon : weapons) {
            if (weapon == null) throw new IllegalArgumentException("melee candidate");
            if (weapon.melee() && (best == null || weapon.priority() > best.priority()
                    || weapon.priority() == best.priority() && weapon.slot() == activeSlot)) best = weapon;
        }
        return best == null ? -1 : best.slot();
    }
    public static Advice calculate(Snapshot snapshot) {
        int best = -1; String key = ""; double bestScore = Double.NEGATIVE_INFINITY;
        double pressure = Math.min(1, (snapshot.recentHurt() ? 0.35 : 0) + Math.min(0.45, snapshot.enemies() * 0.12)
            + (snapshot.health() <= snapshot.lowHealth() ? 0.45 : 0) + (snapshot.distance() <= 6 ? 0.15 : 0));
        for (Weapon weapon : snapshot.weapons()) {
            if (snapshot.onlyMelee() && !weapon.melee()) continue;
            if (snapshot.requireLoaded() && weapon.gun() && !snapshot.infinite() && weapon.loaded() == 0) continue;
            if (snapshot.requireLoaded() && weapon.melee() && snapshot.distance() > Math.max(4, snapshot.meleeDistance())) continue;
            double score = weapon.priority() * 0.05;
            if (weapon.melee()) {
                score += snapshot.distance() <= snapshot.meleeDistance() ? 140 : snapshot.distance() <= 6 ? 15 : -50;
                if (snapshot.distance() <= 4 && snapshot.targetHealth() < 0.3) score += 24;
                if (snapshot.distance() <= 4 && snapshot.enemies() >= 3) score += 16;
            } else {
                boolean sidearm = weapon.slot() >= 2 && weapon.slot() < 4;
                score += sidearm ? snapshot.distance() < 18 ? 20 : 0 : snapshot.distance() >= 20 ? 24 : snapshot.distance() >= 12 ? 6 : 0;
                if (snapshot.infinite() || weapon.loaded() < 0) score += 32;
                else if (weapon.loaded() == 0) score -= 160;
                else {
                    double fraction = weapon.capacity() > 0 ? Math.min(1, weapon.loaded() / (double)weapon.capacity()) : Math.min(1, weapon.loaded() / 20.0);
                    score += 18 + fraction * 32;
                    if (snapshot.targetHealth() < 0.3) score += 8;
                    score += pressure * fraction * 12;
                }
                if (snapshot.health() <= snapshot.lowHealth()) score += 12;
            }
            if (weapon.slot() == snapshot.activeSlot()) score += 6;
            if (score > bestScore) { best = weapon.slot(); key = weapon.key(); bestScore = score; }
        }
        return new Advice(snapshot, best, key, bestScore, pressure, snapshot.targetHealth() < 0.3, snapshot.distance() > 4, -1);
    }
    /** Each NPC keeps exactly one ticket; the global queue contains at most 128 pure snapshots. */
    public static final class Ticket implements Runnable {
        private final Snapshot snapshot;
        private final long epoch;
        private volatile Advice advice;
        private volatile boolean finished, cancelled;
        private Ticket(Snapshot snapshot, long epoch) { this.snapshot = snapshot; this.epoch = epoch; }
        public boolean finished() { return finished || cancelled || epoch != generation; }
        public Advice advice() { return finished && !cancelled && epoch == generation ? advice : null; }
        public void cancel() { cancelled = true; advice = null; }
        @Override public void run() {
            try {
                if (cancelled || epoch != generation || Thread.currentThread().isInterrupted()) return;
                Advice result = calculate(snapshot);
                if (!cancelled && epoch == generation && !Thread.currentThread().isInterrupted())
                    advice = new Advice(result.snapshot(), result.slot(), result.weapon(), result.score(), result.pressure(), result.finish(), result.pursue(), epoch);
            } finally { finished = true; }
        }
    }
    public static Ticket submit(Snapshot snapshot) {
        synchronized (LIFECYCLE) {
            if (executor == null || executor.isShutdown()) return null;
            Ticket ticket = new Ticket(snapshot, generation);
            try { executor.execute(ticket); return ticket; } catch (RejectedExecutionException full) { return null; }
        }
    }
    public static void start() {
        synchronized (LIFECYCLE) {
            stop(); generation++;
            AtomicInteger names = new AtomicInteger();
            executor = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(MAX_QUEUE), runnable -> {
                Thread thread = new Thread(runnable, "cnpcgun-combat-" + names.incrementAndGet()); thread.setDaemon(true); thread.setPriority(Thread.NORM_PRIORITY - 1); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
            executor.allowCoreThreadTimeOut(true);
        }
    }
    public static void stop() {
        synchronized (LIFECYCLE) {
Object cnpcgun$value0; Ticket cnpcgun$match0;

            generation++; ThreadPoolExecutor previous = executor; executor = null;
            if (previous != null) for (Runnable pending : previous.shutdownNow()) if (((cnpcgun$value0 = pending) instanceof Ticket && (cnpcgun$match0 = (Ticket)cnpcgun$value0) != null)) cnpcgun$match0.cancel();
        }
    }
}
