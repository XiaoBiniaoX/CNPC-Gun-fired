package bincnpc.gun.mixin.client;

import bincnpc.gun.client.gui.legacy.EmbeddedInput;
import net.minecraft.client.gui.GuiSlot;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiSlot.class)
public abstract class EmbeddedGuiSlotMixin {
    @Shadow protected int mouseX;
    @Shadow protected int mouseY;
    @Inject(method = "handleMouseInput", at = @At("HEAD"))
    private void cnpcgun$coordinates(CallbackInfo ci) {
        if (EmbeddedInput.screen != null) { mouseX = EmbeddedInput.x; mouseY = EmbeddedInput.y; }
    }
}
