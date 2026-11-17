package bincnpc.gun.common.combat;

import bincnpc.gun.CNPCGun;
import bincnpc.gun.common.npc.NpcAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.entity.PartEntity;
import noppes.npcs.controllers.data.Faction;
import noppes.npcs.entity.EntityNPCInterface;

/** Shared server-side policy for native hits, area damage and effects; no optional mod classes. */
public final class FriendlyFire {
    private static boolean reportedFailure;
    private FriendlyFire() {}

    public static EntityNPCInterface shooter(Entity source) {
        if (source == null || !(source.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) return null;
        try {
            for (int depth = 0; depth < 4 && source != null; depth++) {
                if (source instanceof EntityNPCInterface npc) return npc;
                Entity owner;
                if (source instanceof Projectile projectile) {
                    owner = projectile.getOwner();
                    if (owner == null && projectile.getPersistentData().hasUUID("CNPCGunThrowOwner"))
                        owner = level.getEntity(projectile.getPersistentData().getUUID("CNPCGunThrowOwner"));
                } else if (source instanceof AreaEffectCloud cloud) owner = cloud.getOwner();
                else if (source instanceof PrimedTnt tnt) owner = tnt.getOwner();
                else if (source instanceof OwnableEntity ownable) owner = ownable.getOwner();
                else return null;
                if (owner == source || owner != null && owner.level() != level) return null;
                source = owner;
            }
        } catch (RuntimeException | LinkageError error) { report(error); }
        return null;
    }

    public static boolean blocksDamage(DamageSource source, Entity victim) {
        if (source == null || victim == null) return false;
        EntityNPCInterface npc = shooter(source.getEntity());
        if (npc == null) npc = shooter(source.getDirectEntity());
        return isProtected(npc, victim);
    }

    public static boolean isProtected(Entity source, Entity victimRaw) {
        EntityNPCInterface npc = shooter(source);
        if (npc == null || victimRaw == null || !(npc instanceof NpcAccess)) return false;
        Entity victim = victimRaw instanceof PartEntity<?> part ? part.getParent() : victimRaw;
        if (victim == null || victim == npc || victim.level() != npc.level()) return false;
        try {
            if (!CombatService.data(npc).config.bool("combat.disableFriendlyFire")) return false;
            Faction faction = npc.getFaction();
            if (faction == null) return false;
            LivingEntity owner = npc.getOwner();
            if (owner != null && victim == owner) return true;
            if (victim instanceof Player player) {
                // Friendly points are a hard guard. Neutral players may be hit only while targeted.
                return faction.isFriendlyToPlayer(player)
                    || !faction.isAggressiveToPlayer(player) && npc.getTarget() != player;
            }
            if (victim instanceof EntityNPCInterface other) {
                Faction otherFaction = other.getFaction();
                if (otherFaction != null && otherFaction.getId() == faction.getId()) return true;
                LivingEntity otherOwner = other.getOwner();
                if (otherOwner != null && (otherOwner == owner
                        || otherOwner instanceof Player player && !faction.isAggressiveToPlayer(player))) return true;
                return (otherFaction == null || !faction.isAggressiveToNpc(other)) && npc.getTarget() != other;
            }
            return false;
        } catch (RuntimeException | LinkageError error) {
            report(error); return false;
        }
    }

    private static void report(Throwable error) {
        if (!reportedFailure) {
            reportedFailure = true;
            CNPCGun.LOGGER.warn("NPC friendly fire policy unavailable; retaining native damage: {}", error.toString());
        }
    }
}
