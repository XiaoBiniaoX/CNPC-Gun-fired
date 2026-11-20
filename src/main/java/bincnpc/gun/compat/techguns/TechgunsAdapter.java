package bincnpc.gun.compat.techguns;

import bincnpc.gun.common.api.*;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.legacy.Platform;
import bincnpc.gun.gun.adapter.NativeMagazineAdapter;
import bincnpc.gun.gun.ammo.AmmoInventory;
import bincnpc.gun.mixin.compat.TechgunsGunAccess;
import techguns.items.guns.*;
import techguns.entities.projectiles.EnumBulletFirePos;
import techguns.entities.projectiles.LaserProjectile;
import techguns.entities.projectiles.NDRProjectile;
import techguns.entities.projectiles.TeslaProjectile;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import java.util.*;

public final class TechgunsAdapter extends NativeMagazineAdapter {
    private GenericGun gun(ItemStack stack) { return (GenericGun)stack.getItem(); }
    private TechgunsGunAccess access(ItemStack stack) { return (TechgunsGunAccess)stack.getItem(); }
    @Override public String id() { return "techguns"; }
    @Override public List<ItemStack> catalogueItems() { return TechgunsCreativeItems.items(); }
    @Override public boolean matches(ItemStack stack) { return stack != null && !stack.isEmpty() && stack.getItem() instanceof GenericGun; }
    @Override public String ammoKey(ItemStack stack) { return "techguns:" + stack.getItem().getRegistryName() + ":" + gun(stack).getCurrentAmmoVariantKey(stack.copy()); }
    @Override public int loadedRounds(ItemStack stack) { return Math.max(0, gun(stack).getCurrentAmmo(stack.copy())); }
    @Override public int magazineCapacity(ItemStack stack) { return Math.max(1, gun(stack).getClipsize()); }
    @Override protected void addLoaded(ItemStack stack, int count) { gun(stack).reloadAmmo(stack, Math.min(count, magazineCapacity(stack) - loadedRounds(stack))); }
    @Override protected void consumeLoaded(ItemStack stack) { if (!(stack.getItem() instanceof GenericGunCharge)) gun(stack).useAmmo(stack, 1); }
    @Override public int baseInterval(ItemStack stack) { return Math.max(1, access(stack).cnpcgun$fireTicks()); }
    @Override public Double spread(ItemStack stack) { return (double)access(stack).cnpcgun$accuracy(); }
    @Override protected int nativeReloadTicks(ItemStack stack) { return Math.max(1, access(stack).cnpcgun$reloadTicks()); }
    @Override protected int chargeTicks(ItemStack stack) { return stack.getItem() instanceof GenericGunCharge ? Math.max(1, Math.min(200, (int)Math.ceil(((GenericGunCharge)stack.getItem()).fullChargeTime))) : 0; }
    private int batchRounds(ItemStack weapon) { GenericGun gun=gun(weapon); return Math.max(1, (int)Math.ceil(gun.getAmmoType().getShotsPerBullet(gun.getClipsize(),gun.getAmmoCount()))); }
    private ItemStack[] recipe(ItemStack weapon) { return gun(weapon).getReloadItem(weapon.copy()); }
    @Override public boolean matchesAmmo(ItemStack weapon, ItemStack item) {
        if (item.isEmpty()) return false;
        for (ItemStack ingredient : recipe(weapon)) if (ItemStack.areItemsEqual(ingredient,item)) return true;
        return false;
    }
    @Override public int ammoUnits(ItemStack weapon, ItemStack item) {
        ItemStack[] recipe=recipe(weapon);
        return recipe.length==1 && matchesAmmo(weapon,item)
            ? (int)Math.min(100001L,(long)item.getCount()*batchRounds(weapon)/Math.max(1,recipe[0].getCount())) : 0;
    }
    @Override public int extractAmmo(ItemStack weapon, ItemStack item, int count) { return 0; } // Composite recipes are consumed atomically below.
    private Map<Integer,Integer> plan(AmmoInventory inventory,ItemStack[] recipe) {
        Map<Integer,Integer> plan=new LinkedHashMap<>();
        for(ItemStack ingredient:recipe) {
            int left=Math.max(1,ingredient.getCount());
            for(int i=0;i<inventory.getSlots() && left>0;i++) {
                ItemStack item=inventory.getStackInSlot(i);
                if(!item.isEmpty() && ItemStack.areItemsEqual(ingredient,item)) {
                    int take=Math.min(left,Math.max(0,item.getCount()-plan.getOrDefault(i,0)));
                    if(take>0){plan.put(i,plan.getOrDefault(i,0)+take);left-=take;}
                }
            }
            if(left>0)return Collections.emptyMap();
        }
        return plan;
    }
    @Override protected int reserveAvailable(CombatContext ctx) {
        if(ctx.config.bool("ammo.infinite"))return 100000;
        int reserve=Math.max(0,ctx.data.ammo.independent(ctx,this,ctx.weapon)-ctx.config.integer("ammo.minimumReserve"));
        AmmoInventory inventory=new AmmoInventory(ctx.npc);
        ItemStack[] recipe=recipe(ctx.weapon); int batches=Integer.MAX_VALUE;
        List<ItemStack> ingredients=new ArrayList<>();
        for(ItemStack ingredient:recipe) {
            if(ingredient.isEmpty())continue;
            ItemStack same=null;
            for(ItemStack entry:ingredients)if(ItemStack.areItemsEqual(entry,ingredient)){same=entry;break;}
            if(same==null){same=ingredient.copy();same.setCount(Math.max(1,ingredient.getCount()));ingredients.add(same);}
            else same.grow(Math.max(1,ingredient.getCount()));
        }
        for(ItemStack ingredient:ingredients) {
            int amount=0;
            for(int i=0;i<inventory.getSlots();i++)if(ItemStack.areItemsEqual(ingredient,inventory.getStackInSlot(i)))amount+=inventory.getStackInSlot(i).getCount();
            batches=Math.min(batches,amount/Math.max(1,ingredient.getCount()));
        }
        if(ingredients.isEmpty() || batches==Integer.MAX_VALUE)batches=0;
        return (int)Math.min(100000L,(long)reserve+(long)batches*batchRounds(ctx.weapon));
    }
    @Override protected int loadRounds(CombatContext ctx,int requested) {
        int loaded=ctx.data.ammo.takeReserve(ctx,ammoKey(ctx.weapon),requested);
        AmmoInventory inventory=new AmmoInventory(ctx.npc);
        int batch=batchRounds(ctx.weapon);
        while(loaded<requested) {
            Map<Integer,Integer> plan=plan(inventory,recipe(ctx.weapon));
            if(plan.isEmpty())break;
            int need=Math.min(batch,requested-loaded), excess=batch-need;
            String key=ctx.data.ammo.key(ctx,this,ctx.weapon);
            if(key==null || excess>ctx.data.ammo.creditRoom(ctx,this,ctx.weapon))break;
            if(excess>0 && ctx.data.ammo.credit(ctx,this,ctx.weapon,excess)!=excess)break;
            plan.forEach((slot,amount)->inventory.extractItem(slot,amount,false));
            loaded+=need;
            for(ItemStack empty:gun(ctx.weapon).getAmmoType().getEmptyMag())if(!empty.isEmpty()) {
                ItemStack leftover=empty.copy();
                for(int i=0;i<21&&!leftover.isEmpty();i++)leftover=inventory.insertItem(i,leftover,false);
                if(!leftover.isEmpty())ctx.npc.entityDropItem(leftover,0.5F);
            }
        }
        return loaded;
    }
    @Override public Views.GunCaps capabilities(ItemStack stack) {
        Views.GunCaps c=new Views.GunCaps();
        c.canFire=c.canControlFireRate=c.canOverrideNativeRate=c.hasMagazine=c.canReload=c.canReadAmmo=c.canConsumeAmmo=c.hasReserveAmmo=true;
        c.supportsPartialReload=c.hasAimState=c.canAim=true;
        c.hasSpread=c.canControlSpread=c.canControlSpreadLimit=true;
        c.supportsSingleShot=c.supportsAutomaticFire=c.supportsBurst=true;
        c.firingModes=new String[]{"NATIVE","SINGLE","SEMI","AUTO","BURST","CUSTOM"};c.projectileModel="INTERNAL";
        c.supportsSpecialMechanics=stack.getItem() instanceof GenericGunCharge;
        c.specialMechanics=c.supportsSpecialMechanics?new String[]{"CHARGE"}:new String[0];return c;
    }
    @Override public double projectileSpeed(CombatContext ctx) {
        GenericGun gun=gun(ctx.weapon);
        if(gun instanceof GenericGunCharge)return 0;
        TechgunsGunAccess access=access(ctx.weapon);
        ProjectileSelector<?> projectiles=access.cnpcgun$projectiles();
        if(projectiles==null)return 0;
        IProjectileFactory<?> factory=projectiles.getFactoryForType(gun.getCurrentAmmoVariantKey(ctx.weapon));
        if(factory==null || factory instanceof LaserProjectile.Factory || factory instanceof NDRProjectile.Factory
                || factory instanceof TeslaProjectile.Factory)return 0;
        return factory.getDamageModifier().getVelocity(access.cnpcgun$projectileSpeed());
    }
    @Override protected Result shoot(CombatContext ctx) {
        GenericGun gun=gun(ctx.weapon);
        float spread=(float)Math.min(ctx.config.number("gun.maxSpread"),ctx.spreadMultiplier());
        if(gun instanceof GenericGunCharge) {
            GenericGunCharge charged=(GenericGunCharge)gun;
            float charge=infinite(ctx)?1:Math.min(1,(float)loadedRounds(ctx.weapon)/Math.max(1,charged.ammoConsumedOnFullCharge));
            int used=charged.consumeAmmoCharge(ctx.weapon,charge,infinite(ctx));
            if(used<=0 && !infinite(ctx))return Result.fail("NO_AMMO");
            charged.spawnChargedProjectile(ctx.npc.world,ctx.npc,ctx.weapon,access(ctx.weapon).cnpcgun$accuracy()*spread,charge,used,EnumBulletFirePos.RIGHT);
            SoundEvent sound=access(ctx.weapon).cnpcgun$fireSound();
            if(sound!=null)ctx.npc.world.playSound(null,ctx.npc.getPosition(),sound,SoundCategory.HOSTILE,4,1);
        } else gun.fireWeaponFromNPC(ctx.npc,1,spread);
        return Result.success();
    }
    @Override protected void reloadSound(CombatContext ctx) {
        SoundEvent sound=access(ctx.weapon).cnpcgun$reloadSound();
        if(sound!=null)ctx.npc.world.playSound(null,ctx.npc.getPosition(),sound,SoundCategory.HOSTILE,1,1);
    }
}
