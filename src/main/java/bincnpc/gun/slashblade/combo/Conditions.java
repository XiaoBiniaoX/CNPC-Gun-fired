package bincnpc.gun.slashblade.combo;

import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.combat.CombatContext;
import com.google.gson.*;
import java.util.*;

public final class Conditions {
    public static final Set<String> IDS = bincnpc.gun.common.legacy.Legacy.set("always", "target_valid", "target_close", "target_far", "multiple_targets",
        "on_ground", "in_air", "low_health", "ammo_available", "damaged_recently", "target_changed",
        "distance", "health", "target_count", "weapon_type", "state", "resource", "combat_time");
    private Conditions() {}
    public static String[] ids() { return bincnpc.gun.common.legacy.Legacy.array(IDS.stream().sorted(), String[]::new); }
    public static void validate(JsonElement condition) {
        String id = condition.isJsonPrimitive() && condition.getAsJsonPrimitive().isString() ? condition.getAsString()
            : condition.isJsonObject() ? SafeJson.string(condition.getAsJsonObject(), "id", "") : "";
        if (!IDS.contains(id)) throw new IllegalArgumentException("unknown condition: " + id);
        if (condition.isJsonObject()) {
            JsonObject obj = condition.getAsJsonObject();
            for (String key : bincnpc.gun.common.legacy.Legacy.keys(obj)) {
                if (!bincnpc.gun.common.legacy.Legacy.set("id", "min", "max", "value", "not").contains(key)) throw new IllegalArgumentException("condition field: " + key);
            }
            for (String key : bincnpc.gun.common.legacy.Legacy.list("min", "max")) if (obj.has(key)) {
                JsonElement v = obj.get(key);
                if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber() || !Double.isFinite(v.getAsDouble())
                        || v.getAsDouble() < 0 || v.getAsDouble() > 100000) throw new IllegalArgumentException("condition range");
            }
            if (obj.has("min") && obj.has("max") && obj.get("min").getAsDouble() > obj.get("max").getAsDouble())
                throw new IllegalArgumentException("condition range");
            if (obj.has("not") && (!obj.get("not").isJsonPrimitive() || !obj.get("not").getAsJsonPrimitive().isBoolean()))
                throw new IllegalArgumentException("condition negation");
            if (bincnpc.gun.common.legacy.Legacy.set("weapon_type", "state").contains(id) && (!obj.has("value") || !obj.get("value").isJsonPrimitive()
                    || !obj.get("value").getAsJsonPrimitive().isString())) throw new IllegalArgumentException("condition value");
        } else if (bincnpc.gun.common.legacy.Legacy.set("distance", "health", "target_count", "weapon_type", "state", "resource", "combat_time").contains(id)) {
            throw new IllegalArgumentException("condition needs parameters");
        }
    }
    public static boolean all(JsonArray conditions, CombatContext ctx) {
        for (JsonElement condition : conditions) if (!test(condition, ctx)) return false;
        return true;
    }
    public static boolean any(JsonArray conditions, CombatContext ctx) {
        for (JsonElement condition : conditions) if (test(condition, ctx)) return true;
        return false;
    }
    public static boolean test(JsonElement condition, CombatContext ctx) {
        JsonObject params = condition.isJsonObject() ? condition.getAsJsonObject() : new JsonObject();
        String id = condition.isJsonObject() ? SafeJson.string(params, "id", "") : condition.getAsString();
        double health = ctx.npc.getHealth() / Math.max(1, ctx.npc.getMaxHealth());boolean result;
switch (id) {
case "always":
result = true; break;
case "target_valid":
result = ctx.validTarget(); break;
case "target_close":
result = ctx.validTarget() && ctx.distance <= 4; break;
case "target_far":
result = ctx.validTarget() && ctx.distance > 8; break;
case "multiple_targets":
result = ctx.targets().size() >= 2; break;
case "on_ground":
result = ctx.npc.onGround; break;
case "in_air":
result = !ctx.npc.onGround; break;
case "low_health":
result = health <= ctx.config.number("weapon.lowHealth"); break;
case "ammo_available":
result = !ctx.runtime.ammoEmpty; break;
case "damaged_recently":
result = ctx.now - ctx.runtime.lastHurt <= 20; break;
case "target_changed":
result = !Objects.equals(ctx.runtime.comboTarget, ctx.runtime.targetId); break;
case "distance":
result = ctx.validTarget() && range(ctx.distance, params); break;
case "health":
result = range(health, params); break;
case "target_count":
result = range(ctx.targets().size(), params); break;
case "combat_time":
result = ctx.validTarget() && range(ctx.now - ctx.runtime.firstTargetTick, params); break;
case "weapon_type":
result = ctx.runtime.domain.equals(SafeJson.string(params, "value", "")); break;
case "state":
result = ctx.runtime.combatState.equals(SafeJson.string(params, "value", "")); break;
case "resource":
{
                JsonObject state = ctx.data.blade.state(ctx);
                result = state.has("resource") && state.get("resource").isJsonPrimitive() && range(state.get("resource").getAsDouble(), params); break;
            }
default:
result = false; break;
};
        return params.has("not") && params.get("not").getAsBoolean() ? !result : result;
    }
    private static boolean range(double value, JsonObject params) {
        return value >= (params.has("min") ? params.get("min").getAsDouble() : 0)
            && value <= (params.has("max") ? params.get("max").getAsDouble() : 100000);
    }
}
