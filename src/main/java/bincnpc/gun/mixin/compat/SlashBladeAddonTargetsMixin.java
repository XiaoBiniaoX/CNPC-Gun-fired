package bincnpc.gun.mixin.compat;

import bincnpc.gun.compat.slashblade.LegacyBladeCombat;
import com.google.common.base.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import java.util.List;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets = {"cn.mmf.slashblade_addon.entity.EntityDriveEx", "cn.mmf.slashblade_addon.entity.EntityPhantomSwordEx"}, remap = false)
public abstract class SlashBladeAddonTargetsMixin {
    @Redirect(method = {"detectCollision()V", "getNearestHitEntity(Lnet/minecraft/util/math/AxisAlignedBB;)Lnet/minecraft/entity/Entity;"}, remap = false,
        at = @At(value = "INVOKE", remap = true, target = "Lnet/minecraft/world/World;getEntitiesInAABBexcluding(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/AxisAlignedBB;Lcom/google/common/base/Predicate;)Ljava/util/List;"))
    private List<Entity> cnpcgun$targets(World world, Entity excluded, AxisAlignedBB area, Predicate<Entity> predicate) {
        return LegacyBladeCombat.targets((Entity)(Object)this, world, excluded, area, predicate);
    }
}
