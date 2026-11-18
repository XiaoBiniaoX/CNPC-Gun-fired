package bincnpc.gun.mixin;

import bincnpc.gun.common.npc.*;
import bincnpc.gun.common.persistence.GunPersistence;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class NpcDataMixin implements NpcAccess {
    @Unique private NpcData cnpcgun$data;
    @Override public NpcData cnpcgun$data() {
        if (cnpcgun$data == null) cnpcgun$data = new NpcData();
        return cnpcgun$data;
    }
    @Inject(method = {"m_7378_", "readAdditionalSaveData"}, at = @At(value = "INVOKE",
        target = "Lnoppes/npcs/entity/data/DataDisplay;readToNBT(Lnet/minecraft/nbt/CompoundTag;)V"), require = 0)
    private void cnpcgun$load(CompoundTag tag, CallbackInfo ci) {
        GunPersistence.load((EntityNPCInterface)(Object)this, tag);
    }
    @Inject(method = {"m_7380_", "addAdditionalSaveData"}, at = @At("TAIL"), require = 0)
    private void cnpcgun$save(CompoundTag tag, CallbackInfo ci) {
        GunPersistence.save((EntityNPCInterface)(Object)this, tag);
    }
    // Spawn packets carry a compact active-slot summary.
    @Inject(method = "writeSpawnData()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private void cnpcgun$summary(CallbackInfoReturnable<CompoundTag> cir) {
        cir.getReturnValue().putInt("CNPCGunActiveSlot", cnpcgun$data().armory.active);
    }
    @Inject(method = "reset", at = @At("TAIL"))
    private void cnpcgun$reset(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface)(Object)this;
        if (bincnpc.gun.common.combat.CombatService.serverThread(npc)) {
            cnpcgun$data().throwing.reset(); cnpcgun$data().throwItems.reset();
            cnpcgun$data().runtime.restoreSupplies = true;
        }
    }
    @Inject(method = {"m_8107_", "aiStep"}, at = @At("TAIL"), require = 0)
    private void cnpcgun$faceAfterAi(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface)(Object)this;
        if (bincnpc.gun.common.combat.CombatService.serverThread(npc)) cnpcgun$data().throwing.afterAi(new bincnpc.gun.common.combat.CombatContext(npc));
        bincnpc.gun.common.combat.CombatMovement.face(npc);
    }
    @Inject(method = {"m_6667_", "die"}, at = @At("TAIL"), require = 0)
    private void cnpcgun$kill(net.minecraft.world.damagesource.DamageSource source, CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface)(Object)this;
        if (!npc.level().isClientSide && (npc.isKilled() || !npc.isAlive())) {
            cnpcgun$data().throwing.cancel(); cnpcgun$data().throwItems.died();
            cnpcgun$data().runtime.restoreSupplies = true;
            if (source.getEntity() instanceof EntityNPCInterface owner) bincnpc.gun.common.combat.CombatService.rewardKill(owner, npc);
        }
    }
}
