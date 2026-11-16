package bincnpc.gun.compat.slashblade;

import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatMovement;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.common.npc.NpcAccess;
import com.google.common.base.Predicate;
import mods.flammpfeil.slashblade.entity.selector.EntitySelectorAttackable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MultiPartEntityPart;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import noppes.npcs.entity.EntityNPCInterface;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Legacy melee uses the same distance as NPC movement; ranged blade entities keep their own range. */
public final class LegacyBladeReach {
    private LegacyBladeReach() {}

    public static boolean controlled(Entity user) {
        if (!(user instanceof EntityNPCInterface) || !(user instanceof NpcAccess)) return false;
        EntityNPCInterface npc = (EntityNPCInterface) user;
        if (!CombatService.serverThread(npc)) return false;
        return CombatService.data(npc).runtime.adapter != null
            && "slashblade".equals(CombatService.data(npc).runtime.adapter.id());
    }

    public static AxisAlignedBB area(CombatContext ctx, AxisAlignedBB nativeArea) {
        double reach = CombatMovement.meleeReach(ctx);
        double x = ctx.npc.posX, z = ctx.npc.posZ;
        double centerX = (nativeArea.minX + nativeArea.maxX) * 0.5 - x;
        double centerZ = (nativeArea.minZ + nativeArea.maxZ) * 0.5 - z;
        // Circular sweeps and dash managers retain their coverage around the owner.
        if (centerX * centerX + centerZ * centerZ < 1.0E-8)
            return new AxisAlignedBB(x - reach, nativeArea.minY, z - reach, x + reach, nativeArea.maxY, z + reach);

        // Scale the native forward footprint until the facing ray reaches the configured distance.
        // Keeping native Y bounds preserves the different grounded, aerial and falling swings.
        double yaw = Math.toRadians(ctx.npc.rotationYaw);
        double forwardX = -Math.sin(yaw), forwardZ = Math.cos(yaw);
        double nativeReach = Double.POSITIVE_INFINITY;
        if (Math.abs(forwardX) > 1.0E-6)
            nativeReach = Math.min(nativeReach, ((forwardX > 0 ? nativeArea.maxX : nativeArea.minX) - x) / forwardX);
        if (Math.abs(forwardZ) > 1.0E-6)
            nativeReach = Math.min(nativeReach, ((forwardZ > 0 ? nativeArea.maxZ : nativeArea.minZ) - z) / forwardZ);
        if (!Double.isFinite(nativeReach) || nativeReach <= 0)
            return new AxisAlignedBB(x - reach, nativeArea.minY, z - reach, x + reach, nativeArea.maxY, z + reach);
        double scale = reach / nativeReach;
        return new AxisAlignedBB(x + (nativeArea.minX - x) * scale, nativeArea.minY, z + (nativeArea.minZ - z) * scale,
            x + (nativeArea.maxX - x) * scale, nativeArea.maxY, z + (nativeArea.maxZ - z) * scale);
    }

    public static boolean withinReach(CombatContext ctx, EntityLivingBase target, double reach) {
        // AABB intersections alone admit targets beyond the configured radius, especially at corners.
        return CombatService.hostile(ctx.npc, target) && ctx.npc.getDistanceSq(target) <= reach * reach
            && ctx.npc.getEntitySenses().canSee(target);
    }

    private static EntityLivingBase living(Entity entity) {
        if (entity instanceof EntityLivingBase) return (EntityLivingBase) entity;
        if (entity instanceof MultiPartEntityPart && ((MultiPartEntityPart) entity).parent instanceof EntityLivingBase)
            return (EntityLivingBase) ((MultiPartEntityPart) entity).parent;
        return null;
    }

    public static List<Entity> targets(World world, Entity owner, AxisAlignedBB nativeArea, Predicate<Entity> nativePredicate) {
        if (!controlled(owner) || nativePredicate != EntitySelectorAttackable.getInstance())
            return world.getEntitiesInAABBexcluding(owner, nativeArea, nativePredicate);
        EntityNPCInterface npc = (EntityNPCInterface) owner;
        CombatContext ctx = new CombatContext(npc);
        if (world != npc.world || !CombatService.validNpc(npc) || !ctx.config.bool("combat.enabled")
                || !ctx.config.bool("slashblade.enabled") || ctx.runtime.editorPaused) return new ArrayList<>();
        double reach = CombatMovement.meleeReach(ctx);
        List<Entity> targets = world.getEntitiesInAABBexcluding(owner, area(ctx, nativeArea), entity -> {
            EntityLivingBase target = living(entity);
            return target != null ? withinReach(ctx, target, reach)
                : nativeArea.intersects(entity.getEntityBoundingBox()) && nativePredicate.apply(entity);
        });
        int hits = 0;
        for (Iterator<Entity> iterator = targets.iterator(); iterator.hasNext();) {
            if (living(iterator.next()) != null && ++hits > 16) iterator.remove();
        }
        return targets;
    }
}
