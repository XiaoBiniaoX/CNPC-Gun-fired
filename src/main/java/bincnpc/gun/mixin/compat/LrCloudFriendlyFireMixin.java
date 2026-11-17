package bincnpc.gun.mixin.compat;

import bincnpc.gun.common.combat.FriendlyFire;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Group;

@Mixin(targets = "me.xjqsh.lrtactical.entity.sp.SpEffectCloudEntity", remap = false)
public abstract class LrCloudFriendlyFireMixin {
    @Group(name = "cnpcgun$cloudIgnite", min = 1, max = 1)
    @Redirect(method = {"m_8119_()V", "tick()V"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;m_20254_(I)V"), require = 0)
    private void cnpcgun$protectFromIgnite(Entity victim, int seconds) {
        if (!FriendlyFire.isProtected((Entity)(Object)this, victim)) victim.setSecondsOnFire(seconds);
    }

    @Group(name = "cnpcgun$cloudIgnite", min = 1, max = 1)
    @Redirect(method = {"m_8119_()V", "tick()V"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setSecondsOnFire(I)V"), require = 0)
    private void cnpcgun$protectFromMappedIgnite(Entity victim, int seconds) {
        if (!FriendlyFire.isProtected((Entity)(Object)this, victim)) victim.setSecondsOnFire(seconds);
    }
}
