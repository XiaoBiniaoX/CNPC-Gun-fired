package bincnpc.gun.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NPC-local follow distance, without changing the shared attribute limit for every mob. */
@Mixin(TargetGoal.class)
public abstract class NpcFollowDistanceMixin {
    @Shadow @Final protected Mob mob;
    @Inject(method = "getFollowDistance", at = @At("HEAD"), cancellable = true)
    private void cnpcgun$range(CallbackInfoReturnable<Double> cir) {
        if (mob instanceof EntityNPCInterface npc) cir.setReturnValue((double)npc.stats.aggroRange);
    }
}
