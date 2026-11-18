package bincnpc.gun.compat.slashblade;

import bincnpc.gun.common.combat.*;
import bincnpc.gun.common.npc.NpcAccess;
import bincnpc.gun.common.npc.NpcData;
import com.google.common.base.Predicate;
import mods.flammpfeil.slashblade.entity.selector.EntitySelectorAttackable;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.Explosion;
import net.minecraftforge.fml.common.registry.IThrowableEntity;
import net.minecraftforge.event.world.ExplosionEvent;
import noppes.npcs.entity.EntityNPCInterface;
import java.util.*;

/** NPC ownership, factions and normal damage events shared by r33 and its native addon entities. */
public final class LegacyBladeCombat {
    private static final ThreadLocal<Entity> BURST_SOURCE = new ThreadLocal<>();
    private LegacyBladeCombat() {}

    public static EntityNPCInterface owner(Entity source) {
        Entity user = source instanceof IThrowableEntity ? ((IThrowableEntity) source).getThrower() : source;
        if (!(user instanceof EntityNPCInterface) || !(user instanceof NpcAccess)) return null;
        EntityNPCInterface npc = (EntityNPCInterface) user;
        return CombatService.serverThread(npc) ? npc : null;
    }

    public static boolean active(EntityNPCInterface npc) {
        if (!CombatService.serverThread(npc) || !CombatService.validNpc(npc)) return false;
        NpcData data = CombatService.data(npc);
        return data.config.bool("combat.enabled") && data.config.bool("slashblade.enabled") && !data.runtime.editorPaused;
    }

    public static EntityLivingBase living(Entity entity) {
        if (entity instanceof EntityLivingBase) return (EntityLivingBase) entity;
        if (entity instanceof MultiPartEntityPart && ((MultiPartEntityPart) entity).parent instanceof EntityLivingBase)
            return (EntityLivingBase) ((MultiPartEntityPart) entity).parent;
        return null;
    }

    public static boolean allowed(Entity source, Entity target, Predicate<Entity> nativePredicate) {
        EntityNPCInterface npc = owner(source);
        if (npc == null) return target != null && nativePredicate.apply(target);
        if (!active(npc) || target == null || target.world != npc.world || target == npc) return false;
        EntityLivingBase living = living(target);
        if (living != null) {
            double range = Math.max(npc.stats.aggroRange, npc.stats.ranged.getRange());
            return CombatService.hostile(npc, living) && npc.getDistanceSq(living) <= range * range;
        }
        Entity otherOwner = target instanceof IThrowableEntity ? ((IThrowableEntity) target).getThrower() : null;
        if (otherOwner == npc || otherOwner instanceof EntityLivingBase
                && !CombatService.hostile(npc, (EntityLivingBase) otherOwner)) return false;
        return nativePredicate.apply(target);
    }

    public static List<Entity> targets(Entity source, World world, Entity excluded, AxisAlignedBB area, Predicate<Entity> predicate) {
        EntityNPCInterface npc = owner(source);
        if (npc == null || predicate != EntitySelectorAttackable.getInstance())
            return world.getEntitiesInAABBexcluding(excluded, area, predicate);
        if (world != npc.world || !active(npc)) return new ArrayList<>();
        List<Entity> result = world.getEntitiesInAABBexcluding(excluded, area, target -> allowed(source, target, predicate));
        int limit = source instanceof mods.flammpfeil.slashblade.entity.EntityJudgmentCutManager ? 16 : 32;
        int count = 0;
        for (Iterator<Entity> iterator = result.iterator(); iterator.hasNext();)
            if (living(iterator.next()) != null && ++count > limit) iterator.remove();
        return result;
    }

    public static float strength(EntityLivingBase user, ItemStack stack, EntityLivingBase target) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ItemSlashBlade)) return 1;
        ItemSlashBlade blade = (ItemSlashBlade) stack.getItem();
        NBTTagCompound tag = ItemSlashBlade.getItemTagCompound(stack);
        double attribute = user.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE) == null ? 1
            : user.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
        double result = attribute + blade.getBaseAttackModifiers(tag) + ItemSlashBlade.AttackAmplifier.get(tag);
        if (target != null) result += EnchantmentHelper.getModifierForCreature(stack, target.getCreatureAttribute());
        return Double.isFinite(result) ? (float) Math.max(1, Math.min(1000000, result)) : 1;
    }

    public static boolean damage(Entity source, Entity target, DamageSource nativeSource, float amount, boolean melee) {
        EntityNPCInterface npc = owner(source);
        if (npc == null) return target.attackEntityFrom(nativeSource, amount);
        if (!allowed(source, target, EntitySelectorAttackable.getInstance())) return false;
        EntityLivingBase living = living(target);
        if (living != null) {
            if (melee) {
                CombatContext ctx = new CombatContext(npc);
                if (!LegacyBladeReach.withinReach(ctx, living, CombatMovement.meleeReach(ctx))) return false;
                amount = strength(npc, ctx.weapon, living);
            }
            living.hurtResistantTime = 0;
        }
        if (!Float.isFinite(amount) || amount <= 0) return false;
        target.hurtResistantTime = 0;
        DamageSource attributed = source == npc ? DamageSource.causeMobDamage(npc)
            : new EntityDamageSourceIndirect(nativeSource.getDamageType(), source, npc).setProjectile();
        if (nativeSource.isMagicDamage()) attributed.setMagicDamage();
        if (nativeSource.isUnblockable()) attributed.setDamageBypassesArmor();
        if (nativeSource.isDamageAbsolute()) attributed.setDamageIsAbsolute();
        if (nativeSource.isFireDamage()) attributed.setFireDamage();
        return target.attackEntityFrom(attributed, amount);
    }

    public static Explosion burst(Entity source, World world, double x, double y, double z, float power, boolean fire, boolean terrain) {
        EntityNPCInterface npc = owner(source);
        if (npc == null) return world.newExplosion(source, x, y, z, power, fire, terrain);
        if (world != npc.world || !active(npc) || !bincnpc.gun.common.adapter.AdapterRegistry.loaded("slashblade"))
            return world.newExplosion(npc, x, y, z, 0, false, false);
        Entity previous = BURST_SOURCE.get();
        BURST_SOURCE.set(source);
        try { return world.newExplosion(npc, x, y, z, power, false, false); }
        finally { if (previous == null) BURST_SOURCE.remove(); else BURST_SOURCE.set(previous); }
    }

    public static void filterBurst(ExplosionEvent.Detonate event) {
        Entity source = BURST_SOURCE.get();
        EntityNPCInterface npc = owner(source);
        if (npc == null || event.getWorld() != npc.world || event.getExplosion().getExplosivePlacedBy() != npc) return;
        Iterator<Entity> iterator = event.getAffectedEntities().iterator();
        int count = 0;
        while (iterator.hasNext()) {
            Entity target = iterator.next();
            if (!allowed(source, target, EntitySelectorAttackable.getInstance()) || ++count > 32) iterator.remove();
            else target.hurtResistantTime = 0;
        }
    }

    static int phantomCount(CombatContext ctx) {
        return (int)ctx.npc.world.loadedEntityList.stream().filter(entity ->
            !entity.isDead && entity instanceof IThrowableEntity && ((IThrowableEntity) entity).getThrower() == ctx.npc
                && (entity instanceof mods.flammpfeil.slashblade.entity.EntitySummonedSwordBase
                    || entity.getClass().getName().startsWith("cn.mmf.slashblade_addon.entity.EntityPhantom")
                    || entity.getClass().getName().endsWith("EntityLightningSword"))).count();
    }
}
