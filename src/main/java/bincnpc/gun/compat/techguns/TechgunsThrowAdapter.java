package bincnpc.gun.compat.techguns;

import bincnpc.gun.throwing.ThrowAdapter;
import bincnpc.gun.mixin.compat.*;
import techguns.items.guns.*;
import techguns.entities.projectiles.*;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;

public final class TechgunsThrowAdapter implements ThrowAdapter {
    @Override public boolean matches(ItemStack item){return item.getItem() instanceof GenericGrenade;}
    @Override public Entity create(WorldServer level,EntityNPCInterface owner,ItemStack item){
        GenericGrenade grenade=(GenericGrenade)item.getItem();
        return ((TechgunsGrenadeAccess)grenade).cnpcgun$factory().createProjectile(level,owner,grenade.damage,grenade.speed,
            grenade.ticksToLive,0,grenade.radius,grenade.radiusMin,grenade.damageMin,grenade.penetration,
            GenericGun.getDoBlockDamage(owner),EnumBulletFirePos.LEFT,grenade.radiusMin,0.015,1,grenade.maxBounces);
    }
    @Override public Profile profile(Entity projectile){
        TechgunsProjectileAccess access=(TechgunsProjectileAccess)projectile;
        double blast=access.cnpcgun$blastRadius();
        return new Profile(Math.max(0.01,bincnpc.gun.common.legacy.Platform.motion(projectile).length()),
            access.cnpcgun$gravity(),0.99,Math.min(2,projectile.width*0.5),Math.max(0,Math.min(32,blast)),
            Math.max(1,Math.min(1200,access.cnpcgun$life())),false);
    }
    @Override public void straight(Entity projectile){((TechgunsProjectileAccess)projectile).cnpcgun$gravity(0);projectile.setNoGravity(true);}
    @Override public void restore(Entity projectile,Profile profile){((TechgunsProjectileAccess)projectile).cnpcgun$gravity(profile.gravity());projectile.setNoGravity(false);}
    @Override public void detonate(Entity projectile){if(!projectile.isDead)((TechgunsGrenadeImpact)projectile).cnpcgun$detonate();}
}
