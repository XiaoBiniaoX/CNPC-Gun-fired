package bincnpc.gun.client.gui.legacy;

import net.minecraft.client.gui.GuiScreen;

/** Client-thread scope for native input; early hooks have no NPC dependencies. */
public final class EmbeddedInput {
    public static GuiScreen screen;
    public static int x, y;
    public static boolean wheel;
    private EmbeddedInput() {}
}
