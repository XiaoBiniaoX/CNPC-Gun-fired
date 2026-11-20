package bincnpc.gun.compat.flan;

import bincnpc.gun.common.api.*;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.gun.adapter.NativeMagazineAdapter;
import bincnpc.gun.gun.ammo.*;
import com.flansmod.common.guns.*;
import com.flansmod.common.vector.Vector3f;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import java.util.*;

public final class FlanAdapter extends NativeMagazineAdapter {
    private ItemGun gun(ItemStack stack) { return (ItemGun)stack.getItem(); }
    private GunType type(ItemStack stack) { return gun(stack).GetType(); }
    private int slots(ItemStack stack) { return Math.max(1,Math.min(64,type(stack).numAmmoItemsInGun)); }
    private ItemStack magazine(ItemStack stack,int slot) { return gun(stack).getBulletItemStack(stack,slot); }
    private int rounds(ItemStack ammo) { return ammo.isEmpty()?0:Math.max(0,ammo.getMaxDamage()-ammo.getItemDamage()); }
    private ItemStack defaultMagazine(ItemStack weapon) {
        for(ShootableType ammo:type(weapon).ammo)if(ammo.item instanceof ItemShootable && ammo.roundsPerItem>0)return new ItemStack(ammo.item);
        return ItemStack.EMPTY;
    }
    private ItemStack loadedMagazine(ItemStack weapon) {
        for(int i=0;i<slots(weapon);i++) {
            ItemStack mag=magazine(weapon,i);if(rounds(mag)>0)return mag;
        }
        return ItemStack.EMPTY;
    }
    @Override public String id() { return "flansmod"; }
    @Override public boolean matches(ItemStack stack) { return stack!=null && !stack.isEmpty() && stack.getItem() instanceof ItemGun && type(stack)!=null; }
    @Override public String weaponId(ItemStack stack) { return "flansmod:"+type(stack).shortName; }
    @Override public String ammoKey(ItemStack stack) { return "flansmod:"+type(stack).shortName; }
    @Override public boolean matchesAmmo(ItemStack weapon,ItemStack ammo) {
        return !ammo.isEmpty() && ammo.getItem() instanceof ItemShootable && type(weapon).isCorrectAmmo(((ItemShootable)ammo.getItem()).type);
    }
    @Override public int ammoUnits(ItemStack weapon,ItemStack ammo) {
        return matchesAmmo(weapon,ammo)?(int)Math.min(100001L,(long)rounds(ammo)*ammo.getCount()):0;
    }
    @Override public int extractAmmo(ItemStack weapon,ItemStack ammo,int amount) {
        if(!matchesAmmo(weapon,ammo))return 0;
        int per=rounds(ammo);
        if(per==0)return 0;
        int whole=Math.min(ammo.getCount(),Math.max(0,amount)/per);
        int count=whole*per;ammo.shrink(whole);
        if(ammo.getCount()==1 && count<amount) {
            int part=Math.min(amount-count,per);ammo.setItemDamage(ammo.getItemDamage()+part);count+=part;
        }
        return count;
    }
    @Override public int loadedRounds(ItemStack stack) {
        ItemStack copy=stack.copy();int rounds=0;
        for(int i=0;i<slots(copy);i++)rounds+=rounds(magazine(copy,i));return rounds;
    }
    @Override public int magazineCapacity(ItemStack stack) {
        ItemStack copy=stack.copy(),fallback=defaultMagazine(stack);int capacity=0;
        for(int i=0;i<slots(stack);i++) {
            ItemStack mag=magazine(copy,i);capacity+=Math.max(1,(mag.isEmpty()?fallback:mag).getMaxDamage());
        }
        return Math.max(1,capacity);
    }
    @Override protected void addLoaded(ItemStack stack,int rounds) {} // Native Reload already writes the native magazine compounds.
    @Override protected int loadRounds(CombatContext ctx,int amount) {
        int before=loadedRounds(ctx.weapon);ItemGun gun=gun(ctx.weapon);
        gun.Reload(ctx.weapon,ctx.npc.world,ctx.npc,new NativeAmmoInventory(new AmmoInventory(ctx.npc)),EnumHand.MAIN_HAND,false,type(ctx.weapon).canForceReload,false);
        int left=Math.max(0,amount-(loadedRounds(ctx.weapon)-before));
        for(int i=0;i<slots(ctx.weapon) && left>0;i++) {
            ItemStack mag=magazine(ctx.weapon,i);
            if(mag.isEmpty()){mag=defaultMagazine(ctx.weapon);if(mag.isEmpty())break;mag.setItemDamage(mag.getMaxDamage());}
            int missing=Math.max(0,mag.getMaxDamage()-rounds(mag));
            int loaded=ctx.data.ammo.takeReserve(ctx,ammoKey(ctx.weapon),Math.min(left,missing));
            if(loaded>0){mag.setItemDamage(mag.getItemDamage()-loaded);gun.setBulletItemStack(ctx.weapon,mag,i);left-=loaded;}
        }
        return Math.max(0,loadedRounds(ctx.weapon)-before);
    }
    @Override protected void consumeLoaded(ItemStack stack) {
        for(int i=0;i<slots(stack);i++) {
            ItemStack mag=magazine(stack,i);
            if(rounds(mag)>0){mag.setItemDamage(mag.getItemDamage()+1);gun(stack).setBulletItemStack(stack,mag,i);return;}
        }
    }
    @Override public int baseInterval(ItemStack stack) { return Math.max(1,(int)Math.ceil(type(stack).GetShootDelay(stack))); }
    @Override public double projectileSpeed(CombatContext ctx) {
        ItemStack ammo=loadedMagazine(ctx.weapon);
        if(ammo.isEmpty() && infinite(ctx))ammo=defaultMagazine(ctx.weapon);
        // EntityBullet divides the configured speed by five; zero uses Flan's hitscan path.
        return ammo.getItem() instanceof ItemShootable && ((ItemShootable)ammo.getItem()).type instanceof BulletType
            ? type(ctx.weapon).getBulletSpeed(ctx.weapon)/5.0 : 0;
    }
    @Override public Double recoil(ItemStack stack) { return (double)type(stack).getRecoil(stack); }
    @Override public Double spread(ItemStack stack) { return (double)type(stack).getSpread(stack); }
    @Override protected int nativeReloadTicks(ItemStack stack) { return Math.max(1,(int)Math.ceil(type(stack).getReloadTime(stack))); }
    @Override public Views.GunCaps capabilities(ItemStack stack) {
        Views.GunCaps c=new Views.GunCaps();
        c.canFire=c.canControlFireRate=c.canOverrideNativeRate=c.hasMagazine=c.canReload=c.canReadAmmo=c.canConsumeAmmo=c.hasReserveAmmo=true;
        c.supportsPartialReload=c.hasAimState=c.canAim=true;c.hasRecoil=c.canControlRecoil=type(stack).getRecoil(stack)>0;
        c.hasSpread=c.canControlSpread=c.canControlSpreadLimit=true;
        c.supportsSingleShot=c.supportsAutomaticFire=c.supportsBurst=true;
        c.firingModes=new String[]{"NATIVE","SINGLE","SEMI","AUTO","BURST","CUSTOM"};c.projectileModel="INTERNAL";return c;
    }
    @Override protected Result shoot(CombatContext ctx) {
        GunType type=type(ctx.weapon);ItemStack ammo=loadedMagazine(ctx.weapon);
        if(ammo.isEmpty() && infinite(ctx)){ammo=defaultMagazine(ctx.weapon);if(!ammo.isEmpty())gun(ctx.weapon).setBulletItemStack(ctx.weapon,ammo,0);}
        if(ammo.isEmpty())return Result.fail("NO_AMMO");
        ShootableType shootable=((ItemShootable)ammo.getItem()).type;
        if(shootable instanceof BulletType) {
            float spread=(float)Math.min(ctx.config.number("gun.maxSpread"),type.getSpread(ctx.weapon)*ctx.spreadMultiplier());
            FireableGun fireable=new FireableGun(type,type.getDamage(ctx.weapon),spread,type.getBulletSpeed(ctx.weapon),type.getSpreadPattern(ctx.weapon));
            FiredShot shot=new FiredShot(fireable,(BulletType)shootable,ctx.npc);
            ShotHandler.fireGun(ctx.npc.world,shot,Math.min(64,Math.max(1,type.numBullets*shootable.numBullets)),
                new Vector3f(ctx.npc.getPositionEyes(1)),new Vector3f(ctx.shotDirection()));
        } else if(shootable instanceof GrenadeType) {
            if(!ctx.npc.world.spawnEntity(new EntityGrenade(ctx.npc,(GrenadeType)shootable)))return Result.fail("INTERNAL_ERROR");
        } else return Result.fail("UNSUPPORTED");
        boolean silenced=type.getBarrel(ctx.weapon)!=null && type.getBarrel(ctx.weapon).silencer;
        gun(ctx.weapon).playShotSound(ctx.npc.world,new Vector3f(ctx.npc.getPositionEyes(1)),silenced);
        return Result.success();
    }
}
