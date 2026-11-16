package bincnpc.gun.compat.pathfinding;

import bincnpc.gun.common.combat.CombatContext;
import top.npcpathfindinglegacy.pathfinding.IDataAIPathfinding;
import top.npcpathfindinglegacy.pathfinding.INpcPathfindingEntity;

/** Optional server bridge. Destinations are chosen by CNPC-Gun, using the ordinary enhanced navigator. */
public final class PathfindingBridge {
    private PathfindingBridge() {}
    public static void prepare(CombatContext ctx, boolean use) {
Object cnpcgun$value1; INpcPathfindingEntity cnpcgun$match1;

Object cnpcgun$value0; IDataAIPathfinding cnpcgun$match0;

        if (!(((cnpcgun$value0 = ctx.npc.ais) instanceof IDataAIPathfinding && (cnpcgun$match0 = (IDataAIPathfinding)cnpcgun$value0) != null)) || !(((cnpcgun$value1 = ctx.npc) instanceof INpcPathfindingEntity && (cnpcgun$match1 = (INpcPathfindingEntity)cnpcgun$value1) != null))) return;
        bincnpc.gun.common.combat.MovementState movement = ctx.runtime.movement;
        if (use && ctx.npc.ais.movementType == 0) {
            if (movement.priorNavigationMode < 0) movement.priorNavigationMode = cnpcgun$match0.npcpathfindinglegacy$getMode();
            if (cnpcgun$match0.npcpathfindinglegacy$getMode() != 1) cnpcgun$match0.npcpathfindinglegacy$setMode(1);
            cnpcgun$match1.npcpathfindinglegacy$refreshNavigationMode();
        } else if (movement.priorNavigationMode >= 0) {
            cnpcgun$match1.npcpathfindinglegacy$clearEnhancedState();
            cnpcgun$match0.npcpathfindinglegacy$setMode(movement.priorNavigationMode);
            movement.priorNavigationMode = -1;
            cnpcgun$match1.npcpathfindinglegacy$refreshNavigationMode();
        }
    }
}
