package bincnpc.gun.mixin.compat;

import bincnpc.gun.common.combat.FriendlyFire;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Group;

@Mixin(targets = "me.xjqsh.lrtactical.entity.EffectCloudGrenadeEntity", remap = false)
public abstract class LrSplashFriendlyFireMixin {
    @Group(name = "cnpcgun$splashIgnite", min = 1, max = 1)
    @Redirect(method = "applyAllEffects(Ljava/util/List;Lnet/minecraft/world/entity/LivingEntity;DLnet/minecraft/world/entity/Entity;ZI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;m_20254_(I)V"), require = 0)
    private void cnpcgun$protectFromIgnite(LivingEntity victim, int seconds) {
        if (!FriendlyFire.isProtected((Entity)(Object)this, victim)) victim.setSecondsOnFire(seconds);
    }

    @Group(name = "cnpcgun$splashIgnite", min = 1, max = 1)
    @Redirect(method = "applyAllEffects(Ljava/util/List;Lnet/minecraft/world/entity/LivingEntity;DLnet/minecraft/world/entity/Entity;ZI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setSecondsOnFire(I)V"), require = 0)
    private void cnpcgun$protectFromMappedIgnite(LivingEntity victim, int seconds) {
        if (!FriendlyFire.isProtected((Entity)(Object)this, victim)) victim.setSecondsOnFire(seconds);
    }
}
