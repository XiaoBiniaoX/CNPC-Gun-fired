package bincnpc.gun.mixin.compat;

import bincnpc.gun.common.combat.FriendlyFire;
import me.xjqsh.lrtactical.item.throwable.flash.StunThrowableData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "me.xjqsh.lrtactical.entity.StunGrenadeEntity", remap = false)
public abstract class LrStunFriendlyFireMixin {
    @Inject(method = "calculateAndApplyEffect(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;Lme/xjqsh/lrtactical/item/throwable/flash/StunThrowableData$StunData;)V",
        at = @At("HEAD"), cancellable = true)
    private static void cnpcgun$protectFromStun(Entity grenade, LivingEntity victim, StunThrowableData.StunData data, CallbackInfo ci) {
        if (FriendlyFire.isProtected(grenade, victim)) ci.cancel();
    }
}
