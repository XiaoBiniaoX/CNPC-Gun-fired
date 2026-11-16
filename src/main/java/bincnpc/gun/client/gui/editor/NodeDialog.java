package bincnpc.gun.client.gui.editor;

import bincnpc.gun.client.EditorSession;
import bincnpc.gun.client.gui.GuiSupport;
import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.config.Parameter;
import bincnpc.gun.slashblade.combo.GraphValidator;
import com.google.gson.*;
import java.util.*;
import java.util.function.Consumer;
import bincnpc.gun.client.gui.legacy.GuiCompat.Component;

/** Node properties are separated by purpose, using the same form controls as the main editor. */
public final class NodeDialog extends EditorDialogs.Base {
    private final EditorSession session;
    private final JsonObject node;
    private final List<String> nodeIds;
    private final Consumer<JsonObject> applied;
    private EditorForm form;
    private int tab;
    public NodeDialog(EditorDialogs.Host host, EditorSession session, JsonObject initial, List<String> nodeIds, Consumer<JsonObject> applied) {
        super(host, Component.translatable("cnpcgun.gui.node")); this.session = session; node = bincnpc.gun.common.legacy.Legacy.copy(initial); this.nodeIds = bincnpc.gun.common.legacy.Legacy.copyList(nodeIds); this.applied = applied;
    }
    private JsonObject parameters() { if (!node.has("parameters") || !node.get("parameters").isJsonObject()) node.add("parameters", new JsonObject()); return node.getAsJsonObject("parameters"); }
    private JsonArray array(String key) { if (!node.has(key) || !node.get(key).isJsonArray()) node.add(key, new JsonArray()); return node.getAsJsonArray(key); }
    @Override protected void init() {
        super.init(); subtitle = GuiSupport.tr("cnpcgun.editor.nodeHint");
        String[] names = {"nodeGeneral", "nodeParameters", "nodeConditions", "nodeInterrupts", "nodeEdges"};
        int w = (content.width() - 16) / 5;
        for (int i = 0; i < 5; i++) {
            int chosen = i;
            EditorButton button = addRenderableWidget(new EditorButton(content.x() + i * (w + 4), content.y(), w, 20, Component.translatable("cnpcgun.editor." + names[i]), EditorButton.Kind.NORMAL, () -> { if (form != null && form.invalid()) { failure(); return; } tab = chosen; rebuild(); })); button.selected = tab == i;
        }
        EditorLayout.Box area = content.below(26);
        form = null;
        if (tab == 0) {
            JsonObject action = session.actionInfo(node.get("action").getAsString());
            EditorButton actionButton = addRenderableWidget(new EditorButton(area.x(), area.y(), area.width(), 20, Component.literal(action == null ? GuiSupport.tr("cnpcgun.gui.unavailable") : GuiSupport.actionName(action)), EditorButton.Kind.NORMAL, () -> {
                EditorDialogs.actions(host, session, Component.translatable("cnpcgun.gui.actions"), id -> { if (!id.equals(node.get("action").getAsString())) { node.addProperty("action", id); node.add("parameters", new JsonObject()); } });
            })); actionButton.active = !session.pending;
            form = addRenderableWidget(new EditorForm(font, area.below(26), picker()));
            form.field(new Parameter("id", "string", "n1", null, null, null, ""), node.get("id"), value -> node.add("id", value));
            form.field(Parameter.integer("timeout", 200, 1, 2400), node.get("timeout"), value -> node.add("timeout", value));
        } else if (tab == 1) {
            form = addRenderableWidget(new EditorForm(font, area, picker()));
            JsonObject info = session.actionInfo(node.get("action").getAsString());
            if (info != null) for (Parameter p : EditorSession.parameters(info)) form.field(p, parameters().has(p.id) ? parameters().get(p.id) : SafeJson.copy(p.defaultValue), value -> parameters().add(p.id, value));
        } else if (tab == 2 || tab == 3) {
            String key = tab == 2 ? "conditions" : "interrupts", name = GuiSupport.tr("cnpcgun.gui." + key);
            addRenderableWidget(new EditorButton(area.x(), area.y(), area.width(), 20, Component.literal(GuiSupport.tr("cnpcgun.editor.editClauseCount", name, array(key).size())), EditorButton.Kind.NORMAL, () -> EditorDialogs.conditions(host, name, array(key), values -> node.add(key, values))));
            List<EditorList.Row> rows = new ArrayList<>();
            for (int i = 0; i < array(key).size(); i++) {
                JsonElement raw = array(key).get(i); String id = raw.isJsonObject() ? raw.getAsJsonObject().get("id").getAsString() : raw.getAsString();
                rows.add(new EditorList.Row(String.valueOf(i), GuiSupport.tr("cnpcgun.condition." + id), "", true));
            }
            addRenderableWidget(new EditorList(font, area.below(26), rows, "", ignored -> {}));
        } else {
            form = addRenderableWidget(new EditorForm(font, area, picker()));
            List<String> targets = new ArrayList<>(); targets.add("end"); for (String id : nodeIds) if (!id.equals(node.get("id").getAsString())) targets.add(id);
            for (String key : bincnpc.gun.common.legacy.Legacy.list("onSuccess", "onFailure", "onTimeout")) form.field(Parameter.choice(key, "end", targets.toArray(new String[0])), node.get(key), value -> node.add(key, value));
        }
        button(0, 2, Component.translatable("cnpcgun.gui.apply"), EditorButton.Kind.PRIMARY, () -> {
            try {
                if (form != null && form.invalid()) throw new IllegalArgumentException();
                String id = node.get("id").getAsString(); if (!id.matches("[A-Za-z0-9_.:-]{1,64}") || id.equals("end")) throw new IllegalArgumentException();
                Parameter.integer("timeout", 200, 1, 2400).validate(node.get("timeout"));
                GraphValidator.validateConditions(array("conditions")); GraphValidator.validateConditions(array("interrupts"));
                JsonObject info = session.actionInfo(node.get("action").getAsString());
                if (info != null) GraphValidator.parameters(parameters(), EditorSession.parameters(info));
                applied.accept(bincnpc.gun.common.legacy.Legacy.copy(node)); host.closeDialog();
            } catch (IllegalArgumentException | IllegalStateException e) { failure(); }
        });
        button(1, 2, Component.translatable("gui.cancel"), EditorButton.Kind.NORMAL, host::closeDialog);
    }
    @Override public void tick() { super.tick(); if (form != null) form.tick(); }
}
