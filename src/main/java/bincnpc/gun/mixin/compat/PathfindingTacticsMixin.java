package bincnpc.gun.mixin.compat;

import bincnpc.gun.common.combat.CombatMovement;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "top.npcpathfindinglegacy.tactics.TacticsRuntime", remap = false)
public abstract class PathfindingTacticsMixin {
    @Inject(method = "active", at = @At("HEAD"), cancellable = true)
    private static void cnpcgun$ownDestination(EntityNPCInterface npc, CallbackInfoReturnable<Boolean> cir) {
        if (CombatMovement.enhanced(npc)) cir.setReturnValue(false);
    }
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private static void cnpcgun$ownMovement(EntityNPCInterface npc, CallbackInfo ci) {
        if (CombatMovement.enhanced(npc)) ci.cancel();
    }
}
