package bincnpc.gun.compat.slashblade;

import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.combat.*;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.entity.*;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Enchantments;
import net.minecraft.entity.*;
import com.google.gson.JsonObject;
import java.util.*;

final class LegacyPhantoms {
    private LegacyPhantoms(){}
    static Result execute(CombatContext ctx,String kind,JsonObject params,int baseCost,int reserve) {
        if(kind.equals("spiral_fire")) {
            int fired=0;
            for(EntitySpiralSwords sword:ctx.npc.world.getEntitiesWithinAABB(EntitySpiralSwords.class,ctx.npc.getEntityBoundingBox().grow(40),e->e.getThrower()==ctx.npc)) {
                sword.setTargetEntityId(ctx.validTarget()?ctx.target.getEntityId():0);sword.setHasFired(true);fired++;
            }
            return fired>0?Result.success(fired):Result.fail("CONDITION_NOT_MET");
        }
        boolean unrestricted=ctx.config.bool("slashblade.ignoreRequirements"),maid=ctx.config.text("slashblade.preset").equals("MAID");
        ItemSlashBlade blade=(ItemSlashBlade)ctx.weapon.getItem();
        if(!unrestricted && !blade.getSwordType(ctx.weapon).contains(ItemSlashBlade.SwordType.Bewitched))return Result.fail("CONDITION_NOT_MET");
        int power=EnchantmentHelper.getEnchantmentLevel(Enchantments.POWER,ctx.weapon);
        if(!unrestricted && power<=0)return Result.fail("CONDITION_NOT_MET");
        if(unrestricted)power=maid?(Math.max(0,power)+4)*2:Math.max(1,power);
        int count=params.get("count").getAsInt(),baseline=kind.equals("basic")?1:kind.equals("rain")?18:6;
        int cost=(int)Math.ceil(baseCost*Math.max(1,count/(double)baseline));
        net.minecraft.nbt.NBTTagCompound tag=ItemSlashBlade.getItemTagCompound(ctx.weapon);
        if(ItemSlashBlade.ProudSoul.get(tag)-cost<reserve)return Result.fail("INSUFFICIENT_RESOURCE");
        int existing=LegacyBladeCombat.phantomCount(ctx);
        if(existing+count>64)return Result.fail("BUSY");
        List<EntityLivingBase> targets=new ArrayList<>();
        if(ctx.validTarget())targets.add(ctx.target);
        if(params.has("targetCount"))for(EntityLivingBase target:ctx.targets())if(!targets.contains(target)&&targets.size()<params.get("targetCount").getAsInt())targets.add(target);
        if(kind.equals("basic") && maid)targets.removeIf(target->Math.abs(target.posY-ctx.npc.posY)>=7);
        if(targets.isEmpty() && !kind.equals("spiral"))return Result.fail("INVALID_TARGET");
        int spawned=0,hold=(int)(ctx.now&0x7fffffff);
        if(kind.equals("spiral"))ctx.npc.getEntityData().setInteger("SB.SPHOLDID",hold);
        for(int i=0;i<count;i++) {
            EntityLivingBase target=targets.isEmpty()?null:targets.get(i%targets.size());
            int targetId=target==null?0:target.getEntityId(),delay=params.has("delay")?params.get("delay").getAsInt():0;
            EntitySummonedSwordBase sword;
            switch(kind) {
                case "spiral":EntitySpiralSwords spiral=new EntitySpiralSwords(ctx.npc.world,ctx.npc,power,0,360F/count*i);
                    spiral.setHoldId(hold);spiral.setInterval(delay);sword=spiral;break;
                case "storm":sword=new EntityStormSwords(ctx.npc.world,ctx.npc,power,0,360F/count*i,targetId);break;
                case "blistering":sword=new EntityBlisteringSwords(ctx.npc.world,ctx.npc,power,0,delay+i);sword.setTargetEntityId(targetId);break;
                case "rain":sword=new EntityHeavyRainSwords(ctx.npc.world,ctx.npc,power,0,delay+i,targetId);break;
                default:sword=new EntitySummonedSwordBase(ctx.npc.world,ctx.npc,power,ctx.npc.getRNG().nextFloat()*360);
                    sword.setTargetEntityId(targetId);sword.setDriveVector(3);break;
            }
            sword.setColor(ItemSlashBlade.SummonedSwordColor.get(tag));sword.setThrowerEntityId(ctx.npc.getEntityId());sword.setLifeTime(100);
            if(ctx.npc.world.spawnEntity(sword))spawned++;else sword.setDead();
        }
        if(spawned==0)return Result.fail("INTERNAL_ERROR");
        ItemSlashBlade.ProudSoul.set(tag,ItemSlashBlade.ProudSoul.get(tag)-cost);return Result.success(spawned);
    }
}
