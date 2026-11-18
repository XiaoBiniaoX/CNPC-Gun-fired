package bincnpc.gun.mixin;

import bincnpc.gun.common.combat.LongRangeTargets;
import com.google.common.base.Predicate;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITarget;
import noppes.npcs.ai.target.EntityAIClosestTarget;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep the native selector and startExecuting; only replace expensive long-range acquisition. */
@Mixin(value = EntityAIClosestTarget.class, priority = 1100)
public abstract class NpcTargetSearchMixin extends EntityAITarget {
    @Shadow(remap = false) private EntityNPCInterface npc;
    @Shadow(remap = false) @Final private Class<?> targetClass;
    @Shadow(remap = false) @Final private Predicate<EntityLivingBase> field_82643_g;
    @Shadow(remap = false) private EntityLivingBase targetEntity;
    @Unique private final LongRangeTargets.Search cnpcgun$search = new LongRangeTargets.Search();
    protected NpcTargetSearchMixin(EntityCreature creature, boolean sight) { super(creature, sight); }
    @Override protected double getTargetDistance() { return npc.stats.aggroRange; }
    @Inject(method = {"func_75250_a()Z", "shouldExecute()Z"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcgun$find(CallbackInfoReturnable<Boolean> cir) {
        if (npc.stats.aggroRange > 64 || npc.stats.aggroRange == 0) {
            targetEntity = cnpcgun$search.find(npc, targetClass, field_82643_g);
            cir.setReturnValue(targetEntity != null);
        } else cnpcgun$search.cancel();
    }
}
