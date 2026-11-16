package bincnpc.gun.mixin.compat;

import bincnpc.gun.compat.slashblade.LegacyBladeCombat;
import com.google.common.base.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value = {
    mods.flammpfeil.slashblade.entity.EntityDrive.class,
    mods.flammpfeil.slashblade.entity.EntitySlashDimension.class,
    mods.flammpfeil.slashblade.entity.EntityJudgmentCutManager.class,
    mods.flammpfeil.slashblade.entity.EntitySummonedSwordBase.class
}, remap = false)
public abstract class SlashBladeRangedTargetsMixin {
    @Redirect(method = {"func_70071_h_", "onUpdate", "func_70106_y", "setDead", "getRayTraceResult"}, remap = false,
        at = @At(value = "INVOKE", remap = true, target = "Lnet/minecraft/world/World;getEntitiesInAABBexcluding(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/AxisAlignedBB;Lcom/google/common/base/Predicate;)Ljava/util/List;"))
    private List<Entity> cnpcgun$targets(World world, Entity excluded, AxisAlignedBB area, Predicate<Entity> predicate) {
        return LegacyBladeCombat.targets((Entity)(Object)this, world, excluded, area, predicate);
    }
}
