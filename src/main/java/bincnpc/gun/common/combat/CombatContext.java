package bincnpc.gun.common.combat;

import bincnpc.gun.common.config.NpcConfig;
import bincnpc.gun.common.npc.*;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;

public final class CombatContext {
    public final EntityNPCInterface npc;
    public final NpcData data;
    public final RuntimeState runtime;
    public final NpcConfig config;
    public final ItemStack weapon;
    public final LivingEntity target;
    public final long now;
    public final double distance;
    private net.minecraft.world.phys.Vec3 aimPoint;
    public CombatContext(EntityNPCInterface npc) { this(npc, npc == null ? null : npc.getTarget()); }
    public CombatContext(EntityNPCInterface npc, LivingEntity target) {
        this(npc, target, mainHand(npc));
    }
    private static ItemStack mainHand(EntityNPCInterface npc) {
        if (npc == null) throw new IllegalArgumentException("NPC is required");
        return npc.inventory == null ? ItemStack.EMPTY : noppes.npcs.api.wrapper.ItemStackWrapper.MCItem(npc.inventory.getRightHand());
    }
    private CombatContext(EntityNPCInterface npc, LivingEntity target, ItemStack weapon) {
        this.npc = npc; this.data = CombatService.data(npc); this.runtime = data.runtime; this.config = data.config;
        this.weapon = weapon == null ? ItemStack.EMPTY : weapon;
        this.target = target; this.now = npc.level().getGameTime();
        this.distance = target == null ? Double.POSITIVE_INFINITY : npc.distanceTo(target);
    }
    public CombatContext withWeapon(ItemStack stack) { return new CombatContext(npc, target, stack); }
    public boolean validTarget() { return CombatService.validTarget(npc, target); }
    /** One firing-range source shared with the native CNPC editor and scripts. */
    public int gunRange() { return npc.stats.ranged.getRange(); }
    public List<LivingEntity> targets() {
        if (now >= runtime.nextTargetScan) {
            runtime.nearbyTargets.clear();
            double range = Math.max(npc.stats.aggroRange, gunRange());
            // Only loaded entities: a 2048-block AABB would iterate tens of thousands of empty chunk sections.
            for (var entity : ((net.minecraft.server.level.ServerLevel)npc.level()).getAllEntities()) {
                if (runtime.nearbyTargets.size() >= 16) break;
                if (entity instanceof LivingEntity living && npc.distanceToSqr(living) <= range * range && CombatService.hostile(npc, living))
                    runtime.nearbyTargets.add(living);
            }
            runtime.nextTargetScan = now + config.integer("combat.decisionInterval");
        }
        double range = Math.max(npc.stats.aggroRange, gunRange());
        return runtime.nearbyTargets.stream().filter(e -> npc.distanceToSqr(e) <= range * range && CombatService.hostile(npc, e)).toList();
    }
    public float yaw() {
        if (target == null) return npc.getYRot();
        var point = aimPoint();
        return (float)(Math.toDegrees(Math.atan2(point.z - npc.getZ(), point.x - npc.getX())) - 90) + runtime.shotYawOffset;
    }
    public float pitch() {
        if (target == null) return npc.getXRot();
        var point = aimPoint();
        double dx = point.x - npc.getX(), dz = point.z - npc.getZ();
        return net.minecraft.util.Mth.clamp((float)(-Math.toDegrees(Math.atan2(point.y - npc.getEyeY(), Math.sqrt(dx * dx + dz * dz)))
            + runtime.recoil.pitch()) + runtime.shotPitchOffset, -90, 90);
    }
    private net.minecraft.world.phys.Vec3 aimPoint() {
        if (aimPoint != null) return aimPoint;
        aimPoint = target.getEyePosition();
        if (config.bool("gun.predictiveAim") && CombatService.serverThread(npc) && validTarget()
                && runtime.adapter instanceof bincnpc.gun.gun.adapter.GunAdapter gun) {
            double speed = bincnpc.gun.common.adapter.AdapterRegistry.query(gun, () -> gun.projectileSpeed(this), 0.0);
            double time = runtime.prediction.time(target.getUUID(), now, aimPoint.x - npc.getX(), aimPoint.y - npc.getEyeY(), aimPoint.z - npc.getZ(), speed);
            aimPoint = aimPoint.add(runtime.prediction.vx() * time, runtime.prediction.vy() * time, runtime.prediction.vz() * time);
        }
        return aimPoint;
    }
    public boolean isPrefiring() {
        String mode = config.text("gun.prefire");
        if ((!mode.equals("TRACK") && !mode.equals("SWEEP")) || !validTarget() || target != npc.getTarget()
                || distance > gunRange()) return false;
        return Prefire.active(mode, true, npc.getSensing().hasLineOfSight(target));
    }
    public boolean canFireWithoutSight() { return config.bool("gun.blindFire") || isPrefiring(); }
    public net.minecraft.world.phys.Vec3 shotDirection() { return net.minecraft.world.phys.Vec3.directionFromRotation(pitch(), yaw()); }
    public double spreadMultiplier() {
        double spread = config.number("gun.spreadMultiplier") * (100 - config.number("gun.accuracy")) / 50;
        if (npc.getDeltaMovement().horizontalDistanceSqr() > 0.005) spread *= config.number("gun.moveSpread");
        if (!npc.onGround()) spread *= config.number("gun.jumpSpread");
        if (runtime.aiming) spread *= config.number("gun.aimSpread");
        if (isPrefiring() && config.text("gun.prefire").equals("SWEEP")) spread *= 2;
        return spread * Math.pow(config.number("gun.continuousSpread"), Math.min(runtime.shotStreak, 10));
    }
}
