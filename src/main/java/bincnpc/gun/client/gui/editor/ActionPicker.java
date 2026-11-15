package bincnpc.gun.client.gui.editor;

import bincnpc.gun.client.EditorSession;
import bincnpc.gun.client.gui.GuiSupport;
import bincnpc.gun.common.api.SafeJson;
import com.google.gson.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import static bincnpc.gun.client.gui.editor.EditorLayout.Box;

/** Shared, categorized picker for combo nodes and the action editor. */
public final class ActionPicker extends EditorDialogs.Base {
    public static final String[] CATEGORIES = {"all", "blade", "sa", "phantom", "movement", "defense"};
    private final EditorSession session;
    private final Consumer<String> chosen;
    private String category = "all", query = "";
    private EditorTextField search;
    public ActionPicker(EditorDialogs.Host host, EditorSession session, Component title, Consumer<String> chosen) {
        super(host, title); this.session = session; this.chosen = chosen;
    }
    public static Component categoryLabel(String category) { return Component.translatable("cnpcgun.editor.actionCategory." + category); }
    public static boolean matches(JsonObject info, String category) {
        if (info == null) return false;
        String type = SafeJson.string(info, "type", "");
        return switch (category == null ? "all" : category) {
            case "blade" -> type.equals("NORMAL_ATTACK") || type.equals("COMBO_ATTACK");
            case "sa" -> type.equals("SPECIAL_ATTACK");
            case "phantom" -> type.equals("PHANTOM_SWORD");
            case "movement" -> type.equals("MOVEMENT");
            case "defense" -> Set.of("DEFENSE", "COUNTER", "LOCK_ON").contains(type);
            default -> true;
        };
    }
    public static Map<String, String> categories() {
        Map<String, String> rows = new LinkedHashMap<>();
        for (String id : CATEGORIES) rows.put(categoryLabel(id).getString(), id);
        return rows;
    }
    @Override protected void init() {
        super.init(); subtitle = GuiSupport.tr("cnpcgun.editor.actionPickerHint");
        int sideWidth = Math.max(66, Math.min(106, content.width() / 3));
        int rowHeight = Math.max(18, Math.min(24, content.height() / CATEGORIES.length));
        for (int i = 0; i < CATEGORIES.length; i++) {
            String id = CATEGORIES[i];
            EditorButton button = addRenderableWidget(new EditorButton(content.x(), content.y() + i * rowHeight, sideWidth, rowHeight - 2,
                categoryLabel(id), EditorButton.Kind.NAVIGATION, () -> { category = id; rebuild(); }));
            button.selected = category.equals(id); button.setTooltip(Tooltip.create(categoryLabel(id)));
        }
        Box main = new Box(content.x() + sideWidth + 6, content.y(), content.width() - sideWidth - 6, content.height());
        search = addRenderableWidget(new EditorTextField(font, main.x() + 4, main.y() + 4, main.width() - 8, 8, Component.translatable("cnpcgun.editor.searchActions")));
        search.setHint(Component.translatable("cnpcgun.editor.searchActions")); search.setValue(query);
        List<EditorList.Row> rows = new ArrayList<>();
        for (JsonElement raw : session.actions()) {
            if (!raw.isJsonObject()) continue;
            JsonObject info = raw.getAsJsonObject(); if (!matches(info, category)) continue;
            String type = SafeJson.string(info, "type", ""), id = SafeJson.string(info, "id", "");
            if (id.isEmpty()) continue;
            rows.add(new EditorList.Row(id, GuiSupport.actionName(info),
                GuiSupport.tr("cnpcgun.type." + type.toLowerCase(Locale.ROOT)), SafeJson.bool(info, "available", false)));
        }
        EditorList list = addRenderableWidget(new EditorList(font, main.below(23), rows, "", id -> { host.closeDialog(); chosen.accept(id); }));
        list.filter(query); search.setResponder(text -> { query = text; list.filter(text); });
        button(0, 1, Component.translatable("gui.cancel"), EditorButton.Kind.NORMAL, host::closeDialog);
    }
    @Override public void tick() { super.tick(); if (search != null) search.tick(); }
}
