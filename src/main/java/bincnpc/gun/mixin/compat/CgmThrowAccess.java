package bincnpc.gun.mixin.compat;

import com.mrcrayfish.guns.entity.ThrowableItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ThrowableItemEntity.class, remap = false)
public interface CgmThrowAccess {
    @Accessor("gravityVelocity") float cnpcgun$gravity();
    @Accessor("maxLife") int cnpcgun$life();
}
