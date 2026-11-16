package bincnpc.gun.slashblade.combo;

import bincnpc.gun.common.api.SafeJson;
import com.google.gson.*;
import java.util.*;

public final class ComboGraph {
    public final String id, entryNode;
    public final Map<String, Node> nodes;
    public final JsonObject original;
    public static final class Node {
        public final String id, action, onSuccess, onFailure, onTimeout;
        public final int timeout;
        public final JsonObject parameters;
        public final JsonArray conditions, interrupts;
        Node(JsonObject obj) {
            for (String key : bincnpc.gun.common.legacy.Legacy.list("id", "action", "onSuccess", "onFailure", "onTimeout")) if (obj.has(key)
                    && (!obj.get(key).isJsonPrimitive() || !obj.get(key).getAsJsonPrimitive().isString())) throw new IllegalArgumentException("node field: " + key);
            for (String key : bincnpc.gun.common.legacy.Legacy.list("conditions", "interrupts")) if (obj.has(key) && !obj.get(key).isJsonArray()) throw new IllegalArgumentException("node array: " + key);
            id = SafeJson.string(obj, "id", ""); action = SafeJson.string(obj, "action", "");
            onSuccess = SafeJson.string(obj, "onSuccess", "end"); onFailure = SafeJson.string(obj, "onFailure", "end");
            onTimeout = SafeJson.string(obj, "onTimeout", onFailure);
            timeout = obj.has("timeout") ? bincnpc.gun.common.config.Parameter.integer("timeout", 200, 1, 2400).validate(obj.get("timeout")).getAsInt() : 200;
            parameters = obj.has("parameters") ? bincnpc.gun.common.legacy.Legacy.copy(obj.getAsJsonObject("parameters")) : new JsonObject();
            conditions = obj.has("conditions") && obj.get("conditions").isJsonArray() ? bincnpc.gun.common.legacy.Legacy.copy(obj.getAsJsonArray("conditions")) : new JsonArray();
            interrupts = obj.has("interrupts") && obj.get("interrupts").isJsonArray() ? bincnpc.gun.common.legacy.Legacy.copy(obj.getAsJsonArray("interrupts")) : new JsonArray();
        }
    }
    ComboGraph(JsonObject obj) {
        original = bincnpc.gun.common.legacy.Legacy.copy(obj); id = obj.get("id").getAsString(); entryNode = obj.get("entryNode").getAsString();
        Map<String, Node> out = new LinkedHashMap<>();
        for (JsonElement e : obj.getAsJsonArray("nodes")) {
            Node node = new Node(e.getAsJsonObject());
            if (out.putIfAbsent(node.id, node) != null) throw new IllegalArgumentException("duplicate node");
        }
        nodes = Collections.unmodifiableMap(out);
    }
}
