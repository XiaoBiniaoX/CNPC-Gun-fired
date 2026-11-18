package bincnpc.gun.common.combat;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Pure snapshot calculations. This class deliberately has no game, entity, item or adapter imports. */
public final class CombatDecisions {
    public static final int MAX_AGE = 8, MAX_QUEUE = 128;
    public record Weapon(int slot, String key, boolean melee, boolean gun, int loaded, int capacity, int priority) {
        public Weapon {
            if (slot < 0 || slot >= 6 || key == null || key.isEmpty() || key.length() > 256 || melee == gun
                || loaded < -1 || loaded > 100000 || capacity < -1 || capacity > 100000 || priority < 0 || priority > 1000)
                throw new IllegalArgumentException("weapon snapshot");
        }
    }
    public record Snapshot(UUID npc, UUID target, String dimension, long tick, long revision, long weaponEpoch,
            int activeSlot, String activeWeapon, double distance, double health, double targetHealth, int enemies,
            double meleeDistance, double lowHealth, boolean recentHurt, boolean infinite, boolean onlyMelee,
            boolean requireLoaded, List<Weapon> weapons) {
        public Snapshot {
            if (npc == null || target == null || dimension == null || dimension.length() > 256 || activeWeapon == null
                || activeSlot < 0 || activeSlot >= 6 || !Double.isFinite(distance) || distance < 0
                || !unit(health) || !unit(targetHealth) || !unit(lowHealth) || !Double.isFinite(meleeDistance) || meleeDistance < 0
                || enemies < 0 || enemies > 16 || weapons == null || weapons.size() > 6) throw new IllegalArgumentException("combat snapshot");
            weapons = List.copyOf(weapons);
            int mask = 0;
            for (Weapon weapon : weapons) { int bit = 1 << weapon.slot(); if ((mask & bit) != 0) throw new IllegalArgumentException("duplicate slot"); mask |= bit; }
        }
        public boolean matches(UUID npc, UUID target, String dimension, long now, long revision, long weaponEpoch, int activeSlot, String activeWeapon) {
            return this.npc.equals(npc) && this.target.equals(target) && this.dimension.equals(dimension)
                && now >= tick && now - tick <= MAX_AGE && this.revision == revision && this.weaponEpoch == weaponEpoch
                && this.activeSlot == activeSlot && this.activeWeapon.equals(activeWeapon);
        }
    }
    public record Advice(Snapshot snapshot, int slot, String weapon, double score, double pressure, boolean finish, boolean pursue, long generation) {
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
    public record SearchResult(TargetSearch.Snapshot snapshot, List<UUID> targets, long generation) {
        public boolean currentGeneration() { return generation == CombatDecisions.generation; }
    }
    /** Shares the existing bounded two-thread worker; the callable captures only a pure snapshot. */
    public static FutureTask<SearchResult> submitSearch(TargetSearch.Snapshot snapshot) {
        synchronized (LIFECYCLE) {
            if (executor == null || executor.isShutdown()) return null;
            long epoch = generation;
            FutureTask<SearchResult> task = new FutureTask<>(() -> new SearchResult(snapshot, TargetSearch.rank(snapshot), epoch));
            try { executor.execute(task); return task; } catch (RejectedExecutionException full) { return null; }
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
            generation++; ThreadPoolExecutor previous = executor; executor = null;
            if (previous != null) for (Runnable pending : previous.shutdownNow()) {
                if (pending instanceof Ticket ticket) ticket.cancel();
                else if (pending instanceof Future<?> future) future.cancel(false);
            }
        }
    }
}
