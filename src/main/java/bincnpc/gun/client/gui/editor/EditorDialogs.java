package bincnpc.gun.client.gui.editor;

import bincnpc.gun.client.gui.GuiSupport;
import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.config.Parameter;
import bincnpc.gun.slashblade.combo.Conditions;
import com.google.gson.*;
import java.util.*;
import java.util.function.Consumer;
import bincnpc.gun.client.gui.legacy.GuiCompat.GuiGraphics;
import bincnpc.gun.client.gui.legacy.GuiCompat.Screen;
import bincnpc.gun.client.gui.legacy.GuiCompat.Component;

/** Local dialogs render inside the fullscreen editor; the active Minecraft screen keeps its session. */
public final class EditorDialogs {
    public interface Host { void openDialog(Screen dialog); void closeDialog(); }
    private EditorDialogs() {}
    public static void choose(Host host, Component title, Map<String, String> options, Consumer<String> chosen) { host.openDialog(new Choice(host, title, options, chosen)); }
    public static void chooseRows(Host host, Component title, List<EditorList.Row> options, Consumer<String> chosen) { host.openDialog(new Choice(host, title, options, chosen)); }
    public static void actions(Host host, bincnpc.gun.client.EditorSession session, Component title, Consumer<String> chosen) { host.openDialog(new ActionPicker(host, session, title, chosen)); }
    public static void form(Host host, Component title, Parameter[] schema, JsonObject initial, Consumer<JsonObject> applied) { host.openDialog(new Form(host, title, schema, initial, applied)); }
    public static void conditions(Host host, String title, JsonArray initial, Consumer<JsonArray> applied) { host.openDialog(new Clauses(host, Component.literal(title), initial, applied)); }
    public static final class Decision  {
private final Component label;
private final EditorButton.Kind kind;
private final Runnable action;
public Decision(Component label, EditorButton.Kind kind, Runnable action) {
this.label = label; this.kind = kind; this.action = action;}
public Component label() { return label; }
public EditorButton.Kind kind() { return kind; }
public Runnable action() { return action; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof Decision))return false; Decision that=(Decision)value; return java.util.Objects.equals(label, that.label) && java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(action, that.action); }
@Override public int hashCode() { return java.util.Objects.hash(label, kind, action); }

}
    public static void confirm(Host host, Component title, String message, List<Decision> choices) { confirm(host, title, message, bincnpc.gun.common.legacy.Legacy.list(), choices); }
    public static void confirm(Host host, Component title, String message, List<String> details, List<Decision> choices) { host.openDialog(new Confirm(host, title, message, details, choices)); }

    public abstract static class Base extends Screen {
        protected final Host host;
        protected EditorLayout.Box box, content;
        protected String error = "", subtitle = "";
        private boolean rebuildPending;
        private static final class FormMemory  {
private final int scroll;
private final Map<String, List<String>> invalid;
public FormMemory(int scroll, Map<String, List<String>> invalid) {
this.scroll = scroll; this.invalid = invalid;}
public int scroll() { return scroll; }
public Map<String, List<String>> invalid() { return invalid; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof FormMemory))return false; FormMemory that=(FormMemory)value; return java.util.Objects.equals(scroll, that.scroll) && java.util.Objects.equals(invalid, that.invalid); }
@Override public int hashCode() { return java.util.Objects.hash(scroll, invalid); }

}
        protected Base(Host host, Component title) { super(title); this.host = host; }
        @Override protected void init() {
            box = EditorLayout.dialog(width, height);
            content = EditorLayout.dialogContent(box);
        }
        public final void rebuild() { rebuildPending = true; }
        private void rebuildNow() {
Object cnpcgun$value1; EditorForm cnpcgun$match1;

Object cnpcgun$value0; EditorForm cnpcgun$match0;

            if (!rebuildPending) return; rebuildPending = false;
            List<FormMemory> memory = new ArrayList<>();
            for (bincnpc.gun.client.gui.legacy.GuiCompat.AbstractWidget child : children()) if (((cnpcgun$value0 = child) instanceof EditorForm && (cnpcgun$match0 = (EditorForm)cnpcgun$value0) != null)) memory.add(new FormMemory(cnpcgun$match0.scrollOffset(), cnpcgun$match0.invalidText()));
            clearWidgets(); init(); int index = 0;
            for (bincnpc.gun.client.gui.legacy.GuiCompat.AbstractWidget child : children()) if (((cnpcgun$value1 = child) instanceof EditorForm && (cnpcgun$match1 = (EditorForm)cnpcgun$value1) != null) && index < memory.size()) {
                FormMemory saved = memory.get(index++); cnpcgun$match1.scrollTo(saved.scroll); cnpcgun$match1.restoreInvalidText(saved.invalid);
            }
        }
        protected EditorButton button(int index, int count, Component label, EditorButton.Kind kind, Runnable clicked) {
            int w = (box.width() - 20 - (count - 1) * 6) / count;
            EditorButton button = addRenderableWidget(new EditorButton(box.x() + 10 + index * (w + 6), box.bottom() - 28, w, 20, label, kind, clicked));
            button.setTooltip(bincnpc.gun.client.gui.legacy.GuiCompat.Tooltip.create(label)); return button;
        }
        @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            rebuildNow();
            g.flush();
            g.fill(0, 0, width, height, 0xA0222723); EditorStyle.panel(g, box, panelFill());
            g.fill(box.x() + 2, box.y() + 2, box.right() - 2, box.y() + 36, EditorStyle.SLATE);
            EditorStyle.text(g, font, title.getString(), box.x() + 12, box.y() + 9, box.width() - 24, EditorStyle.WHITE);
            EditorStyle.text(g, font, error.isEmpty() ? subtitle : error, box.x() + 12, box.y() + 23, box.width() - 24, error.isEmpty() ? 0xFFC9CEC0 : 0xFFF1B6A7);
            super.render(g, mouseX, mouseY, partialTick);
        }
        @Override public void onClose() { host.closeDialog(); }
        @Override public void tick() { rebuildNow(); }
        @Override public boolean isPauseScreen() { return false; }
        protected void failure() { error = GuiSupport.tr("cnpcgun.error.invalid_argument"); }
        protected int panelFill() { return EditorStyle.PAPER; }
        protected EditorForm.Picker picker() { return (title, options, chosen) -> choose(host, title, options, chosen); }
    }
    private static final class Choice extends Base {
        private final List<EditorList.Row> choices;
        private final Consumer<String> chosen;
        private String query = "";
        Choice(Host host, Component title, Map<String, String> options, Consumer<String> chosen) {
            super(host, title); List<EditorList.Row> rows = new ArrayList<>(); options.forEach((label, id) -> rows.add(new EditorList.Row(id, label, "", true))); choices = bincnpc.gun.common.legacy.Legacy.copyList(rows); this.chosen = chosen;
        }
        Choice(Host host, Component title, List<EditorList.Row> options, Consumer<String> chosen) { super(host, title); choices = bincnpc.gun.common.legacy.Legacy.copyList(options); this.chosen = chosen; }
        @Override protected void init() {
            super.init(); subtitle = GuiSupport.tr("cnpcgun.editor.chooseHint");
            EditorTextField search = addRenderableWidget(new EditorTextField(font, content.x() + 4, content.y() + 4, content.width() - 8, 8, Component.translatable("cnpcgun.editor.search")));
            search.setValue(query);
            EditorList list = addRenderableWidget(new EditorList(font, content.below(23), choices, "", id -> { host.closeDialog(); chosen.accept(id); }));
            list.filter(query); search.setResponder(text -> { query = text; list.filter(text); });
            button(0, 1, Component.translatable("gui.cancel"), EditorButton.Kind.NORMAL, host::closeDialog);
        }
    }
    private static final class Form extends Base {
        private final Parameter[] schema;
        private final JsonObject data;
        private final Consumer<JsonObject> applied;
        private EditorForm form;
        private int scroll;
        Form(Host host, Component title, Parameter[] schema, JsonObject initial, Consumer<JsonObject> applied) {
            super(host, title); this.schema = schema.clone(); data = bincnpc.gun.common.legacy.Legacy.copy(initial); this.applied = applied;
        }
        @Override protected void init() {
            if (form != null) scroll = form.scrollOffset();
            super.init(); subtitle = GuiSupport.tr("cnpcgun.editor.formHint");
            form = addRenderableWidget(new EditorForm(font, content, picker()));
            for (Parameter p : schema) form.field(p, data.has(p.id) ? data.get(p.id) : SafeJson.copy(p.defaultValue), value -> data.add(p.id, value));
            form.scrollTo(scroll);
            button(0, 2, Component.translatable("cnpcgun.gui.apply"), EditorButton.Kind.PRIMARY, () -> {
                if (form.invalid()) { failure(); return; }
                try { for (Parameter p : schema) data.add(p.id, p.validate(data.has(p.id) ? data.get(p.id) : SafeJson.copy(p.defaultValue))); applied.accept(bincnpc.gun.common.legacy.Legacy.copy(data)); host.closeDialog(); }
                catch (IllegalArgumentException | IllegalStateException e) { failure(); }
            });
            button(1, 2, Component.translatable("gui.cancel"), EditorButton.Kind.NORMAL, host::closeDialog);
        }
        @Override public void tick() { super.tick(); form.tick(); }
    }
    private static final class Confirm extends Base {
        private final String message;
        private final List<String> details;
        private final List<Decision> decisions;
        Confirm(Host host, Component title, String message, List<String> details, List<Decision> decisions) { super(host, title); this.message = message; this.details = bincnpc.gun.common.legacy.Legacy.copyList(details); this.decisions = bincnpc.gun.common.legacy.Legacy.copyList(decisions); }
        @Override protected void init() {
            super.init(); subtitle = GuiSupport.tr("cnpcgun.editor.confirmHint");
            int w = Math.min(340, (int)(box.width() * 0.85)), h = Math.min(174, (int)(box.height() * 0.56));
            box = new EditorLayout.Box((width - w) / 2, (height - h) / 2, w, h); content = EditorLayout.dialogContent(box);
            List<String> lines = new ArrayList<>(); lines.add(message); lines.addAll(details);
            EditorScrollText scroll = addRenderableWidget(new EditorScrollText(font, content, lines)); setFocused(scroll);
            for (int i = 0; i < decisions.size(); i++) { Decision decision = decisions.get(i); button(i, decisions.size(), decision.label, decision.kind, () -> { host.closeDialog(); decision.action.run(); }); }
        }
    }
    private static final class Clauses extends Base {
        private final JsonArray data;
        private final Consumer<JsonArray> applied;
        private int selection;
        private EditorForm form;
        Clauses(Host host, Component title, JsonArray source, Consumer<JsonArray> applied) { super(host, title); data = bincnpc.gun.common.legacy.Legacy.copy(source); this.applied = applied; }
        @Override protected void init() {
            super.init(); subtitle = GuiSupport.tr("cnpcgun.editor.conditionsHint");
            int listWidth = Math.max(96, Math.min(150, content.width() / 3));
            EditorButton add = addRenderableWidget(new EditorButton(content.x(), content.y(), listWidth, 20, Component.translatable("cnpcgun.gui.addCondition"), EditorButton.Kind.NORMAL, () -> {
                Map<String, String> options = new LinkedHashMap<>(); for (String id : Conditions.ids()) options.put(GuiSupport.tr("cnpcgun.condition." + id), id);
                choose(host, Component.translatable("cnpcgun.gui.addCondition"), options, id -> {
                    JsonObject item = new JsonObject(); item.addProperty("id", id);
                    if (id.equals("state")) item.addProperty("value", "ATTACKING");
                    if (id.equals("weapon_type")) item.addProperty("value", "SLASHBLADE");
                    data.add(item); selection = data.size() - 1;
                });
            })); add.active = data.size() < 16;
            List<EditorList.Row> rows = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                JsonElement raw = data.get(i); String id = raw.isJsonObject() ? raw.getAsJsonObject().get("id").getAsString() : raw.getAsString();
                rows.add(new EditorList.Row(String.valueOf(i), GuiSupport.tr("cnpcgun.condition." + id), GuiSupport.tr("cnpcgun.editor.conditionNumber", i + 1), true));
            }
            addRenderableWidget(new EditorList(font, new EditorLayout.Box(content.x(), content.y() + 24, listWidth, content.height() - 24), rows, String.valueOf(selection), id -> { selection = Integer.parseInt(id); rebuild(); }));
            EditorLayout.Box detail = new EditorLayout.Box(content.x() + listWidth + 8, content.y(), content.width() - listWidth - 8, content.height() - 24);
            form = addRenderableWidget(new EditorForm(font, detail, picker()));
            if (selection >= 0 && selection < data.size()) {
                JsonElement raw = data.get(selection); JsonObject item;
                if (raw.isJsonObject()) item = raw.getAsJsonObject(); else { item = new JsonObject(); item.addProperty("id", raw.getAsString()); data.set(selection, item); }
                String id = item.get("id").getAsString();
                List<Parameter> fields = new ArrayList<>(); fields.add(Parameter.bool("not", false));
                if (bincnpc.gun.common.legacy.Legacy.set("distance", "health", "target_count", "resource", "combat_time").contains(id)) {
                    fields.add(Parameter.number("min", 0, 0, id.equals("health") ? 1 : 100000));
                    fields.add(Parameter.number("max", id.equals("health") ? 1 : 100000, 0, id.equals("health") ? 1 : 100000));
                }
                if (id.equals("weapon_type")) fields.add(Parameter.choice("value", "SLASHBLADE", "SLASHBLADE", "GUN", "MELEE", "UNKNOWN"));
                if (id.equals("state")) fields.add(Parameter.choice("value", "ATTACKING", "IDLE", "ATTACKING", "RELOADING", "EXECUTING_ACTION", "RETREATING", "WAITING", "NO_AMMO", "DISABLED"));
                for (Parameter p : fields) form.field(p, item.has(p.id) ? item.get(p.id) : SafeJson.copy(p.defaultValue), value -> item.add(p.id, value));
            }
            EditorButton remove = addRenderableWidget(new EditorButton(detail.x(), content.bottom() - 20, detail.width(), 20, Component.translatable("cnpcgun.editor.removeCondition"), EditorButton.Kind.NORMAL, () -> { if (selection >= 0 && selection < data.size()) data.remove(selection); selection = Math.max(0, selection - 1); rebuild(); }));
            remove.active = !(data.size() == 0);
            button(0, 2, Component.translatable("cnpcgun.gui.apply"), EditorButton.Kind.PRIMARY, () -> {
                try { if (form.invalid()) throw new IllegalArgumentException(); for (JsonElement item : data) Conditions.validate(item); applied.accept(bincnpc.gun.common.legacy.Legacy.copy(data)); host.closeDialog(); }
                catch (IllegalArgumentException | IllegalStateException e) { failure(); }
            });
            button(1, 2, Component.translatable("gui.cancel"), EditorButton.Kind.NORMAL, host::closeDialog);
        }
        @Override public void tick() { super.tick(); form.tick(); }
    }
}
