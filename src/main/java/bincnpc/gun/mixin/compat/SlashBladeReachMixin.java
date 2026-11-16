package bincnpc.gun.mixin.compat;

import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.compat.slashblade.LegacyBladeReach;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemSlashBlade.class, remap = false)
public abstract class SlashBladeReachMixin {
    @Inject(method = "getBBofCombo", at = @At("RETURN"), cancellable = true)
    private void cnpcgun$meleeReach(ItemStack stack, ItemSlashBlade.ComboSequence combo, EntityLivingBase user,
            CallbackInfoReturnable<AxisAlignedBB> cir) {
        if (LegacyBladeReach.controlled(user))
            cir.setReturnValue(LegacyBladeReach.area(new CombatContext((EntityNPCInterface) user), cir.getReturnValue()));
    }
}
