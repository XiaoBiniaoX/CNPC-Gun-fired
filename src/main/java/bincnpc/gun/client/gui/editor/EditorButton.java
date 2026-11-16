package bincnpc.gun.client.gui.editor;

import net.minecraft.client.Minecraft;
import bincnpc.gun.client.gui.legacy.GuiCompat.GuiGraphics;
import bincnpc.gun.client.gui.legacy.GuiCompat.AbstractWidget;
import bincnpc.gun.client.gui.legacy.GuiCompat.NarrationElementOutput;
import bincnpc.gun.client.gui.legacy.GuiCompat.Component;
import bincnpc.gun.client.gui.legacy.GuiCompat.Keys;

public class EditorButton extends AbstractWidget {
    public enum Kind { NORMAL, PRIMARY, DANGER, NAVIGATION, BACK, REFRESH, SEGMENT, ADD, EDIT, TOOL }
    private final Runnable clicked;
    public final Kind kind;
    public boolean selected;
    public EditorButton(int x, int y, int width, int height, Component label, Kind kind, Runnable clicked) {
        super(x, y, width, height, label); this.kind = kind; this.clicked = clicked;
    }
    public EditorButton(EditorLayout.Box box, Component label, Kind kind, Runnable clicked) {
        this(box.x(), box.y(), box.width(), box.height(), label, kind, clicked);
    }
    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hover = isHoveredOrFocused();
        int fill = !active ? 0xFFD1D1C6 : selected || kind == Kind.PRIMARY ? EditorStyle.GREEN
            : kind == Kind.DANGER ? EditorStyle.RED : hover ? EditorStyle.HOVER : EditorStyle.PAPER;
        if (kind == Kind.NAVIGATION) fill = selected ? EditorStyle.PAPER : hover ? 0xFF555F58 : EditorStyle.SLATE;
        if (active && kind == Kind.ADD) fill = hover ? 0xFF819974 : 0xFF657E54;
        if (active && kind == Kind.EDIT) fill = hover ? 0xFFB6A378 : 0xFF9A8358;
        if (kind == Kind.TOOL) fill = !active ? 0xFF707673 : hover ? 0xFF58635D : EditorStyle.SLATE;
        if (kind == Kind.SEGMENT) fill = selected ? 0xFFBABFB5 : !active ? 0xFFD1D1C6 : hover ? EditorStyle.HOVER : EditorStyle.PAPER;
        int color = kind == Kind.NAVIGATION && !selected || active && (selected || kind == Kind.PRIMARY || kind == Kind.DANGER) ? EditorStyle.WHITE : EditorStyle.INK;
        if (kind == Kind.NAVIGATION) color = selected ? EditorStyle.INK : EditorStyle.WHITE;
        if (!active) color = EditorStyle.MUTED;
        if (kind == Kind.TOOL || active && (kind == Kind.ADD || kind == Kind.EDIT)) color = EditorStyle.WHITE;
        if (kind == Kind.SEGMENT && selected) color = EditorStyle.INK;
        EditorStyle.panel(g, new EditorLayout.Box(getX(), getY(), width, height), fill);
        if (selected && kind == Kind.NAVIGATION) g.fill(getX() + 1, getY() + 2, getX() + 4, getY() + height - 2, EditorStyle.GREEN);
        if (isFocused()) g.renderOutline(getX() + 2, getY() + 2, width - 4, height - 4, EditorStyle.GOLD);
        if (kind == Kind.REFRESH) { EditorStyle.refresh(g, getX() + (width - 10) / 2, getY() + (height - 10) / 2, color); return; }
        bincnpc.gun.client.gui.legacy.GuiCompat.Font font = bincnpc.gun.client.gui.legacy.GuiCompat.Font.current();
        int offset = kind == Kind.BACK ? 20 : kind == Kind.NAVIGATION ? 10 : 6;
        if (kind == Kind.BACK) EditorStyle.arrow(g, getX() + 6, getY() + (height - 8) / 2, color);
        String text = EditorStyle.clipped(font, getMessage().getString(), width - (kind == Kind.TOOL ? 4 : offset + 6));
        int textX = kind == Kind.NAVIGATION || kind == Kind.BACK ? getX() + offset : getX() + (width - font.width(text)) / 2;
        g.drawString(font, text, textX, getY() + (height - 8) / 2, color, false);
    }
    @Override public void onClick(double x, double y) { if (active) clicked.run(); }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (active && isFocused() && (key == Keys.KEY_ENTER || key == Keys.KEY_KP_ENTER || key == Keys.KEY_SPACE)) { clicked.run(); return true; }
        return false;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
