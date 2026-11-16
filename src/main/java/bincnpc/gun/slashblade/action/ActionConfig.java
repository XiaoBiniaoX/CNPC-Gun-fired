package bincnpc.gun.slashblade.action;

import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.config.Parameter;
import bincnpc.gun.slashblade.combo.*;
import com.google.gson.*;
import java.util.*;
import static bincnpc.gun.common.config.Parameter.*;

public final class ActionConfig {
    public static final Parameter[] SCHEMA = {
        bool("enabled", false), bool("auto", true), integer("priority", 100, 0, 1000),
        number("minDistance", 0, 0, 128), number("maxDistance", 32, 0, 128),
        integer("targetCount", 1, 1, 16), choice("targetType", "ANY", "ANY", "PLAYER", "NPC", "MOB"),
        number("cooldownMultiplier", 1, 0.05, 10), number("resourceMultiplier", 1, 0.1, 10),
        integer("resourceReserve", 0, 0, 100000), bool("interruptNormal", false),
        bool("consecutive", false), bool("linkPhantom", false),
        choice("failure", "SKIP", "SKIP", "STOP"), new Parameter("successNode", "string", "", null, null, null, "")
    };
    private ActionConfig() {}
    public static JsonObject validate(JsonObject source, ActionRegistry.Definition def) {
        JsonObject input = bincnpc.gun.common.legacy.Legacy.copy(source);
        JsonArray conditions = input.has("conditions") ? input.getAsJsonArray("conditions") : new JsonArray();
        GraphValidator.validateConditions(conditions);
        JsonArray stages = input.has("stages") ? input.getAsJsonArray("stages") : new JsonArray();
        if (stages.size() > 16) throw new IllegalArgumentException("stages");
        for (JsonElement stage : stages) if (!stage.isJsonPrimitive() || !stage.getAsJsonPrimitive().isString()
                || stage.getAsString().length() > 64) throw new IllegalArgumentException("stage");
        JsonObject params = input.has("parameters") ? input.getAsJsonObject("parameters") : new JsonObject();
        input.remove("conditions"); input.remove("stages"); input.remove("parameters");
        JsonObject out = GraphValidator.parameters(input, SCHEMA);
        if (out.get("minDistance").getAsDouble() > out.get("maxDistance").getAsDouble()) throw new IllegalArgumentException("distance range");
        out.add("conditions", bincnpc.gun.common.legacy.Legacy.copy(conditions)); out.add("stages", bincnpc.gun.common.legacy.Legacy.copy(stages));
        out.add("parameters", GraphValidator.parameters(params, def.info.parameters));
        return out;
    }
    public static JsonElement value(JsonObject obj, String id) {
        if (obj.has(id)) return obj.get(id);
        for (Parameter p : SCHEMA) if (p.id.equals(id)) return SafeJson.copy(p.defaultValue);
        return JsonNull.INSTANCE;
    }
}
