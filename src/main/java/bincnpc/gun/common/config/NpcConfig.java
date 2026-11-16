package bincnpc.gun.common.config;

import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.api.Result;
import com.google.gson.*;

public final class NpcConfig {
    public static final int SCHEMA = 1;
    private JsonObject root;
    public long revision;

    public NpcConfig() { this(new JsonObject()); }
    public NpcConfig(JsonObject source) {
        if (source == null) throw new IllegalArgumentException("configuration is null");
        root = bincnpc.gun.common.legacy.Legacy.copy(source);
        int version = root.has("schemaVersion") ? Parameter.integer("schemaVersion", SCHEMA, 0, Integer.MAX_VALUE)
            .validate(root.get("schemaVersion")).getAsInt() : source.size() == 0 ? SCHEMA : 0;
        for (String field : new String[]{"values", "graphs", "actions", "presetCopies"}) {
            if (!root.has(field)) root.add(field, new JsonObject());
            else if (!root.get(field).isJsonObject()) throw new IllegalArgumentException("invalid " + field);
        }
        if (version == 0) {
            JsonObject values = root.getAsJsonObject("values");
            for (Parameter p : ConfigSchema.all()) if (root.has(p.id) && !values.has(p.id)) values.add(p.id, bincnpc.gun.common.legacy.Legacy.copy(root.get(p.id)));
            root.addProperty("migratedFromSchema", 0); version = SCHEMA;
        }
        root.addProperty("schemaVersion", version);
        repairRange("gun.minRange", "gun.range");
        repairRange("gun.minInterval", "gun.maxInterval");
        repairRange("gun.reloadMinTicks", "gun.reloadMaxTicks");
        repairRange("ammo.minimumReserve", "ammo.reserveLimit");
        for (int i = 0; i < 6; i++) if (bool("weapon.slot" + i + ".minRangeEnabled") && bool("weapon.slot" + i + ".maxRangeEnabled"))
            repairRange("weapon.slot" + i + ".minRange", "weapon.slot" + i + ".maxRange");
        for (int i = 0; i < 3; i++) repairRange("throw.slot" + i + ".minRange", "throw.slot" + i + ".maxRange");
    }
    private void repairRange(String lower, String upper) {
        if (number(lower) <= number(upper)) return;
        JsonObject values = root.getAsJsonObject("values");
        JsonObject recovery = root.has("recoveredRanges") && root.get("recoveredRanges").isJsonObject()
            ? root.getAsJsonObject("recoveredRanges") : new JsonObject();
        for (String key : new String[]{lower, upper}) {
            if (values.has(key)) recovery.add(key, bincnpc.gun.common.legacy.Legacy.copy(values.get(key)));
            values.add(key, SafeJson.copy(ConfigSchema.get(key).defaultValue));
        }
        root.add("recoveredRanges", recovery);
    }

    public JsonObject snapshot() { return bincnpc.gun.common.legacy.Legacy.copy(root); }
    public JsonElement get(String key) {
        Parameter p = ConfigSchema.get(key);
        if (p == null) return JsonNull.INSTANCE;
        JsonElement value = root.getAsJsonObject("values").get(key);
        try { return p.validate(value == null ? SafeJson.copy(p.defaultValue) : value); }
        catch (IllegalArgumentException | IllegalStateException e) { return SafeJson.copy(p.defaultValue); }
    }
    public boolean bool(String key) { return get(key).getAsBoolean(); }
    public int integer(String key) { return get(key).getAsInt(); }
    public double number(String key) { return get(key).getAsDouble(); }
    public String text(String key) { return get(key).getAsString(); }
    public void set(String key, JsonElement value) {
        Parameter p = ConfigSchema.get(key);
        if (p == null) throw new IllegalArgumentException("unknown configuration key");
        root.getAsJsonObject("values").add(key, p.validate(value));
        revision++;
    }
    public JsonObject graphs() { return bincnpc.gun.common.legacy.Legacy.copy(root.getAsJsonObject("graphs")); }
    public JsonObject graph(String id) {
        if (id == null) return null;
        JsonElement graph = root.getAsJsonObject("graphs").get(id);
        return graph != null && graph.isJsonObject() ? bincnpc.gun.common.legacy.Legacy.copy(graph.getAsJsonObject()) : null;
    }
    public void putGraph(String id, JsonObject graph) {
        root.getAsJsonObject("graphs").add(id, bincnpc.gun.common.legacy.Legacy.copy(graph));
        root.addProperty("selectedGraph", id);
        revision++;
    }
    public String selectedGraph() { return SafeJson.string(root, "selectedGraph", ""); }
    public Result selectGraph(String id) {
        if (graph(id) == null) return Result.fail("NOT_FOUND");
        if (!id.equals(selectedGraph())) { root.addProperty("selectedGraph", id); revision++; }
        return Result.success();
    }
    public Result removeGraph(String id) {
        if (graph(id) == null) return Result.fail("NOT_FOUND");
        root.getAsJsonObject("graphs").remove(id);
        if (id.equals(selectedGraph())) root.addProperty("selectedGraph", "");
        revision++; return Result.success();
    }
    public JsonObject action(String id) {
        JsonElement action = root.getAsJsonObject("actions").get(id);
        return action != null && action.isJsonObject() ? bincnpc.gun.common.legacy.Legacy.copy(action.getAsJsonObject()) : new JsonObject();
    }
    public void putAction(String id, JsonObject value) {
        root.getAsJsonObject("actions").add(id, bincnpc.gun.common.legacy.Legacy.copy(value)); revision++;
    }
    public JsonObject actions() { return bincnpc.gun.common.legacy.Legacy.copy(root.getAsJsonObject("actions")); }
    public void saveCopy(String id, JsonObject value) {
        root.getAsJsonObject("presetCopies").add(id, bincnpc.gun.common.legacy.Legacy.copy(value)); revision++;
    }
    public JsonObject getCopy(String id) {
        if (id == null) return null;
        JsonElement v = root.getAsJsonObject("presetCopies").get(id);
        return v != null && v.isJsonObject() ? bincnpc.gun.common.legacy.Legacy.copy(v.getAsJsonObject()) : null;
    }
    public Result removeCopy(String id) {
        if (getCopy(id) == null) return Result.fail("NOT_FOUND");
        root.getAsJsonObject("presetCopies").remove(id); revision++; return Result.success();
    }
    public void replace(JsonObject value) {
        root = new NpcConfig(value).snapshot(); revision++;
    }
}
