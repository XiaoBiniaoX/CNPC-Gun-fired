package bincnpc.gun.mixin;

import bincnpc.gun.common.persistence.GunPersistence;
import net.minecraft.nbt.CompoundTag;
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
    private void cnpcgun$writeLinked(EntityNPCInterface npc, CallbackInfoReturnable<CompoundTag> cir) {
        GunPersistence.memoryWrite(npc, cir.getReturnValue());
    }
    @Redirect(method = "loadNpcData", at = @At(value = "INVOKE",
        target = "Lnoppes/npcs/entity/data/DataInventory;load(Lnet/minecraft/nbt/CompoundTag;)V"))
    private void cnpcgun$loadLinked(DataInventory inventory, CompoundTag tag, EntityNPCInterface npc) {
        inventory.load(tag);
        GunPersistence.memoryRead(npc, tag);
    }
}
