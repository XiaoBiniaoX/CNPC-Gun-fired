package bincnpc.gun.common.combat;

import java.util.*;
import java.util.concurrent.*;
import com.google.common.base.Predicate;
import net.minecraft.entity.*;
import net.minecraft.world.WorldServer;
import noppes.npcs.entity.EntityNPCInterface;

/** Main-thread capture and native policy revalidation around the shared pure worker. */
public final class LongRangeTargets {
    private static final class World {
        final long tick;
        final List<TargetSearch.Candidate> entities;
        World(long tick, List<TargetSearch.Candidate> entities) { this.tick = tick; this.entities = Collections.unmodifiableList(entities); }
    }
    private static final Map<WorldServer, World> WORLDS = new HashMap<>();
    private LongRangeTargets() {}
    public static void clear() { WORLDS.clear(); }
    public static final class Search {
        private FutureTask<CombatDecisions.SearchResult> pending;
        private CombatDecisions.SearchResult result;
        private TargetSearch.Snapshot submitted;
        private int cursor;
        private long nextAttempt;
        public EntityLivingBase find(EntityNPCInterface npc, Class<?> type, Predicate<EntityLivingBase> conditions) {
            if (!CombatService.serverThread(npc)) return null;
            if (!CombatService.validNpc(npc)) { cancel(); return null; }
            long now = npc.world.getTotalWorldTime(); int range = npc.stats.aggroRange;
            String dimension = Integer.toString(npc.dimension);
            if (range == 0) { cancel(); return null; }
            if (pending != null && !submitted.matches(npc.getUniqueID(), dimension, now, range, npc.posX, npc.posY, npc.posZ)) cancel();
            if (pending != null && pending.isDone()) {
                try { result = pending.get(); cursor = 0; }
                catch (CancellationException | ExecutionException failed) { result = null; }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); result = null; }
                pending = null; submitted = null;
            }
            if (result != null && (!result.currentGeneration() || !result.snapshot().matches(npc.getUniqueID(), dimension, now, range, npc.posX, npc.posY, npc.posZ))) result = null;
            if (result != null) {
                WorldServer level = (WorldServer)npc.world;
                // ponytail: 16 native policy/visibility checks per invocation; snapshots refresh after four seconds.
                for (int checked = 0; cursor < result.targets().size() && checked < 16; checked++) {
                    Entity entity = level.getEntityFromUuid(result.targets().get(cursor++));
                    if (type.isInstance(entity) && entity instanceof EntityLivingBase) {
                        EntityLivingBase living = (EntityLivingBase)entity;
                        if (CombatService.validTarget(npc, living) && npc.getDistanceSq(living) <= (double)range * range
                                && (conditions == null || conditions.apply(living))) {
                            result = null; nextAttempt = now + 10; return living;
                        }
                    }
                }
                if (cursor >= result.targets().size()) { result = null; nextAttempt = now + 10; }
            }
            if (pending == null && result == null && now >= nextAttempt) {
                WorldServer level = (WorldServer)npc.world; World world = WORLDS.get(level);
                if (world == null || now < world.tick || now - world.tick >= 10) {
                    List<TargetSearch.Candidate> entities = new ArrayList<>();
                    // ponytail: cap loaded-world snapshots at 65536 living entities; partition only if a real server exceeds this.
                    for (Entity entity : level.loadedEntityList) if (entity instanceof EntityLivingBase && entity.isEntityAlive()
                            && !bincnpc.gun.common.legacy.Platform.spectator((EntityLivingBase)entity)) {
                        if (entities.size() >= TargetSearch.MAX_ENTITIES) break;
                        entities.add(new TargetSearch.Candidate(entity.getUniqueID(), entity.posX, entity.posY, entity.posZ));
                    }
                    world = new World(now, entities); WORLDS.put(level, world);
                }
                submitted = new TargetSearch.Snapshot(npc.getUniqueID(), dimension, world.tick, npc.posX, npc.posY, npc.posZ, range, world.entities);
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
