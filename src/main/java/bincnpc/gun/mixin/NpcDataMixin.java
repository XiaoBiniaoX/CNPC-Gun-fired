package bincnpc.gun.mixin;

import bincnpc.gun.common.npc.*;
import bincnpc.gun.common.persistence.GunPersistence;
import bincnpc.gun.common.combat.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value=EntityNPCInterface.class,remap=false)
public abstract class NpcDataMixin implements NpcAccess {
    @Unique private NpcData cnpcgun$data;
    @Override public NpcData cnpcgun$data(){if(cnpcgun$data==null)cnpcgun$data=new NpcData();return cnpcgun$data;}
    @Inject(method={"func_70037_a","readEntityFromNBT"},at=@At(value="INVOKE",
        target="Lnoppes/npcs/entity/data/DataDisplay;readToNBT(Lnet/minecraft/nbt/NBTTagCompound;)V"))
    private void cnpcgun$load(NBTTagCompound tag,CallbackInfo ci){GunPersistence.load((EntityNPCInterface)(Object)this,tag);}
    @Inject(method={"func_70014_b","writeEntityToNBT"},at=@At("TAIL"))
    private void cnpcgun$save(NBTTagCompound tag,CallbackInfo ci){GunPersistence.save((EntityNPCInterface)(Object)this,tag);}
    @Inject(method="writeSpawnData()Lnet/minecraft/nbt/NBTTagCompound;",at=@At("RETURN"))
    private void cnpcgun$summary(CallbackInfoReturnable<NBTTagCompound> cir){cir.getReturnValue().setInteger("CNPCGunActiveSlot",cnpcgun$data().armory.active);}
    @Inject(method="readSpawnData(Lnet/minecraft/nbt/NBTTagCompound;)V",at=@At("TAIL"))
    private void cnpcgun$clientSlot(NBTTagCompound tag,CallbackInfo ci){
        if(((EntityNPCInterface)(Object)this).world.isRemote)cnpcgun$data().armory.active=Math.max(0,Math.min(5,tag.getInteger("CNPCGunActiveSlot")));
    }
    @Inject(method="reset",at=@At("TAIL"))
    private void cnpcgun$reset(CallbackInfo ci){
        EntityNPCInterface npc=(EntityNPCInterface)(Object)this;
        if(CombatService.serverThread(npc)){
            cnpcgun$data().throwing.reset();cnpcgun$data().throwItems.reset();cnpcgun$data().runtime.restoreSupplies=true;
            cnpcgun$data().runtime.editorUntil=0;cnpcgun$data().runtime.editorPaused=false;
        }
    }
    @Inject(method={"func_70636_d","onLivingUpdate"},at=@At("TAIL"))
    private void cnpcgun$faceAfterAi(CallbackInfo ci){
        EntityNPCInterface npc=(EntityNPCInterface)(Object)this;
        if(CombatService.serverThread(npc))cnpcgun$data().throwing.afterAi(new CombatContext(npc));
        CombatMovement.face(npc);
    }
    @Inject(method={"func_70645_a","onDeath"},at=@At("TAIL"))
    private void cnpcgun$kill(DamageSource source,CallbackInfo ci){
        EntityNPCInterface npc=(EntityNPCInterface)(Object)this;
        if(!npc.world.isRemote&&(npc.isKilled()||!npc.isEntityAlive())){
            cnpcgun$data().throwing.cancel();cnpcgun$data().throwItems.died();cnpcgun$data().runtime.restoreSupplies=true;
            if(source.getTrueSource() instanceof EntityNPCInterface)CombatService.rewardKill((EntityNPCInterface)source.getTrueSource(),npc);
        }
    }
}
