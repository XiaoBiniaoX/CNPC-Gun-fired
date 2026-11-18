package bincnpc.gun.mixin;

import noppes.npcs.client.gui.mainmenu.GuiNpcStats;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNpcStats.class, remap = false, priority = 900)
public abstract class NpcAggroGuiMixin {
    @Inject(method = {"m_7856_", "init"}, at = @At("TAIL"), require = 0)
    private void cnpcgun$range(CallbackInfo ci) {
        GuiTextFieldNop field = ((GuiNpcStats)(Object)this).getTextField(1);
        field.setMinMaxDefault(0, 2048, 16);
    }
}
