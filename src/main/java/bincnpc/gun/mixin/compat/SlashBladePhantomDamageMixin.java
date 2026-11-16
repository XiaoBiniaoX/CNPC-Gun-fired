package bincnpc.gun.mixin.compat;

import bincnpc.gun.compat.slashblade.LegacyBladeCombat;
import net.minecraft.entity.Entity;
import net.minecraft.util.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value = {
    mods.flammpfeil.slashblade.entity.EntitySummonedSwordBase.class,
    mods.flammpfeil.slashblade.entity.EntityHeavyRainSwords.class,
    mods.flammpfeil.slashblade.entity.EntityStormSwords.class,
    mods.flammpfeil.slashblade.entity.EntitySpiralSwords.class,
    mods.flammpfeil.slashblade.entity.EntityWitherSword.class
}, remap = false)
public abstract class SlashBladePhantomDamageMixin {
    @Redirect(method = {"attackEntity", "blastAttackEntity", "func_70098_U", "updateRidden"},
        at = @At(value = "INVOKE", remap = true, target = "Lnet/minecraft/entity/Entity;attackEntityFrom(Lnet/minecraft/util/DamageSource;F)Z"))
    private boolean cnpcgun$damage(Entity target, DamageSource source, float amount) {
        return LegacyBladeCombat.damage((Entity)(Object)this, target, source, amount, false);
    }
}
