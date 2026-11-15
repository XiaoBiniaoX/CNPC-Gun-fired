package bincnpc.gun.mixin.compat;

import bincnpc.gun.common.combat.CombatService;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.util.function.Consumer;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mods.flammpfeil.slashblade.item.ItemSlashBlade", remap = false)
public abstract class BladeDurabilityMixin {
    @Inject(method = "damageItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void cnpcgun$protect(ItemStack stack, int amount, LivingEntity entity, Consumer<LivingEntity> onBroken, CallbackInfoReturnable<Integer> cir) {
        if (entity instanceof EntityNPCInterface npc && !npc.level().isClientSide
            && CombatService.data(npc).config.bool("slashblade.protectDurability")) cir.setReturnValue(0);
    }
}
