package bincnpc.gun.mixin.compat;

import bincnpc.gun.common.combat.CombatService;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=mods.flammpfeil.slashblade.item.ItemSlashBlade.class,remap=false)
public abstract class SlashBladeDurabilityMixin {
    @Inject(method="damageItem",at=@At("HEAD"),cancellable=true)
    private static void cnpcgun$durability(ItemStack stack,int damage,EntityLivingBase user,CallbackInfo ci){
        if(user instanceof EntityNPCInterface && CombatService.serverThread((EntityNPCInterface)user)
            && CombatService.isControlled((EntityNPCInterface)user)
            && CombatService.data((EntityNPCInterface)user).config.bool("slashblade.protectDurability"))ci.cancel();
    }
}
