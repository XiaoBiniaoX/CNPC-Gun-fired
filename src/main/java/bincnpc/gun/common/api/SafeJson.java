package bincnpc.gun.common.api;

import com.google.gson.*;
import java.lang.reflect.Array;
import java.util.*;

/** Copies data, never evaluates code or reflects over arbitrary script objects. */
public final class SafeJson {
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    public static final int MAX_BYTES = 65536;
    private SafeJson() {}
    public static JsonObject parseObject(String raw) { return object(parseBounded(raw, MAX_BYTES, 14)); }
    /** Stored roots contain independently bounded graphs and preset copies. */
    public static JsonObject persistedObject(String raw) {
        JsonElement parsed = parseBounded(raw, 4 * 1024 * 1024, 28);
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("expected stored object");
        return parsed.getAsJsonObject();
    }
    private static JsonElement parseBounded(String raw, int maxBytes, int maxDepth) {
        if (raw == null || raw.length() > maxBytes || raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxBytes)
            throw new IllegalArgumentException("configuration too large");
        int depth = 0; boolean quoted = false, escaped = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') { if (++depth > maxDepth) throw new IllegalArgumentException("data nesting limit"); }
            else if (c == '}' || c == ']') { if (--depth < 0) throw new IllegalArgumentException("unbalanced JSON"); }
        }
        if (quoted || depth != 0) throw new IllegalArgumentException("unbalanced JSON");
        try { return new JsonParser().parse(raw); }
        catch (JsonParseException e) { throw new IllegalArgumentException("invalid JSON", e); }
    }

    public static JsonElement copy(Object value) {
        JsonElement out = copy(value, 0, new int[]{8192});
        if (GSON.toJson(out).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("configuration too large");
        return out;
    }

    private static JsonElement copy(Object value, int depth, int[] budget) {
Object cnpcgun$value6; Iterable<?> cnpcgun$match6;

Object cnpcgun$value4; Map<?, ?> cnpcgun$match4;

Object cnpcgun$value3; CharSequence cnpcgun$match3;

Object cnpcgun$value2; Number cnpcgun$match2;

Object cnpcgun$value1; Boolean cnpcgun$match1;

Object cnpcgun$value0; JsonElement cnpcgun$match0;

        if (depth > 14 || --budget[0] < 0) throw new IllegalArgumentException("data nesting/size limit");
        if (value == null) return JsonNull.INSTANCE;
        if (((cnpcgun$value0 = value) instanceof JsonElement && (cnpcgun$match0 = (JsonElement)cnpcgun$value0) != null)) {
            // Revisit every element to enforce the same budget for parsed network JSON.
            if (cnpcgun$match0.isJsonNull()) return JsonNull.INSTANCE;
            if (cnpcgun$match0.isJsonPrimitive()) {
                JsonPrimitive p = cnpcgun$match0.getAsJsonPrimitive();
                return copy(p.isBoolean() ? p.getAsBoolean() : p.isNumber() ? p.getAsNumber() : p.getAsString(), depth, budget);
            }
            if (cnpcgun$match0.isJsonArray()) return copy(bincnpc.gun.common.legacy.Legacy.jsonList(cnpcgun$match0.getAsJsonArray()), depth, budget);
            Map<String, Object> map = new LinkedHashMap<>();
            cnpcgun$match0.getAsJsonObject().entrySet().forEach(e -> map.put(e.getKey(), e.getValue()));
            return copy(map, depth, budget);
        }
        if (((cnpcgun$value1 = value) instanceof Boolean && (cnpcgun$match1 = (Boolean)cnpcgun$value1) != null)) return new JsonPrimitive(cnpcgun$match1);
        if (((cnpcgun$value2 = value) instanceof Number && (cnpcgun$match2 = (Number)cnpcgun$value2) != null)) {
            if (!Double.isFinite(cnpcgun$match2.doubleValue())) throw new IllegalArgumentException("nonfinite number");
            return new JsonPrimitive(cnpcgun$match2);
        }
        if (((cnpcgun$value3 = value) instanceof CharSequence && (cnpcgun$match3 = (CharSequence)cnpcgun$value3) != null)) {
            if (cnpcgun$match3.length() > 4096) throw new IllegalArgumentException("string too long");
            return new JsonPrimitive(cnpcgun$match3.toString());
        }
        if (((cnpcgun$value4 = value) instanceof Map<?, ?> && (cnpcgun$match4 = (Map<?, ?>)cnpcgun$value4) != null)) {
            if (cnpcgun$match4.size() > 256) throw new IllegalArgumentException("too many fields");
            boolean indexed = !cnpcgun$match4.isEmpty();
            for (Object key : bincnpc.gun.common.legacy.Legacy.keys(cnpcgun$match4)) {
                if (!(key instanceof String) || !((String)key).matches("0|[1-9][0-9]*")) indexed = false;
            }
            if (indexed) {
                JsonArray array = new JsonArray();
                for (int i = 0; i < cnpcgun$match4.size(); i++) {
                    if (!cnpcgun$match4.containsKey(Integer.toString(i))) throw new IllegalArgumentException("sparse array");
                    array.add(copy(cnpcgun$match4.get(Integer.toString(i)), depth + 1, budget));
                }
                return array;
            }
            JsonObject obj = new JsonObject();
            for (Map.Entry<?, ?> e : cnpcgun$match4.entrySet()) {
Object cnpcgun$value5; String cnpcgun$match5;

                if (!(((cnpcgun$value5 = e.getKey()) instanceof String && (cnpcgun$match5 = (String)cnpcgun$value5) != null)) || cnpcgun$match5.length() > 128) throw new IllegalArgumentException("invalid field");
                obj.add(cnpcgun$match5, copy(e.getValue(), depth + 1, budget));
            }
            return obj;
        }
        if (((cnpcgun$value6 = value) instanceof Iterable<?> && (cnpcgun$match6 = (Iterable<?>)cnpcgun$value6) != null)) {
            JsonArray array = new JsonArray();
            for (Object v : cnpcgun$match6) array.add(copy(v, depth + 1, budget));
            return array;
        }
        if (value.getClass().isArray()) {
            JsonArray array = new JsonArray();
            for (int i = 0; i < Array.getLength(value); i++) array.add(copy(Array.get(value, i), depth + 1, budget));
            return array;
        }
        throw new IllegalArgumentException("expected primitive, map, list or array");
    }

    public static JsonObject object(Object value) {
        JsonElement json = copy(value);
        if (!json.isJsonObject()) throw new IllegalArgumentException("expected object");
        return json.getAsJsonObject();
    }

    public static Object plain(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        if (value.isJsonArray()) {
            Object[] out = new Object[value.getAsJsonArray().size()];
            for (int i = 0; i < out.length; i++) out[i] = plain(value.getAsJsonArray().get(i));
            return out;
        }
        if (value.isJsonObject()) {
            Map<String, Object> out = new LinkedHashMap<>();
            value.getAsJsonObject().entrySet().forEach(e -> out.put(e.getKey(), plain(e.getValue())));
            return out;
        }
        JsonPrimitive p = value.getAsJsonPrimitive();
        return p.isBoolean() ? p.getAsBoolean() : p.isNumber() ? p.getAsDouble() : p.getAsString();
    }

    public static String string(JsonObject obj, String key, String fallback) {
        JsonElement e = obj == null ? null : obj.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : fallback;
    }
    public static JsonObject objectField(JsonObject obj, String key) {
        JsonElement value = obj == null ? null : obj.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }
    public static JsonArray arrayField(JsonObject obj, String key) {
        JsonElement value = obj == null ? null : obj.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }
    public static boolean bool(JsonObject obj, String key, boolean fallback) {
        JsonElement value = obj == null ? null : obj.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : fallback;
    }
    public static int integer(JsonObject obj, String key, int fallback) {
        JsonElement value = obj == null ? null : obj.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return fallback;
        double number = value.getAsDouble();
        return Double.isFinite(number) && number == Math.rint(number) && number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE ? (int)number : fallback;
    }
}
