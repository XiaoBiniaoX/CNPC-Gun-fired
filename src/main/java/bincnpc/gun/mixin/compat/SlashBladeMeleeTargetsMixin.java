package bincnpc.gun.mixin.compat;

import bincnpc.gun.compat.slashblade.LegacyBladeReach;
import bincnpc.gun.compat.slashblade.LegacyBladeCombat;
import com.google.common.base.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.util.DamageSource;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Only owner-centered melee managers are included; drives, dimensions and phantom swords stay native. */
@Mixin(value = {
    mods.flammpfeil.slashblade.entity.EntityRapidSlashManager.class,
    mods.flammpfeil.slashblade.entity.EntityStingerManager.class,
    mods.flammpfeil.slashblade.entity.EntityCaliburManager.class,
    mods.flammpfeil.slashblade.entity.EntityHelmBrakerManager.class,
    mods.flammpfeil.slashblade.entity.EntitySpearManager.class,
    mods.flammpfeil.slashblade.entity.EntitySakuraEndManager.class,
    mods.flammpfeil.slashblade.entity.EntityMaximumBetManager.class
}, remap = false)
public abstract class SlashBladeMeleeTargetsMixin {
    @Redirect(method = {"func_70071_h_", "onUpdate", "doAttack"}, remap = false,
        at = @At(value = "INVOKE", remap = true, target = "Lnet/minecraft/world/World;getEntitiesInAABBexcluding(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/AxisAlignedBB;Lcom/google/common/base/Predicate;)Ljava/util/List;"))
    private List<Entity> cnpcgun$meleeTargets(World world, Entity owner, AxisAlignedBB area, Predicate<Entity> predicate) {
        return LegacyBladeReach.targets(world, owner, area, predicate);
    }
    @Redirect(method = {"func_70071_h_", "onUpdate", "doAttack"}, remap = false,
        at = @At(value = "INVOKE", remap = true, target = "Lnet/minecraft/entity/Entity;attackEntityFrom(Lnet/minecraft/util/DamageSource;F)Z"), require = 0)
    private boolean cnpcgun$meleeDamage(Entity target, DamageSource source, float amount) {
        return LegacyBladeCombat.damage((Entity)(Object)this, target, source, amount, true);
    }
}
