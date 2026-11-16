package bincnpc.gun.client.gui.editor;

import com.google.gson.*;
import java.util.*;

/** Node editing preserves all three explicit edges and keeps canvas positions out of NPC data. */
public final class GraphDraft {
    public static final class Point  {
private final double x;
private final double y;
public Point(double x, double y) {
this.x = x; this.y = y;}
public double x() { return x; }
public double y() { return y; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof Point))return false; Point that=(Point)value; return java.util.Objects.equals(x, that.x) && java.util.Objects.equals(y, that.y); }
@Override public int hashCode() { return java.util.Objects.hash(x, y); }

}
    private JsonObject original;
    public final JsonObject graph;
    public final Map<String, Point> positions = new HashMap<>();
    public GraphDraft(JsonObject source, String initialId) {
        graph = source == null ? new JsonObject() : bincnpc.gun.common.legacy.Legacy.copy(source);
        if (!graph.has("id")) graph.addProperty("id", initialId == null ? "" : initialId);
        if (!graph.has("entryNode")) graph.addProperty("entryNode", "");
        if (!graph.has("nodes")) graph.add("nodes", new JsonArray());
        text(graph, "id"); text(graph, "entryNode");
        if (nodes().size() > 128) throw new IllegalArgumentException("node limit");
        for (JsonElement raw : nodes()) {
            if (!raw.isJsonObject()) throw new IllegalArgumentException("node must be an object");
            normalizeNode(raw.getAsJsonObject());
        }
        original = bincnpc.gun.common.legacy.Legacy.copy(graph);
    }
    private static String text(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("graph field: " + key);
        return value.getAsString();
    }
    private static boolean absent(JsonObject object, String key) { return !object.has(key) || object.get(key).isJsonNull(); }
    private static void normalizeNode(JsonObject node) {
        if (text(node, "id").isEmpty() || text(node, "action").isEmpty()) throw new IllegalArgumentException("node identity");
        if (absent(node, "onSuccess")) node.addProperty("onSuccess", "end");
        if (absent(node, "onFailure")) node.addProperty("onFailure", "end");
        if (absent(node, "onTimeout")) node.addProperty("onTimeout", text(node, "onFailure"));
        for (String edge : bincnpc.gun.common.legacy.Legacy.list("onSuccess", "onFailure", "onTimeout")) text(node, edge);
        if (absent(node, "timeout")) node.addProperty("timeout", 200);
        JsonElement timeout = node.get("timeout");
        if (!timeout.isJsonPrimitive() || !timeout.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("node timeout");
        double ticks = timeout.getAsDouble();
        if (!Double.isFinite(ticks) || ticks != Math.rint(ticks) || ticks < 1 || ticks > 2400) throw new IllegalArgumentException("node timeout");
        for (String key : bincnpc.gun.common.legacy.Legacy.list("conditions", "interrupts")) {
            if (absent(node, key)) node.add(key, new JsonArray());
            else if (!node.get(key).isJsonArray()) throw new IllegalArgumentException("node array: " + key);
        }
        if (absent(node, "parameters")) node.add("parameters", new JsonObject());
        else if (!node.get("parameters").isJsonObject()) throw new IllegalArgumentException("node parameters");
    }
    public JsonArray nodes() {
        JsonElement value = graph.get("nodes");
        if (value == null || !value.isJsonArray()) throw new IllegalArgumentException("graph nodes must be an array");
        return value.getAsJsonArray();
    }
    public List<String> ids() { return bincnpc.gun.common.legacy.Legacy.jsonList(nodes()).stream().map(raw -> text(raw.getAsJsonObject(), "id")).collect(bincnpc.gun.common.legacy.Legacy.toList()); }
    public JsonObject node(String id) { for (JsonElement raw : nodes()) if (text(raw.getAsJsonObject(), "id").equals(id)) return raw.getAsJsonObject(); return null; }
    public boolean dirty() { return !graph.equals(original); }
    public JsonObject original() { return bincnpc.gun.common.legacy.Legacy.copy(original); }
    public void acknowledge(JsonObject submitted) { if (submitted == null) throw new IllegalArgumentException("submitted graph"); original = bincnpc.gun.common.legacy.Legacy.copy(submitted); }
    public String unique(String prefix) { int n = 1; while (ids().contains(prefix + n)) n++; return prefix + n; }
    public String add(String action) {
        if (action == null || action.isEmpty()) throw new IllegalArgumentException("node action");
        if (nodes().size() >= 128) throw new IllegalArgumentException("node limit");
        String id = unique("n"); JsonObject node = new JsonObject(); node.addProperty("id", id); node.addProperty("action", action);
        node.add("parameters", new JsonObject()); node.add("conditions", new JsonArray()); node.add("interrupts", new JsonArray()); node.addProperty("timeout", 200);
        for (String edge : bincnpc.gun.common.legacy.Legacy.list("onSuccess", "onFailure", "onTimeout")) node.addProperty(edge, "end");
        if ((nodes().size() == 0)) graph.addProperty("entryNode", id);
        else { JsonObject prior = nodes().get(nodes().size() - 1).getAsJsonObject(); if (prior.get("onSuccess").getAsString().equals("end")) prior.addProperty("onSuccess", id); }
        nodes().add(node); return id;
    }
    public String copy(String id) {
        if (nodes().size() >= 128 || node(id) == null) throw new IllegalArgumentException("node");
        JsonObject copy = bincnpc.gun.common.legacy.Legacy.copy(node(id)); String next = unique("copy"); copy.addProperty("id", next); nodes().add(copy); return next;
    }
    public void update(String oldId, JsonObject changed) {
        if (changed == null) throw new IllegalArgumentException("node");
        JsonObject replacement = bincnpc.gun.common.legacy.Legacy.copy(changed); normalizeNode(replacement);
        String next = text(replacement, "id");
        if (!next.matches("[A-Za-z0-9_.:-]{1,64}") || next.equals("end") || !next.equals(oldId) && ids().contains(next)) throw new IllegalArgumentException("node ID");
        int index = ids().indexOf(oldId); if (index < 0) throw new IllegalArgumentException("missing node");
        JsonObject before = bincnpc.gun.common.legacy.Legacy.copy(graph); nodes().set(index, replacement);
        if (!next.equals(oldId)) {
            if (graph.get("entryNode").getAsString().equals(oldId)) graph.addProperty("entryNode", next);
            for (JsonElement raw : nodes()) for (String edge : bincnpc.gun.common.legacy.Legacy.list("onSuccess", "onFailure", "onTimeout"))
                if (raw.getAsJsonObject().get(edge).getAsString().equals(oldId)) raw.getAsJsonObject().addProperty(edge, next);
        }
        try { validateEdges(); }
        catch (IllegalArgumentException e) { graph.entrySet().clear(); before.entrySet().forEach(entry -> graph.add(entry.getKey(), entry.getValue())); throw e; }
        if (positions.containsKey(oldId)) { Point position = positions.remove(oldId); positions.put(next, position); }
    }
    public void remove(String id) {
        int index = ids().indexOf(id); if (index < 0) return;
        nodes().remove(index); positions.remove(id);
        for (JsonElement raw : nodes()) for (String edge : bincnpc.gun.common.legacy.Legacy.list("onSuccess", "onFailure", "onTimeout"))
            if (raw.getAsJsonObject().get(edge).getAsString().equals(id)) raw.getAsJsonObject().addProperty(edge, "end");
        if (graph.get("entryNode").getAsString().equals(id)) graph.addProperty("entryNode", (nodes().size() == 0) ? "" : nodes().get(0).getAsJsonObject().get("id").getAsString());
    }
    public void reorder(String id, int offset) {
        int index = ids().indexOf(id), next = index + offset;
        if (index < 0 || next < 0 || next >= nodes().size()) return;
        JsonElement item = nodes().get(index); nodes().set(index, nodes().get(next)); nodes().set(next, item);
    }
    public void validateEdges() {
        Set<String> ids = new HashSet<>(ids()); if (ids.size() != nodes().size()) throw new IllegalArgumentException("duplicate node");
        if (ids.contains("end") || !ids.isEmpty() && !ids.contains(graph.get("entryNode").getAsString())) throw new IllegalArgumentException("entry node");
        Map<String, Integer> colors = new HashMap<>(); for (String id : ids) visit(id, ids, colors);
    }
    private void visit(String id, Set<String> ids, Map<String, Integer> colors) {
        if (id.equals("end")) return;
        if (!ids.contains(id)) throw new IllegalArgumentException("missing jump");
        int color = colors.getOrDefault(id, 0); if (color == 1) throw new IllegalArgumentException("cycle"); if (color == 2) return;
        colors.put(id, 1); JsonObject node = node(id);
        for (String edge : bincnpc.gun.common.legacy.Legacy.list("onSuccess", "onFailure", "onTimeout")) visit(node.get(edge).getAsString(), ids, colors);
        colors.put(id, 2);
    }
}
