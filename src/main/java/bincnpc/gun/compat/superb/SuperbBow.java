package bincnpc.gun.compat.superb;

import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatService;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.entity.projectile.ProjectileEntity;
import com.atsuishio.superbwarfare.init.ModPerks;
import com.atsuishio.superbwarfare.init.ModSounds;
import com.atsuishio.superbwarfare.item.gun.special.BocekItem;
import com.atsuishio.superbwarfare.perk.AmmoPerk;
import com.atsuishio.superbwarfare.perk.Perk;
import com.atsuishio.superbwarfare.perk.PerkInstance;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.api.wrapper.ItemStackWrapper;

/** NPC bridge for BOCEK's player-only fire-key release; never creates a surrogate player. */
final class SuperbBow {
    private static final String DRAW = "superb.bocekDraw";
    private SuperbBow() {}
    static boolean matches(GunData gun) { return gun.item instanceof BocekItem; }
    private static BocekDrawState draw(CombatContext ctx) {
        return ctx.runtime.adapterState.get(DRAW) instanceof BocekDrawState state ? state : null;
    }
    static void cancel(CombatContext ctx) {
        if (ctx.runtime.adapterState.remove(DRAW) instanceof BocekDrawState state) state.cancel();
    }
    static void clearWrongCharge(GunData gun) {
        if (!matches(gun)) return;
        // GunData.charge belongs to Sentinel, not to BOCEK's bowPower. Remove old adapter leftovers.
        gun.charge.starter.finish(); gun.charge.timer.set(0); gun.save();
    }
    private static boolean allowed(CombatContext ctx, GunData gun) {
        if (!CombatService.serverThread(ctx.npc) || !CombatService.validNpc(ctx.npc) || !ctx.validTarget()
                || ctx.npc.isPassenger() || ctx.runtime.editorPaused || ctx.runtime.fireStopped
                || !ctx.config.bool("combat.enabled") || !ctx.config.bool("gun.enabled")
                || ctx.runtime.reloading || gun.reloading()
                || ctx.runtime.movement.ammoFallback || !matches(gun) || gun.stack != ctx.weapon
                || !(ctx.runtime.adapter instanceof SuperbAdapter) || ctx.runtime.lastStack != ctx.weapon
                || ctx.npc.inventory == null || ItemStackWrapper.MCItem(ctx.npc.inventory.getRightHand()) != ctx.weapon
                || ctx.npc.getTarget() != ctx.target || ctx.now != ctx.npc.level().getGameTime()) return false;
        double distance = ctx.npc.distanceTo(ctx.target);
        if (distance > Math.min(ctx.config.number("gun.range"), ctx.config.number("combat.attackRange"))
                || distance < ctx.config.number("gun.minRange")) return false;
        if (!ctx.npc.getSensing().hasLineOfSight(ctx.target) && !ctx.canFireWithoutSight()) return false;
        return ctx.npc.onGround() || ctx.config.bool("gun.jumpFire") && ctx.runtime.gunCaps != null && ctx.runtime.gunCaps.canJumpFire;
    }
    static void tick(CombatContext ctx, GunData gun) {
        BocekDrawState state = draw(ctx);
        if (state == null) return;
        boolean allowed = allowed(ctx, gun) && gun.canShoot(ctx.npc);
        var target = ctx.target == null ? null : ctx.target.getUUID();
        boolean valid = ctx.data.throwing.pausesCharge()
            ? state.pause(ctx.now, target, ctx.config.revision, ctx.runtime.weaponEpoch, allowed)
            : state.advance(ctx.now, target, ctx.config.revision, ctx.runtime.weaponEpoch, allowed && !ctx.data.throwing.blocksAttack(ctx.now));
        if (!valid) cancel(ctx);
    }
    private static boolean validRelease(CombatContext ctx, GunData gun, BocekDrawState state) {
        return draw(ctx) == state && state.releasing() && !ctx.data.throwing.blocksAttack(ctx.now) && allowed(ctx, gun)
            && state.matches(ctx.target.getUUID(), ctx.config.revision, ctx.runtime.weaponEpoch) && gun.canShoot(ctx.npc);
    }
    static Result fire(CombatContext ctx, GunData gun) {
        if (ctx.data.throwing.pausesCharge()) return Result.fail("BUSY");
        if (!allowed(ctx, gun) || !gun.canShoot(ctx.npc) || ctx.data.throwing.blocksAttack(ctx.now)) { cancel(ctx); return Result.fail("BUSY"); }
        BocekDrawState state = draw(ctx);
        if (state != null && state.releasing()) return Result.fail("BUSY");
        if (state != null && !state.advance(ctx.now, ctx.target.getUUID(), ctx.config.revision, ctx.runtime.weaponEpoch, true)) {
            cancel(ctx); state = null;
        }
        if (state == null) {
            state = new BocekDrawState(ctx.now, ctx.target.getUUID(), ctx.config.revision, ctx.runtime.weaponEpoch);
            ctx.runtime.adapterState.put(DRAW, state);
            ctx.npc.playSound(ModSounds.BOCEK_PULL_3P.get(), 1, 1);
        }
        if (!state.release(ctx.now)) return Result.fail("BUSY");

        // BocekItem.shoot is empty. This mirrors its native onFireKeyRelease/spawnBullet at full power.
        ServerLevel level = (ServerLevel)ctx.npc.level();
        List<ProjectileEntity> projectiles = new ArrayList<>(10);
        boolean committed = false;
        try {
            boolean zoom = gun.zooming.get();
            Perk ammoPerk = gun.perk.get(Perk.Type.AMMO);
            int count = zoom || ammoPerk instanceof AmmoPerk ammo && ammo.getSlug() ? 1 : 10;
            float velocity = gun.get(GunProp.VELOCITY).floatValue();
            float damage = (float)(gun.get(GunProp.DAMAGE) / (zoom ? 1 : 10));
            float spread = (float)Math.min(ctx.config.number("gun.maxSpread"), (zoom ? 0.01 : 2.5) * ctx.spreadMultiplier());
            Vec3 direction = ctx.shotDirection();
            Vec3 position = ctx.npc.getEyePosition().add(0, -0.1, 0).subtract(direction.scale(0.1));
            for (int i = 0; i < count; i++) {
                ProjectileEntity projectile = new ProjectileEntity(level).shooter(ctx.npc)
                    .headShot(gun.get(GunProp.HEADSHOT).floatValue()).zoom(zoom)
                    .bypassArmorRate(gun.get(GunProp.BYPASSES_ARMOR).floatValue()).velocity(velocity).setGunItemId(gun.stack);
                projectiles.add(projectile);
                projectile.setExplosionDamage(gun.get(GunProp.EXPLOSION_DAMAGE).floatValue());
                projectile.setExplosionRadius(gun.get(GunProp.EXPLOSION_RADIUS).floatValue());
                for (Perk.Type type : Perk.Type.values())
                    for (PerkInstance perk : gun.perk.getInstances(type)) perk.perk().modifyProjectile(gun, perk, projectile);
                if (!validRelease(ctx, gun, state)) return Result.fail("BUSY");
                projectile.setPos(position.x, position.y, position.z);
                projectile.shoot(direction.x, direction.y, direction.z, velocity, spread);
                projectile.damage(damage);
            }
            // Native perks/entity-join events may change equipment or cancel an action. Recheck before committing.
            for (ProjectileEntity projectile : projectiles) {
                if (!validRelease(ctx, gun, state) || projectile.isRemoved() || !level.addFreshEntity(projectile))
                    return Result.fail("CONDITION_NOT_MET");
            }
            if (!validRelease(ctx, gun, state)) return Result.fail("BUSY");
            // One release consumes once, including shatter-cap volleys. Native before/afterShoot would double-consume.
            gun.ammo.set(gun.ammo.get() - gun.get(GunProp.AMMO_COST_PER_SHOOT)); gun.save();
            committed = true;
            ctx.npc.playSound(zoom ? ModSounds.BOCEK_ZOOM_FIRE_3P.get() : ModSounds.BOCEK_SHATTER_CAP_FIRE_3P.get(), 2, 1);
            if (ammoPerk == ModPerks.INSTANCE.getBEAST_BULLET().get()) ctx.npc.playSound(ModSounds.HENG.get(), 4, 1);
            return Result.success();
        } finally {
            if (!committed) for (ProjectileEntity projectile : projectiles) projectile.discard();
            if (draw(ctx) == state) cancel(ctx); else state.cancel();
        }
    }
}
