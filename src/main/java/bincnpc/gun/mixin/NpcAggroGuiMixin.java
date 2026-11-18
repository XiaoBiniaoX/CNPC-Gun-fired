package bincnpc.gun.mixin;

import noppes.npcs.client.gui.mainmenu.GuiNpcStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNpcStats.class, remap = false, priority = 900)
public abstract class NpcAggroGuiMixin {
    @Inject(method = {"func_73866_w_()V", "initGui()V"}, at = @At("TAIL"))
    private void cnpcgun$range(CallbackInfo ci) { ((GuiNpcStats)(Object)this).getTextField(1).setMinMaxDefault(0, 2048, 16); }
}
