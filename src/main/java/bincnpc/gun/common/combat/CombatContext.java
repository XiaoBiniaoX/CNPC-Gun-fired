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
    public List<LivingEntity> targets() {
        if (now >= runtime.nextTargetScan) {
            runtime.nearbyTargets.clear();
            double range = Math.min(config.number("combat.attackRange"), 32);
            for (LivingEntity e : npc.level().getEntitiesOfClass(LivingEntity.class, npc.getBoundingBox().inflate(range),
                    e -> CombatService.hostile(npc, e))) {
                if (runtime.nearbyTargets.size() >= 16) break;
                runtime.nearbyTargets.add(e);
            }
            runtime.nextTargetScan = now + config.integer("combat.decisionInterval");
        }
        return runtime.nearbyTargets.stream().filter(e -> CombatService.validTarget(npc, e)).toList();
    }
    public float yaw() {
        if (target == null) return npc.getYRot();
        return (float)(Math.toDegrees(Math.atan2(target.getZ() - npc.getZ(), target.getX() - npc.getX())) - 90) + runtime.shotYawOffset;
    }
    public float pitch() {
        if (target == null) return npc.getXRot();
        double dx = target.getX() - npc.getX(), dz = target.getZ() - npc.getZ();
        return net.minecraft.util.Mth.clamp((float)-Math.toDegrees(Math.atan2(target.getEyeY() - npc.getEyeY(), Math.sqrt(dx * dx + dz * dz))) + runtime.shotPitchOffset, -90, 90);
    }
    public net.minecraft.world.phys.Vec3 shotDirection() { return net.minecraft.world.phys.Vec3.directionFromRotation(pitch(), yaw()); }
    public double spreadMultiplier() {
        if (config.text("gun.prefire").equals("SWEEP")) return 2;
        double spread = config.number("gun.spreadMultiplier") * (100 - config.number("gun.accuracy")) / 50;
        if (npc.getDeltaMovement().horizontalDistanceSqr() > 0.005) spread *= config.number("gun.moveSpread");
        if (!npc.onGround()) spread *= config.number("gun.jumpSpread");
        if (runtime.aiming) spread *= config.number("gun.aimSpread");
        return spread * Math.pow(config.number("gun.continuousSpread"), Math.min(runtime.shotStreak, 10));
    }
}
