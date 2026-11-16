package bincnpc.gun.client.gui.editor;

import java.util.*;

/** The final header layout is independent of the order in which addon tabs were injected. */
public final class MenuTabLayout {
    public static final class Tab  {
private final int id;
private final int width;
public Tab(int id, int width) {
this.id = id; this.width = width;}
public int id() { return id; }
public int width() { return width; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof Tab))return false; Tab that=(Tab)value; return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(width, that.width); }
@Override public int hashCode() { return java.util.Objects.hash(id, width); }

}
    public static final class Position  {
private final int id;
private final int x;
private final int y;
public Position(int id, int x, int y) {
this.id = id; this.x = x; this.y = y;}
public int id() { return id; }
public int x() { return x; }
public int y() { return y; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof Position))return false; Position that=(Position)value; return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(x, that.x) && java.util.Objects.equals(y, that.y); }
@Override public int hashCode() { return java.util.Objects.hash(id, x, y); }

}
    private MenuTabLayout() {}
    public static List<Position> arrange(List<Tab> input, int weaponId, int left, int top, int width) {
        List<Tab> tabs = new ArrayList<>(), actions = new ArrayList<>(); Tab weapon = null;
        for (Tab tab : input) {
            if (tab.id == weaponId) weapon = tab;
            else if (tab.id == 0 || tab.id == 66) actions.add(tab);
            else tabs.add(tab);
        }
        if (weapon != null) tabs.add(weapon);
        int actionWidth = actions.stream().mapToInt(Tab::width).sum() + (actions.size() > 1 ? 2 : 0);
        int room = width - 8 - actionWidth - 4;
        List<List<Tab>> rows = new ArrayList<>(); List<Tab> row = new ArrayList<>(); int used = 0;
        for (Tab tab : tabs) {
            if (!row.isEmpty() && used + tab.width > room) { rows.add(row); row = new ArrayList<>(); used = 0; }
            row.add(tab); used += tab.width;
        }
        if (!row.isEmpty()) rows.add(row);
        List<Position> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            int x = left + 4, y = top - 17 - (rows.size() - 1 - i) * 26;
            for (Tab tab : rows.get(i)) { result.add(new Position(tab.id, x, y)); x += tab.width; }
        }
        int right = left + width - 4;
        for (int id : new int[]{0, 66}) for (Tab tab : actions) if (tab.id == id) {
            right -= tab.width; result.add(new Position(id, right, top - 17)); right -= 2;
        }
        return result;
    }
}
