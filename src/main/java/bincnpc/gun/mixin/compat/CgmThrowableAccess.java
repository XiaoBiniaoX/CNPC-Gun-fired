package bincnpc.gun.mixin.compat;

import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value=com.mrcrayfish.guns.entity.EntityThrowable.class,remap=false)
public interface CgmThrowableAccess {
    @Accessor("thrower") void cnpcgun$thrower(EntityLivingBase owner);
    @Accessor("throwerName") void cnpcgun$throwerName(String owner);
}
