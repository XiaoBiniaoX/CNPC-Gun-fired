package bincnpc.gun.mixin.compat;

import bincnpc.gun.compat.slashblade.LegacyBladeCombat;
import mods.flammpfeil.slashblade.ability.ArmorPiercing;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import net.minecraft.entity.Entity;
import net.minecraft.util.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value = EntityDrive.class, remap = false)
public abstract class SlashBladeDriveDamageMixin {
    @Redirect(method = {"func_70071_h_", "onUpdate"}, at = @At(value = "INVOKE",
        target = "Lmods/flammpfeil/slashblade/ability/ArmorPiercing;doAPAttack(Lnet/minecraft/entity/Entity;F)F"))
    private float cnpcgun$armorPiercing(Entity target, float amount) {
        return LegacyBladeCombat.owner((Entity)(Object)this) == null ? ArmorPiercing.doAPAttack(target, amount) : 0;
    }
    @Redirect(method = {"func_70071_h_", "onUpdate"}, at = @At(value = "INVOKE", remap = true,
        target = "Lnet/minecraft/entity/Entity;attackEntityFrom(Lnet/minecraft/util/DamageSource;F)Z"))
    private boolean cnpcgun$damage(Entity target, DamageSource source, float amount) {
        return LegacyBladeCombat.damage((Entity)(Object)this, target, source, amount, false);
    }
}
