package bincnpc.gun.mixin.client;

import java.io.IOException;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Native protected input methods, dispatched by the editor without changing currentScreen. */
@Mixin(GuiScreen.class)
public interface GuiScreenAccess {
    @Invoker("keyTyped") void cnpcgun$key(char letter, int key) throws IOException;
    @Accessor("eventButton") void cnpcgun$button(int button);
}
