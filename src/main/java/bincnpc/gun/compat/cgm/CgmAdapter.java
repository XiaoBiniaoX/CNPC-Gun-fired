package bincnpc.gun.compat.cgm;

import bincnpc.gun.common.api.*;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.legacy.Platform;
import bincnpc.gun.gun.adapter.NativeMagazineAdapter;
import com.mrcrayfish.guns.object.Gun;
import com.mrcrayfish.guns.item.*;
import com.mrcrayfish.guns.common.ProjectileFactory;
import com.mrcrayfish.guns.entity.EntityProjectile;
import com.mrcrayfish.guns.init.ModSounds;
import com.mrcrayfish.guns.network.PacketHandler;
import com.mrcrayfish.guns.network.message.MessageBullet;
import net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;

public final class CgmAdapter extends NativeMagazineAdapter {
    private Gun gun(ItemStack stack) { return ((ItemGun)stack.getItem()).getModifiedGun(stack); }
    @Override public String id() { return "cgm"; }
    @Override public java.util.List<ItemStack> catalogueItems() {
        java.util.List<ItemStack> result = new java.util.ArrayList<>();
        for(net.minecraft.item.Item item:net.minecraft.item.Item.REGISTRY) if(item instanceof ItemGun) {
            ItemStack stack=new ItemStack(item);
            Platform.tag(stack).setInteger("AmmoCount",magazineCapacity(stack));
            result.add(stack);
        }
        return result;
    }
    @Override public boolean matches(ItemStack stack) { return stack != null && !stack.isEmpty() && stack.getItem() instanceof ItemGun && gun(stack) != null; }
    @Override public String ammoKey(ItemStack stack) { return "cgm:" + gun(stack).projectile.item; }
    @Override public boolean matchesAmmo(ItemStack weapon, ItemStack ammo) { return !ammo.isEmpty() && java.util.Objects.equals(ammo.getItem().getRegistryName(), gun(weapon).projectile.item); }
    @Override protected boolean infinite(ItemStack stack) { return stack.hasTagCompound() && stack.getTagCompound().getBoolean("IgnoreAmmo"); }
    @Override public int loadedRounds(ItemStack stack) { return stack.hasTagCompound() ? Math.max(0, stack.getTagCompound().getInteger("AmmoCount")) : 0; }
    @Override public int magazineCapacity(ItemStack stack) { Gun gun = gun(stack); return Math.max(1, gun.general.getMaxAmmo(gun)); }
    @Override protected void addLoaded(ItemStack stack, int rounds) { Platform.tag(stack).setInteger("AmmoCount", Math.min(magazineCapacity(stack), loadedRounds(stack) + rounds)); }
    @Override protected void consumeLoaded(ItemStack stack) { Platform.tag(stack).setInteger("AmmoCount", Math.max(0, loadedRounds(stack) - 1)); }
    @Override public int baseInterval(ItemStack stack) { return Math.max(1, gun(stack).general.rate); }
    @Override public Double spread(ItemStack stack) { return (double)gun(stack).general.spread; }
    @Override public Double recoil(ItemStack stack) { return (double)gun(stack).general.recoilAngle; }
    @Override protected int loadBatch(ItemStack stack) { return Math.max(1, gun(stack).general.reloadSpeed); }
    @Override protected int nativeReloadTicks(ItemStack stack) { return Math.max(10, 10 * bincnpc.gun.gun.controller.ReloadTiming.steps(magazineCapacity(stack) - loadedRounds(stack), loadBatch(stack))); }
    @Override public Views.GunCaps capabilities(ItemStack stack) {
        Gun gun = gun(stack); Views.GunCaps c = new Views.GunCaps();
        c.canFire = c.canControlFireRate = c.canOverrideNativeRate = true;
        c.hasMagazine = true; c.canReadAmmo = c.canConsumeAmmo = c.canReload = c.hasReserveAmmo = !infinite(stack);
        c.supportsPartialReload = true; c.hasRecoil = gun.general.recoilAngle > 0 || gun.general.recoilKick > 0;
        c.canControlRecoil = c.hasRecoil; c.hasSpread = gun.general.spread > 0;
        c.canControlSpread = c.canControlSpreadLimit = c.hasSpread;
        c.hasAimState = c.canAim = true; c.supportsBurst = c.supportsAutomaticFire = c.supportsSingleShot = true;
        c.firingModes = new String[]{"NATIVE", "SINGLE", "SEMI", "AUTO", "BURST", "CUSTOM"};
        c.projectileModel = "CUSTOM_PROJECTILE"; return c;
    }
    @Override public double projectileSpeed(CombatContext ctx) { return gun(ctx.weapon).projectile.speed; }
    @Override protected Result shoot(CombatContext ctx) {
        Gun gun = gun(ctx.weapon).copy();
        gun.general.spread = (float)Math.min(ctx.config.number("gun.maxSpread"), gun.general.spread * ctx.spreadMultiplier());
        ProjectileFactory factory = AmmoRegistry.getInstance().getFactory(gun.projectile.item);
        int spawned = 0;
        for (int i = 0, count = Math.min(64, Math.max(1, gun.general.projectileAmount)); i < count; i++) {
            EntityProjectile bullet = factory.create(ctx.npc.world, ctx.npc, (ItemGun)ctx.weapon.getItem(), gun);
            if (bullet == null) continue;
            bullet.setWeapon(ctx.weapon); bullet.setAdditionalDamage(Platform.tag(ctx.weapon).getFloat("AdditionalDamage"));
            if (!ctx.npc.world.spawnEntity(bullet)) continue;
            spawned++;
            if (!gun.projectile.visible) PacketHandler.INSTANCE.sendToAllAround(new MessageBullet(bullet.getEntityId(),
                bullet.posX, bullet.posY, bullet.posZ, bullet.motionX, bullet.motionY, bullet.motionZ,
                gun.projectile.trailColor, gun.projectile.trailLengthMultiplier),
                new TargetPoint(ctx.npc.dimension, ctx.npc.posX, ctx.npc.posY, ctx.npc.posZ, 96));
        }
        if (spawned == 0) return Result.fail("INTERNAL_ERROR");
        sound(ctx, gun.sounds.getFire(gun), 5); return Result.success();
    }
    @Override protected void reloadSound(CombatContext ctx) { Gun gun = gun(ctx.weapon); sound(ctx, gun.sounds.getReload(gun), 1); }
    private void sound(CombatContext ctx, String name, float volume) {
        if (name == null || name.isEmpty()) return;
        SoundEvent event = ModSounds.getSound(name);
        if (event == null) event = SoundEvent.REGISTRY.getObject(new ResourceLocation(name));
        if (event != null) ctx.npc.world.playSound(null, ctx.npc.posX, Platform.eyeY(ctx.npc), ctx.npc.posZ, event, SoundCategory.HOSTILE, volume, 1);
    }
}
