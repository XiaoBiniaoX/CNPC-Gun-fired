package bincnpc.gun.compat.pathfinding;

import bincnpc.gun.common.combat.CombatContext;
import top.npcpathfindinglegacy.pathfinding.IDataAIPathfinding;
import top.npcpathfindinglegacy.pathfinding.INpcPathfindingEntity;

/** Optional server bridge. Destinations are chosen by CNPC-Gun, using the ordinary enhanced navigator. */
public final class PathfindingBridge {
    private PathfindingBridge() {}
    public static void prepare(CombatContext ctx, boolean use) {
        if (!(ctx.npc.ais instanceof IDataAIPathfinding settings) || !(ctx.npc instanceof INpcPathfindingEntity npc)) return;
        var movement = ctx.runtime.movement;
        if (use && ctx.npc.ais.movementType == 0) {
            if (movement.priorNavigationMode < 0) movement.priorNavigationMode = settings.npcpathfindinglegacy$getMode();
            if (settings.npcpathfindinglegacy$getMode() != 1) settings.npcpathfindinglegacy$setMode(1);
            npc.npcpathfindinglegacy$refreshNavigationMode();
        } else if (movement.priorNavigationMode >= 0) {
            npc.npcpathfindinglegacy$clearEnhancedState();
            settings.npcpathfindinglegacy$setMode(movement.priorNavigationMode);
            movement.priorNavigationMode = -1;
            npc.npcpathfindinglegacy$refreshNavigationMode();
        }
    }
}
