package bincnpc.gun.mixin.compat;

import bincnpc.gun.compat.slashblade.LegacyBladeCombat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "cn.mmf.slashblade_addon.entity.EntityBase", remap = false)
public abstract class SlashBladeAddonDamageMixin {
    @Shadow(remap = false) protected EntityLivingBase thrower_;
    @Shadow(remap = false) protected ItemStack blade_;

    // Only the DamageSource overload performs damage; its name belongs to the addon, not MCP.
    @Inject(method = "onImpact(Lnet/minecraft/entity/Entity;FLnet/minecraft/util/DamageSource;)Z",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcgun$damage(Entity target, float amount, DamageSource source, CallbackInfoReturnable<Boolean> ci) {
        Entity projectile = (Entity)(Object)this;
        if (LegacyBladeCombat.owner(projectile) == null) return;
        if (!LegacyBladeCombat.damage(projectile, target, source, amount, false)
                || blade_.isEmpty() || !(target instanceof EntityLivingBase)) {
            ci.setReturnValue(false);
            return;
        }
        // Keep the native blade callback after an allowed hit, including its durability policy.
        blade_.getItem().hitEntity(blade_, (EntityLivingBase)target, thrower_);
        ci.setReturnValue(true);
    }
}
