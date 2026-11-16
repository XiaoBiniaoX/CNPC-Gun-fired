package bincnpc.gun.mixin;

import bincnpc.gun.common.persistence.GunPersistence;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.LinkedNpcController;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Linked templates use CNPC's existing in-memory merge and file lifecycle. */
@Mixin(value = LinkedNpcController.class, remap = false)
public abstract class LinkedNpcMixin {
    @Inject(method = "readNpcData", at = @At("RETURN"))
    private void cnpcgun$writeLinked(EntityNPCInterface npc, CallbackInfoReturnable<NBTTagCompound> cir) {
        GunPersistence.memoryWrite(npc, cir.getReturnValue());
    }
    @Redirect(method = "loadNpcData", at = @At(value = "INVOKE",
        target = "Lnoppes/npcs/entity/data/DataInventory;readEntityFromNBT(Lnet/minecraft/nbt/NBTTagCompound;)V"))
    private void cnpcgun$loadLinked(DataInventory inventory, NBTTagCompound tag, EntityNPCInterface npc) {
        inventory.readEntityFromNBT(tag);
        GunPersistence.memoryRead(npc, tag);
    }
}
