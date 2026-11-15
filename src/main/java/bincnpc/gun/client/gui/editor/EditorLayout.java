package bincnpc.gun.client.gui.editor;

import java.util.*;

/** Integer-pixel layout shared by rendering and the viewport regression checks. */
public final class EditorLayout {
    public record Box(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) { return px >= x && py >= y && px < right() && py < bottom(); }
        public boolean intersects(Box other) { return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y; }
        public Box inset(int n) { return new Box(x + n, y + n, Math.max(0, width - n * 2), Math.max(0, height - n * 2)); }
        public Box below(int offset) { return new Box(x, y + offset, width, Math.max(0, height - offset)); }
    }
    public final int width, height;
    public final Box header, sidebar, workspace, footer, body;
    public EditorLayout(int width, int height) {
        this.width = width; this.height = height;
        header = new Box(0, 0, width, 34);
        footer = new Box(0, height - 26, width, 26);
        int sideWidth = Math.max(84, Math.min(128, width / 6));
        sidebar = new Box(8, 42, sideWidth, Math.max(0, height - 76));
        workspace = new Box(sidebar.right() + 8, 42, Math.max(0, width - sidebar.right() - 16), Math.max(0, height - 76));
        body = workspace.below(28);
    }
    public Box navigation(int index) { return new Box(sidebar.x(), sidebar.y() + index * 22, sidebar.width(), 20); }
    public Box saveButton() { return new Box(width - 96, height - 22, 88, 18); }
    public Box discardButton() { return new Box(width - 184, height - 22, 82, 18); }
    public int statusWidth() { return width - 200; }
    public Box weaponSlot(int index) {
        int gap = body.width() >= 300 ? 6 : 4;
        int cellWidth = (body.width() - 16 - gap * 5) / 6;
        return new Box(body.x() + 8 + index * (cellWidth + gap), body.y() + 5, cellWidth, 45);
    }
    public Box weaponForm() { return body.below(56); }
    public Box listPane() {
        int listWidth = Math.max(92, Math.min(200, body.width() / 3));
        return new Box(body.x(), body.y(), listWidth, body.height());
    }
    public Box detailPane() { Box list = listPane(); return new Box(list.right() + 8, body.y(), Math.max(0, body.right() - list.right() - 8), body.height()); }
    public static Box dialog(int width, int height) { return new Box(Math.max(8, (width - 460) / 2), 12, Math.min(460, width - 16), height - 24); }
    public static Box dialogContent(Box box) { return new Box(box.x() + 10, box.y() + 42, box.width() - 20, box.height() - 78); }
    public record InventorySlot(int index, Box box) {}
    public static List<InventorySlot> inventorySlots(Box content) {
        int cell = Math.min(26, (content.width() - 32) / 9), gap = 2;
        int left = content.x() + Math.max(0, (content.width() - (cell + gap) * 9 - 4 - cell) / 2);
        List<InventorySlot> result = new ArrayList<>();
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            result.add(new InventorySlot(9 + row * 9 + column, new Box(left + column * (cell + gap), content.y() + 11 + row * (cell + gap), cell, cell)));
        int hotbarY = content.y() + 18 + 3 * (cell + gap);
        for (int i = 0; i < 9; i++) result.add(new InventorySlot(i, new Box(left + i * (cell + gap), hotbarY, cell, cell)));
        result.add(new InventorySlot(40, new Box(Math.min(content.right() - cell, left + 9 * (cell + gap) + 4), hotbarY, cell, cell)));
        return result;
    }
    /** Zero parts is a heading; multiple components occupy one complete row. */
    public record FormField(int parts, boolean text) {}
    public record FormCell(Box row, List<Box> controls) {}
    public record FormGrid(List<FormCell> cells, int totalHeight) {}
    public static FormGrid formGrid(Box view, List<FormField> fields, boolean compact, int scroll) {
        int rowHeight = compact ? 26 : 30, columns = view.width() >= (compact ? 280 : 500) ? 2 : 1;
        int cellWidth = (view.width() - 12 - (columns - 1) * 8) / columns, column = 0, y = compact ? 3 : 4;
        List<FormCell> cells = new ArrayList<>();
        for (FormField field : fields) {
            boolean full = field.parts != 1;
            if (full && column != 0) { y += rowHeight; column = 0; }
            int h = field.parts == 0 ? 22 : full ? 46 : rowHeight;
            Box row = new Box(view.x() + 6 + column * (cellWidth + 8), view.y() + y - scroll, full ? view.width() - 12 : cellWidth, h);
            List<Box> controls = new ArrayList<>();
            if (field.parts == 1) {
                int controlWidth = Math.max(52, Math.min(126, cellWidth / 3));
                controls.add(new Box(row.right() - controlWidth - 4, row.y() + (field.text ? compact ? 10 : 12 : compact ? 3 : 5), controlWidth, field.text ? 8 : 20));
            } else if (field.parts > 1) {
                int partWidth = (row.width() - 8 - (field.parts - 1) * 8) / field.parts, labelWidth = field.parts == 2 ? 24 : 12;
                for (int i = 0; i < field.parts; i++)
                    controls.add(new Box(row.x() + 8 + i * (partWidth + 8) + labelWidth, row.y() + 28, partWidth - labelWidth - 8, 8));
            }
            cells.add(new FormCell(row, controls));
            if (full) y += h;
            else if (++column == columns) { y += rowHeight; column = 0; }
        }
        return new FormGrid(cells, y + (column == 0 ? 0 : rowHeight) + (compact ? 3 : 4));
    }
    public static int clampScroll(int scroll, int totalHeight, int viewHeight) { return Math.max(0, Math.min(scroll, Math.max(0, totalHeight - viewHeight))); }
}
