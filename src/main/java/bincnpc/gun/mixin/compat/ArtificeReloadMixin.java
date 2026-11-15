package bincnpc.gun.mixin.compat;

import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.compat.artifice.ArtificeAdapter;
import io.redspace.irons_artifice.data.ReloadResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "io.redspace.irons_artifice.item.GunplayManager", remap = false)
public abstract class ArtificeReloadMixin {
    @Inject(method = "attemptStartReload", at = @At(value = "INVOKE", target = "Lio/redspace/irons_artifice/item/GunplayManager;playReloadAnimation(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V", remap = false), require = 1)
    private static void cnpcgun$reloadSpeed(LivingEntity living, ItemStack stack, CallbackInfoReturnable<ReloadResult> cir) {
        if (living instanceof EntityNPCInterface npc && !npc.level().isClientSide && CombatService.isControlled(npc))
            ArtificeAdapter.adjustNpcReload(npc, stack);
    }
    @Inject(method = "attemptFinishReload", at = @At("HEAD"), cancellable = true, require = 0)
    private static void cnpcgun$finiteReload(LivingEntity living, ItemStack stack, int rounds, CallbackInfoReturnable<ReloadResult> cir) {
        if (living instanceof EntityNPCInterface npc && !npc.level().isClientSide && CombatService.isControlled(npc))
            cir.setReturnValue(ArtificeAdapter.finishNpc(npc, stack, rounds));
    }
}
