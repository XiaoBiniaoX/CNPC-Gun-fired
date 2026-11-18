package bincnpc.gun.mixin;

import noppes.npcs.client.gui.SubGuiNpcRangeProperties;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubGuiNpcRangeProperties.class, remap = false, priority = 900)
public abstract class NpcRangeGuiMixin {
    @Inject(method = {"m_7856_", "init"}, at = @At("TAIL"), require = 0)
    private void cnpcgun$range(CallbackInfo ci) {
        GuiTextFieldNop field = ((SubGuiNpcRangeProperties)(Object)this).getTextField(2);
        field.setMinMaxDefault(0, 2048, 15);
    }
}
