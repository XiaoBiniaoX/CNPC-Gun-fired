package bincnpc.gun.mixin;

import bincnpc.gun.throwing.CollisionSnapshot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extend NPC sight before CNPCplus's 128-block guard, never changing sight for other mobs. */
@Mixin(value = LivingEntity.class, priority = 1100)
public abstract class NpcLongRangeSightMixin {
    @Inject(method = "hasLineOfSight", at = @At("HEAD"), cancellable = true)
    private void cnpcgun$sight(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object)this instanceof EntityNPCInterface npc) || !(npc.level() instanceof ServerLevel level)) return;
        if (!level.getServer().isSameThread()) { cir.setReturnValue(false); return; }
        if (target.level() != level) { cir.setReturnValue(false); return; }
        Vec3 from = npc.getEyePosition(), to = target.getEyePosition();
        if (from.distanceToSqr(to) <= 128.0 * 128.0) return;
        // Range uses entity positions; the shared ray validates finite coordinates and eye-height offsets.
        if (npc.distanceToSqr(target) > 2048.0 * 2048.0) { cir.setReturnValue(false); return; }
        cir.setReturnValue(CollisionSnapshot.clear(level, npc, from, to));
    }
}
