package bincnpc.gun.client.gui.editor;

import com.google.gson.*;
import bincnpc.gun.common.config.ConfigSchema;
import bincnpc.gun.common.api.SafeJson;
import java.util.*;

/** Local edits survive refresh/revision conflicts until the server acknowledges them. */
public final class EditorDraft {
    private final JsonObject changed = new JsonObject();
    public JsonElement value(JsonObject base, String key) {
        JsonElement value = changed.has(key) ? changed.get(key) : base == null ? null : base.get(key);
        if (value != null && !value.isJsonNull()) return bincnpc.gun.common.legacy.Legacy.copy(value);
        bincnpc.gun.common.config.Parameter schema = ConfigSchema.get(key);
        return schema == null ? JsonNull.INSTANCE : SafeJson.copy(schema.defaultValue);
    }
    public void set(JsonObject base, String key, JsonElement value) {
        if (key == null || value == null || value.isJsonNull()) throw new IllegalArgumentException("draft value");
        if (value.equals(base == null ? null : base.get(key))) changed.remove(key); else changed.add(key, bincnpc.gun.common.legacy.Legacy.copy(value));
    }
    public boolean dirty() { return changed.size() > 0; }
    public int size() { return changed.size(); }
    public JsonObject snapshot() { return bincnpc.gun.common.legacy.Legacy.copy(changed); }
    public List<JsonObject> batches(int limit) {
        if (limit < 4) throw new IllegalArgumentException("batch limit");
        Map<String, JsonObject> groups = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : changed.entrySet()) groups.computeIfAbsent(group(entry.getKey()), ignored -> new JsonObject()).add(entry.getKey(), bincnpc.gun.common.legacy.Legacy.copy(entry.getValue()));
        List<JsonObject> batches = new ArrayList<>(); JsonObject batch = new JsonObject();
        for (JsonObject group : groups.values()) {
            if (group.size() > limit) throw new IllegalArgumentException("dependent fields exceed batch limit");
            if (batch.size() + group.size() > limit) { batches.add(batch); batch = new JsonObject(); }
            for (Map.Entry<String, JsonElement> entry : group.entrySet()) batch.add(entry.getKey(), entry.getValue());
        }
        if (batch.size() > 0) batches.add(batch); return batches;
    }
    private static String group(String key) {
        if (bincnpc.gun.common.legacy.Legacy.set("gun.minRange", "gun.range").contains(key)) return "gun.range";
        if (bincnpc.gun.common.legacy.Legacy.set("gun.minInterval", "gun.maxInterval").contains(key)) return "gun.interval";
        if (bincnpc.gun.common.legacy.Legacy.set("gun.reloadMinTicks", "gun.reloadMaxTicks").contains(key)) return "gun.reloadDuration";
        if (bincnpc.gun.common.legacy.Legacy.set("ammo.minimumReserve", "ammo.reserveLimit").contains(key)) return "ammo.reserve";
        if (key.startsWith("weapon.slot")) return key.substring(0, key.lastIndexOf('.'));
        if (key.startsWith("throw.slot") && (key.endsWith(".minRange") || key.endsWith(".maxRange"))) return key.substring(0, key.lastIndexOf('.')) + ".range";
        return key;
    }
    public void clear() { changed.entrySet().clear(); }
    public void reconcile(JsonObject current) {
        if (current == null) return;
        for (String key : new ArrayList<>(bincnpc.gun.common.legacy.Legacy.keys(changed))) if (changed.get(key).equals(current.get(key))) changed.remove(key);
    }
    public void acknowledge(JsonObject submitted) {
        if (submitted == null) return;
        for (String key : bincnpc.gun.common.legacy.Legacy.keys(submitted)) if (changed.has(key) && changed.get(key).equals(submitted.get(key))) changed.remove(key);
    }
}
