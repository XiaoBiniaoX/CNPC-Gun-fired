package bincnpc.gun.compat.slashblade;

import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.slashblade.adapter.BladeAdapter;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.item.ItemSlashBlade.ComboSequence;
import mods.flammpfeil.slashblade.specialattack.*;
import mods.flammpfeil.slashblade.entity.*;
import mods.flammpfeil.slashblade.ability.UntouchableTime;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import java.lang.reflect.Field;

/** r33 special attacks instantiated with the real NPC owner, without a fake player. */
final class LegacySwordArts {
    private LegacySwordArts(){}
    static boolean supported(SpecialAttackBase art) {
        return art instanceof SlashDimension || art instanceof SlashDimensionSpiral || art instanceof Drive || art instanceof WaveEdge
            || art instanceof CircleSlash || art instanceof Spear || art instanceof SakuraEnd || art instanceof MaximumBet || art instanceof BlisteringWitherSwords
            || LegacyAddonArts.supported(art);
    }
    static int cost(SpecialAttackBase art){return LegacyAddonArts.supported(art)?LegacyAddonArts.cost(art):art instanceof Drive?10:art instanceof BlisteringWitherSwords?40:20;}
    static Result execute(CombatContext ctx,SpecialAttackBase art,String mode) {
        if(art==null || !supported(art))return Result.fail("UNSUPPORTED");
        if(LegacyAddonArts.supported(art))return LegacyAddonArts.execute(ctx,art,mode);
        ItemSlashBlade blade=(ItemSlashBlade)ctx.weapon.getItem();
        float damage=LegacyBladeCombat.strength(ctx.npc,ctx.weapon,ctx.target);
        boolean perfect=mode.equals("Jackpot") || mode.equals("Super"), superArt=mode.equals("Super");
        ComboSequence combo=ComboSequence.SlashDim;int spawned=0;
        if(art instanceof Drive) {
            float speed=((Number)field(art,Drive.class,"speed")).floatValue();
            int life=((Number)field(art,Drive.class,"lifetime")).intValue();
            combo=(ComboSequence)field(art,Drive.class,"setCombo");boolean multi=(Boolean)field(art,Drive.class,"multihit");
            spawned+=drive(ctx,damage,multi,90-combo.swingDirection,speed,life,ctx.yaw(),ctx.pitch());
        } else if(art instanceof WaveEdge) {
            combo=ComboSequence.Kiriage;
            for(float speed:new float[]{0.25F,0.3F,0.35F})spawned+=drive(ctx,damage/2,false,0,speed,20,ctx.yaw(),ctx.pitch());
            spawned+=drive(ctx,damage/2,true,0,0.225F,20,ctx.yaw(),ctx.pitch());
        } else if(art instanceof CircleSlash) {
            combo=ComboSequence.Battou;
            for(int i=0;i<6;i++)spawned+=drive(ctx,damage/2,false,90,0.5F,10,ctx.yaw()+60*i,0);
        } else if(art instanceof Spear) {
            combo=ComboSequence.HiraTuki;LegacyBladeActions.dash(ctx,3.5);
            EntitySpearManager spear=new EntitySpearManager(ctx.npc.world,ctx.npc,false);spear.setLifeTime(7);spawned+=spawn(ctx,spear);
            UntouchableTime.setUntouchableTime(ctx.npc,10);
        } else if(art instanceof SakuraEnd) {
            combo=ComboSequence.SlashEdge;spawned+=spawn(ctx,new EntitySakuraEndManager(ctx.npc.world,ctx.npc));
        } else if(art instanceof MaximumBet) {
            combo=ComboSequence.SlashEdge;spawned+=spawn(ctx,new EntityMaximumBetManager(ctx.npc.world,ctx.npc));
        } else if(art instanceof BlisteringWitherSwords) {
            int count=perfect?8:4;
            if(LegacyBladeCombat.phantomCount(ctx)+count>64)return Result.fail("BUSY");
            for(int i=0;i<count;i++){
                EntityWitherSword sword=new EntityWitherSword(ctx.npc.world,ctx.npc,damage,90);
                sword.setInterval(7+i*2);sword.setLifeTime(30);sword.setBurst(i%2==0);sword.setTargetEntityId(ctx.target.getEntityId());spawned+=spawn(ctx,sword);
            }
        } else {
            int count=superArt?5:1;
            Vec3d target=ctx.target.getPositionVector().add(0,ctx.target.height*0.5,0);
            for(int i=0;i<count;i++) {
                EntitySlashDimension dimension=new EntitySlashDimension(ctx.npc.world,ctx.npc,perfect?damage:Math.max(1,damage*0.5F));
                dimension.setPosition(target.x+(i==0?0:Math.cos(i*Math.PI/2)*2),target.y,target.z+(i==0?0:Math.sin(i*Math.PI/2)*2));
                dimension.setLifeTime(perfect?20:10);dimension.setInterval(perfect?2:7);dimension.setIsSlashDimension(perfect);
                dimension.setThrowerEntityId(ctx.npc.getEntityId());dimension.setColor(ItemSlashBlade.SummonedSwordColor.get(ItemSlashBlade.getItemTagCompound(ctx.weapon)));
                spawned+=spawn(ctx,dimension);
            }
            // The native manager immobilizes its owner for the entire super-art sequence.
            if(superArt)spawned+=spawn(ctx,new EntityJudgmentCutManager(ctx.npc.world,ctx.npc));
            if(art instanceof SlashDimensionSpiral)for(int i=0;i<6;i++)spawned+=drive(ctx,damage/3,false,90,0.5F,12,ctx.yaw()+i*60,0);
        }
        if(spawned==0)return Result.fail("INTERNAL_ERROR");
        LegacyBladeActions.pose(ctx,combo);ItemSlashBlade.IsCharged.set(ItemSlashBlade.getItemTagCompound(ctx.weapon),true);
        return Result.success(new BladeAdapter.Execution("slashblade:"+LegacyBladeActions.path(combo),superArt?40:perfect?12:16));
    }
    private static Object field(Object object,Class<?> owner,String name) {
        try {Field field=owner.getDeclaredField(name);field.setAccessible(true);return field.get(object);}
        catch(ReflectiveOperationException error){throw new IllegalStateException("native sword art "+name,error);}
    }
    static int spawn(CombatContext ctx,Entity entity){return ctx.npc.world.spawnEntity(entity)?1:0;}
    static int drive(CombatContext ctx,float damage,boolean multi,float roll,float speed,int life,float yaw,float pitch) {
        EntityDrive drive=new EntityDrive(ctx.npc.world,ctx.npc,damage,multi,roll);
        drive.setLocationAndAngles(ctx.npc.posX,ctx.npc.posY+ctx.npc.getEyeHeight()/2,ctx.npc.posZ,yaw,pitch);
        drive.setThrower(ctx.npc);
        drive.setInitialSpeed(speed);drive.setLifeTime(Math.min(100,life));return spawn(ctx,drive);
    }
}
