package bincnpc.gun.compat.cgm;

import bincnpc.gun.common.api.*;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.gun.adapter.GunAdapter;
import bincnpc.gun.gun.controller.ReloadTiming;
import com.mrcrayfish.guns.common.*;
import com.mrcrayfish.guns.item.GunItem;
import com.mrcrayfish.guns.entity.ProjectileEntity;
import com.mrcrayfish.guns.util.*;
import com.mrcrayfish.guns.network.PacketHandler;
import com.mrcrayfish.guns.network.message.*;
import com.mrcrayfish.framework.api.network.LevelLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class CgmAdapter implements GunAdapter {
    private static final class State {
        long nextLoad, reloadStart;
        int reloadDuration, reloadSteps, loadedSteps;
        boolean reloading, aim;
    }
    private State state(CombatContext ctx) { return (State)ctx.runtime.adapterState.computeIfAbsent(id(), key -> new State()); }
    private Gun gun(ItemStack stack) { return ((GunItem)stack.getItem()).getModifiedGun(stack); }
    @Override public String id() { return "cgm"; }
    @Override public boolean matches(ItemStack stack) { return stack != null && stack.getItem() instanceof GunItem && gun(stack) != null; }
    @Override public String ammoKey(ItemStack stack) { return "cgm:" + gun(stack).getProjectile().getItem(); }
    @Override public boolean matchesAmmo(ItemStack weapon, ItemStack item) { return Gun.isAmmo(item, gun(weapon).getProjectile().getItem()); }
    private boolean infinite(ItemStack stack) { return stack.hasTag() && stack.getTag().getBoolean("IgnoreAmmo"); }
    private boolean infinite(CombatContext ctx) { return infinite(ctx.weapon) || ctx.config.bool("ammo.infinite"); }
    @Override public int loadedRounds(ItemStack stack) { return stack.hasTag() ? stack.getTag().getInt("AmmoCount") : 0; }
    @Override public int magazineCapacity(ItemStack stack) { return GunEnchantmentHelper.getAmmoCapacity(stack, gun(stack)); }
    @Override public boolean canUse(CombatContext ctx, ItemStack stack) {
        return ctx.config.bool("ammo.infinite") || infinite(stack) || stack.hasTag() && stack.getTag().getInt("AmmoCount") > 0 || ctx.data.ammo.available(ctx, this, stack) > 0;
    }
    @Override public Views.GunCaps capabilities(ItemStack stack) {
        Views.GunCaps c = new Views.GunCaps();
        Gun nativeGun = gun(stack);
        c.canFire = true; c.canControlFireRate = true; c.canOverrideNativeRate = true;
        c.hasMagazine = true; c.canReadAmmo = !infinite(stack); c.canConsumeAmmo = !infinite(stack);
        c.canReload = !infinite(stack); c.hasReserveAmmo = !infinite(stack); c.supportsPartialReload = true;
        c.hasRecoil = nativeGun.getGeneral().getRecoilAngle() > 0 || nativeGun.getGeneral().getRecoilKick() > 0;
        c.canControlRecoil = c.hasRecoil;
        c.hasSpread = nativeGun.getGeneral().getSpread() > 0; c.canControlSpread = c.hasSpread; c.canControlSpreadLimit = c.hasSpread;
        c.hasAimState = nativeGun.canAimDownSight(); c.canAim = c.hasAimState;
        c.supportsBurst = true; c.supportsAutomaticFire = true; c.supportsSingleShot = true;
        c.firingModes = new String[]{"NATIVE", "SINGLE", "SEMI", "AUTO", "BURST", "CUSTOM"};
        c.projectileModel = "CUSTOM_PROJECTILE";
        return c.withNpcControls();
    }
    @Override public Views.AmmoState ammo(CombatContext ctx) {
        Gun nativeGun = gun(ctx.weapon); boolean infinite = infinite(ctx);
        int count = ctx.weapon.getOrCreateTag().getInt("AmmoCount");
        int cap = GunEnchantmentHelper.getAmmoCapacity(ctx.weapon, nativeGun);
        int reserve = ctx.data.ammo.available(ctx, this);
        return new Views.AmmoState(true, infinite ? null : count, cap, !infinite, infinite ? null : reserve,
            state(ctx).reloading, !infinite && !state(ctx).reloading && count < cap && reserve > 0);
    }
    @Override public int baseInterval(ItemStack stack) { return Math.max(1, GunEnchantmentHelper.getRate(stack, gun(stack))); }
    @Override public Double spread(ItemStack stack) { return (double)gun(stack).getGeneral().getSpread(); }
    @Override public Double recoil(ItemStack stack) { return (double)gun(stack).getGeneral().getRecoilAngle(); }
    @Override public Result aim(CombatContext ctx, boolean aiming) {
        if (!gun(ctx.weapon).canAimDownSight()) return Result.fail("UNSUPPORTED");
        state(ctx).aim = aiming; return Result.success();
    }
    @Override public Object aimState(CombatContext ctx) { return state(ctx).aim; }
    @Override public Result fire(CombatContext ctx) {
        State s = state(ctx);
        if (s.reloading) return Result.fail("BUSY");
        if (!infinite(ctx) && ctx.weapon.getOrCreateTag().getInt("AmmoCount") <= 0) return Result.fail("NO_AMMO");
        Gun nativeGun = gun(ctx.weapon).copy();
        CompoundTag general = nativeGun.getGeneral().serializeNBT();
        general.putFloat("Spread", (float)Math.min(ctx.config.number("gun.maxSpread"), nativeGun.getGeneral().getSpread() * ctx.spreadMultiplier()));
        nativeGun.getGeneral().deserializeNBT(general);
        int count = Math.min(64, Math.max(1, nativeGun.getGeneral().getProjectileAmount()));
        List<ProjectileEntity> projectiles = new ArrayList<>();
        var factory = ProjectileManager.getInstance().getFactory(nativeGun.getProjectile().getItem());
        if (factory == null) return Result.fail("UNSUPPORTED");
        for (int i = 0; i < count; i++) {
            ProjectileEntity projectile = factory.create(ctx.npc.level(), ctx.npc, ctx.weapon, (GunItem)ctx.weapon.getItem(), nativeGun);
            if (projectile == null) continue;
            projectile.setWeapon(ctx.weapon); projectile.setAdditionalDamage(Gun.getAdditionalDamage(ctx.weapon));
            if (!ctx.npc.level().addFreshEntity(projectile)) continue;
            projectiles.add(projectile);
            if (projectiles.size() == 1) commitShot(ctx);
            projectile.tick();
        }
        if (projectiles.isEmpty()) return Result.fail("INTERNAL_ERROR");
        if (!nativeGun.getProjectile().isVisible()) {
            S2CMessageBulletTrail trail = new S2CMessageBulletTrail(projectiles.toArray(ProjectileEntity[]::new), nativeGun.getProjectile(), ctx.npc.getId(), GunEnchantmentHelper.getParticle(ctx.weapon));
            PacketHandler.getPlayChannel().sendToNearbyPlayers(() -> LevelLocation.create(ctx.npc.level(), ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ(), 96), trail);
        }
        sound(ctx, nativeGun, false);
        return Result.success();
    }
    private void commitShot(CombatContext ctx) {
        if (!infinite(ctx)) {
            int level = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(com.mrcrayfish.guns.init.ModEnchantments.RECLAIMED.get(), ctx.weapon);
            if (level == 0 || ctx.npc.getRandom().nextInt(4 - Math.max(1, Math.min(2, level))) != 0)
                ctx.weapon.getOrCreateTag().putInt("AmmoCount", Math.max(0, ctx.weapon.getOrCreateTag().getInt("AmmoCount") - 1));
        }
        // GunController owns the shot deadline, including the native rate when no override is enabled.
        ctx.npc.updateClient = true;
    }
    private void sound(CombatContext ctx, Gun gun, boolean reload) {
        ResourceLocation sound = reload ? gun.getSounds().getReload() : GunModifierHelper.isSilencedFire(ctx.weapon) ? gun.getSounds().getSilencedFire() : gun.getSounds().getFire();
        if (sound == null) return;
        S2CMessageGunSound packet = new S2CMessageGunSound(sound, SoundSource.HOSTILE,
            (float)ctx.npc.getX(), (float)ctx.npc.getEyeY(), (float)ctx.npc.getZ(),
            reload ? 1 : GunModifierHelper.getFireSoundVolume(ctx.weapon), 1, ctx.npc.getId(), !reload && gun.getDisplay().getFlash() != null, reload);
        PacketHandler.getPlayChannel().sendToNearbyPlayers(() -> LevelLocation.create(ctx.npc.level(), ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ(), 96), packet);
    }
    @Override public Result reload(CombatContext ctx) {
        if (infinite(ctx)) return Result.fail("UNSUPPORTED");
        State s = state(ctx);
        if (s.reloading) return Result.fail("BUSY");
        if (ctx.data.ammo.available(ctx, this) <= 0) return Result.fail("NO_AMMO");
        if (ctx.weapon.getOrCreateTag().getInt("AmmoCount") >= GunEnchantmentHelper.getAmmoCapacity(ctx.weapon, gun(ctx.weapon))) return Result.fail("CONDITION_NOT_MET");
        int missing = GunEnchantmentHelper.getAmmoCapacity(ctx.weapon, gun(ctx.weapon)) - ctx.weapon.getOrCreateTag().getInt("AmmoCount");
        s.reloadSteps = ReloadTiming.steps(Math.min(missing, ctx.data.ammo.available(ctx, this)), Math.max(1, gun(ctx.weapon).getGeneral().getReloadAmount()));
        s.reloadStart = ctx.now; s.reloadDuration = ctx.runtime.reloadTargetTicks; s.loadedSteps = 0;
        s.reloading = true; s.nextLoad = s.reloadDuration > 0 ? ReloadTiming.stepAt(ctx.now, s.reloadDuration, 1, s.reloadSteps)
            : ctx.now + Math.max(1, GunEnchantmentHelper.getReloadInterval(ctx.weapon));
        sound(ctx, gun(ctx.weapon), true); return Result.success();
    }
    @Override public void tick(CombatContext ctx) {
        State s = state(ctx);
        if (!s.reloading || ctx.now < s.nextLoad) return;
        Gun nativeGun = gun(ctx.weapon);
        int cap = GunEnchantmentHelper.getAmmoCapacity(ctx.weapon, nativeGun), count = ctx.weapon.getOrCreateTag().getInt("AmmoCount");
        int dueSteps = 1;
        if (s.reloadDuration > 0) {
            int dueTotal = (int)Math.min(s.reloadSteps, Math.max(0, ctx.now - s.reloadStart) * s.reloadSteps / s.reloadDuration);
            dueSteps = Math.max(1, dueTotal - s.loadedSteps);
        }
        int toLoad = (int)Math.min(Math.max(0, cap - count), (long)Math.max(1, nativeGun.getGeneral().getReloadAmount()) * dueSteps);
        int loaded = ctx.data.ammo.take(ctx, this, toLoad);
        ctx.weapon.getOrCreateTag().putInt("AmmoCount", count + loaded);
        ctx.npc.updateClient = true;
        if (loaded == 0 || count + loaded >= cap || ctx.data.ammo.available(ctx, this) <= 0) s.reloading = false;
        else if (s.reloadDuration > 0) {
            s.loadedSteps += dueSteps;
            if (s.loadedSteps >= s.reloadSteps) s.reloading = false;
            else s.nextLoad = ReloadTiming.stepAt(s.reloadStart, s.reloadDuration, s.loadedSteps + 1, s.reloadSteps);
        } else s.nextLoad = ctx.now + Math.max(1, GunEnchantmentHelper.getReloadInterval(ctx.weapon));
    }
    @Override public void cancel(CombatContext ctx) { state(ctx).reloading = false; }
}
