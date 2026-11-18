package bincnpc.gun.common.combat;

import java.util.*;
import java.util.concurrent.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import noppes.npcs.entity.EntityNPCInterface;

/** Main-thread capture and native policy revalidation around the shared pure worker. */
public final class LongRangeTargets {
    private record World(long tick, List<TargetSearch.Candidate> entities) {}
    private static final Map<ServerLevel, World> WORLDS = new HashMap<>();
    private LongRangeTargets() {}
    public static void clear() { WORLDS.clear(); }
    public static final class Search {
        private FutureTask<CombatDecisions.SearchResult> pending;
        private CombatDecisions.SearchResult result;
        private TargetSearch.Snapshot submitted;
        private int cursor;
        private long nextAttempt;
        public LivingEntity find(EntityNPCInterface npc, Class<? extends LivingEntity> type, TargetingConditions conditions) {
            if (!CombatService.serverThread(npc)) return null;
            if (!CombatService.validNpc(npc)) { cancel(); return null; }
            long now = npc.level().getGameTime(); int range = npc.stats.aggroRange;
            String dimension = npc.level().dimension().location().toString();
            if (range == 0) { cancel(); return null; }
            if (pending != null && !submitted.matches(npc.getUUID(), dimension, now, range, npc.getX(), npc.getY(), npc.getZ())) cancel();
            if (pending != null && pending.isDone()) {
                try { result = pending.get(); cursor = 0; }
                catch (CancellationException | ExecutionException failed) { result = null; }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); result = null; }
                pending = null; submitted = null;
            }
            if (result != null) {
                var source = result.snapshot();
                if (!result.currentGeneration() || !source.matches(npc.getUUID(), dimension, now, range, npc.getX(), npc.getY(), npc.getZ())) result = null;
            }
            if (result != null) {
                ServerLevel level = (ServerLevel)npc.level();
                // ponytail: 16 native policy/visibility checks per invocation; snapshots refresh after four seconds.
                for (int checked = 0; cursor < result.targets().size() && checked < 16; checked++) {
                    var entity = level.getEntity(result.targets().get(cursor++));
                    if (type.isInstance(entity) && entity instanceof LivingEntity living && CombatService.validTarget(npc, living)
                            && npc.distanceToSqr(living) <= (double)range * range && conditions.test(npc, living)) {
                        result = null; nextAttempt = now + 10; return living;
                    }
                }
                if (cursor >= result.targets().size()) { result = null; nextAttempt = now + 10; }
            }
            if (pending == null && result == null && now >= nextAttempt) {
                ServerLevel level = (ServerLevel)npc.level(); World world = WORLDS.get(level);
                if (world == null || now < world.tick() || now - world.tick() >= 10) {
                    List<TargetSearch.Candidate> entities = new ArrayList<>();
                    // ponytail: cap a loaded-world snapshot at 65536 living entities; partition only if a real server exceeds this.
                    for (var entity : level.getAllEntities()) if (entity instanceof LivingEntity living && living.isAlive() && !living.isSpectator()) {
                        if (entities.size() >= TargetSearch.MAX_ENTITIES) break;
                        entities.add(new TargetSearch.Candidate(living.getUUID(), living.getX(), living.getY(), living.getZ()));
                    }
                    world = new World(now, List.copyOf(entities)); WORLDS.put(level, world);
                }
                submitted = new TargetSearch.Snapshot(npc.getUUID(), dimension, world.tick(), npc.getX(), npc.getY(), npc.getZ(), range, world.entities());
                pending = CombatDecisions.submitSearch(submitted);
                if (pending == null) submitted = null;
                nextAttempt = now + 10;
            }
            return null;
        }
        public void cancel() {
            if (pending != null) pending.cancel(false);
            pending = null; submitted = null; result = null; cursor = 0; nextAttempt = 0;
        }
    }
}
