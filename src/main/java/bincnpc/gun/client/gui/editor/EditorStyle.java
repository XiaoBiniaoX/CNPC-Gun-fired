package bincnpc.gun.client.gui.editor;

import bincnpc.gun.client.gui.legacy.GuiCompat.Font;
import bincnpc.gun.client.gui.legacy.GuiCompat.GuiGraphics;

public final class EditorStyle {
    public static final int IRON = 0xFFC6C6C6, PAPER = 0xFFEEECE2, SLATE = 0xFF3C4143;
    public static final int EDGE = 0xFF373737, INK = 0xFF293032, MUTED = 0xFF68706B;
    public static final int GREEN = 0xFF576C43, GOLD = 0xFFA88445, RED = 0xFFA34F47;
    public static final int WHITE = 0xFFF9F8F0, HOVER = 0xFFDCE1D1, LINE = 0xFFD0D0C5;
    private EditorStyle() {}
    public static void panel(GuiGraphics g, EditorLayout.Box box, int fill) {
        if (box.width() <= 0 || box.height() <= 0) return;
        g.fill(box.x(), box.y(), box.right(), box.bottom(), EDGE);
        g.fill(box.x() + 1, box.y() + 1, box.right() - 1, box.bottom() - 1, fill);
        g.hLine(box.x() + 1, box.right() - 2, box.y() + 1, WHITE);
        g.vLine(box.x() + 1, box.y() + 1, box.bottom() - 2, WHITE);
    }
    public static void slot(GuiGraphics g, int x, int y, int size, boolean selected) {
        g.fill(x, y, x + size, y + size, selected ? GREEN : EDGE);
        g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF8B8B80);
        g.hLine(x + 1, x + size - 2, y + 1, 0xFF54594F);
        g.vLine(x + 1, y + 1, y + size - 2, 0xFF54594F);
        g.hLine(x + 1, x + size - 2, y + size - 2, WHITE);
        g.vLine(x + size - 2, y + 1, y + size - 2, WHITE);
    }
    public static String clipped(Font font, String text, int width) {
        if (width <= 0) return "";
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }
    public static void text(GuiGraphics g, Font font, String text, int x, int y, int width, int color) {
        g.drawString(font, clipped(font, text, width), x, y, color, false);
    }
    public static void arrow(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 3, y + 3, x + 11, y + 5, color);
        g.fill(x + 1, y + 3, x + 3, y + 5, color);
        g.fill(x + 3, y + 1, x + 5, y + 3, color);
        g.fill(x + 3, y + 5, x + 5, y + 7, color);
    }
    public static void refresh(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 3, y + 1, x + 8, y + 3, color);
        g.fill(x + 1, y + 3, x + 3, y + 7, color);
        g.fill(x + 3, y + 7, x + 7, y + 9, color);
        g.fill(x + 7, y + 5, x + 9, y + 7, color);
        g.fill(x + 8, y + 1, x + 10, y + 5, color);
        g.fill(x + 6, y + 3, x + 8, y + 5, color);
    }
}
