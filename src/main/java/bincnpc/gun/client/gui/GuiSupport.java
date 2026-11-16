package bincnpc.gun.client.gui;

import com.google.gson.*;
import java.util.*;
import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.client.gui.legacy.GuiCompat.Language;
import bincnpc.gun.client.gui.legacy.GuiCompat.Component;

public final class GuiSupport {
    private GuiSupport() {}
    public static String tr(String key, Object... args) { return Component.translatable(key, args).getString(); }
    public static String actionName(JsonObject info) {
        String label = SafeJson.string(info, "displayName", "");
        if (Language.getInstance().has(label)) return tr(label);
        String id = SafeJson.string(info, "id", ""); return id.substring(id.lastIndexOf('/') + 1).replace('_', ' ');
    }
    public static String value(JsonElement value) {
        if (value == null || value.isJsonNull()) return tr("cnpcgun.gui.unsupported");
        if (value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) return tr(primitive.getAsBoolean() ? "cnpcgun.gui.on" : "cnpcgun.gui.off");
            String text = primitive.getAsString(), key = "cnpcgun.value." + text.toLowerCase(Locale.ROOT);
            return primitive.isString() && Language.getInstance().has(key) ? tr(key) : text;
        }
        if (value.isJsonArray()) {
            List<String> parts = new ArrayList<>(); value.getAsJsonArray().forEach(p -> parts.add(value(p))); return String.join(", ", parts);
        }
        return "…";
    }
    public static JsonObject with(String key, String value) { JsonObject out = new JsonObject(); out.addProperty(key, value); return out; }
}
