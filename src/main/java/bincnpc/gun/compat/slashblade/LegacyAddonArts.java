package bincnpc.gun.compat.slashblade;

import bincnpc.gun.CNPCGun;
import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.slashblade.adapter.BladeAdapter;
import mods.flammpfeil.slashblade.entity.EntityStormSwords;
import mods.flammpfeil.slashblade.entity.EntitySpearManager;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.item.ItemSlashBlade.ComboSequence;
import mods.flammpfeil.slashblade.specialattack.SpecialAttackBase;
import net.minecraft.entity.*;
import net.minecraft.world.World;
import java.lang.reflect.*;
import java.util.*;

/** Optional Japanese Addon 1.7 arts use its own entities and callbacks with a real NPC owner. */
final class LegacyAddonArts {
    private static final String PACKAGE = "cn.mmf.slashblade_addon.";
    private static final Set<String> ARTS = new HashSet<>(Arrays.asList(
        "OverSlash", "RapidPhantomSwords", "SpiralEdge", "GalePhantomSwords", "AquaEdge", "FlareSpiral", "LightningSwords", "TerraSwords"));
    private LegacyAddonArts() {}
    static boolean supported(SpecialAttackBase art) {
        return art != null && art.getClass().getName().startsWith(PACKAGE + "specialattack.") && ARTS.contains(art.getClass().getSimpleName());
    }
    static int cost(SpecialAttackBase art) { return art.getClass().getSimpleName().equals("TerraSwords") ? 40 : 20; }
    static ComboSequence combo(SpecialAttackBase art) {
        switch (art.getClass().getSimpleName()) {
            case "AquaEdge": return ComboSequence.Iai;
            case "SpiralEdge": case "FlareSpiral": return ComboSequence.Battou;
            case "OverSlash": return ComboSequence.HiraTuki;
            default: return ComboSequence.SlashDim;
        }
    }
    static Result execute(CombatContext ctx, SpecialAttackBase art, String mode) {
        String kind = art.getClass().getSimpleName();
        boolean perfect = !mode.equals("Success");
        float damage = LegacyBladeCombat.strength(ctx.npc, ctx.weapon, ctx.target);
        int spawned = 0;
        ComboSequence combo = combo(art);
        try {
            if (kind.equals("RapidPhantomSwords") || kind.equals("GalePhantomSwords") || kind.equals("LightningSwords")) {
                int count = perfect ? 8 : 4;
                boolean lightning = kind.equals("LightningSwords"), rapid = kind.equals("RapidPhantomSwords");
                if (LegacyBladeCombat.phantomCount(ctx) + count + (lightning ? 1 : 0) > 64) return Result.fail("BUSY");
                Class<?> type = Class.forName(PACKAGE + "entity.EntityPhantomSwordEx", true, art.getClass().getClassLoader());
                Class<?> base = Class.forName(PACKAGE + "specialattack.PhantomSwordsBase", true, art.getClass().getClassLoader());
                for (int i = 0; i < count; i++) {
                    Entity sword = (Entity) type.getConstructor(World.class, EntityLivingBase.class, float.class, base).newInstance(ctx.npc.world, ctx.npc, damage, art);
                    call(sword, "setTargetEntityId", new Class<?>[]{int.class}, ctx.target.getEntityId());
                    call(sword, "setInterval", new Class<?>[]{int.class}, rapid ? 7 + i * 2 : lightning ? 7 + i / 2 : 0);
                    double angle = Math.PI * 2 * i / count;
                    double offset = ctx.npc.getRNG().nextDouble() * 2 - 1, yaw = Math.toRadians(ctx.yaw());
                    double x = rapid ? ctx.npc.posX + 2 * offset * Math.cos(yaw) : ctx.target.posX - 2 * Math.sin(angle);
                    double y = rapid ? ctx.npc.posY + 2 * (1 - Math.abs(offset)) + 0.5 : ctx.target.posY + 2.5 + 2 * ctx.npc.getRNG().nextDouble();
                    double z = rapid ? ctx.npc.posZ + 2 * offset * Math.sin(yaw) : ctx.target.posZ + 2 * Math.cos(angle);
                    initial(sword, x, y, z, rapid ? ctx.yaw() : (float)Math.toDegrees(angle) + 180, rapid ? ctx.pitch() : 90, 90, 1.75F);
                    spawned += spawn(ctx, sword, 30, lightning ? 7364008 : ItemSlashBlade.SummonedSwordColor.get(ItemSlashBlade.getItemTagCompound(ctx.weapon)));
                }
                if (lightning) {
                    Class<?> lightningType = Class.forName(PACKAGE + "entity.EntityLightningSword", true, art.getClass().getClassLoader());
                    Entity sword = (Entity) lightningType.getConstructor(World.class, EntityLivingBase.class, float.class).newInstance(ctx.npc.world, ctx.npc, damage);
                    call(sword, "setTargetEntityId", new Class<?>[]{int.class}, ctx.target.getEntityId());
                    call(sword, "setInterval", new Class<?>[]{int.class}, 17 + count / 2);
                    initial(sword, ctx.target.posX, ctx.target.posY + 4.5, ctx.target.posZ, 180, 90, 90, 1.75F);
                    spawned += spawn(ctx, sword, 40, 16766720);
                }
            } else if (kind.equals("TerraSwords")) {
                int count = perfect ? 8 : 6;
                if (LegacyBladeCombat.phantomCount(ctx) + count > 64) return Result.fail("BUSY");
                for (int i = 0; i < count; i++) {
                    EntityStormSwords sword = new EntityStormSwords(ctx.npc.world, ctx.npc, damage, 90, i * 360F / count, ctx.target.getEntityId());
                    sword.setInterval(7 + i * 2); sword.setLifeTime(30); sword.setColor(3524113);
                    spawned += LegacySwordArts.spawn(ctx, sword);
                }
            } else if (kind.equals("AquaEdge") || kind.equals("FlareSpiral")) {
                int count = kind.equals("AquaEdge") ? 1 : 6;
                Class<?> type = Class.forName(PACKAGE + "entity.Entity" + (kind.equals("AquaEdge") ? "AquaEdge" : "FlareEdge"), true, art.getClass().getClassLoader());
                for (int i = 0; i < count; i++) {
                    Entity drive = (Entity) type.getConstructor(World.class, EntityLivingBase.class, float.class).newInstance(ctx.npc.world, ctx.npc, kind.equals("AquaEdge") ? damage : damage / 2);
                    initial(drive, ctx.npc.posX, ctx.npc.posY + ctx.npc.getEyeHeight() / 2, ctx.npc.posZ,
                        ctx.yaw() + i * 60, kind.equals("AquaEdge") ? ctx.pitch() : 0, 90, kind.equals("AquaEdge") ? 1.5F : 0.5F);
                    spawned += spawn(ctx, drive, 10, ItemSlashBlade.SummonedSwordColor.get(ItemSlashBlade.getItemTagCompound(ctx.weapon)));
                }
            } else if (kind.equals("SpiralEdge")) {
                for (int i = 0; i < 6; i++) spawned += LegacySwordArts.drive(ctx, damage / 2, false, 90, 0.5F, 10, ctx.yaw() + 60 * i, 0);
            } else if (kind.equals("OverSlash")) {
                for (int i = 0; i < (perfect ? 5 : 3); i++)
                    spawned += LegacySwordArts.drive(ctx, damage, true, i % 2 == 0 ? 0 : 90, 0.75F, 20, ctx.yaw(), ctx.pitch());
                LegacyBladeActions.dash(ctx, 3.5);
                EntitySpearManager spear = new EntitySpearManager(ctx.npc.world, ctx.npc, false); spear.setLifeTime(7);
                spawned += LegacySwordArts.spawn(ctx, spear);
            }
        } catch (ReflectiveOperationException | LinkageError error) {
            CNPCGun.LOGGER.warn("Cannot execute optional NPC sword art " + kind, error);
            return Result.fail("INTERNAL_ERROR", kind);
        }
        if (spawned == 0) return Result.fail("INTERNAL_ERROR");
        // Spiral arts have a native circular melee sweep in addition to their remote entities.
        if (kind.equals("SpiralEdge") || kind.equals("FlareSpiral")) LegacyBladeActions.attack(ctx, combo);
        else LegacyBladeActions.pose(ctx, combo);
        ItemSlashBlade.IsCharged.set(ItemSlashBlade.getItemTagCompound(ctx.weapon), true);
        return Result.success(new BladeAdapter.Execution("slashblade:" + LegacyBladeActions.path(combo), perfect ? 12 : 16));
    }
    private static void call(Entity entity, String method, Class<?>[] types, Object... values) throws ReflectiveOperationException {
        Method callable = entity.getClass().getMethod(method, types);
        // Public final setters can be declared by the addon's package-private EntityBase.
        if (!Modifier.isPublic(callable.getDeclaringClass().getModifiers())) callable.setAccessible(true);
        callable.invoke(entity, values);
    }
    private static void initial(Entity entity, double x, double y, double z, float yaw, float pitch, float roll, float speed) throws ReflectiveOperationException {
        call(entity, "setInitialPosition", new Class<?>[]{double.class,double.class,double.class,float.class,float.class,float.class,float.class}, x,y,z,yaw,pitch,roll,speed);
    }
    private static int spawn(CombatContext ctx, Entity entity, int life, int color) throws ReflectiveOperationException {
        call(entity, "setLifeTime", new Class<?>[]{int.class}, life);
        call(entity, "setColor", new Class<?>[]{int.class}, color);
        return LegacySwordArts.spawn(ctx, entity);
    }
}
