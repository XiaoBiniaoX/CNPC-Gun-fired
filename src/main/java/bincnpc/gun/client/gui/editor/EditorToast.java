package bincnpc.gun.client.gui.editor;

import net.minecraft.client.Minecraft;
import bincnpc.gun.client.gui.legacy.GuiCompat.GuiGraphics;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.math.MathHelper;

public final class EditorToast {
    private String message = "";
    private long started;
    public void show(String text) {
        message = text; started = System.currentTimeMillis();
        Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.45F, 1.1F));
    }
    public void render(GuiGraphics g, int width) {
        long age = System.currentTimeMillis() - started; if (message.isEmpty() || age >= 1700) return;
        bincnpc.gun.client.gui.legacy.GuiCompat.Font font = bincnpc.gun.client.gui.legacy.GuiCompat.Font.current(); int w = Math.min(width - 16, Math.max(120, font.width(message) + 28));
        float slide = age < 180 ? age / 180f : age > 1500 ? (1700 - age) / 200f : 1;
        int x = width - 8 - (int)(w * MathHelper.clamp(slide, 0, 1));
        EditorStyle.panel(g, new EditorLayout.Box(x, 39, w, 26), EditorStyle.GREEN);
        g.fill(x + 5, 46, x + 8, 54, 0xFFAFC494);
        EditorStyle.text(g, font, message, x + 15, 48, w - 22, EditorStyle.WHITE);
    }
}
