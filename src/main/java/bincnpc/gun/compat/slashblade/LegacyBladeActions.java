package bincnpc.gun.compat.slashblade;

import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.combat.*;
import bincnpc.gun.common.legacy.Platform;
import bincnpc.gun.slashblade.adapter.BladeAdapter;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.item.ItemSlashBlade.ComboSequence;
import mods.flammpfeil.slashblade.entity.*;
import mods.flammpfeil.slashblade.ability.*;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.*;
import java.util.*;

/** Stable modern action names mapped to the actual r33 combo table. */
public final class LegacyBladeActions {
    public static final Map<String,ComboSequence> COMBOS = new LinkedHashMap<>();
    private static final Map<ComboSequence,String> PATHS = new EnumMap<>(ComboSequence.class);
    static {
        bind("combo_a1",ComboSequence.Saya1);bind("combo_a2",ComboSequence.Saya2);bind("combo_c",ComboSequence.Battou);
        bind("combo_a3",ComboSequence.Kiriage);bind("combo_a4",ComboSequence.Kiriorosi);bind("combo_a5",ComboSequence.SlashDim);
        bind("combo_b1",ComboSequence.SlashEdge);bind("combo_b2",ComboSequence.ReturnEdge);bind("combo_b7",ComboSequence.SSlashBlade);
        bind("aerial_rave_a1",ComboSequence.AerialRave);bind("aerial_rave_a2",ComboSequence.ASlashEdge);
        bind("aerial_rave_a3",ComboSequence.AKiriorosi);bind("aerial_rave_b4",ComboSequence.AKiriorosiFinish);
        bind("rapid_slash",ComboSequence.RapidSlash);bind("rapid_slash_end",ComboSequence.RapidSlashEnd);
        bind("rising_star",ComboSequence.RisingStar);bind("upperslash",ComboSequence.Kiriage);bind("upperslash_jump",ComboSequence.AKiriage);
        bind("aerial_cleave",ComboSequence.HelmBraker);bind("aerial_cleave_landing",ComboSequence.AKiriorosiFinish);
        for(ComboSequence combo:ComboSequence.values())if(combo!=ComboSequence.None && !PATHS.containsKey(combo))bind(combo.name().replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT),combo);
    }
    private static void bind(String path,ComboSequence combo){COMBOS.put(path,combo);PATHS.putIfAbsent(combo,path);}
    public static String path(ComboSequence combo){return PATHS.getOrDefault(combo,"none");}
    public static ComboSequence resolve(String stage) {
        String path=stage.startsWith("slashblade:")?stage.substring(11):stage;
        ComboSequence combo=COMBOS.get(path);if(combo!=null)return combo;
        try{return ComboSequence.valueOf(stage);}catch(IllegalArgumentException invalid){return ComboSequence.None;}
    }
    /** Recovery ends before r33's combo timeout, so the next input can actually chain. */
    public static int duration(ComboSequence combo) {
        switch (combo) {
            case Saya1: case Saya2: case RapidSlash: case RisingStar: case Stinger: case Calibur: return 6;
            case SlashDim: return 6;
            case RapidSlashEnd: case Noutou: return 4;
            default: return Math.max(4, Math.min(10, combo.comboResetTicks / 2));
        }
    }
    public static ComboSequence next(ComboSequence current,boolean air) {
        if(air) {
            switch(current){case AerialRave:return ComboSequence.ASlashEdge;case ASlashEdge:return ComboSequence.AKiriorosi;
                case AKiriorosi:return ComboSequence.AKiriorosiFinish;default:return ComboSequence.AerialRave;}
        }
        switch(current){case Saya1:return ComboSequence.Saya2;case Saya2:return ComboSequence.Battou;case Battou:return ComboSequence.Kiriage;
            case Kiriage:return ComboSequence.Kiriorosi;case SlashEdge:return ComboSequence.ReturnEdge;
            case ReturnEdge:return ComboSequence.SSlashBlade;case RapidSlash:return ComboSequence.RisingStar;
            case RisingStar:return ComboSequence.AerialRave;default:return ComboSequence.Saya1;}
    }
    public static void pose(CombatContext ctx,ComboSequence combo) {
        NBTTagCompound tag=ItemSlashBlade.getItemTagCompound(ctx.weapon);
        ItemSlashBlade.setComboSequence(tag,combo);ItemSlashBlade.LastActionTime.set(tag,ctx.now);
        ItemSlashBlade.TargetEntityId.set(tag,ctx.validTarget()?ctx.target.getEntityId():0);
        ctx.npc.rotationYaw=ctx.yaw();ctx.npc.rotationYawHead=ctx.yaw();ctx.npc.renderYawOffset=ctx.yaw();ctx.npc.rotationPitch=ctx.pitch();
        ((ItemSlashBlade)ctx.weapon.getItem()).doSwingItem(ctx.weapon,ctx.npc);
        ctx.runtime.maid.lastSlash=ctx.now;ctx.npc.updateClient=true;
    }
    public static Result attack(CombatContext ctx,ComboSequence combo) {
        if(combo==ComboSequence.None)return Result.fail("UNSUPPORTED");
        ItemSlashBlade blade=(ItemSlashBlade)ctx.weapon.getItem();pose(ctx,combo);
        switch(combo) {
            case RapidSlash:
                dash(ctx,2.5);EntityRapidSlashManager rapid=new EntityRapidSlashManager(ctx.npc.world,ctx.npc,false);
                rapid.setLifeTime(6);ctx.npc.world.spawnEntity(rapid);UntouchableTime.setUntouchableTime(ctx.npc,6,false);break;
            case Stinger:
                dash(ctx,1.4);EntityStingerManager stinger=new EntityStingerManager(ctx.npc.world,ctx.npc,true);
                stinger.setLifeTime(4);ctx.npc.world.spawnEntity(stinger);break;
            case RisingStar:case AKiriage:
                Platform.motion(ctx.npc,ctx.npc.motionX,0.7,ctx.npc.motionZ);ctx.npc.fallDistance=0;
                UntouchableTime.setUntouchableTime(ctx.npc,6,false);break;
            case HelmBraker:
                ctx.npc.fallDistance=0;Platform.motion(ctx.npc,ctx.npc.motionX,-1.1,ctx.npc.motionZ);
                EntityHelmBrakerManager helm=new EntityHelmBrakerManager(ctx.npc.world,ctx.npc,false);helm.setLifeTime(20);
                ctx.npc.world.spawnEntity(helm);UntouchableTime.setUntouchableTime(ctx.npc,6,false);break;
            case Calibur:
                dash(ctx,2.5);EntityCaliburManager calibur=new EntityCaliburManager(ctx.npc.world,ctx.npc,false);
                calibur.setLifeTime(6);ctx.npc.world.spawnEntity(calibur);break;
            case AerialRave:case ASlashEdge:case AKiriorosi:case AKiriorosiB:case AKiriorosiFinish:
                ctx.npc.fallDistance=0;Platform.motion(ctx.npc,ctx.npc.motionX,Math.max(0.1,ctx.npc.motionY),ctx.npc.motionZ);break;
            default:break;
        }
        AxisAlignedBB area=LegacyBladeReach.area(ctx,blade.getBBofCombo(ctx.weapon,combo,ctx.npc));
        double reach=CombatMovement.meleeReach(ctx);
        int hits=0;
        for(EntityLivingBase target:ctx.npc.world.getEntitiesWithinAABB(EntityLivingBase.class,area,
                e->LegacyBladeReach.withinReach(ctx,e,reach))) {
            if(hits++>=16)break;
            hit(ctx,target,combo,1);
        }
        if(combo==ComboSequence.SSlashBlade || combo==ComboSequence.Force5 || combo==ComboSequence.Force6)blade.doSlashBladeAttack(ctx.weapon,ctx.npc,combo);
        return Result.success(new BladeAdapter.Execution("slashblade:"+path(combo),duration(combo)));
    }
    static void hit(CombatContext ctx,EntityLivingBase target,ComboSequence combo,float multiplier) {
        if(!LegacyBladeReach.withinReach(ctx,target,CombatMovement.meleeReach(ctx)))return;
        ItemSlashBlade blade=(ItemSlashBlade)ctx.weapon.getItem();NBTTagCompound tag=ItemSlashBlade.getItemTagCompound(ctx.weapon);
        float damage=LegacyBladeCombat.strength(ctx.npc,ctx.weapon,target)*multiplier;
        if(combo.useScabbard)damage=Math.max(0,Math.min(damage,target.getHealth()-1));
        if(damage<=0)return;
        if(LegacyBladeCombat.damage(ctx.npc,target,DamageSource.causeMobDamage(ctx.npc),damage,false)) {
            blade.hitEntity(ctx.weapon,target,ctx.npc);StylishRankManager.doAttack(ctx.npc);
        }
    }
    static void dash(CombatContext ctx,double speed) {
        Vec3d direction=new Vec3d(ctx.shotDirection().x,0,ctx.shotDirection().z).normalize();
        speed*=ctx.npc.onGround?1:0.35;
        AxisAlignedBB next=ctx.npc.getEntityBoundingBox().offset(direction.scale(speed));
        net.minecraft.world.WorldServer world=(net.minecraft.world.WorldServer)ctx.npc.world;
        if(bincnpc.gun.throwing.CollisionSnapshot.volumeLoaded(world,next) && world.getCollisionBoxes(ctx.npc,next).isEmpty())
            Platform.motion(ctx.npc,direction.x*speed,ctx.npc.motionY,direction.z*speed);
    }
}
