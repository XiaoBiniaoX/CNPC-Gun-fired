package bincnpc.gun.mixin.compat;

import bincnpc.gun.compat.slashblade.LegacyBladeCombat;
import com.google.common.base.Predicate;
import mods.flammpfeil.slashblade.entity.selector.EntitySelectorAttackable;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {
    mods.flammpfeil.slashblade.entity.EntitySummonedSwordBase.class,
    mods.flammpfeil.slashblade.entity.EntitySpiralSwords.class,
    mods.flammpfeil.slashblade.entity.EntityStormSwords.class,
    mods.flammpfeil.slashblade.entity.EntityBlisteringSwords.class,
    mods.flammpfeil.slashblade.entity.EntityHeavyRainSwords.class,
    mods.flammpfeil.slashblade.entity.EntityWitherSword.class
}, remap = false)
public abstract class SlashBladePhantomTargetsMixin {
    @Redirect(method = {"getRayTrace", "getRayTraceResult", "doTargeting"}, at = @At(value = "INVOKE", target = "Lcom/google/common/base/Predicate;apply(Ljava/lang/Object;)Z"), require = 0)
    private boolean cnpcgun$rayTarget(Predicate<Entity> predicate, Object target) {
        return target instanceof Entity && LegacyBladeCombat.allowed((Entity)(Object)this, (Entity) target, predicate);
    }
    @Redirect(method = {"getRayTraceResult", "doTargeting", "func_70071_h_", "onUpdate"},
        at = @At(value = "INVOKE", remap = true, target = "Lnet/minecraft/world/World;getEntityByID(I)Lnet/minecraft/entity/Entity;"), require = 0)
    private Entity cnpcgun$lockedTarget(World world, int id) {
        Entity target = world.getEntityByID(id), source = (Entity)(Object)this;
        return LegacyBladeCombat.owner(source) == null || LegacyBladeCombat.allowed(source, target, EntitySelectorAttackable.getInstance()) ? target : null;
    }
    @Inject(method = {"attackEntity", "blastAttackEntity"}, at = @At("HEAD"), cancellable = true)
    private void cnpcgun$validateHit(Entity target, CallbackInfo ci) {
        Entity source = (Entity)(Object)this;
        if (LegacyBladeCombat.owner(source) != null && !LegacyBladeCombat.allowed(source, target, EntitySelectorAttackable.getInstance())) ci.cancel();
    }
}
