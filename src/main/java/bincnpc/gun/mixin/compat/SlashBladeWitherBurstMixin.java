package bincnpc.gun.mixin.compat;

import bincnpc.gun.compat.slashblade.LegacyBladeCombat;
import net.minecraft.entity.Entity;
import net.minecraft.world.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value = mods.flammpfeil.slashblade.entity.EntityWitherSword.class, remap = false)
public abstract class SlashBladeWitherBurstMixin {
    @Redirect(method = "attackEntity", at = @At(value = "INVOKE", remap = true,
        target = "Lnet/minecraft/world/World;newExplosion(Lnet/minecraft/entity/Entity;DDDFZZ)Lnet/minecraft/world/Explosion;"))
    private Explosion cnpcgun$burst(World world, Entity source, double x, double y, double z, float power, boolean fire, boolean terrain) {
        return LegacyBladeCombat.burst(source, world, x, y, z, power, fire, terrain);
    }
}
