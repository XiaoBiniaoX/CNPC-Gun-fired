package bincnpc.gun.mixin.compat;

import bincnpc.gun.compat.slashblade.LegacyBladeCombat;
import mods.flammpfeil.slashblade.entity.selector.EntitySelectorAttackable;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets = "cn.mmf.slashblade_addon.entity.EntityPhantomSwordEx", remap = false)
public abstract class SlashBladeAddonPhantomMixin {
    @Inject(method = "getTargetEntity", at = @At("RETURN"), cancellable = true)
    private void cnpcgun$lockedTarget(CallbackInfoReturnable<Entity> ci) {
        Entity source = (Entity)(Object)this;
        if (LegacyBladeCombat.owner(source) != null && !LegacyBladeCombat.allowed(source, ci.getReturnValue(), EntitySelectorAttackable.getInstance())) ci.setReturnValue(null);
    }
    @Inject(method = "attackToEntity", at = @At("HEAD"), cancellable = true)
    private void cnpcgun$validateHit(Entity target, CallbackInfo ci) {
        Entity source = (Entity)(Object)this;
        if (LegacyBladeCombat.owner(source) != null && !LegacyBladeCombat.allowed(source, target, EntitySelectorAttackable.getInstance())) ci.cancel();
    }
}
