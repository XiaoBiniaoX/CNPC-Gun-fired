package bincnpc.gun.client.gui.editor;

import bincnpc.gun.client.gui.legacy.GuiCompat.Font;
import bincnpc.gun.client.gui.legacy.GuiCompat.GuiGraphics;
import bincnpc.gun.client.gui.legacy.GuiCompat.EditBox;
import bincnpc.gun.client.gui.legacy.GuiCompat.Component;

public final class EditorTextField extends EditBox {
    public boolean invalid;
    public EditorTextField(Font font, int x, int y, int width, int height, Component label) {
        super(font, x, y, width, height, label);
        setBordered(false); setTextColor(EditorStyle.INK); setTextColorUneditable(EditorStyle.MUTED); setMaxLength(128);
    }
    @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int edge = invalid ? EditorStyle.RED : isFocused() ? EditorStyle.GREEN : EditorStyle.MUTED;
        g.fill(getX() - 4, getY() - 4, getX() + getWidth() + 4, getY() + getHeight() + 4, edge);
        g.fill(getX() - 3, getY() - 3, getX() + getWidth() + 3, getY() + getHeight() + 3, active ? EditorStyle.WHITE : EditorStyle.IRON);
        super.renderWidget(g, mouseX, mouseY, partialTick);
    }
}
