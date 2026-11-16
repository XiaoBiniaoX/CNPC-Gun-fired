package bincnpc.gun.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import techguns.items.guns.GenericGun;
import net.minecraft.util.SoundEvent;

@Mixin(value = GenericGun.class, remap = false)
public interface TechgunsGunAccess {
    @Accessor("minFiretime") int cnpcgun$fireTicks();
    @Accessor("reloadtime") int cnpcgun$reloadTicks();
    @Accessor("reloadsound") SoundEvent cnpcgun$reloadSound();
    @Accessor("firesound") SoundEvent cnpcgun$fireSound();
    @Accessor("accuracy") float cnpcgun$accuracy();
}
