package bincnpc.gun.mixin.client;

import bincnpc.gun.client.gui.legacy.EmbeddedInput;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiScreen.class)
public abstract class EmbeddedGuiScreenMixin {
    @Inject(method = "handleMouseInput", at = @At("HEAD"), cancellable = true)
    private void cnpcgun$wheel(CallbackInfo ci) {
        if (EmbeddedInput.screen == (Object)this && EmbeddedInput.wheel) ci.cancel();
    }
    @ModifyVariable(method = "handleMouseInput", at = @At("STORE"), ordinal = 0)
    private int cnpcgun$x(int x) { return EmbeddedInput.screen == (Object)this ? EmbeddedInput.x : x; }
    @ModifyVariable(method = "handleMouseInput", at = @At("STORE"), ordinal = 1)
    private int cnpcgun$y(int y) { return EmbeddedInput.screen == (Object)this ? EmbeddedInput.y : y; }
}
