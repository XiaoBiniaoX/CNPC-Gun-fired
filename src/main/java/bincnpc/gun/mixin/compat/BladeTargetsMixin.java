package bincnpc.gun.mixin.compat;

import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatMovement;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.common.npc.NpcAccess;
import mods.flammpfeil.slashblade.entity.IShootable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.entity.PartEntity;
import noppes.npcs.entity.EntityNPCInterface;
import java.util.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NPC factions determine native blade targets; player-wide friendly/PVP settings stay native for players. */
@Mixin(targets = "mods.flammpfeil.slashblade.util.TargetSelector", remap = false)
public abstract class BladeTargetsMixin {
    private static boolean controlled(EntityNPCInterface npc) {
        return CombatService.serverThread(npc) && npc instanceof NpcAccess && CombatService.data(npc).runtime.adapter != null
            && CombatService.data(npc).runtime.adapter.id().equals("slashblade");
    }
    private static boolean active(EntityNPCInterface npc) {
        var data = CombatService.data(npc);
        return CombatService.validNpc(npc) && data.config.bool("combat.enabled") && data.config.bool("slashblade.enabled") && !data.runtime.editorPaused;
    }
    private static List<Entity> targets(EntityNPCInterface npc, AABB area, List<Entity> nativeTargets) {
        if (!active(npc)) return new ArrayList<>();
        Set<Entity> result = new LinkedHashSet<>();
        for (Entity entity : nativeTargets) if (!(entity instanceof LivingEntity) && !(entity instanceof PartEntity<?>)) result.add(entity);
        for (LivingEntity living : npc.level().getEntitiesOfClass(LivingEntity.class, area, target -> CombatService.validTarget(npc, target))) {
            if (living.isMultipartEntity()) {
                var parts = living.getParts();
                if (parts != null) for (PartEntity<?> part : parts) if (part.getBoundingBox().intersects(area)) result.add(part);
            } else result.add(living);
        }
        return new ArrayList<>(result);
    }
    @Inject(method = "getResolvedReach", at = @At("HEAD"), cancellable = true)
    private static void cnpcgun$npcReach(LivingEntity user, CallbackInfoReturnable<Double> cir) {
        if (user instanceof EntityNPCInterface npc && controlled(npc)) cir.setReturnValue(CombatMovement.meleeReach(new CombatContext(npc)));
    }
    @Inject(method = "getTargettableEntitiesWithinAABB(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/AABB;D)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private static void cnpcgun$nativeMeleeTargets(Level world, LivingEntity attacker, AABB area, double reach, CallbackInfoReturnable<List<Entity>> cir) {
        if (attacker instanceof EntityNPCInterface npc && controlled(npc)) cir.setReturnValue(targets(npc, area, cir.getReturnValue()));
    }
    @Inject(method = "getTargettableEntitiesWithinAABB(Lnet/minecraft/world/level/Level;DLnet/minecraft/world/entity/Entity;)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private static void cnpcgun$nativeProjectileTargets(Level world, double reach, Entity owner, CallbackInfoReturnable<List<Entity>> cir) {
        if (owner instanceof IShootable shootable && shootable.getShooter() instanceof EntityNPCInterface npc && controlled(npc))
            cir.setReturnValue(targets(npc, owner.getBoundingBox().inflate(Math.max(0, Math.min(32, reach))), cir.getReturnValue()));
    }
}
