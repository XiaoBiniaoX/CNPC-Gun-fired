package bincnpc.gun.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value=com.mrcrayfish.guns.entity.EntityThrowableItem.class,remap=false)
public interface CgmThrowAccess {
    @Accessor("shouldBounce") void cnpcgun$bounce(boolean value);
    @Accessor("gravityVelocity") float cnpcgun$gravity();
    @Accessor("gravityVelocity") void cnpcgun$gravity(float value);
    @Accessor("maxLife") int cnpcgun$life();
}
