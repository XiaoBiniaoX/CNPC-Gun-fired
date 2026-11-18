package bincnpc.gun.mixin;

import noppes.npcs.client.gui.SubGuiNpcRangeProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubGuiNpcRangeProperties.class, remap = false, priority = 900)
public abstract class NpcRangeGuiMixin {
    @Inject(method = {"func_73866_w_()V", "initGui()V"}, at = @At("TAIL"))
    private void cnpcgun$range(CallbackInfo ci) { ((SubGuiNpcRangeProperties)(Object)this).getTextField(2).setMinMaxDefault(0, 2048, 15); }
}
