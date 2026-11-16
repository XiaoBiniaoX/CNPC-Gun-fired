package bincnpc.gun.client.gui.editor;

import java.util.*;
import bincnpc.gun.client.gui.legacy.GuiCompat.*;
import bincnpc.gun.client.gui.legacy.GuiCompat.AbstractWidget;
import bincnpc.gun.client.gui.legacy.GuiCompat.NarrationElementOutput;
import bincnpc.gun.client.gui.legacy.GuiCompat.Component;
import bincnpc.gun.client.gui.legacy.GuiCompat.FormattedCharSequence;

/** Compact wrapped text, used for the exact unsaved-field list. */
public final class EditorScrollText extends AbstractWidget {
    private final Font font;
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private int scroll;
    public EditorScrollText(Font font, EditorLayout.Box box, List<String> text) {
        super(box.x(), box.y(), box.width(), box.height(), Component.empty()); this.font = font;
        for (String line : text) lines.addAll(font.split(Component.literal(line), Math.max(12, box.width() - 16)));
    }
    @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
        EditorStyle.panel(g, new EditorLayout.Box(getX(), getY(), width, height), EditorStyle.PAPER);
        g.enableScissor(getX() + 2, getY() + 2, getX() + width - 4, getY() + height - 2);
        for (int i = 0; i < lines.size(); i++) {
            int y = getY() + 5 + i * 12 - scroll;
            if (y + 10 >= getY() && y < getY() + height) g.drawString(font, lines.get(i), getX() + 6, y, EditorStyle.INK, false);
        }
        g.disableScissor();
        int total = lines.size() * 12 + 10;
        if (total > height) {
            int thumb = Math.max(8, height * height / total);
            int top = getY() + (height - thumb) * scroll / Math.max(1, total - height);
            g.fill(getX() + width - 4, top, getX() + width - 2, top + thumb, EditorStyle.MUTED);
        }
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (!isMouseOver(x, y)) return false;
        scroll = EditorLayout.clampScroll(scroll - (int)(delta * 24), lines.size() * 12 + 10, height); return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        int step = key == bincnpc.gun.client.gui.legacy.GuiCompat.Keys.KEY_DOWN ? 12 : key == bincnpc.gun.client.gui.legacy.GuiCompat.Keys.KEY_UP ? -12
            : key == bincnpc.gun.client.gui.legacy.GuiCompat.Keys.KEY_PAGE_DOWN ? height - 12 : key == bincnpc.gun.client.gui.legacy.GuiCompat.Keys.KEY_PAGE_UP ? 12 - height : 0;
        if (step == 0) return false; scroll = EditorLayout.clampScroll(scroll + step, lines.size() * 12 + 10, height); return true;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
