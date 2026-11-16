package bincnpc.gun.mixin;

import bincnpc.gun.common.combat.CombatService;
import noppes.npcs.ai.EntityAIAttackTarget;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = EntityAIAttackTarget.class, remap = false)
public abstract class MeleeAiMixin {
    @Shadow private EntityNPCInterface npc;
    @Inject(method = {"func_75250_a", "shouldExecute", "func_75253_b", "shouldContinueExecuting"}, at = @At("HEAD"), cancellable = true)
    private void cnpcgun$ownsAttack(CallbackInfoReturnable<Boolean> cir) {
        if (CombatService.blocksNative(npc)) cir.setReturnValue(false);
    }
    @Inject(method = {"func_75246_d", "updateTask"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void cnpcgun$skip(CallbackInfo ci) { if (CombatService.blocksNative(npc)) ci.cancel(); }
}
