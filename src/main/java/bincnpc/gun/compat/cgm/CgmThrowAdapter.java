package bincnpc.gun.compat.cgm;

import bincnpc.gun.throwing.ThrowAdapter;
import bincnpc.gun.common.legacy.Platform;
import bincnpc.gun.mixin.compat.CgmThrowableAccess;
import bincnpc.gun.mixin.compat.CgmThrowAccess;
import com.mrcrayfish.guns.item.ItemGrenade;
import com.mrcrayfish.guns.item.ItemStunGrenade;
import com.mrcrayfish.guns.entity.EntityThrowableGrenade;
import com.mrcrayfish.guns.entity.EntityThrowableStunGrenade;
import com.mrcrayfish.guns.GunConfig;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;

public final class CgmThrowAdapter implements ThrowAdapter {
    @Override public boolean matches(ItemStack item){return item.getItem() instanceof ItemGrenade;}
    @Override public Entity create(WorldServer level,EntityNPCInterface owner,ItemStack item){
        EntityThrowableGrenade grenade=item.getItem() instanceof ItemStunGrenade?new EntityThrowableStunGrenade(level):new EntityThrowableGrenade(level);
        ((CgmThrowableAccess)grenade).cnpcgun$thrower(owner);
        ((CgmThrowableAccess)grenade).cnpcgun$throwerName(owner.getUniqueID().toString());
        CgmThrowAccess access=(CgmThrowAccess)grenade;access.cnpcgun$bounce(true);access.cnpcgun$gravity(0.05F);
        grenade.setItem(Platform.copy(item,1));grenade.setMaxLife(60);
        grenade.setPosition(owner.posX,Platform.eyeY(owner)-0.1,owner.posZ);return grenade;
    }
    @Override public Profile profile(Entity projectile){
        CgmThrowAccess access=(CgmThrowAccess)projectile;
        double blast=projectile instanceof EntityThrowableStunGrenade
            ?Math.max(GunConfig.SERVER.stunGrenades.deafen.criteria.radius,GunConfig.SERVER.stunGrenades.blind.criteria.radius)
            :GunConfig.SERVER.grenades.explosionRadius;
        return new Profile(1,access.cnpcgun$gravity(),0.99,Math.min(2,projectile.width*0.5),Math.max(0,Math.min(32,blast)),Math.max(1,Math.min(1200,access.cnpcgun$life())),false);
    }
    @Override public void straight(Entity projectile){((CgmThrowAccess)projectile).cnpcgun$gravity(0);projectile.setNoGravity(true);}
    @Override public void restore(Entity projectile,Profile profile){((CgmThrowAccess)projectile).cnpcgun$gravity((float)profile.gravity());projectile.setNoGravity(false);}
    @Override public void detonate(Entity projectile){if(!projectile.isDead){projectile.setDead();((EntityThrowableGrenade)projectile).onDeath();}}
}
