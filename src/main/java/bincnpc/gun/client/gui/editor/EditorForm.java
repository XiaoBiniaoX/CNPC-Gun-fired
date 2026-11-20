package bincnpc.gun.client.gui.editor;

import bincnpc.gun.client.gui.GuiSupport;
import bincnpc.gun.common.config.Parameter;
import bincnpc.gun.common.config.ConfigSchema;
import com.google.gson.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** A clipped form with actual controls; its rows never double as a data/debug list. */
public final class EditorForm extends AbstractWidget {
    public interface Picker { void choose(Component title, Map<String, String> choices, Consumer<String> selected); }
    private final Font font;
    private final Picker picker;
    private final List<Field> fields = new ArrayList<>();
    private AbstractWidget focusedControl;
    private int scroll, totalHeight;
    private boolean locked, compactSlots;
    private static final int ROW = 30;
    private final class Field {
        final Parameter parameter;
        final String heading;
        final List<AbstractWidget> controls = new ArrayList<>();
        final BooleanSupplier available;
        final String reason;
        EditorLayout.Box box;
        JsonElement value;
        boolean invalid, segmented, combined, flagValue;
        Field(String heading) { this.heading = heading; parameter = null; available = () -> true; reason = ""; }
        Field(Parameter parameter, JsonElement value, BooleanSupplier available, String reason, Consumer<JsonElement> changed) {
            this.heading = ""; this.parameter = parameter; this.value = value.deepCopy(); this.available = available; this.reason = reason;
            if (parameter.type.equals("boolean")) {
                EditorButton button = new EditorButton(0, 0, 80, 20, Component.empty(), EditorButton.Kind.NORMAL, () -> {
                    this.value = new JsonPrimitive(!this.value.getAsBoolean()); changed.accept(this.value.deepCopy()); updateLabel();
                }); controls.add(button); updateLabel();
            } else if (parameter.type.equals("enum")) {
                EditorButton button = new EditorButton(0, 0, 100, 20, Component.empty(), EditorButton.Kind.NORMAL, () -> {
                    Map<String, String> options = new LinkedHashMap<>();
                    for (String id : parameter.values) options.put(valueLabel(parameter, new JsonPrimitive(id)), id);
                    picker.choose(Component.translatable(parameter.label), options, id -> {
                        this.value = parameter.validate(new JsonPrimitive(id)); changed.accept(this.value.deepCopy()); updateLabel();
                    });
                }); controls.add(button); updateLabel();
            } else {
                int count = parameter.type.equals("range") ? 2 : parameter.type.equals("vector") ? 3 : 1;
                for (int i = 0; i < count; i++) {
                    EditorTextField input = new EditorTextField(font, 0, 0, 80, 8, Component.translatable(parameter.label));
                    JsonElement component = count == 1 ? value : value.getAsJsonArray().get(i);
                    input.setValue(displayNumber(component, percentage(parameter)));
                    controls.add(input);
                }
                List<EditorTextField> inputs = controls.stream().map(control -> (EditorTextField)control).toList();
                for (EditorTextField control : inputs) control.setResponder(ignored -> {
                    try {
                        JsonElement next;
                        if (count > 1) {
                            JsonArray array = new JsonArray();
                            for (EditorTextField part : inputs) array.add(Double.parseDouble(part.getValue().trim()));
                            next = array;
                        } else if (parameter.type.equals("integer") || parameter.type.equals("number")) {
                            double number = Double.parseDouble(inputs.get(0).getValue().trim());
                            next = new JsonPrimitive(percentage(parameter) ? number / 100 : number);
                        } else next = new JsonPrimitive(inputs.get(0).getValue());
                        this.value = parameter.validate(next); invalid = false;
                        changed.accept(this.value.deepCopy());
                    } catch (IllegalArgumentException | IllegalStateException e) { invalid = true; }
                    for (EditorTextField part : inputs) part.invalid = invalid;
                });
            }
        }
        void updateLabel() {
            if (controls.isEmpty()) return;
            if (segmented) {
                for (int i = 0; i < controls.size(); i++) {
                    EditorButton button = (EditorButton)controls.get(i);
                    button.setMessage(Component.literal(valueLabel(parameter, new JsonPrimitive(parameter.values[i]))));
                    button.selected = value.getAsString().equals(parameter.values[i]);
                }
                return;
            }
            if (combined) {
                EditorButton button = (EditorButton)controls.get(0); button.setMessage(Component.literal(flagValue ? "✓" : "—")); button.selected = flagValue; return;
            }
            EditorButton button = (EditorButton) controls.get(0);
            button.setMessage(Component.literal(valueLabel(parameter, value)));
            button.selected = parameter.type.equals("boolean") && value.getAsBoolean();
            button.setTooltip(Tooltip.create(Component.literal(valueLabel(parameter, value))));
        }
    }
    public EditorForm(Font font, EditorLayout.Box box, Picker picker) {
        super(box.x(), box.y(), box.width(), box.height(), Component.translatable("cnpcgun.gui.parameters"));
        this.font = font; this.picker = picker;
    }
    public EditorForm heading(String title) { fields.add(new Field(title)); layout(); return this; }
    public EditorForm compactSlots() { compactSlots = true; layout(); return this; }
    public EditorForm field(Parameter parameter, JsonElement initial, BooleanSupplier available, String reason, Consumer<JsonElement> changed) {
        if (initial == null || initial.isJsonNull()) initial = bincnpc.gun.common.api.SafeJson.copy(parameter.defaultValue);
        fields.add(new Field(parameter, initial, available, reason, changed)); layout(); return this;
    }
    public EditorForm field(Parameter parameter, JsonElement initial, Consumer<JsonElement> changed) { return field(parameter, initial, () -> true, "", changed); }
    public EditorForm segments(Parameter parameter, JsonElement initial, BooleanSupplier available, String reason, Consumer<JsonElement> changed) {
        Field field = new Field(parameter, initial, available, reason, changed); field.segmented = true; field.controls.clear();
        for (String id : parameter.values) {
            EditorButton button = new EditorButton(0, 0, 40, 20, Component.empty(), EditorButton.Kind.SEGMENT, () -> {
                if (field.value.getAsString().equals(id)) return;
                field.value = parameter.validate(new JsonPrimitive(id)); changed.accept(field.value.deepCopy()); field.updateLabel();
            });
            button.setTooltip(Tooltip.create(Component.literal(GuiSupport.tr((parameter.id.startsWith("throw.") ? "cnpcgun.editor.throw.option." : "cnpcgun.editor.option.") + id.toLowerCase(Locale.ROOT)))));
            field.controls.add(button);
        }
        field.updateLabel(); fields.add(field); layout(); return this;
    }
    public EditorForm toggleNumber(Parameter flag, JsonElement enabled, Parameter number, JsonElement initial, BooleanSupplier available,
                                   String reason, Consumer<JsonElement> flagChanged, Consumer<JsonElement> numberChanged) {
        Field field = new Field(number, initial, available, reason, numberChanged); field.combined = true; field.flagValue = enabled.getAsBoolean();
        EditorButton toggle = new EditorButton(0, 0, 28, 20, Component.empty(), EditorButton.Kind.NORMAL, () -> {
            field.flagValue = !field.flagValue; flagChanged.accept(new JsonPrimitive(field.flagValue)); field.updateLabel();
        }); toggle.setTooltip(Tooltip.create(Component.translatable(flag.label)));
        field.controls.add(0, toggle); field.updateLabel(); fields.add(field); layout(); return this;
    }
    private boolean numberEditable(Field field) {
        return !field.combined || field.flagValue || ConfigSchema.isGunSetting(field.parameter.id) || field.parameter.id.startsWith("weapon.");
    }
    public boolean invalid() { return fields.stream().anyMatch(field -> field.invalid && numberEditable(field)); }
    public boolean editing() { return isFocused() && focusedControl instanceof EditorTextField; }
    public int scrollOffset() { return scroll; }
    public Map<String, List<String>> invalidText() {
        Map<String, List<String>> raw = new HashMap<>();
        for (Field field : fields) if (field.parameter != null && field.invalid && numberEditable(field))
            raw.put(field.parameter.id, field.controls.stream().filter(control -> control instanceof EditorTextField).map(control -> ((EditorTextField)control).getValue()).toList());
        return raw;
    }
    public void restoreInvalidText(Map<String, List<String>> raw) {
        for (Field field : fields) if (field.parameter != null && raw.containsKey(field.parameter.id)) {
            List<String> text = raw.get(field.parameter.id); int i = 0;
            for (AbstractWidget control : field.controls) if (control instanceof EditorTextField input && i < text.size()) input.setValue(text.get(i++));
        }
    }
    public void scrollTo(int position) { scroll = EditorLayout.clampScroll(position, totalHeight, height - 8); layout(); }
    public void locked(boolean locked) { this.locked = locked; }
    public void tick() { for (Field field : fields) for (AbstractWidget control : field.controls) if (control instanceof EditorTextField input) input.tick(); }
    private static String valueLabel(Parameter p, JsonElement value) {
        String key = "cnpcgun.editor.throw.value." + value.getAsString().toLowerCase(Locale.ROOT);
        return p.id.startsWith("throw.") && net.minecraft.locale.Language.getInstance().has(key) ? GuiSupport.tr(key) : GuiSupport.value(value);
    }
    private static boolean percentage(Parameter p) { return p.max != null && p.max == 1 && p.id.toLowerCase(Locale.ROOT).contains("health"); }
    private static String displayNumber(JsonElement value, boolean percent) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return value.getAsString();
        double number = value.getAsDouble() * (percent ? 100 : 1);
        return java.math.BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
    }
    private static String unit(Parameter p) {
        String id = p.id.toLowerCase(Locale.ROOT);
        if (percentage(p)) return "%";
        if (id.startsWith("throw.") && id.endsWith("speed")) return GuiSupport.tr("cnpcgun.editor.multiplier");
        if (id.endsWith("elevation") || id.endsWith("depression")) return GuiSupport.tr("cnpcgun.editor.degrees");
        if (id.endsWith("deviation")) return GuiSupport.tr("cnpcgun.editor.blocks");
        if (id.contains("distance") || id.contains("range")) return GuiSupport.tr("cnpcgun.editor.blocks");
        if (id.contains("interval") || id.contains("cooldown") || id.contains("delay") || id.contains("timeout") || id.contains("maxticks") || id.endsWith("windup") || id.endsWith("recovery")) return GuiSupport.tr("cnpcgun.editor.ticks");
        if (id.contains("multiplier")) return GuiSupport.tr("cnpcgun.editor.multiplier");
        return "";
    }
    private String hint(Field field) {
        if (!field.available.getAsBoolean()) return field.reason;
        Parameter p = field.parameter;
        if (p.id.equals("combat.disableFriendlyFire")) return GuiSupport.tr("cnpcgun.editor.friendlyFireHint");
        if (p.id.equals("gun.prefire")) return GuiSupport.tr("cnpcgun.editor.prefireHint");
        if (p.id.equals("gun.overrideNativeRate")) return GuiSupport.tr("cnpcgun.editor.nativeRateHint");
        if (p.id.equals("gun.disableIdleAnimation")) return GuiSupport.tr("cnpcgun.editor.idleGunAnimationHint");
        if (p.id.equals("weapon.returnPrimary")) return GuiSupport.tr("cnpcgun.editor.returnPrimaryHint");
        if (p.id.equals("combat.disablePlayerAnimation")) return GuiSupport.tr("cnpcgun.editor.playerAnimationHint");
        if (p.id.equals("gun.predictiveAim")) return GuiSupport.tr("cnpcgun.editor.predictiveAimHint");
        if (p.min != null) {
            double scale = percentage(p) ? 100 : 1;
            return displayNumber(new JsonPrimitive(p.min * scale), false) + " – " + displayNumber(new JsonPrimitive(p.max * scale), false) + " " + unit(p);
        }
        return unit(p);
    }
    private String fieldLabel(Field field) {
        String id = field.parameter.id;
        return compactSlots && id.startsWith("weapon.slot") ? GuiSupport.tr("cnpcgun.editor.slot." + id.substring(id.lastIndexOf('.') + 1)) : GuiSupport.tr(field.parameter.label);
    }
    private void layout() {
        List<EditorLayout.FormField> entries = fields.stream().map(field -> new EditorLayout.FormField(field.combined ? 1 : field.controls.size(),
            field.combined || !field.controls.isEmpty() && field.controls.get(0) instanceof EditorTextField)).toList();
        EditorLayout.FormGrid grid = EditorLayout.formGrid(new EditorLayout.Box(getX(), getY(), width, height), entries, compactSlots, scroll);
        for (int n = 0; n < fields.size(); n++) {
            Field field = fields.get(n); EditorLayout.FormCell cell = grid.cells().get(n); field.box = cell.row();
            if (field.segmented) {
                int count = field.controls.size(), w = (field.box.width() - 8) / count;
                for (int i = 0; i < count; i++) {
                    AbstractWidget control = field.controls.get(i); control.setX(field.box.x() + 4 + i * w); control.setY(field.box.y() + 22);
                    control.setWidth(i == count - 1 ? field.box.right() - 4 - control.getX() : w);
                }
                continue;
            }
            if (field.combined) {
                int w = Math.max(82, Math.min(126, field.box.width() / 2));
                int x = field.box.right() - w - 4;
                AbstractWidget toggle = field.controls.get(0), input = field.controls.get(1);
                toggle.setX(x); toggle.setY(field.box.y() + (compactSlots ? 3 : 5)); toggle.setWidth(26);
                input.setX(x + 34); input.setY(field.box.y() + (compactSlots ? 10 : 12)); input.setWidth(w - 38); continue;
            }
            for (int i = 0; i < field.controls.size(); i++) {
                AbstractWidget control = field.controls.get(i); EditorLayout.Box box = cell.controls().get(i);
                control.setX(box.x()); control.setY(box.y()); control.setWidth(box.width());
            }
        }
        totalHeight = grid.totalHeight();
    }
    private boolean inViewport(Field field) { return field.box.bottom() > getY() + 1 && field.box.y() < getY() + height - 1; }
    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        EditorStyle.panel(g, new EditorLayout.Box(getX(), getY(), width, height), EditorStyle.PAPER);
        layout();
        g.enableScissor(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1);
        for (Field field : fields) {
            if (!inViewport(field)) continue;
            if (field.parameter == null) {
                g.fill(field.box.x(), field.box.y() + 6, field.box.x() + 3, field.box.y() + 16, EditorStyle.GREEN);
                EditorStyle.text(g, font, field.heading, field.box.x() + 8, field.box.y() + 7, field.box.width() - 12, EditorStyle.INK);
                continue;
            }
            boolean enabled = !locked && field.available.getAsBoolean();
            boolean components = field.controls.size() > 1 && !field.combined && !field.segmented;
            int labelWidth = field.segmented ? field.box.width() - 8 : components ? field.box.width() / 2 - 8 : field.controls.get(0).getX() - field.box.x() - 10;
            EditorStyle.text(g, font, fieldLabel(field), field.box.x() + 2, field.box.y() + (compactSlots ? 4 : 5), labelWidth, enabled ? EditorStyle.INK : EditorStyle.MUTED);
            if (!field.segmented) EditorStyle.text(g, font, field.invalid && numberEditable(field) ? GuiSupport.tr("cnpcgun.editor.invalidField") : hint(field),
                field.box.x() + (components ? field.box.width() / 2 : 2), field.box.y() + (components ? 5 : compactSlots ? 15 : 18),
                components ? field.box.width() / 2 - 4 : labelWidth, field.invalid ? EditorStyle.RED : EditorStyle.MUTED);
            if (components) for (int i = 0; i < field.controls.size(); i++) {
                AbstractWidget control = field.controls.get(i);
                String part = field.controls.size() == 2 ? GuiSupport.tr("cnpcgun.editor." + (i == 0 ? "minimum" : "maximum")) : new String[]{"X", "Y", "Z"}[i];
                EditorStyle.text(g, font, part, control.getX() - (field.controls.size() == 2 ? 28 : 16), control.getY(), field.controls.size() == 2 ? 22 : 10, EditorStyle.MUTED);
            }
            g.hLine(field.box.x(), field.box.right() - 6, field.box.bottom() - 1, EditorStyle.LINE);
            for (AbstractWidget control : field.controls) {
                boolean controlEnabled = enabled && (!field.segmented || !((EditorButton)control).selected)
                    && (control == field.controls.get(0) || numberEditable(field));
                control.active = controlEnabled; if (control instanceof EditorTextField input) input.setEditable(controlEnabled);
                control.render(g, mouseX, mouseY, partialTick);
            }
        }
        g.disableScissor();
        if (totalHeight > height - 8) {
            int track = Math.max(1, height - 8), thumb = Math.max(12, track * track / totalHeight);
            int top = getY() + 4 + (track - thumb) * scroll / Math.max(1, totalHeight - track);
            g.fill(getX() + width - 4, top, getX() + width - 2, top + thumb, EditorStyle.MUTED);
        }
        setTooltip(null);
        if (isHovered) {
            for (Field field : fields) if (field.parameter != null && inViewport(field) && field.box.contains(mouseX, mouseY)
                && (field.controls.size() > 1 && mouseY < field.box.y() + 20 || mouseX < field.controls.get(0).getX() - 4 || !field.available.getAsBoolean())) {
                String label = fieldLabel(field);
                String description = field.invalid && numberEditable(field)
                    ? GuiSupport.tr("cnpcgun.editor.invalidField") : hint(field);
                boolean components = field.controls.size() > 1 && !field.combined && !field.segmented;
                int labelWidth = field.segmented ? field.box.width() - 8 : components ? field.box.width() / 2 - 8
                    : field.controls.get(0).getX() - field.box.x() - 10;
                int hintWidth = components ? field.box.width() / 2 - 4 : labelWidth;
                if (Set.of("gun.prefire", "weapon.returnPrimary", "combat.disablePlayerAnimation", "gun.predictiveAim").contains(field.parameter.id) || font.width(label) > labelWidth || !field.segmented && font.width(description) > hintWidth || !field.available.getAsBoolean())
                    setTooltip(Tooltip.create(Component.literal(label + (description.isEmpty() ? "" : "\n" + description))));
            }
        }
    }
    private void focus(AbstractWidget chosen) {
        if (focusedControl != null) focusedControl.setFocused(false);
        focusedControl = chosen; if (chosen != null) chosen.setFocused(true);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (!visible || !new EditorLayout.Box(getX(), getY(), width, height).contains(x, y)) return false;
        layout();
        for (Field field : fields) if (field.parameter != null && inViewport(field) && !locked && field.available.getAsBoolean())
            for (AbstractWidget control : field.controls) if ((!field.segmented || !((EditorButton)control).selected)
                    && (control == field.controls.get(0) || numberEditable(field)) && control.mouseClicked(x, y, button)) { focus(control); return true; }
        focus(null); return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) { return focusedControl != null && focusedControl.mouseReleased(x, y, button); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return focusedControl != null && focusedControl.mouseDragged(x, y, button, dx, dy); }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (!new EditorLayout.Box(getX(), getY(), width, height).contains(x, y)) return false;
        scrollTo(scroll - (int)Math.round(delta * ROW));
        if (focusedControl != null && (focusedControl.getY() < getY() || focusedControl.getY() >= getY() + height)) focus(null);
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (locked) return false;
        if (key == GLFW.GLFW_KEY_TAB && isFocused()) {
            List<AbstractWidget> controls = new ArrayList<>();
            for (Field field : fields) if (field.parameter != null && field.available.getAsBoolean()) controls.addAll(field.controls);
            int index = controls.indexOf(focusedControl), next = index + ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1);
            if (next >= 0 && next < controls.size()) {
                AbstractWidget chosen = controls.get(next); focus(chosen);
                for (Field field : fields) if (field.controls.contains(chosen)) {
                    if (field.box.y() < getY() || field.box.bottom() > getY() + height) scrollTo(scroll + field.box.y() - getY() - 3);
                }
                return true;
            }
            focus(null); return false;
        }
        return focusedControl != null && focusedControl.keyPressed(key, scan, modifiers);
    }
    @Override public boolean charTyped(char letter, int modifiers) { return !locked && focusedControl != null && focusedControl.charTyped(letter, modifiers); }
    @Override public void setFocused(boolean focused) { super.setFocused(focused); if (focusedControl != null) focusedControl.setFocused(focused); }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
