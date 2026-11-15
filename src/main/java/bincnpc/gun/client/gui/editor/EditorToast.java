package bincnpc.gun.client.gui.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

public final class EditorToast {
    private String message = "";
    private long started;
    public void show(String text) {
        message = text; started = System.currentTimeMillis();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.1F, 0.45F));
    }
    public void render(GuiGraphics g, int width) {
        long age = System.currentTimeMillis() - started; if (message.isEmpty() || age >= 1700) return;
        var font = Minecraft.getInstance().font; int w = Math.min(width - 16, Math.max(120, font.width(message) + 28));
        float slide = age < 180 ? age / 180f : age > 1500 ? (1700 - age) / 200f : 1;
        int x = width - 8 - (int)(w * Mth.clamp(slide, 0, 1));
        EditorStyle.panel(g, new EditorLayout.Box(x, 39, w, 26), EditorStyle.GREEN);
        g.fill(x + 5, 46, x + 8, 54, 0xFFAFC494);
        EditorStyle.text(g, font, message, x + 15, 48, w - 22, EditorStyle.WHITE);
    }
}
