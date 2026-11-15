package bincnpc.gun.client.gui.editor;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Searchable cards with one semantic item per row. */
public final class EditorList extends AbstractWidget {
    public record Row(String id, String title, String subtitle, boolean enabled) {}
    private final Font font;
    private List<Row> all;
    private final Consumer<String> selected;
    private List<Row> shown;
    private String selection = "";
    private int scroll;
    private String query = "";
    private static final int ROW = 34;
    public EditorList(Font font, EditorLayout.Box box, List<Row> rows, String initial, Consumer<String> selected) {
        super(box.x(), box.y(), box.width(), box.height(), Component.translatable("cnpcgun.editor.selection"));
        this.font = font; all = List.copyOf(rows); shown = all; selection = initial; this.selected = selected;
    }
    public void filter(String search) {
        String needle = search.trim().toLowerCase(Locale.ROOT);
        query = search;
        shown = all.stream().filter(row -> (row.title + " " + row.subtitle + " " + row.id).toLowerCase(Locale.ROOT).contains(needle)).toList(); scroll = 0;
    }
    public void replaceRows(List<Row> rows) { int prior = scroll; all = List.copyOf(rows); filter(query); scrollTo(prior); }
    public void selection(String id) { selection = id; }
    public int size() { return shown.size(); }
    public int scrollOffset() { return scroll; }
    public void scrollTo(int offset) { scroll = EditorLayout.clampScroll(offset, shown.size() * ROW, height - 6); }
    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        EditorStyle.panel(g, new EditorLayout.Box(getX(), getY(), width, height), EditorStyle.PAPER);
        g.enableScissor(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1);
        for (int i = 0; i < shown.size(); i++) {
            Row row = shown.get(i); int y = getY() + 3 + i * ROW - scroll;
            if (y + ROW <= getY() || y >= getY() + height) continue;
            boolean chosen = row.id.equals(selection), hover = mouseX >= getX() && mouseX < getX() + width && mouseY >= y && mouseY < y + ROW;
            if (chosen || hover) g.fill(getX() + 2, y, getX() + width - 2, y + ROW - 1, chosen ? EditorStyle.HOVER : 0xFFE2E3D8);
            if (chosen) g.fill(getX() + 2, y + 3, getX() + 5, y + ROW - 4, EditorStyle.GREEN);
            EditorStyle.text(g, font, row.title, getX() + 9, y + 6, width - 16, row.enabled ? EditorStyle.INK : EditorStyle.MUTED);
            EditorStyle.text(g, font, row.subtitle, getX() + 9, y + 19, width - 16, EditorStyle.MUTED);
            g.hLine(getX() + 7, getX() + width - 7, y + ROW - 1, EditorStyle.LINE);
        }
        g.disableScissor();
        if (shown.isEmpty()) EditorStyle.text(g, font, Component.translatable("cnpcgun.editor.noResults").getString(), getX() + 8, getY() + 10, width - 16, EditorStyle.MUTED);
        if (shown.size() * ROW > height - 6) {
            int track = height - 6, thumb = Math.max(12, track * track / (shown.size() * ROW));
            int y = getY() + 3 + (track - thumb) * scroll / Math.max(1, shown.size() * ROW - track);
            g.fill(getX() + width - 4, y, getX() + width - 2, y + thumb, EditorStyle.MUTED);
        }
    }
    private void select(int index) { if (index >= 0 && index < shown.size()) { selection = shown.get(index).id; selected.accept(selection); } }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != 0 || !active || !new EditorLayout.Box(getX(), getY(), width, height).contains(x, y)) return false;
        select((int)(y - getY() - 3 + scroll) / ROW); return true;
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (!new EditorLayout.Box(getX(), getY(), width, height).contains(x, y)) return false;
        scroll = EditorLayout.clampScroll(scroll - (int)Math.round(delta * ROW), shown.size() * ROW, height - 6); return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (!active || !isFocused()) return false;
        int index = 0; for (int i = 0; i < shown.size(); i++) if (shown.get(i).id.equals(selection)) index = i;
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) {
            int next = Math.max(0, Math.min(shown.size() - 1, index + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1)));
            scroll = EditorLayout.clampScroll(Math.max(0, next * ROW - height / 2), shown.size() * ROW, height - 6); select(next); return true;
        }
        return false;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
