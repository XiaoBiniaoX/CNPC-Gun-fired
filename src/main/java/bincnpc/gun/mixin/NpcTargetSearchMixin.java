package bincnpc.gun.mixin;

import bincnpc.gun.common.combat.LongRangeTargets;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import noppes.npcs.ai.target.NpcNearestAttackableTargetGoal;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Runs before CNPCplus's native short-range optimization; keeps the original selector and Goal.start. */
@Mixin(value = NearestAttackableTargetGoal.class, priority = 1100)
public abstract class NpcTargetSearchMixin extends TargetGoal {
    @Shadow @Final protected Class<? extends LivingEntity> targetType;
    @Shadow protected LivingEntity target;
    @Shadow protected TargetingConditions targetConditions;
    @Unique private final LongRangeTargets.Search cnpcgun$search = new LongRangeTargets.Search();
    protected NpcTargetSearchMixin(Mob mob, boolean mustSee) { super(mob, mustSee); }
    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void cnpcgun$find(CallbackInfoReturnable<Boolean> cir) {
        if (!((Object)this instanceof NpcNearestAttackableTargetGoal<?>) || !(mob instanceof EntityNPCInterface npc)) return;
        int range = npc.stats.aggroRange;
        targetConditions.range(range);
        if (range > 64 || range == 0) {
            target = cnpcgun$search.find(npc, targetType, targetConditions);
            cir.setReturnValue(target != null);
        } else cnpcgun$search.cancel();
    }
}
