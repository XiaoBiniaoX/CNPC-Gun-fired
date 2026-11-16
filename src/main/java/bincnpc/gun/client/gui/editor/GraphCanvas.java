package bincnpc.gun.client.gui.editor;

import bincnpc.gun.client.gui.GuiSupport;
import com.google.gson.*;
import java.util.*;
import java.util.function.*;
import bincnpc.gun.client.gui.legacy.GuiCompat.Font;
import bincnpc.gun.client.gui.legacy.GuiCompat.GuiGraphics;
import bincnpc.gun.client.gui.legacy.GuiCompat.AbstractWidget;
import bincnpc.gun.client.gui.legacy.GuiCompat.NarrationElementOutput;
import bincnpc.gun.client.gui.legacy.GuiCompat.Component;

public final class GraphCanvas extends AbstractWidget {
    public static final class View  {
private final double x;
private final double y;
private final double zoom;
public View(double x, double y, double zoom) {
this.x = x; this.y = y; this.zoom = zoom;}
public double x() { return x; }
public double y() { return y; }
public double zoom() { return zoom; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof View))return false; View that=(View)value; return java.util.Objects.equals(x, that.x) && java.util.Objects.equals(y, that.y) && java.util.Objects.equals(zoom, that.zoom); }
@Override public int hashCode() { return java.util.Objects.hash(x, y, zoom); }

}
    private static final int NODE_W = 128, NODE_H = 54;
    private final Font font;
    private final GraphDraft draft;
    private final Function<String, String> actionName;
    private final Consumer<String> select, edit;
    private String selection, dragging = "";
    private double panX = 12, panY = 12, zoom = 1;
    private boolean panning, moved;
    private long lastClick;
    private String lastNode = "";
    public GraphCanvas(Font font, EditorLayout.Box box, GraphDraft draft, String initial, Function<String, String> actionName, Consumer<String> select, Consumer<String> edit) {
        super(box.x(), box.y(), box.width(), box.height(), Component.translatable("cnpcgun.gui.comboEditor"));
        this.font = font; this.draft = draft; selection = initial; this.actionName = actionName; this.select = select; this.edit = edit;
        int i = 0;
        for (String id : draft.ids()) { draft.positions.putIfAbsent(id, new GraphDraft.Point((i % 3) * 164, (i / 3) * 90)); i++; }
    }
    public View view() { return new View(panX, panY, zoom); }
    public void view(View view) { panX = view.x; panY = view.y; zoom = view.zoom; }
    public void arrange() {
        draft.positions.clear(); int columns = Math.max(1, Math.min(4, (width - 20) / 164)), i = 0;
        for (String id : draft.ids()) { draft.positions.put(id, new GraphDraft.Point((i % columns) * 164, (i / columns) * 90)); i++; }
        panX = 12; panY = 12; zoom = 1;
    }
    private String hit(double mouseX, double mouseY) {
        double x = (mouseX - getX() - panX) / zoom, y = (mouseY - getY() - panY) / zoom;
        for (String id : draft.ids()) {
            GraphDraft.Point p = draft.positions.get(id);
            if (x >= p.x() && x < p.x() + NODE_W && y >= p.y() && y < p.y() + NODE_H) return id;
        }
        return "";
    }
    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        EditorStyle.panel(g, new EditorLayout.Box(getX(), getY(), width, height), 0xFFE2E3D8);
        g.enableScissor(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1);
        for (int x = getX() + 8; x < getX() + width; x += 20) for (int y = getY() + 8; y < getY() + height; y += 20) g.fill(x, y, x + 1, y + 1, 0xFFC2C7BA);
        g.pose().pushPose(); g.pose().translate(getX() + panX, getY() + panY, 0); g.pose().scale((float)zoom, (float)zoom, 1);
        String[] edges = {"onSuccess", "onFailure", "onTimeout"}; int[] colors = {EditorStyle.GREEN, EditorStyle.RED, EditorStyle.GOLD};
        for (JsonElement raw : draft.nodes()) {
            JsonObject node = raw.getAsJsonObject(); GraphDraft.Point from = draft.positions.get(node.get("id").getAsString());
            for (int channel = 0; channel < edges.length; channel++) {
                String target = node.get(edges[channel]).getAsString(); GraphDraft.Point to = draft.positions.get(target);
                if (to == null) continue;
                int sx = (int)from.x() + NODE_W, sy = (int)from.y() + 19 + channel * 9;
                int tx = (int)to.x(), ty = (int)to.y() + 22 + channel * 5, turn = sx + 12 + channel * 4;
                g.hLine(sx, turn, sy, colors[channel]); g.vLine(turn, Math.min(sy, ty), Math.max(sy, ty), colors[channel]);
                g.hLine(Math.min(turn, tx), Math.max(turn, tx), ty, colors[channel]); g.fill(tx - 3, ty - 1, tx, ty + 2, colors[channel]);
            }
        }
        for (JsonElement raw : draft.nodes()) {
            JsonObject node = raw.getAsJsonObject(); String id = node.get("id").getAsString(); GraphDraft.Point p = draft.positions.get(id);
            int x = (int)Math.round(p.x()), y = (int)Math.round(p.y()); boolean chosen = id.equals(selection);
            EditorStyle.panel(g, new EditorLayout.Box(x, y, NODE_W, NODE_H), chosen ? EditorStyle.HOVER : EditorStyle.PAPER);
            if (chosen) g.renderOutline(x, y, NODE_W, NODE_H, EditorStyle.GREEN);
            boolean entry = id.equals(draft.graph.get("entryNode").getAsString());
            EditorStyle.text(g, font, id, x + 7, y + 6, NODE_W - (entry ? 48 : 12), EditorStyle.MUTED);
            if (entry) EditorStyle.text(g, font, GuiSupport.tr("cnpcgun.editor.entry"), x + NODE_W - 38, y + 6, 32, EditorStyle.GREEN);
            EditorStyle.text(g, font, actionName.apply(node.get("action").getAsString()), x + 7, y + 21, NODE_W - 16, EditorStyle.INK);
            int count = node.getAsJsonArray("conditions").size();
            EditorStyle.text(g, font, GuiSupport.tr("cnpcgun.editor.nodeSummary", count, node.get("timeout").getAsInt()), x + 7, y + 37, NODE_W - 16, EditorStyle.MUTED);
            for (int i = 0; i < 3; i++) g.fill(x + NODE_W - 2, y + 18 + i * 9, x + NODE_W + 2, y + 22 + i * 9, colors[i]);
        }
        g.pose().popPose(); g.disableScissor();
        if ((draft.nodes().size() == 0)) {
            EditorStyle.text(g, font, GuiSupport.tr("cnpcgun.editor.emptyGraph"), getX() + 14, getY() + 18, width - 28, EditorStyle.INK);
            EditorStyle.text(g, font, GuiSupport.tr("cnpcgun.editor.emptyGraphHint"), getX() + 14, getY() + 34, width - 28, EditorStyle.MUTED);
        }
        if (height >= 72) {
            int x = getX() + 8, y = getY() + height - 14;
            g.fill(getX() + 3, y - 4, getX() + width - 3, getY() + height - 3, EditorStyle.PAPER);
            String[] labels = {"success", "failure", "timeout"};
            for (int i = 0; i < labels.length; i++) {
                String label = GuiSupport.tr("cnpcgun.editor." + labels[i]);
                g.fill(x, y + 2, x + 4, y + 6, colors[i]);
                EditorStyle.text(g, font, label, x + 8, y, width - (x - getX()) - 12, EditorStyle.MUTED);
                x += font.width(label) + 18;
            }
            String scale = Math.round(zoom * 100) + "%";
            if (width - (x - getX()) >= font.width(scale) + 14)
                EditorStyle.text(g, font, scale, getX() + width - font.width(scale) - 8, y, font.width(scale), EditorStyle.MUTED);
        }
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (!new EditorLayout.Box(getX(), getY(), width, height).contains(x, y)) return false;
        if (button == 2) { panning = true; dragging = ""; moved = false; return true; }
        if (button != 0 && button != 1) return false;
        String id = hit(x, y); selection = id; select.accept(id);
        if (button == 1 && !id.isEmpty()) { edit.accept(id); return true; }
        dragging = id; panning = id.isEmpty(); moved = false;
        long now = System.currentTimeMillis();
        if (!id.isEmpty() && id.equals(lastNode) && now - lastClick < 280) { dragging = ""; edit.accept(id); }
        lastClick = now; lastNode = id; return true;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (panning) { panX += dx; panY += dy; moved = true; return true; }
        if (!dragging.isEmpty() && active) {
            GraphDraft.Point p = draft.positions.get(dragging);
            draft.positions.put(dragging, new GraphDraft.Point(Math.max(-10000, Math.min(10000, p.x() + dx / zoom)), Math.max(-10000, Math.min(10000, p.y() + dy / zoom))));
            moved = true; return true;
        }
        return false;
    }
    @Override public boolean mouseReleased(double x, double y, int button) { panning = false; dragging = ""; if (moved) lastNode = ""; return true; }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (!new EditorLayout.Box(getX(), getY(), width, height).contains(x, y)) return false;
        double before = zoom; zoom = Math.max(0.5, Math.min(1.75, zoom + delta * 0.1));
        panX = x - getX() - (x - getX() - panX) * zoom / before; panY = y - getY() - (y - getY() - panY) * zoom / before; return true;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
