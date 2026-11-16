package bincnpc.gun.common.config;

import com.google.gson.*;
import java.util.*;

public final class Parameter {
    private static final Set<String> NUMERIC_TYPES = bincnpc.gun.common.legacy.Legacy.set("integer", "number", "range", "vector");
    public final String id;
    public final String type;
    public final Object defaultValue;
    public final Double min;
    public final Double max;
    public final String[] values;
    public final String capability;
    public final String label;

    public Parameter(String id, String type, Object defaultValue, Double min, Double max, String[] values, String capability) {
        this.id = id; this.type = type; this.defaultValue = defaultValue;
        this.min = min; this.max = max;
        this.values = values == null ? new String[0] : values.clone();
        this.capability = capability == null ? "" : capability;
        this.label = "cnpcgun.config." + id;
    }

    public static Parameter number(String id, double def, double min, double max) {
        return new Parameter(id, "number", def, min, max, null, "");
    }
    public static Parameter integer(String id, int def, int min, int max) {
        return new Parameter(id, "integer", def, (double)min, (double)max, null, "");
    }
    public static Parameter bool(String id, boolean def) {
        return new Parameter(id, "boolean", def, null, null, null, "");
    }
    public static Parameter choice(String id, String def, String... values) {
        return new Parameter(id, "enum", def, null, null, values, "");
    }
    public Parameter requires(String cap) {
        return new Parameter(id, type, defaultValue, min, max, values, cap);
    }

    public JsonElement validate(JsonElement input) {
        if (input == null || input.isJsonNull() || type == null) throw new IllegalArgumentException(id);
        if (NUMERIC_TYPES.contains(type)
                && (min == null || max == null || !Double.isFinite(min) || !Double.isFinite(max) || min > max)) throw new IllegalArgumentException("invalid parameter bounds: " + id);
        switch (type) {
case "boolean":
{
                if (!input.isJsonPrimitive() || !input.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(id);
            } break;
case "integer":
case "number":
{
                if (!input.isJsonPrimitive() || !input.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(id);
                double n = input.getAsDouble();
                if (!Double.isFinite(n) || n < min || n > max || ("integer".equals(type) && n != Math.rint(n)))
                    throw new IllegalArgumentException(id);
            } break;
case "enum":
case "string":
case "target":
{
                if (!input.isJsonPrimitive() || !input.getAsJsonPrimitive().isString() || input.getAsString().length() > 128)
                    throw new IllegalArgumentException(id);
                if ("enum".equals(type) && (values == null || !Arrays.asList(values).contains(input.getAsString()))) throw new IllegalArgumentException(id);
            } break;
case "range":
{
                if (!input.isJsonArray() || input.getAsJsonArray().size() != 2) throw new IllegalArgumentException(id);
                for (JsonElement component : input.getAsJsonArray()) if (!component.isJsonPrimitive() || !component.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(id);
                double a = input.getAsJsonArray().get(0).getAsDouble(), b = input.getAsJsonArray().get(1).getAsDouble();
                if (!Double.isFinite(a) || !Double.isFinite(b) || a < min || b > max || a > b) throw new IllegalArgumentException(id);
            } break;
case "vector":
{
                if (!input.isJsonArray() || input.getAsJsonArray().size() != 3) throw new IllegalArgumentException(id);
                for (JsonElement component : input.getAsJsonArray()) {
                    if (!component.isJsonPrimitive() || !component.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(id);
                    double n = component.getAsDouble();
                    if (!Double.isFinite(n) || n < min || n > max) throw new IllegalArgumentException(id);
                }
            } break;
default:
throw new IllegalArgumentException("unknown parameter type");
}
        return bincnpc.gun.common.legacy.Legacy.copy(input);
    }
}
