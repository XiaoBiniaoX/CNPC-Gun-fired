package bincnpc.gun.compat.flan;

import bincnpc.gun.throwing.ThrowAdapter;
import com.flansmod.common.guns.*;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;

public final class FlanThrowAdapter implements ThrowAdapter {
    @Override public boolean matches(ItemStack item){return item.getItem() instanceof ItemGrenade && ((ItemGrenade)item.getItem()).type.canThrow;}
    @Override public Entity create(WorldServer level,EntityNPCInterface owner,ItemStack item){return new EntityGrenade(owner,((ItemGrenade)item.getItem()).type);}
    @Override public Profile profile(Entity projectile){
        GrenadeType type=((EntityGrenade)projectile).type;
        return new Profile(Math.max(0.01,0.5*type.throwSpeed),Math.max(0,Math.min(2,0.024525*type.fallSpeed)),1,
            Math.min(2,projectile.width*0.5),Math.min(32,Math.max(type.explosionRadius,type.smokeRadius)),
            Math.max(1,Math.min(1200,type.fuse>0?type.fuse:type.despawnTime>0?type.despawnTime:200)),false);
    }
    @Override public void detonate(Entity projectile){((EntityGrenade)projectile).detonate();}
    @Override public boolean settleAtLanding(Entity projectile){return ((EntityGrenade)projectile).smoking || ((EntityGrenade)projectile).type.smokeTime>0;}
}
