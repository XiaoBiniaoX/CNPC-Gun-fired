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
    @Inject(method = {"m_8036_", "canUse", "m_8045_", "canContinueToUse"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void cnpcgun$ownsAttack(CallbackInfoReturnable<Boolean> cir) {
        if (CombatService.blocksNative(npc)) cir.setReturnValue(false);
    }
    @Inject(method = {"m_8037_", "tick"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void cnpcgun$skip(CallbackInfo ci) { if (CombatService.blocksNative(npc)) ci.cancel(); }
}
