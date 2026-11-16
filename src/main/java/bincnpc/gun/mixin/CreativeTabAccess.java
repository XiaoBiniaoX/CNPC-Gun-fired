package bincnpc.gun.mixin;

import net.minecraft.creativetab.CreativeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Both fields exist on the dedicated server; the native label getter is client only. */
@Mixin(CreativeTabs.class)
public interface CreativeTabAccess {
    @Accessor("index") int cnpcgun$index();
    @Accessor("tabLabel") String cnpcgun$label();
}
