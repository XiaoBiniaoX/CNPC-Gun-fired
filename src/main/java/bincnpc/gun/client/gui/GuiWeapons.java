package bincnpc.gun.client.gui;

import bincnpc.gun.client.EditorSession;
import bincnpc.gun.api.client.DrawerApi;
import bincnpc.gun.api.client.LeaveHandler;
import bincnpc.gun.client.gui.editor.*;
import bincnpc.gun.client.gui.editor.EditorButton.Kind;
import bincnpc.gun.client.gui.editor.EditorLayout.Box;
import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.config.*;
import bincnpc.gun.common.network.GunNetwork;
import bincnpc.gun.common.network.GunNetwork.Operation;
import bincnpc.gun.slashblade.action.ActionConfig;
import com.google.gson.*;
import java.util.*;
import java.util.function.*;
import bincnpc.gun.client.gui.legacy.GuiCompat.GuiGraphics;
import bincnpc.gun.client.gui.legacy.GuiCompat.AbstractWidget;
import bincnpc.gun.client.gui.legacy.GuiCompat.Tooltip;
import bincnpc.gun.client.gui.legacy.GuiCompat.Screen;
import bincnpc.gun.client.gui.legacy.GuiCompat.ClientTooltipPositioner;
import bincnpc.gun.client.gui.legacy.GuiCompat.DefaultTooltipPositioner;
import bincnpc.gun.client.gui.legacy.GuiCompat.FormattedCharSequence;
import bincnpc.gun.client.gui.legacy.GuiCompat.Language;
import bincnpc.gun.client.gui.legacy.GuiCompat.Component;
import noppes.npcs.client.gui.mainmenu.GuiNpcDisplay;
import noppes.npcs.entity.EntityNPCInterface;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import bincnpc.gun.client.gui.legacy.GuiCompat.Keys;

/** Fullscreen editor with local drafts and the existing server-authoritative protocol. */
public final class GuiWeapons extends Screen implements EditorDialogs.Host {
    public static final int TAB_ID = 87;
    private enum Page {
        WEAPONS("weapons"), SHOOTING("shooting"), THROWING("throwing"), ACTIONS("actions"),
        COMBOS("combos"), PRESETS("presets"), ADVANCED("settings"), BLADE_SETTINGS("bladeSettings");
        final String key;
        Page(String key) { this.key = key; }
    }
    private enum Group {
        INVENTORY("inventoryGroup", Page.WEAPONS, Page.ADVANCED), GUN("gunGroup", Page.SHOOTING, Page.THROWING),
        BLADE("bladeGroup", Page.ACTIONS, Page.COMBOS, Page.PRESETS, Page.BLADE_SETTINGS);
        final String key; final Page[] pages;
        Group(String key, Page... pages) { this.key = key; this.pages = pages; }
        ResourceLocation id() { return new ResourceLocation("cnpcgun", name().toLowerCase(Locale.ROOT)); }
    }
    private static final class Guard  {
private final AbstractWidget widget;
private final BooleanSupplier allowed;
private final String reason;
public Guard(AbstractWidget widget, BooleanSupplier allowed, String reason) {
this.widget = widget; this.allowed = allowed; this.reason = reason;}
public AbstractWidget widget() { return widget; }
public BooleanSupplier allowed() { return allowed; }
public String reason() { return reason; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof Guard))return false; Guard that=(Guard)value; return java.util.Objects.equals(widget, that.widget) && java.util.Objects.equals(allowed, that.allowed) && java.util.Objects.equals(reason, that.reason); }
@Override public int hashCode() { return java.util.Objects.hash(widget, allowed, reason); }

}
    private static final class Text  {
private final String text;
private final int x;
private final int y;
private final int width;
private final int color;
public Text(String text, int x, int y, int width, int color) {
this.text = text; this.x = x; this.y = y; this.width = width; this.color = color;}
public String text() { return text; }
public int x() { return x; }
public int y() { return y; }
public int width() { return width; }
public int color() { return color; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof Text))return false; Text that=(Text)value; return java.util.Objects.equals(text, that.text) && java.util.Objects.equals(x, that.x) && java.util.Objects.equals(y, that.y) && java.util.Objects.equals(width, that.width) && java.util.Objects.equals(color, that.color); }
@Override public int hashCode() { return java.util.Objects.hash(text, x, y, width, color); }

}
    private static final class SaveStep  {
private final Operation operation;
private final JsonObject payload;
private final Runnable acknowledged;
public SaveStep(Operation operation, JsonObject payload, Runnable acknowledged) {
this.operation = operation; this.payload = payload; this.acknowledged = acknowledged;}
public Operation operation() { return operation; }
public JsonObject payload() { return payload; }
public Runnable acknowledged() { return acknowledged; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof SaveStep))return false; SaveStep that=(SaveStep)value; return java.util.Objects.equals(operation, that.operation) && java.util.Objects.equals(payload, that.payload) && java.util.Objects.equals(acknowledged, that.acknowledged); }
@Override public int hashCode() { return java.util.Objects.hash(operation, payload, acknowledged); }

}
    private static final class ActionEdit {
        JsonObject original;
        final JsonObject data;
        ActionEdit(JsonObject source) {
            data = source == null ? new JsonObject() : bincnpc.gun.common.legacy.Legacy.copy(source);
            for (Parameter p : ActionConfig.SCHEMA) {
                try { data.add(p.id, p.validate(data.has(p.id) ? data.get(p.id) : SafeJson.copy(p.defaultValue))); }
                catch (IllegalArgumentException | IllegalStateException invalid) { data.add(p.id, SafeJson.copy(p.defaultValue)); }
            }
            if (!data.has("parameters") || !data.get("parameters").isJsonObject()) data.add("parameters", new JsonObject());
            if (!data.has("conditions") || !data.get("conditions").isJsonArray()) data.add("conditions", new JsonArray());
            if (!data.has("stages") || !data.get("stages").isJsonArray()) data.add("stages", new JsonArray());
            original = bincnpc.gun.common.legacy.Legacy.copy(data);
        }
        boolean dirty() { return !data.equals(original); }
        JsonObject parameters() { return data.getAsJsonObject("parameters"); }
        JsonArray array(String key) { return data.getAsJsonArray(key); }
    }
    private final EntityNPCInterface npc;
    private final EditorSession session;
    private final EditorDraft draft = new EditorDraft();
    private final Map<String, ActionEdit> actionEdits = new LinkedHashMap<>();
    private final Map<String, Integer> scrollPositions = new HashMap<>();
    private final Map<String, Map<String, List<String>>> invalidText = new HashMap<>();
    private final Map<Page, String> searches = new EnumMap<>(Page.class);
    private final Deque<Screen> dialogs = new ArrayDeque<>();
    private DrawerApi.Page extensionPage;
    private Screen extensionScreen, extensionDialog, tooltipTarget;
    private boolean extensionFocused;
    private long leaveRequest;
    private final List<Guard> guards = new ArrayList<>();
    private final List<Text> texts = new ArrayList<>();
    private final List<WeaponSlotButton> weaponButtons = new ArrayList<>();
    private final EditorToast toast = new EditorToast();
    private final Map<AbstractWidget, Tooltip> enabledTooltips = new HashMap<>();
    private List<FormattedCharSequence> queuedTooltip;
    private ClientTooltipPositioner tooltipPositioner = DefaultTooltipPositioner.INSTANCE;
    private EditorLayout layout;
    private EditorForm form;
    private EditorList masterList;
    private GraphCanvas canvas;
    private GraphDraft graph;
    private EditorButton save, discard;
    private EditorButton previewButton;
    private NpcPreview preview;
    private EditorTextField searchField;
    private ResourceLocation openGroup = DrawerApi.INVENTORY;
    private int navigationScroll, navigationHeight;
    private boolean saving;
    private Runnable saveContinuation;
    private int saveDelay;
    private Page page = Page.WEAPONS;
    private String formKey = "", masterKey = "", selectedAction = "", selectedPreset = "", selectedNode = "", selectedGraph = "";
    private String pageTitle = "", pageHint = "", localError = "";
    private String actionCategory = "all";
    private int titleOffset, titleReserved;
    private int weaponSlot, actionTab, selectedStage;
    private int throwSlot;
    private boolean opened, closed, actionDetail, canvasExpanded, rebuildPending;
    private GraphCanvas.View canvasView;

    public GuiWeapons(EntityNPCInterface npc) {
        super(Component.translatable("cnpcgun.editor.title"));
        this.npc = npc; session = new EditorSession(npc.getUniqueID());
    }
    public void receive(GunNetwork.Response packet) {
        if (!session.receive(packet)) return;
        localError = packet.code().equals("SUCCESS") ? "" : GuiSupport.tr("cnpcgun.error." + packet.code().toLowerCase(Locale.ROOT));
        if (session.ready()) {
            draft.reconcile(session.values());
            if (packet.code().equals("SUCCESS") && session.completedOperation == Operation.PRESET) {
                actionEdits.clear(); graph = null; selectedGraph = ""; selectedNode = ""; canvas = null; canvasView = null;
                if (preview != null) preview.stop();
            }
            if (!saving) {
            actionEdits.entrySet().removeIf(entry -> !entry.getValue().dirty() && session.actionInfo(entry.getKey()) == null);
            actionEdits.replaceAll((id, edited) -> edited.dirty() ? edited : new ActionEdit(session.action(id)));
            JsonElement current = session.snapshot.get("graph");
            if (graph != null && !graph.dirty() && current != null && current.isJsonObject() && !graph.graph.equals(current)) {
                if (canvas != null) canvasView = canvas.view();
                loadGraph(current.getAsJsonObject(), "");
            }
            if (graph != null && !graph.dirty() && (current == null || current.isJsonNull())) { graph = null; selectedGraph = ""; canvas = null; canvasView = null; }
            }
        }
        if (packet.code().equals("SUCCESS")) operationToast(session.completedOperation);
        if (minecraft != null && !closed) rebuild();
    }
    private Box body() { return canvasExpanded ? new Box(8, 42, width - 16, height - 76) : layout.body; }
    private static Component label(String key) { return Component.translatable("cnpcgun.editor." + key); }
    private static String tr(String key, Object... args) { return GuiSupport.tr("cnpcgun.editor." + key, args); }
    private static String name(String id) {
        if (Language.getInstance().has("cnpcgun.value." + id.toLowerCase(Locale.ROOT))) return GuiSupport.value(new JsonPrimitive(id));
        return id.replaceFirst("^(user:|custom:)", "").replace('_', ' ');
    }
    private JsonObject object(String key) {
        JsonElement value = session.snapshot.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }
    private static boolean bool(JsonObject object, String key) { return SafeJson.bool(object, key, false); }
    private boolean blade() { return session.has("slashblade.attack"); }
    private boolean gun() { return session.has("gun.fire"); }
    private boolean running() { return bool(object("comboState"), "running"); }
    private boolean canSwitch() { return bool(object("capabilities"), "canSwitchAway"); }
    private JsonElement value(String key) { return draft.value(session.values(), key); }
    private boolean dirty() {
        return draft.dirty() || actionEdits.values().stream().anyMatch(ActionEdit::dirty) || graph != null && graph.dirty();
    }
    private boolean busy() { return session.pending || saving; }
    private void text(String text, Box box, int y, int color) { texts.add(new Text(text, box.x() + 8, box.y() + y, box.width() - 16, color)); }
    private EditorButton button(Box box, Component title, Kind kind, Runnable clicked) {
        return addRenderableWidget(new EditorButton(box, title, kind, clicked));
    }
    private EditorButton serverButton(Box box, Component title, BooleanSupplier allowed, String reason, Runnable clicked) {
        return guardedButton(box, title, Kind.NORMAL, allowed, reason, clicked);
    }
    private EditorButton guardedButton(Box box, Component title, Kind kind, BooleanSupplier allowed, String reason, Runnable clicked) {
        EditorButton button = button(box, title, kind, clicked);
        guards.add(new Guard(button, allowed, reason)); return button;
    }
    private void rebuild() { rebuildPending = true; }
    private void rebuildNow() { if (rebuildPending) { rebuildPending = false; clearWidgets(); init(); } }
    @Override protected void init() {
        boolean listFocused = masterList != null && getFocused() == masterList;
        boolean searchFocused = searchField != null && searchField.isFocused();
        if (form != null) { scrollPositions.put(formKey, form.scrollOffset()); invalidText.put(formKey, form.invalidText()); }
        if (masterList != null) scrollPositions.put(masterKey, masterList.scrollOffset());
        if (canvas != null) canvasView = canvas.view();
        form = null; masterList = null; canvas = null; searchField = null; previewButton = null; guards.clear(); texts.clear(); weaponButtons.clear(); enabledTooltips.clear();
        layout = new EditorLayout(width, height);
        button(new Box(8, 7, 84, 20), Component.translatable("menu.display"), Kind.BACK, this::onClose)
            .setTooltip(Tooltip.create(label("returnAppearance")));
        button(new Box(width - 30, 7, 22, 20), Component.translatable("cnpcgun.gui.refresh"), Kind.REFRESH, this::refresh)
            .setTooltip(Tooltip.create(Component.translatable("cnpcgun.gui.refresh")));
        if (!canvasExpanded) navigation();
        discard = button(layout.discardButton(), label("discard"), Kind.NORMAL, this::discardChanges);
        save = button(layout.saveButton(), label("saveChanges"), Kind.PRIMARY, () -> saveChanges(null));
        pageTitle = tr(page.key); pageHint = tr(page.key + "Hint"); titleOffset = 0; titleReserved = 0;
        if (!session.ready()) {
            empty(tr("loadingTitle"), GuiSupport.tr(session.status), false);
            if (!opened) { opened = true; session.send(Operation.OPEN, new JsonObject()); }
        } else if (extensionScreen != null) {
            pageTitle = extensionPage.title().getFormattedText(); pageHint = "";
            Box area = body();
            if (extensionScreen.width != area.width() || extensionScreen.height != area.height())
                extensionScreen.resize(minecraft, area.width(), area.height());
        } else switch (page) {
case WEAPONS:
weapons(); break;
case SHOOTING:
shooting(); break;
case THROWING:
throwing(); break;
case ACTIONS:
actions(); break;
case COMBOS:
combos(); break;
case PRESETS:
presets(); break;
case ADVANCED:
advanced(); break;
case BLADE_SETTINGS:
bladeSettings(); break;
};
        if (form != null) { form.scrollTo(scrollPositions.getOrDefault(formKey, 0)); form.restoreInvalidText(invalidText.getOrDefault(formKey, bincnpc.gun.common.legacy.Legacy.map())); }
        if (listFocused && masterList != null) setFocused(masterList);
        if (searchFocused && searchField != null) setFocused(searchField);
        updateControls();
        for (Screen dialog : dialogs) if (dialog.width != width || dialog.height != height) dialog.resize(minecraft, width, height);
    }
    private void navigation() {
        List<DrawerApi.Drawer> drawers = new ArrayList<>();
        for (Group group : Group.values()) drawers.add(new DrawerApi.Drawer(group.id(), new TextComponentString(tr(group.key))));
        drawers.addAll(DrawerApi.drawers());
        int y = layout.sidebar.y() - navigationScroll;
        for (DrawerApi.Drawer entry : drawers) {
            boolean expanded = entry.id().equals(openGroup);
            Box box = new Box(layout.sidebar.x(), y, layout.sidebar.width(), 22);
            if (box.y() >= layout.sidebar.y() && box.bottom() <= layout.sidebar.bottom()) {
                EditorButton drawer = button(box, Component.literal((expanded ? "▾ " : "▸ ") + entry.title().getFormattedText()), Kind.TOOL,
                    () -> { openGroup = expanded ? null : entry.id(); rebuild(); });
                drawer.setTooltip(Tooltip.create(Component.literal(entry.title().getFormattedText())));
            }
            y += 24;
            if (!expanded) continue;
            for (Group group : Group.values()) if (group.id().equals(entry.id())) for (Page chosen : group.pages) {
                navigationPage(y, label(chosen.key), extensionPage == null && page == chosen, () -> {
                    if (form != null && form.invalid()) { localError = tr("invalidField"); return; }
                    requestLeave(extensionScreen, () -> {
                        removeExtension();
                        if (preview != null && chosen != Page.ACTIONS && chosen != Page.COMBOS) { preview.dispose(); preview = null; }
                        page = chosen; canvasExpanded = false; localError = ""; rebuild();
                    });
                }); y += 20;
            }
            for (DrawerApi.Page extension : DrawerApi.pages(entry.id())) {
                navigationPage(y, Component.literal(extension.title().getFormattedText()), extension == extensionPage, () -> openExtension(extension)); y += 20;
            }
        }
        navigationHeight = y - layout.sidebar.y() + navigationScroll;
        int clamped = EditorLayout.clampScroll(navigationScroll, navigationHeight, layout.sidebar.height());
        if (clamped != navigationScroll) { navigationScroll = clamped; rebuild(); }
    }
    private void navigationPage(int y, Component title, boolean selected, Runnable clicked) {
        Box box = new Box(layout.sidebar.x() + 6, y, layout.sidebar.width() - 6, 18);
        if (box.y() < layout.sidebar.y() || box.bottom() > layout.sidebar.bottom()) return;
        EditorButton tab = button(box, title, Kind.NAVIGATION, clicked); tab.selected = selected;
        tab.setTooltip(Tooltip.create(title));
    }
    private void openExtension(DrawerApi.Page extension) {
        if (extension == extensionPage) return;
        Runnable open = () -> {
            if (preview != null) {
                if (extension.dialog()) preview.stop();
                else { preview.dispose(); preview = null; }
            }
            Screen[] created = new Screen[1];
            Runnable closePage = () -> closeExtension(created[0]);
            DrawerApi.Context context = new DrawerApi.Context(session.npcId, session.snapshot,
                dialog -> openDialog(NativePage.wrap(dialog, this::closeDialog)), this::closeDialog, closePage);
            Screen screen = created[0] = NativePage.wrap(Objects.requireNonNull(extension.factory().apply(context), "drawer page screen"), closePage);
            if (extension.dialog()) { extensionDialog = screen; openDialog(screen); return; }
            removeExtension();
            extensionPage = extension; extensionScreen = screen; extensionFocused = true;
            canvasExpanded = false; setDragging(false); setFocused(null);
            Box area = layout.body;
            screen.init(minecraft, area.width(), area.height()); screen.added(); rebuild();
        };
        saveChanges(() -> { if (extension.dialog()) open.run(); else requestLeave(extensionScreen, open); });
    }
    private void requestLeave(Screen screen, Runnable after) {
        if (closed || screen != null && screen != extensionScreen && !dialogs.contains(screen)) return;
        long request = ++leaveRequest;
        Runnable continueLeaving = () -> minecraft.addScheduledTask(() -> {
            if (closed || request != leaveRequest || screen != null && screen != extensionScreen && !dialogs.contains(screen)) return;
            ++leaveRequest;
            after.run();
        });
        if (screen instanceof LeaveHandler) ((LeaveHandler)screen).requestLeave(continueLeaving);
        else continueLeaving.run();
    }
    private void removeExtension() {
        if (extensionScreen != null) {
            for (Screen dialog : dialogs) dialog.removed();
            dialogs.clear(); extensionDialog = null;
            extensionScreen.removed();
        }
        extensionPage = null; extensionScreen = null; extensionFocused = false;
    }
    private void closeExtension(Screen screen) {
        if (screen == null) return;
        requestLeave(screen, () -> {
            if (screen == extensionScreen) { removeExtension(); rebuild(); }
            else while (dialogs.contains(screen)) closeDialog();
        });
    }
    /** The GL pose is shared; scissor coordinates must also be translated to the host work area. */
    private final class WorkspaceGraphics extends GuiGraphics {
        private final GuiGraphics source;
        private final Box area;
        WorkspaceGraphics(GuiGraphics source, Box area) { super(extensionScreen); this.source = source; this.area = area; }
        @Override public void enableScissor(int x, int y, int right, int bottom) {
            source.enableScissor(area.x() + x, area.y() + y, area.x() + right, area.y() + bottom);
        }
        @Override public void disableScissor() { source.disableScissor(); }
    }
    private EditorForm newForm(Box box, String key) {
        formKey = key;
        form = addRenderableWidget(new EditorForm(font, box, (title, options, chosen) -> EditorDialogs.choose(this, title, options, chosen)));
        return form;
    }
    private void field(Parameter p) {
        form.field(p, value(p.id), () -> editable(p), fieldReason(p), checked -> change(p.id, checked));
    }
    private String fieldReason(Parameter p) { return tr((p.id.startsWith("ammo.") || p.id.startsWith("throw.")) && !session.inventoryAllowed() ? "inventoryDenied" : "weaponCannotControl"); }
    private boolean editable(Parameter p) {
        return (p.capability.isEmpty() || session.has(p.capability)) && ((!p.id.startsWith("ammo.") && !p.id.startsWith("throw.")) || session.inventoryAllowed());
    }
    private void change(String key, JsonElement checked) {
        draft.set(session.values(), key, checked); localError = "";
        if (key.equals("ammo.defaultReserve") && checked.getAsInt() > value("ammo.reserveLimit").getAsInt()) draft.set(session.values(), "ammo.reserveLimit", checked);
        if (key.equals("ammo.infinite") || key.equals("gun.reloadSpeedEnabled")) rebuild();
    }
    private void segments(String key) {
        Parameter p = ConfigSchema.get(key); form.segments(p, value(key), () -> editable(p), fieldReason(p), checked -> change(key, checked));
    }
    private void booleanSegments(String key) {
        Parameter p = ConfigSchema.get(key);
        Parameter display = Parameter.choice(key, "RELOAD_NATIVE", "RELOAD_NATIVE", "RELOAD_CUSTOM").requires(p.capability);
        form.segments(display, new JsonPrimitive(value(key).getAsBoolean() ? "RELOAD_CUSTOM" : "RELOAD_NATIVE"), () -> editable(p), fieldReason(p),
            checked -> change(key, new JsonPrimitive(checked.getAsString().equals("RELOAD_CUSTOM"))));
    }
    private void toggleNumber(String flag, String number) {
        Parameter p = ConfigSchema.get(number);
        form.toggleNumber(ConfigSchema.get(flag), value(flag), p, value(number), () -> editable(p), tr("weaponCannotControl"),
            checked -> change(flag, checked), checked -> change(number, checked));
    }
    private void fields(String title, Predicate<Parameter> include) {
        form.heading(title); for (Parameter p : ConfigSchema.all()) if (include.test(p)) field(p);
    }
    private void empty(String title, String description, boolean goWeapons) {
        Box area = body();
        text(title, area, 16, EditorStyle.INK); text(description, area, 34, EditorStyle.MUTED);
        if (goWeapons) button(new Box(area.x() + 8, area.y() + 57, Math.min(160, area.width() - 16), 20), label("openWeapons"), Kind.NORMAL, () -> { page = Page.WEAPONS; rebuild(); });
    }
    private void weapons() {
        Box area = body();
        for (int i = 0; i < 6; i++) {
            int slot = i; Box box = layout.weaponSlot(i);
            String role = "cnpcgun." + (box.width() < 48 ? "editor.shortSlot" : "gui.slot") + i;
            WeaponSlotButton button = addRenderableWidget(new WeaponSlotButton(box, Component.translatable(role), () -> session.slot(slot),
                () -> value("weapon.primarySlot").getAsInt() == slot, () -> {
                    if (busy() || form != null && form.invalid()) return;
                    weaponSlot = slot; rebuild();
                }, () -> {
                    if (busy() || form != null && form.invalid()) return;
                    weaponSlot = slot; rebuild(); saveChanges(() -> openDialog(new InventoryDialog(this, session, slot)));
                }));
            button.selected = weaponSlot == i; weaponButtons.add(button);
        }
        titleReserved = Math.min(172, area.width() - 48);
        serverButton(new Box(layout.workspace.right() - titleReserved, layout.workspace.y() - 2, titleReserved, 20), label("equipWeapon"),
            () -> session.inventoryAllowed() && !session.slot(weaponSlot).isEmpty() && weaponSlot != value("weapon.primarySlot").getAsInt(),
            tr("equipHint"), () -> { JsonObject input = new JsonObject(); input.addProperty("slot", weaponSlot); mutate(Operation.PRIMARY_WEAPON, input); });
        newForm(layout.weaponForm(), "weapon:" + weaponSlot).compactSlots();
        String slotKey = "weapon.slot" + weaponSlot + "."; field(ConfigSchema.get(slotKey + "priority"));
        for (String key : bincnpc.gun.common.legacy.Legacy.list("minRange", "maxRange", "maxHealth")) toggleNumber(slotKey + key + "Enabled", slotKey + key);
        pageHint = tr("selectedWeapon", GuiSupport.tr("cnpcgun.gui.slot" + weaponSlot), session.slot(weaponSlot).isEmpty() ? tr("emptySlot") : session.slot(weaponSlot).getDisplayName());
        fields(tr("automaticSwitch"), p -> p.id.startsWith("weapon.") && !p.id.startsWith("weapon.slot") && !p.id.equals("weapon.primarySlot"));
    }
    private void shooting() {
        if (!gun()) { empty(tr("noGun"), tr("noGunHint"), true); return; }
        newForm(body(), "shooting");
        Set<String> basic = bincnpc.gun.common.legacy.Legacy.set("gun.enabled", "gun.autoAttack", "gun.minRange", "gun.range", "gun.requireAim", "gun.moveFire", "gun.jumpFire", "gun.blindFire", "gun.lostSight", "gun.prefire", "gun.accuracy");
        fields(tr("engagement"), p -> basic.contains(p.id) && !bincnpc.gun.common.legacy.Legacy.set("gun.requireAim", "gun.blindFire", "gun.lostSight", "gun.prefire").contains(p.id));
        segments("gun.prefire"); segments("gun.lostSight");
        form.heading(tr("firePattern"));
        for (Parameter p : ConfigSchema.all()) if (p.id.startsWith("gun.") && !p.id.startsWith("gun.reload") && !basic.contains(p.id) && !accuracy(p)) {
            if (p.id.equals("gun.firingMode")) {
                Set<String> modes = new LinkedHashSet<>(); modes.add("NATIVE");
                for (JsonElement raw : session.array("editableWeapons")) {
                    if (!raw.isJsonObject()) continue;
                    JsonObject weapon = raw.getAsJsonObject(); if (!weapon.has("gun") || !weapon.get("gun").isJsonObject()) continue;
                    for (JsonElement mode : SafeJson.arrayField(weapon.getAsJsonObject("gun"), "firingModes"))
                        if (mode.isJsonPrimitive() && mode.getAsJsonPrimitive().isString()) modes.add(mode.getAsString());
                }
                p = Parameter.choice(p.id, "NATIVE", modes.toArray(new String[0])).requires("gun.fire");
            }
            field(p);
        }
        fields(tr("accuracy"), GuiWeapons::accuracy);
        ammunition();
    }
    private static boolean accuracy(Parameter p) {
        String key = p.id.toLowerCase(Locale.ROOT);
        return key.startsWith("gun.") && (key.contains("spread") || key.contains("recoil"));
    }
    private void throwing() {
        Box area = body(); int cardHeight = Math.max(38, Math.min(72, area.height() / 3)); int cardWidth = (area.width() - 8) / 3;
        for (int i = 0; i < 3; i++) {
            int selected = i, wireSlot = 6 + i;
            Box card = new Box(area.x() + i * (cardWidth + 4), area.y(), cardWidth, cardHeight);
            WeaponSlotButton button = addRenderableWidget(new WeaponSlotButton(card, Component.translatable("cnpcgun.gui.slot" + wireSlot),
                () -> session.slot(wireSlot), () -> false, () -> { if (!busy() && (form == null || !form.invalid())) { throwSlot = selected; rebuild(); } },
                () -> { if (!busy() && (form == null || !form.invalid())) { throwSlot = selected; saveChanges(() -> openDialog(new InventoryDialog(this, session, wireSlot))); } }));
            button.selected = throwSlot == i; weaponButtons.add(button);
        }
        newForm(area.below(cardHeight + 4), "throw:" + throwSlot);
        form.heading(tr("throwGlobal")); field(ConfigSchema.get("throw.enabled")); field(ConfigSchema.get("throw.cooldown"));
        String key = "throw.slot" + throwSlot + ".";
        JsonArray quotas = session.array("throwRemaining");
        JsonElement quota = throwSlot < quotas.size() ? quotas.get(throwSlot) : JsonNull.INSTANCE;
        int remaining = quota.isJsonPrimitive() && quota.getAsJsonPrimitive().isNumber() ? quota.getAsInt() : 0;
        form.heading(GuiSupport.tr("cnpcgun.gui.slot" + (6 + throwSlot)) + " · " + (remaining < 0 ? tr("throwUnlimited") : tr("throwRemaining", remaining))); field(ConfigSchema.get(key + "once"));
        segments(key + "timing"); segments(key + "trajectory");
        for (String id : bincnpc.gun.common.legacy.Legacy.list("windup", "recovery", "speed", "priority", "minRange", "maxRange")) field(ConfigSchema.get(key + id));
        segments(key + "area");
        for (String id : bincnpc.gun.common.legacy.Legacy.list("deviation", "lostTarget", "maxElevation", "maxDepression", "calculationRange", "prediction")) field(ConfigSchema.get(key + id));
        pageHint = tr("throwingHint");
    }
    private void ammunition() {
        form.heading(tr("reloadSpeed")); booleanSegments("gun.reloadSpeedEnabled");
        if (value("gun.reloadSpeedEnabled").getAsBoolean()) {
            field(ConfigSchema.get("gun.reloadMinTicks")); field(ConfigSchema.get("gun.reloadMaxTicks"));
        }
        form.heading(tr("reloadPolicy")); segments("ammo.reloadTiming"); toggleNumber("ammo.partialReload", "ammo.reloadThreshold");
        field(ConfigSchema.get("ammo.noAmmoBehavior")); form.heading(tr("ammo")); field(ConfigSchema.get("ammo.infinite"));
        if (value("ammo.infinite").getAsBoolean()) return;
        field(ConfigSchema.get("ammo.defaultReserve")); form.heading(tr("finiteSources"));
        toggleNumber("ammo.killSupply", "ammo.killReward"); field(ConfigSchema.get("ammo.dropSupply"));
        form.heading(tr("reservePolicy"));
        for (JsonElement raw : session.array("slotAmmo")) {
            if (!raw.isJsonObject()) continue;
            JsonObject ammo = raw.getAsJsonObject(); int reserve = SafeJson.integer(ammo, "reserve", -1), slot = SafeJson.integer(ammo, "slot", -1);
            if (reserve < 0 || slot < 0 || slot >= 6) continue;
            form.heading(GuiSupport.tr("cnpcgun.gui.slot" + slot) + " · " + tr("rounds", reserve));
        }
    }
    private ActionEdit actionEdit(String id) { return actionEdits.computeIfAbsent(id, key -> new ActionEdit(session.action(key))); }
    private Box editingPane(Box area, Runnable play) {
        int previewWidth = Math.max(82, Math.min(176, area.width() / 3));
        Box side = new Box(area.right() - previewWidth, area.y(), previewWidth, area.height() - 26);
        if (preview == null) preview = new NpcPreview(npc, side, session.previewWeapon("SLASHBLADE"));
        preview.bounds(side); addRenderableWidget(preview);
        previewButton = guardedButton(new Box(side.x(), area.bottom() - 22, side.width(), 20), label("playOnce"), Kind.ADD,
            () -> blade() && (form == null || !form.invalid()), tr("invalidField"), () -> { if (preview.playing()) preview.stop(); else play.run(); });
        previewButton.setTooltip(Tooltip.create(label("playOnceHint")));
        enabledTooltips.put(previewButton, Tooltip.create(label("playOnceHint")));
        return new Box(area.x(), area.y(), area.width() - previewWidth - 8, area.height());
    }
    private void actions() {
        if (!blade() || (session.actions().size() == 0)) { empty(tr("noBladeActions"), tr("noBladeActionsHint"), true); return; }
        List<EditorList.Row> rows = new ArrayList<>();
        for (JsonElement raw : session.actions()) {
            JsonObject info = raw.getAsJsonObject(); String id = info.get("id").getAsString();
            if (!ActionPicker.matches(info, actionCategory)) continue;
            JsonObject config = actionEdits.containsKey(id) ? actionEdits.get(id).data : session.action(id);
            rows.add(new EditorList.Row(id, GuiSupport.actionName(info), GuiSupport.tr("cnpcgun.type." + info.get("type").getAsString().toLowerCase(Locale.ROOT)) + " · " +
                GuiSupport.tr(bool(config, "enabled") ? "cnpcgun.gui.on" : "cnpcgun.gui.off"), bool(info, "available")));
        }
        Box editPane = editingPane(body(), () -> { JsonObject info = session.actionInfo(selectedAction); if (info != null) preview.playAction(info, actionEdit(selectedAction).parameters()); });
        boolean split = editPane.width() >= 360;
        if (rows.stream().noneMatch(row -> row.id().equals(selectedAction))) selectedAction = rows.isEmpty() ? "" : rows.get(0).id();
        if (rows.isEmpty()) actionDetail = false;
        if (!split && actionDetail) {
            JsonObject info = session.actionInfo(selectedAction); pageTitle = GuiSupport.actionName(info);
            titleOffset = 76;
            button(new Box(layout.workspace.x(), layout.workspace.y() - 2, 70, 16), label("backToActions"), Kind.BACK, () -> { actionDetail = false; rebuild(); });
            actionDetails(info, editPane);
        } else {
            int listWidth = Math.max(92, Math.min(160, editPane.width() / 3));
            Box column = split ? new Box(editPane.x(), editPane.y(), listWidth, editPane.height()) : editPane;
            button(new Box(column.x(), column.y(), column.width(), 20), ActionPicker.categoryLabel(actionCategory), Kind.TOOL,
                () -> EditorDialogs.choose(this, label("actionCategories"), ActionPicker.categories(), id -> { actionCategory = id; actionDetail = false; rebuild(); }));
            EditorButton edit = button(new Box(column.x(), column.y() + 24, Math.min(144, column.width()), 28), label("editAction"), Kind.EDIT,
                () -> { if (form != null && form.invalid()) { localError = tr("invalidField"); return; } actionDetail = true; rebuild(); });
            edit.active = !busy() && !selectedAction.isEmpty();
            Box listBox = column.below(58);
            list(rows, listBox, selectedAction, id -> {
                if (form != null && form.invalid()) { localError = tr("invalidField"); return; }
                selectedAction = id; actionDetail = false;
                JsonObject info = session.actionInfo(id);
                if (info != null && preview != null) preview.playAction(info, actionEdit(id).parameters());
                rebuild();
            });
            if (split) actionDetails(session.actionInfo(selectedAction), new Box(listBox.right() + 8, editPane.y(), editPane.right() - listBox.right() - 8, editPane.height()));
        }
    }
    private void list(List<EditorList.Row> rows, Box area, String initial, Consumer<String> selected) {
        EditorTextField search = addRenderableWidget(new EditorTextField(font, area.x() + 4, area.y() + 4, area.width() - 8, 8, label("search")));
        searchField = search; search.setHint(label("search"));
        String query = searches.getOrDefault(page, ""); search.setValue(query);
        masterList = addRenderableWidget(new EditorList(font, area.below(23), rows, initial, selected)); masterList.filter(query);
        masterKey = "list:" + page.key; masterList.scrollTo(scrollPositions.getOrDefault(masterKey, 0));
        EditorList list = masterList; search.setResponder(text -> { searches.put(page, text); list.filter(text); });
    }
    private void actionDetails(JsonObject info, Box area) {
        if (info == null) return;
        ActionEdit edit = actionEdit(selectedAction);
        int columns = area.width() < 250 ? 2 : 4, w = (area.width() - (columns - 1) * 4) / columns;
        String[] tabs = {"actionGeneral", "actionParameters", "actionConditions", "actionStages"};
        for (int i = 0; i < 4; i++) {
            int tab = i;
            EditorButton button = button(new Box(area.x() + i % columns * (w + 4), area.y() + i / columns * 24, w, 20), label(tabs[i]), Kind.NORMAL, () -> {
                if (form != null && form.invalid()) { localError = tr("invalidField"); return; } actionTab = tab; rebuild();
            }); button.selected = actionTab == i; button.setTooltip(Tooltip.create(label(tabs[i])));
        }
        Box fields = area.below((4 / columns) * 24 + 2);
        if (actionTab <= 1) {
            newForm(fields, "action:" + selectedAction + ":" + actionTab);
            JsonObject data = actionTab == 0 ? edit.data : edit.parameters();
            Parameter[] schema = actionTab == 0 ? ActionConfig.SCHEMA : EditorSession.parameters(info);
            for (Parameter p : schema) form.field(p, data.has(p.id) ? data.get(p.id) : SafeJson.copy(p.defaultValue), checked -> data.add(p.id, checked));
        } else if (actionTab == 2) {
            button(new Box(fields.x(), fields.y(), fields.width(), 20), Component.literal(tr("editClauseCount", GuiSupport.tr("cnpcgun.gui.conditions"), edit.array("conditions").size())), Kind.NORMAL,
                () -> EditorDialogs.conditions(this, GuiSupport.tr("cnpcgun.gui.conditions"), edit.array("conditions"), values -> edit.data.add("conditions", values)));
            List<EditorList.Row> clauses = new ArrayList<>();
            for (int i = 0; i < edit.array("conditions").size(); i++) {
                JsonElement raw = edit.array("conditions").get(i); String id = raw.isJsonObject() ? raw.getAsJsonObject().get("id").getAsString() : raw.getAsString();
                clauses.add(new EditorList.Row(String.valueOf(i), GuiSupport.tr("cnpcgun.condition." + id), "", true));
            }
            addRenderableWidget(new EditorList(font, fields.below(26), clauses, "", ignored -> {}));
        } else {
            int half = (fields.width() - 6) / 2;
            EditorButton add = button(new Box(fields.x(), fields.y(), half, 20), label("addStage"), Kind.NORMAL, () -> {
                List<EditorList.Row> options = new ArrayList<>(); Set<String> seen = new HashSet<>();
                for (JsonElement raw : session.actions()) {
                    JsonObject action = raw.getAsJsonObject();
                    if (action.has("stageId") && seen.add(action.get("stageId").getAsString())) options.add(new EditorList.Row(action.get("stageId").getAsString(), GuiSupport.actionName(action), "", bool(action, "available")));
                }
                EditorDialogs.chooseRows(this, Component.translatable("cnpcgun.gui.stages"), options, id -> { if (edit.array("stages").size() < 16 && !bincnpc.gun.common.legacy.Legacy.jsonList(edit.array("stages")).contains(new JsonPrimitive(id))) edit.array("stages").add(id); });
            }); add.active = edit.array("stages").size() < 16;
            EditorButton remove = button(new Box(fields.x() + half + 6, fields.y(), fields.width() - half - 6, 20), label("removeStage"), Kind.NORMAL, () -> {
                if (selectedStage >= 0 && selectedStage < edit.array("stages").size()) edit.array("stages").remove(selectedStage); selectedStage = 0; rebuild();
            }); remove.active = !(edit.array("stages").size() == 0);
            List<EditorList.Row> stages = new ArrayList<>();
            for (int i = 0; i < edit.array("stages").size(); i++) stages.add(new EditorList.Row(String.valueOf(i), stageName(edit.array("stages").get(i).getAsString()), tr("stageNumber", i + 1), true));
            addRenderableWidget(new EditorList(font, fields.below(26), stages, String.valueOf(selectedStage), id -> selectedStage = Integer.parseInt(id)));
        }
        if (!bool(info, "available")) pageHint = tr("actionUnavailable");
        else if (info.has("description")) pageHint = GuiSupport.tr(info.get("description").getAsString());
    }
    private String actionName(String id) { JsonObject info = session.actionInfo(id); return info == null ? GuiSupport.tr("cnpcgun.gui.unavailable") : GuiSupport.actionName(info); }
    private String stageName(String id) { for (JsonElement raw : session.actions()) { if (!raw.isJsonObject()) continue; JsonObject info = raw.getAsJsonObject(); if (SafeJson.string(info, "stageId", "").equals(id)) return GuiSupport.actionName(info); } return name(id); }
    private boolean loadGraph(JsonObject source, String id) {
        try {
            GraphDraft latest = new GraphDraft(source, id);
            if (graph != null) latest.positions.putAll(graph.positions);
            graph = latest; selectedGraph = SafeJson.string(latest.graph, "id", id); canvas = null;
            if (graph.node(selectedNode) == null) selectedNode = "";
            return true;
        } catch (IllegalArgumentException invalid) { localError = tr("invalidGraphData"); return false; }
    }
    private List<EditorList.Row> graphRows() {
        List<EditorList.Row> rows = new ArrayList<>();
        for (JsonElement raw : session.array("graphs")) {
            if (!raw.isJsonObject()) continue;
            JsonObject item = raw.getAsJsonObject(); String id = SafeJson.string(item, "id", "");
            if (!id.isEmpty()) rows.add(new EditorList.Row(id, name(id), GuiSupport.tr("cnpcgun.gui.nodeCount", SafeJson.integer(item, "nodeCount", 0)), true));
        }
        return rows;
    }
    private void combos() {
        if (!blade()) { empty(tr("noBladeActions"), tr("noCombosHint"), true); return; }
        Box area = body();
        if (graph == null) {
            String id = SafeJson.string(session.snapshot, "selectedGraph", ""); JsonElement source = session.snapshot.get("graph");
            if (!id.isEmpty() && source != null && source.isJsonObject()) loadGraph(source.getAsJsonObject(), id);
        }
        int selectorWidth = Math.min(148, Math.max(70, area.width() - 132));
        if (canvasExpanded) selectorWidth = Math.max(60, Math.min(148, area.width() - Math.max(82, Math.min(176, area.width() / 3)) - 76));
        int top = canvasExpanded ? area.y() : layout.workspace.y() - 2;
        int selectorX = area.x() + (canvasExpanded ? 0 : 60);
        titleReserved = area.width() - 54;
        String shownName = graph == null ? tr("chooseCombo") : name(graph.graph.get("id").getAsString());
        serverButton(new Box(selectorX, top, selectorWidth, 20), Component.literal(shownName), () -> !graphRows().isEmpty(), tr("noSavedCombos"), () -> {
            Map<String, String> options = new LinkedHashMap<>(); for (EditorList.Row row : graphRows()) options.put(row.title() + " · " + row.subtitle(), row.id());
            EditorDialogs.choose(this, label("chooseCombo"), options, this::fetchGraph);
        });
        serverButton(new Box(selectorX + selectorWidth + 4, top, Math.min(64, area.right() - selectorX - selectorWidth - 4), 20), label("newCombo"), () -> !running(), tr("comboBusy"), () -> {
            if (graph != null && graph.dirty()) { saveChanges(this::newGraph); return; } newGraph();
        });
        Box editPane = editingPane(area, this::playCombo);
        if (graph == null) { text(tr("emptyComboLibrary"), editPane, 18, EditorStyle.INK); text(tr("emptyComboLibraryHint"), editPane, 36, EditorStyle.MUTED); return; }
        boolean wideTools = editPane.width() >= 344;
        int toolbarHeight = wideTools ? 30 : 56, toolbarY = area.bottom() - toolbarHeight;
        int canvasTop = canvasExpanded ? area.y() + 26 : layout.workspace.y() + 30;
        Box canvasBox = new Box(editPane.x(), canvasTop, editPane.width(), Math.max(30, toolbarY - canvasTop - 5));
        canvas = addRenderableWidget(new GraphCanvas(font, canvasBox, graph, selectedNode, this::actionName, id -> selectedNode = id, this::editNode));
        if (canvasView != null) canvas.view(canvasView); else if (editPane.width() < 200) canvas.view(new GraphCanvas.View(5, 5, 0.65));
        int editorWidth = wideTools ? Math.min(82, editPane.width() / 5) : (editPane.width() - 4) / 2;
        guardedButton(new Box(editPane.x(), toolbarY, editorWidth, 28), label("addNode"), Kind.ADD, () -> graph.nodes().size() < 128 && !(session.actions().size() == 0), tr("nodeLimit"), this::addNode);
        guardedButton(new Box(editPane.x() + editorWidth + 4, toolbarY, editorWidth, 28), label("nodeSettings"), Kind.EDIT, () -> graph.node(selectedNode) != null, tr("selectNode"), () -> editNode(selectedNode));
        int toolX = wideTools ? editPane.x() + editorWidth * 2 + 12 : editPane.x(), toolY = wideTools ? toolbarY : toolbarY + 32;
        int toolWidth = (editPane.right() - toolX - 6) / 4;
        String[] labels = {"toolProperties", "toolArrange", "toolCopy", "toolMore"}, tools = {"properties", "arrange", "copy", "more"};
        for (int i = 0; i < 4; i++) {
            String tool = tools[i];
            EditorButton button = guardedButton(new Box(toolX + i * (toolWidth + 2), toolY, toolWidth, wideTools ? 28 : 22), label(labels[i]), Kind.TOOL,
                () -> !tool.equals("copy") || graph.node(selectedNode) != null && graph.nodes().size() < 128, tr("selectNode"), () -> { if (tool.equals("more")) graphTools(); else graphTool(tool); });
            button.setTooltip(Tooltip.create(label(labels[i] + "Hint")));
            enabledTooltips.put(button, Tooltip.create(label(labels[i] + "Hint")));
        }
        pageHint = tr("canvasHint");
    }
    private void playCombo() {
        if (graph == null || (graph.nodes().size() == 0)) return;
        try { graph.validateEdges(); preview.playGraph(bincnpc.gun.common.legacy.Legacy.copy(graph.graph), this::previewActionInfo); }
        catch (IllegalArgumentException | IllegalStateException e) { localError = tr("invalidEdges"); }
    }
    private JsonObject previewActionInfo(String id) { return session.actionInfo(id); }
    private void newGraph() {
        String id = "combo_1"; Set<String> saved = new HashSet<>(); for (EditorList.Row row : graphRows()) saved.add(row.id());
        for (int i = 2; saved.contains(id); i++) id = "combo_" + i;
        graph = new GraphDraft(null, id); selectedGraph = ""; selectedNode = ""; canvasView = null; canvas = null; rebuild();
    }
    private void fetchGraph(String id) {
        saveChanges(() -> {
            session.send(Operation.FETCH_GRAPH, GuiSupport.with("id", id), response -> {
                JsonElement data = session.snapshot.get("graph");
                if (response.code().equals("SUCCESS") && data != null && data.isJsonObject() && loadGraph(data.getAsJsonObject(), id)) { selectedNode = ""; canvasView = null; }
            }); rebuild();
        });
    }
    private void addNode() {
        EditorDialogs.actions(this, session, label("addNode"), id -> { selectedNode = graph.add(id); rebuild(); });
    }
    private void editNode(String id) {
        if (session.pending || graph == null || graph.node(id) == null) return;
        openDialog(new NodeDialog(this, session, graph.node(id), graph.ids(), changed -> { graph.update(id, changed); selectedNode = changed.get("id").getAsString(); }));
    }
    private void graphTools() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put(tr("comboProperties"), "properties");
        options.put(tr(canvasExpanded ? "collapseCanvas" : "expandCanvas"), "expand");
        options.put(tr("arrangeNodes"), "arrange");
        if (graph.node(selectedNode) != null) {
            options.put(tr("copyNode"), "copy"); options.put(tr("deleteNode"), "remove");
            options.put(tr("nodeEarlier"), "earlier"); options.put(tr("nodeLater"), "later");
        }
        if (!(graph.nodes().size() == 0)) options.put(tr(preview != null && preview.playing() ? "stopPreview" : "playOnce"), preview != null && preview.playing() ? "stop" : "start");
        if (!selectedGraph.isEmpty()) options.put(tr("deleteCombo"), "delete");
        EditorDialogs.choose(this, label("comboTools"), options, this::graphTool);
    }
    private void graphTool(String chosen) {
            if (chosen.equals("properties")) {
                List<Parameter> schema = new ArrayList<>(); schema.add(new Parameter("id", "string", graph.graph.get("id").getAsString(), null, null, null, ""));
                if (!graph.ids().isEmpty()) schema.add(Parameter.choice("entryNode", graph.ids().get(0), graph.ids().toArray(new String[0])));
                EditorDialogs.form(this, label("comboProperties"), schema.toArray(new Parameter[0]), graph.graph, changed -> {
                    if (!changed.get("id").getAsString().matches("[A-Za-z0-9_.:-]{1,64}")) throw new IllegalArgumentException();
                    graph.graph.add("id", changed.get("id")); if (changed.has("entryNode")) graph.graph.add("entryNode", changed.get("entryNode"));
                });
            } else if (chosen.equals("expand")) { canvasExpanded = !canvasExpanded; canvas = null; rebuild(); }
            else if (chosen.equals("arrange")) { canvas.arrange(); canvasView = canvas.view(); }
            else if (chosen.equals("copy")) { selectedNode = graph.copy(selectedNode); rebuild(); }
            else if (chosen.equals("remove")) confirmDelete(tr("deleteNodeMessage"), () -> { graph.remove(selectedNode); selectedNode = ""; rebuild(); });
            else if (chosen.equals("earlier") || chosen.equals("later")) { graph.reorder(selectedNode, chosen.equals("earlier") ? -1 : 1); rebuild(); }
            else if (chosen.equals("start")) playCombo();
            else if (chosen.equals("stop") && preview != null) preview.stop();
            else if (chosen.equals("delete") && !running()) confirmDelete(tr("deleteComboMessage"), () -> {
                graph = null; mutate(Operation.DELETE_GRAPH, GuiSupport.with("id", selectedGraph)); selectedGraph = ""; canvas = null; canvasView = null;
            });
        }
    private void presets() {
        List<EditorList.Row> rows = new ArrayList<>();
        for (JsonElement raw : session.array("presets")) {
            if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isString()) continue;
            String id = raw.getAsString(); boolean builtin = bincnpc.gun.common.legacy.Legacy.set("BEGINNER", "NORMAL", "CUSTOM", "MAID").contains(id);
            rows.add(new EditorList.Row(id, name(id), builtin ? tr("preset." + id.toLowerCase(Locale.ROOT)) : tr(id.startsWith("user:") ? "sharedPreset" : "localPreset"), blade()));
        }
        if (selectedPreset.isEmpty()) selectedPreset = value("slashblade.preset").getAsString();
        Box area = body().below(26);
        int topWidth = (body().width() - 6) / 2;
        serverButton(new Box(body().x(), body().y(), topWidth, 20), label("saveCurrentPreset"), () -> true, "", () -> saveChanges(this::savePreset));
        guardedButton(new Box(body().x() + topWidth + 6, body().y(), body().width() - topWidth - 6, 20), label("applyBattlePreset"), Kind.PRIMARY,
            () -> blade() && !running() && !selectedPreset.isEmpty(), tr("presetUnavailable"), () -> mutate(Operation.PRESET, GuiSupport.with("id", selectedPreset)));
        boolean split = body().width() >= 400;
        Box pane = split ? new Box(area.x(), area.y(), layout.listPane().width(), area.height()) : new Box(area.x(), area.y(), area.width(), area.height() - 26);
        list(rows, pane, selectedPreset, id -> { selectedPreset = id; rebuild(); });
        if (!split) button(new Box(area.x(), area.bottom() - 20, area.width(), 20), label("presetTools"), Kind.TOOL, () -> presetDialog(selectedPreset));
        if (split && !selectedPreset.isEmpty()) {
            Box detail = new Box(pane.right() + 8, area.y(), area.right() - pane.right() - 8, area.height());
            text(name(selectedPreset), detail, 10, EditorStyle.INK);
            text(presetDescription(selectedPreset), detail, 29, EditorStyle.MUTED);
            button(new Box(detail.x() + 8, detail.y() + 53, detail.width() - 16, 20), label("copyPreset"), Kind.NORMAL, () -> copyPreset(selectedPreset));
            if (!builtinPreset(selectedPreset)) guardedButton(new Box(detail.x() + 8, detail.y() + 79, detail.width() - 16, 20), label("deletePreset"), Kind.DANGER,
                () -> !running(), tr("comboBusy"), () -> deletePreset(selectedPreset));
        }
    }
    private String presetDescription(String id) { return bincnpc.gun.common.legacy.Legacy.set("BEGINNER", "NORMAL", "CUSTOM", "MAID").contains(id) ? tr("preset." + id.toLowerCase(Locale.ROOT)) : tr(id.startsWith("user:") ? "sharedPreset" : "localPreset"); }
    private static boolean builtinPreset(String id) { return bincnpc.gun.common.legacy.Legacy.set("BEGINNER", "NORMAL", "CUSTOM", "MAID").contains(id); }
    private void deletePreset(String id) { confirmDelete(tr("deletePresetMessage"), () -> { selectedPreset = ""; mutate(Operation.DELETE_PRESET, GuiSupport.with("id", id)); }); }
    private void presetDialog(String id) {
        List<EditorDialogs.Decision> choices = new ArrayList<>();
        if (blade() && !running()) choices.add(new EditorDialogs.Decision(label("applyPreset"), Kind.PRIMARY, () -> mutate(Operation.PRESET, GuiSupport.with("id", id))));
        choices.add(new EditorDialogs.Decision(label("copyPreset"), Kind.NORMAL, () -> copyPreset(id)));
        if (!builtinPreset(id)) choices.add(new EditorDialogs.Decision(label("deletePreset"), Kind.DANGER, () -> deletePreset(id)));
        choices.add(new EditorDialogs.Decision(Component.translatable("gui.cancel"), Kind.NORMAL, () -> {}));
        EditorDialogs.confirm(this, Component.literal(name(id)), presetDescription(id), choices);
    }
    private void promptName(String title, String initial, Consumer<String> applied) {
        JsonObject data = new JsonObject(); data.addProperty("id", initial);
        EditorDialogs.form(this, label(title), new Parameter[]{new Parameter("id", "string", initial, null, null, null, "")}, data, values -> {
            String id = values.get("id").getAsString().trim(); if (!id.matches("[A-Za-z0-9_.-]{1,48}")) throw new IllegalArgumentException(); applied.accept(id);
        });
    }
    private void copyPreset(String source) {
        promptName("copyPreset", "my_preset", id -> { JsonObject input = GuiSupport.with("id", "custom:" + id); input.addProperty("source", source); mutate(Operation.COPY, input); });
    }
    private void savePreset() { promptName("saveCurrentPreset", "my_preset", id -> mutate(Operation.SAVE_PRESET, GuiSupport.with("id", "user:" + id))); }
    private void advanced() {
        newForm(body(), "advanced");
        fields(tr("combatPolicy"), p -> p.id.startsWith("combat."));
    }
    private void bladeSettings() {
        if (!blade()) { empty(tr("noBladeActions"), tr("noBladeActionsHint"), true); return; }
        newForm(body(), "bladeSettings"); fields(tr("bladePolicy"), p -> p.id.startsWith("slashblade.") && !p.id.equals("slashblade.preset"));
    }
    private void refresh() {
        if (busy()) return;
        if (dirty() || form != null && form.invalid()) { localError = tr("saveBeforeRefresh"); return; }
        session.send(session.token.equals(GunNetwork.NO_TOKEN) ? Operation.OPEN : Operation.REFRESH, new JsonObject()); rebuild();
    }
    private void mutate(Operation operation, JsonObject input) {
        saveChanges(() -> { session.send(operation, input); rebuild(); });
    }
    private void saveChanges(Runnable after) {
        if (busy() || !session.ready()) return;
        if (form != null && form.invalid()) { localError = tr("invalidField"); return; }
        List<SaveStep> queue = new ArrayList<>();
        for (JsonObject values : draft.batches(32)) {
            JsonObject input = new JsonObject(); input.add("values", values);
            queue.add(new SaveStep(Operation.BATCH, input, () -> draft.acknowledge(values)));
        }
        for (Map.Entry<String, ActionEdit> entry : actionEdits.entrySet()) if (entry.getValue().dirty()) {
            ActionEdit action = entry.getValue(); JsonObject submitted = bincnpc.gun.common.legacy.Legacy.copy(action.data);
            JsonObject input = GuiSupport.with("id", entry.getKey()); input.add("config", submitted);
            queue.add(new SaveStep(Operation.ACTION_CONFIG, input, () -> action.original = bincnpc.gun.common.legacy.Legacy.copy(submitted)));
        }
        if (graph != null && graph.dirty()) {
            if ((graph.nodes().size() == 0)) { localError = tr("emptyGraphSave"); return; }
            try { graph.validateEdges(); } catch (IllegalArgumentException e) { localError = tr("invalidEdges"); return; }
            GraphDraft savedGraph = graph; JsonObject submitted = bincnpc.gun.common.legacy.Legacy.copy(graph.graph);
            queue.add(new SaveStep(Operation.GRAPH, submitted, () -> { savedGraph.acknowledge(submitted); selectedGraph = submitted.get("id").getAsString(); }));
        }
        localError = "";
        if (queue.isEmpty()) { if (after != null) after.run(); return; }
        saving = true; saveNext(queue, 0, after);
    }
    private void saveNext(List<SaveStep> queue, int index, Runnable after) {
        if (index >= queue.size()) {
            saving = false; saveContinuation = null; draft.reconcile(session.values());
            actionEdits.replaceAll((id, edited) -> edited.dirty() ? edited : new ActionEdit(session.action(id)));
            JsonElement current = session.snapshot.get("graph");
            if (graph != null && !graph.dirty() && current != null && current.isJsonObject()) {
                loadGraph(current.getAsJsonObject(), "");
            }
            toast.show(tr("savedToast")); rebuild(); if (after != null) after.run(); return;
        }
        SaveStep step = queue.get(index);
        boolean sent = session.send(step.operation, step.payload, result -> {
            if (result.code().equals("SUCCESS")) {
                step.acknowledged.run(); saveDelay = 3; saveContinuation = () -> saveNext(queue, index + 1, after);
            } else { saving = false; saveContinuation = null; }
        });
        if (!sent) { saving = false; localError = GuiSupport.tr(session.status); }
        rebuild();
    }
    private void discardChanges() {
        if (!dirty() && (form == null || !form.invalid())) return;
        EditorDialogs.confirm(this, label("discardTitle"), tr("discardMessage"), unsavedDetails(), bincnpc.gun.common.legacy.Legacy.list(
            new EditorDialogs.Decision(label("discard"), Kind.DANGER, () -> { draft.clear(); actionEdits.clear(); invalidText.clear(); form = null; graph = null; selectedNode = ""; canvas = null; canvasView = null; localError = ""; rebuild(); }),
            new EditorDialogs.Decision(Component.translatable("gui.cancel"), Kind.NORMAL, () -> {})));
    }
    private void operationToast(Operation operation) {
        if (operation == null) return;String key;
switch (operation) {
case PRESET:
key = "presetAppliedToast"; break;
case COPY:
key = "presetCopiedToast"; break;
case SAVE_PRESET:
key = "presetSavedToast"; break;
case DELETE_PRESET:
key = "presetDeletedToast"; break;
case EXCHANGE:
case EQUIP_CREATIVE:
case EQUIP_ITEM_PRESET:
key = "equippedToast"; break;
case UNEQUIP:
key = "unequippedToast"; break;
case PRIMARY_WEAPON:
key = "primaryWeaponToast"; break;
case SAVE_ITEM_PRESET:
key = "itemPresetSavedToast"; break;
case DELETE_ITEM_PRESET:
key = "itemPresetDeletedToast"; break;
default:
key = ""; break;
};
        if (!key.isEmpty()) toast.show(tr(key));
    }
    private static String difference(JsonElement before, JsonElement after) {
        if (after != null && after.isJsonPrimitive()) return GuiSupport.value(before) + " → " + GuiSupport.value(after);
        return tr("changedEntry");
    }
    private List<String> unsavedDetails() {
        List<String> details = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : draft.snapshot().entrySet()) {
            Parameter p = ConfigSchema.get(entry.getKey());
            details.add((p == null ? entry.getKey() : GuiSupport.tr(p.label)) + " · " + difference(session.values().get(entry.getKey()), entry.getValue()));
        }
        for (Map.Entry<String, ActionEdit> entry : actionEdits.entrySet()) if (entry.getValue().dirty()) {
            ActionEdit edit = entry.getValue();
            for (Map.Entry<String, JsonElement> field : edit.data.entrySet()) if (!field.getValue().equals(edit.original.get(field.getKey()))) {
                if (field.getKey().equals("parameters")) {
                    JsonObject before = edit.original.getAsJsonObject("parameters");
                    for (Map.Entry<String, JsonElement> p : edit.parameters().entrySet()) if (!p.getValue().equals(before.get(p.getKey())))
                        details.add(actionName(entry.getKey()) + " / " + GuiSupport.tr("cnpcgun.config." + p.getKey()) + " · " + difference(before.get(p.getKey()), p.getValue()));
                } else details.add(actionName(entry.getKey()) + " / " + GuiSupport.tr("cnpcgun.config." + field.getKey()) + " · " + difference(edit.original.get(field.getKey()), field.getValue()));
            }
        }
        if (graph != null && graph.dirty()) {
            JsonObject before = graph.original(); Map<String, JsonObject> oldNodes = new LinkedHashMap<>();
            for (JsonElement raw : before.getAsJsonArray("nodes")) { JsonObject node = raw.getAsJsonObject(); oldNodes.put(node.get("id").getAsString(), node); }
            String title = tr("combos") + " / " + graph.graph.get("id").getAsString();
            for (String key : bincnpc.gun.common.legacy.Legacy.list("id", "entryNode")) if (!graph.graph.get(key).equals(before.get(key)))
                details.add(title + " / " + GuiSupport.tr("cnpcgun.config." + key) + " · " + difference(before.get(key), graph.graph.get(key)));
            for (JsonElement raw : graph.nodes()) {
                JsonObject node = raw.getAsJsonObject(); String id = node.get("id").getAsString(); JsonObject old = oldNodes.remove(id);
                if (old == null) { details.add(title + " · " + tr("addedNodeDetail", id, actionName(node.get("action").getAsString()))); continue; }
                for (Map.Entry<String, JsonElement> field : node.entrySet()) if (!field.getValue().equals(old.get(field.getKey())))
                    details.add(title + " / " + id + " / " + GuiSupport.tr("cnpcgun.config." + field.getKey()) + " · " + difference(old.get(field.getKey()), field.getValue()));
            }
            for (String id : bincnpc.gun.common.legacy.Legacy.keys(oldNodes)) details.add(title + " · " + tr("deletedNodeDetail", id));
        }
        if (form != null && form.invalid()) details.add(tr("invalidField"));
        return details;
    }
    private void confirmDelete(String message, Runnable action) {
        EditorDialogs.confirm(this, label("deleteTitle"), message, bincnpc.gun.common.legacy.Legacy.list(
            new EditorDialogs.Decision(Component.translatable("cnpcgun.gui.remove"), Kind.DANGER, action),
            new EditorDialogs.Decision(Component.translatable("gui.cancel"), Kind.NORMAL, () -> {})));
    }
    private void updateControls() {
        for (Guard guard : guards) {
            guard.widget.active = !busy() && dialogs.isEmpty() && session.ready() && guard.allowed.getAsBoolean();
            guard.widget.setTooltip(!guard.widget.active && !guard.reason.isEmpty() ? Tooltip.create(Component.literal(guard.reason)) : enabledTooltips.get(guard.widget));
        }
        if (form != null) form.locked(busy() || !dialogs.isEmpty());
        for (WeaponSlotButton slot : weaponButtons) slot.active = !busy() && dialogs.isEmpty();
        if (save != null) save.active = !busy() && dialogs.isEmpty() && session.ready() && dirty() && (form == null || !form.invalid());
        if (discard != null) discard.active = !busy() && dialogs.isEmpty() && (dirty() || form != null && form.invalid());
        if (canvas != null) canvas.active = !busy() && dialogs.isEmpty();
        if (previewButton != null) previewButton.setMessage(label(preview != null && preview.playing() ? "stopPreview" : "playOnce"));
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        rebuildNow();
        queuedTooltip = null; updateControls();
        g.fill(0, 0, width, height, EditorStyle.IRON);
        g.fill(0, 0, width, 34, EditorStyle.SLATE); g.hLine(0, width, 33, EditorStyle.EDGE);
        if (!canvasExpanded) EditorStyle.panel(g, layout.sidebar, EditorStyle.SLATE);
        if (!canvasExpanded) {
            EditorStyle.text(g, font, pageTitle, layout.workspace.x() + 2 + titleOffset, layout.workspace.y() + 1, layout.workspace.width() - 4 - titleOffset - titleReserved, EditorStyle.INK);
            EditorStyle.text(g, font, pageHint, layout.workspace.x() + 2, layout.workspace.y() + (page == Page.COMBOS ? 18 : 14), layout.workspace.width() - 4, EditorStyle.MUTED);
        }
        EditorStyle.text(g, font, title.getString(), 104, 12, 82, EditorStyle.WHITE);
        g.fill(0, height - 26, width, height, EditorStyle.SLATE); g.hLine(0, width, height - 26, EditorStyle.EDGE);
        super.render(g, dialogs.isEmpty() ? mouseX : -10000, dialogs.isEmpty() ? mouseY : -10000, partialTick);
        if (extensionScreen != null) {
            Box area = body(); boolean hovered = dialogs.isEmpty() && !busy() && area.contains(mouseX, mouseY);
            g.flush(); g.enableScissor(area.x(), area.y(), area.right(), area.bottom());
            g.pose().pushPose(); g.pose().translate(area.x(), area.y(), 0);
            WorkspaceGraphics workspace = new WorkspaceGraphics(g, area);
            tooltipTarget = extensionScreen;
            try {
                extensionScreen.renderWithTooltip(workspace, hovered ? mouseX - area.x() : -10000,
                    hovered ? mouseY - area.y() : -10000, partialTick);
            } finally { tooltipTarget = null; g.pose().popPose(); g.disableScissor(); g.prepare2D(); }
        }
        for (Text line : texts) EditorStyle.text(g, font, line.text, line.x, line.y, line.width, line.color);
        String status = !localError.isEmpty() ? localError : session.status.startsWith("cnpcgun.error") ? GuiSupport.tr(session.status)
            : busy() ? GuiSupport.tr("cnpcgun.gui.saving") : dirty() ? tr("unsaved") : GuiSupport.tr(session.status);
        int color = !localError.isEmpty() || session.status.startsWith("cnpcgun.error") ? 0xFFF1B6A7 : session.pending || dirty() ? 0xFFE1CE95 : 0xFFD4DFC7;
        EditorStyle.text(g, font, status, 10, height - 16, layout.statusWidth(), color);
        if (dialogs.isEmpty()) {
            for (WeaponSlotButton slot : weaponButtons) if (slot.isMouseOver(mouseX, mouseY)) {
                g.renderTooltip(font, Component.literal((slot.item().isEmpty() ? tr("emptySlot") : slot.item().getDisplayName()) + "\n" + tr("slotSelectHint")), mouseX, mouseY);
            }
        } else {
            List<Screen> layers = new ArrayList<>(dialogs); Collections.reverse(layers);
            for (int i = 0; i < layers.size(); i++) {
                g.flush(); g.pose().pushPose();
                tooltipTarget = layers.get(i);
                tooltipTarget.renderWithTooltip(new GuiGraphics(tooltipTarget), i == layers.size() - 1 ? mouseX : -10000, i == layers.size() - 1 ? mouseY : -10000, partialTick);
                tooltipTarget = null;
                g.flush(); g.pose().popPose();
            }
        }
        g.flush(); g.pose().pushPose();
        toast.render(g, width);
        if (queuedTooltip != null) g.renderTooltip(font, queuedTooltip, tooltipPositioner, mouseX, mouseY);
        g.flush(); g.pose().popPose();
    }
    @Override public void tick() {
        rebuildNow();
        session.tick(extensionScreen == null && !busy() && !dirty() && dialogs.isEmpty() && (searchField == null || !searchField.isFocused()) && (form == null || !form.editing() && !form.invalid()));
        if (saveContinuation != null && --saveDelay <= 0) { Runnable next = saveContinuation; saveContinuation = null; next.run(); }
        else if (saving && !session.pending && saveContinuation == null) { saving = false; localError = GuiSupport.tr(session.status); }
        if (form != null) form.tick();
        if (searchField != null) searchField.tick();
        if (preview != null) preview.tick();
        if (!dialogs.isEmpty()) dialogs.peek().tick();
        else if (extensionScreen != null) extensionScreen.tick();
        updateControls();
    }
    @Override public void openDialog(Screen dialog) {
        setDragging(false); setFocused(null);
        if (extensionScreen != null) extensionScreen.setDragging(false);
        if (!dialogs.isEmpty()) dialogs.peek().setDragging(false);
        dialogs.push(dialog); dialog.init(minecraft, width, height); dialog.added();
    }
    @Override public void closeDialog() {
        if (dialogs.isEmpty()) return;
        Screen removed = dialogs.pop();
        if (removed == extensionDialog) extensionDialog = null;
        removed.removed();
        if (dialogs.isEmpty()) rebuild(); else if (dialogs.peek() != extensionDialog && dialogs.peek() instanceof EditorDialogs.Base) ((EditorDialogs.Base)dialogs.peek()).rebuild();
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (!dialogs.isEmpty()) {
            Screen dialog = dialogs.peek(); AbstractWidget focused = dialog.getFocused(); long request = leaveRequest;
            dialog.mouseClicked(x, y, button);
            if (dialogs.contains(dialog) && request != leaveRequest) dialog.setFocused(focused);
            return true;
        }
        if (busy()) return true;
        if (extensionScreen != null) {
            Box area = body();
            if (area.contains(x, y)) {
                extensionFocused = true; setFocused(null);
                Screen screen = extensionScreen; AbstractWidget focused = screen.getFocused(); long request = leaveRequest;
                screen.mouseClicked(x - area.x(), y - area.y(), button);
                if (extensionScreen == screen && request != leaveRequest) screen.setFocused(focused);
                return true;
            }
            extensionScreen.setDragging(false);
        }
        boolean clicked = super.mouseClicked(x, y, button);
        if (extensionScreen != null && extensionFocused) { setFocused(null); setDragging(false); }
        return clicked;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (!dialogs.isEmpty()) { dialogs.peek().mouseReleased(x, y, button); return true; }
        if (extensionScreen != null && extensionFocused) {
            Box area = body(); extensionScreen.mouseReleased(x - area.x(), y - area.y(), button); return true;
        }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (!dialogs.isEmpty()) { dialogs.peek().mouseDragged(x, y, button, dx, dy); return true; }
        if (busy()) return true;
        if (extensionScreen != null && extensionFocused) {
            Box area = body(); extensionScreen.mouseDragged(x - area.x(), y - area.y(), button, dx, dy); return true;
        }
        if (canvas != null && getFocused() == canvas) return canvas.mouseDragged(x, y, button, dx, dy);
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (!dialogs.isEmpty()) { dialogs.peek().mouseScrolled(x, y, delta); return true; }
        if (!busy() && !canvasExpanded && layout.sidebar.contains(x, y)) {
            navigationScroll = EditorLayout.clampScroll(navigationScroll - (int)(delta * 20), navigationHeight, layout.sidebar.height()); rebuild(); return true;
        }
        if (!busy() && extensionScreen != null && body().contains(x, y)) {
            Box area = body(); extensionScreen.mouseScrolled(x - area.x(), y - area.y(), delta); return true;
        }
        return busy() || super.mouseScrolled(x, y, delta);
    }
    @Override public void mouseMoved(double x, double y) {
        if (!dialogs.isEmpty()) { dialogs.peek().mouseMoved(x, y); return; }
        if (extensionScreen != null) {
            Box area = body(); boolean hovered = !busy() && area.contains(x, y);
            extensionScreen.mouseMoved(hovered ? x - area.x() : -10000, hovered ? y - area.y() : -10000);
        }
    }
    @Override public boolean charTyped(char letter, int modifiers) {
        if (!dialogs.isEmpty()) { dialogs.peek().charTyped(letter, modifiers); return true; }
        if (busy()) return true;
        return extensionScreen != null && extensionFocused ? extensionScreen.charTyped(letter, modifiers) : super.charTyped(letter, modifiers);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (!dialogs.isEmpty()) return dialogs.peek().keyPressed(key, scan, modifiers);
        if (busy()) return true;
        if (extensionScreen != null && (extensionFocused || key == Keys.KEY_ESCAPE))
            return extensionScreen.keyPressed(key, scan, modifiers);
        if (key == Keys.KEY_S && (modifiers & Keys.GLFW_MOD_CONTROL) != 0) { saveChanges(null); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean keyReleased(int key, int scan, int modifiers) {
        if (!dialogs.isEmpty()) { dialogs.peek().keyReleased(key, scan, modifiers); return true; }
        if (busy()) return true;
        return extensionScreen != null && extensionFocused ? extensionScreen.keyReleased(key, scan, modifiers) : super.keyReleased(key, scan, modifiers);
    }
    @Override public void setTooltipForNextRenderPass(List<FormattedCharSequence> lines, ClientTooltipPositioner positioner, boolean force) {
        if (tooltipTarget != null) { tooltipTarget.setTooltipForNextRenderPass(lines, positioner, force); return; }
        if (queuedTooltip == null || force) { queuedTooltip = bincnpc.gun.common.legacy.Legacy.copyList(lines); tooltipPositioner = positioner; }
    }
    @Override public void setTooltipForNextRenderPass(List<FormattedCharSequence> lines) { setTooltipForNextRenderPass(lines, DefaultTooltipPositioner.INSTANCE, false); }
    @Override public void setTooltipForNextRenderPass(Tooltip tooltip, ClientTooltipPositioner positioner, boolean force) { setTooltipForNextRenderPass(tooltip.toCharSequence(minecraft), positioner, force); }
    @Override protected void setTooltipForNextRenderPass(Component text) { setTooltipForNextRenderPass(Tooltip.splitTooltip(minecraft, text)); }
    private void returnAppearance() { if (!closed) minecraft.displayGuiScreen(new GuiNpcDisplay(npc)); }
    @Override public void onClose() {
        if (busy()) { localError = tr("waitForSave"); return; }
        if (extensionDialog != null) requestLeave(extensionDialog, () -> requestLeave(extensionScreen, this::closeEditor));
        else requestLeave(extensionScreen, this::closeEditor);
    }
    private void closeEditor() {
        if (!dirty() && (form == null || !form.invalid())) { returnAppearance(); return; }
        EditorDialogs.confirm(this, label("leaveTitle"), tr("leaveMessage"), unsavedDetails(), bincnpc.gun.common.legacy.Legacy.list(
            new EditorDialogs.Decision(label("saveAndReturn"), Kind.PRIMARY, () -> saveChanges(this::returnAppearance)),
            new EditorDialogs.Decision(label("discardAndReturn"), Kind.DANGER, this::returnAppearance),
            new EditorDialogs.Decision(label("continueEditing"), Kind.NORMAL, () -> {})));
    }
    @Override public void removed() { if (!closed) { closed = true; ++leaveRequest; if (preview != null) preview.dispose(); removeExtension(); for (Screen dialog : dialogs) dialog.removed(); dialogs.clear(); extensionDialog = null; session.close(); } super.removed(); }
    @Override public boolean isPauseScreen() { return false; }
}
