package bincnpc.gun.common.combat;

import bincnpc.gun.common.adapter.AdapterRegistry;
import bincnpc.gun.common.config.NpcConfig;
import bincnpc.gun.common.legacy.Platform;
import bincnpc.gun.common.npc.*;
import bincnpc.gun.gun.adapter.GunAdapter;
import bincnpc.gun.gun.controller.AimPrediction;
import java.util.List;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import noppes.npcs.entity.EntityNPCInterface;

public final class CombatContext {
    public final EntityNPCInterface npc;
    public final NpcData data;
    public final RuntimeState runtime;
    public final NpcConfig config;
    public final ItemStack weapon;
    public final EntityLivingBase target;
    public final long now;
    public final double distance;
    private Vec3d aimPoint;
    public CombatContext(EntityNPCInterface npc) { this(npc, npc == null ? null : npc.getAttackTarget()); }
    public CombatContext(EntityNPCInterface npc, EntityLivingBase target) {
        this(npc, target, mainHand(npc));
    }
    private static ItemStack mainHand(EntityNPCInterface npc) {
        if (npc == null) throw new IllegalArgumentException("NPC is required");
        return npc.inventory == null ? ItemStack.EMPTY : noppes.npcs.api.wrapper.ItemStackWrapper.MCItem(npc.inventory.getRightHand());
    }
    private CombatContext(EntityNPCInterface npc, EntityLivingBase target, ItemStack weapon) {
        this.npc = npc; this.data = CombatService.data(npc); this.runtime = data.runtime; this.config = data.config;
        this.weapon = weapon == null ? ItemStack.EMPTY : weapon;
        this.target = target; this.now = npc.world.getTotalWorldTime();
        this.distance = target == null ? Double.POSITIVE_INFINITY : npc.getDistance(target);
    }
    public CombatContext withWeapon(ItemStack stack) { return new CombatContext(npc, target, stack); }
    public boolean validTarget() { return CombatService.validTarget(npc, target); }
    public int gunRange() { return npc.stats.ranged.getRange(); }
    public List<EntityLivingBase> targets() {
        if (now >= runtime.nextTargetScan) {
            runtime.nearbyTargets.clear();
            double range = Math.max(npc.stats.aggroRange, gunRange());
            // Only loaded entities; never walk a 2048-block box through empty chunk sections.
            for (net.minecraft.entity.Entity entity : npc.world.loadedEntityList) {
                if (runtime.nearbyTargets.size() >= 16) break;
                if (entity instanceof EntityLivingBase && npc.getDistanceSq(entity) <= range * range
                        && CombatService.hostile(npc, (EntityLivingBase)entity)) runtime.nearbyTargets.add((EntityLivingBase)entity);
            }
            runtime.nextTargetScan = now + config.integer("combat.decisionInterval");
        }
        double range = Math.max(npc.stats.aggroRange, gunRange());
        return runtime.nearbyTargets.stream().filter(e -> npc.getDistanceSq(e) <= range * range && CombatService.hostile(npc, e)).collect(bincnpc.gun.common.legacy.Legacy.toList());
    }
    public float yaw() {
        if (target == null) return npc.rotationYaw;
        Vec3d point = aimPoint();
        return (float)(Math.toDegrees(Math.atan2(point.z - npc.posZ, point.x - npc.posX)) - 90) + runtime.shotYawOffset;
    }
    public float pitch() {
        if (target == null) return npc.rotationPitch;
        Vec3d point = aimPoint();
        double dx = point.x - npc.posX, dz = point.z - npc.posZ;
        return net.minecraft.util.math.MathHelper.clamp((float)-Math.toDegrees(Math.atan2(point.y - Platform.eyeY(npc), Math.sqrt(dx * dx + dz * dz))) + runtime.shotPitchOffset, -90, 90);
    }
    private Vec3d aimPoint() {
        if (aimPoint != null) return aimPoint;
        aimPoint = new Vec3d(target.posX, Platform.eyeY(target), target.posZ);
        AimPrediction prediction = runtime.prediction;
        if (weapon == runtime.lastStack && runtime.adapter instanceof GunAdapter && config.bool("gun.predictiveAim")
                && CombatService.serverThread(npc) && !data.throwing.blocksAttack(now) && prediction.moving(target.getUniqueID(), now)) {
            GunAdapter gun = (GunAdapter)runtime.adapter;
            double speed = AdapterRegistry.query(gun, () -> gun.projectileSpeed(this), 0.0);
            double time = prediction.time(aimPoint.x - npc.posX, aimPoint.y - Platform.eyeY(npc), aimPoint.z - npc.posZ, speed);
            if (time > 0) aimPoint = aimPoint.add(prediction.vx() * time, prediction.vy() * time, prediction.vz() * time);
        }
        return aimPoint;
    }
    public Vec3d shotDirection() { return Vec3d.fromPitchYaw(pitch(), yaw()); }
    public double spreadMultiplier() {
        if (config.text("gun.prefire").equals("SWEEP")) return 2;
        double spread = config.number("gun.spreadMultiplier") * (100 - config.number("gun.accuracy")) / 50;
        if (bincnpc.gun.common.legacy.Platform.horizontalSquare(bincnpc.gun.common.legacy.Platform.motion(npc)) > 0.005) spread *= config.number("gun.moveSpread");
        if (!npc.onGround) spread *= config.number("gun.jumpSpread");
        if (runtime.aiming) spread *= config.number("gun.aimSpread");
        return spread * Math.pow(config.number("gun.continuousSpread"), Math.min(runtime.shotStreak, 10));
    }
}
