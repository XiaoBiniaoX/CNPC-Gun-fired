package bincnpc.gun.client.gui.editor;

import bincnpc.gun.client.EditorSession;
import bincnpc.gun.client.gui.GuiSupport;
import bincnpc.gun.common.config.Parameter;
import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.network.CreativeCatalogue;
import bincnpc.gun.common.network.GunNetwork.Operation;
import com.google.gson.*;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import static bincnpc.gun.client.gui.editor.EditorLayout.Box;

/** An equipment picker that sends only server-resolvable item sources. */
public final class InventoryDialog extends EditorDialogs.Base {
    private final EditorSession session;
    private final BiConsumer<Operation, JsonObject> send;
    private final List<WeaponSlotButton> slots = new ArrayList<>();
    private List<CreativeCatalogue.Tab> catalogue = List.of();
    private int targetSlot, itemPage, tabPage, presetHash, creativeCount = 1;
    private final int slotBase, slotCount;
    private String tabId = "", query = "", presetQuery = "", presetId = "";
    private EditorTextField search, presetSearch;
    private EditorList presets;
    private Browser browser;
    private Box playerArea;
    private EditorButton create, remove, previous, next, unload;
    private TabStrip tabStrip;
    private Box browseArea;
    private int categoryWidth;
    private int cell;
    public InventoryDialog(EditorDialogs.Host host, EditorSession session, int slot, BiConsumer<Operation, JsonObject> send) {
        super(host, Component.translatable("cnpcgun.editor.exchangeTitle")); this.session = session; targetSlot = slot; this.send = send;
        slotBase = slot >= 6 ? 6 : 0; slotCount = slotBase == 6 ? 3 : 6;
    }
    private static Component label(String key) { return Component.translatable("cnpcgun.editor." + key); }
    private static boolean throwable(ItemStack item) { return item != null && (item.isEmpty() || bincnpc.gun.throwing.ThrowAdapters.find(item) != null); }
    @Override protected void init() {
        boolean itemFocus = search != null && search.isFocused(), presetFocus = presetSearch != null && presetSearch.isFocused();
        super.init(); slots.clear();
        int w = Math.min(760, width - 12), h = Math.min(500, height - 12);
        box = new Box((width - w) / 2, (height - h) / 2, w, h);
        content = new Box(box.x() + 8, box.y() + 40, w - 16, h - 74);
        subtitle = GuiSupport.tr("cnpcgun.editor.equipmentTarget", GuiSupport.tr("cnpcgun.gui.slot" + targetSlot));
        int leftWidth = Math.max(76, Math.min(152, content.width() / 4));
        Box left = new Box(content.x(), content.y(), leftWidth, content.height());
        Box main = new Box(left.right() + 6, content.y(), content.right() - left.right() - 6, content.height());
        cell = height < 300 ? 18 : Math.max(18, Math.min(26, (main.width() - 32) / 9));
        int playerHeight = 14 + 4 * (cell + 2);
        playerArea = new Box(main.x(), content.bottom() - playerHeight, main.width(), playerHeight);
        int browserHeight = playerArea.y() - content.y() - 2;
        int reserveWidth = Math.max(60, Math.min(156, main.width() / 4));
        Box browse = new Box(main.x(), main.y(), main.width() - reserveWidth - 6, browserHeight);
        browseArea = browse;
        Box reserve = new Box(browse.right() + 6, content.y(), reserveWidth, browserHeight);
        boolean openedCatalogue = catalogue.isEmpty();
        if (openedCatalogue && minecraft.player != null && minecraft.level != null) {
            try {
                catalogue = slotBase == 6 ? CreativeCatalogue.throwingTabs(minecraft.level.enabledFeatures(), minecraft.player.hasPermissions(2), minecraft.level.registryAccess())
                    : CreativeCatalogue.tabs(minecraft.level.enabledFeatures(), minecraft.player.hasPermissions(2), minecraft.level.registryAccess());
                if (tabId.isEmpty()) tabId = catalogue.stream().filter(tab -> !tab.id().startsWith("minecraft:")).map(CreativeCatalogue.Tab::id).findFirst()
                    .orElse(catalogue.isEmpty() ? "" : catalogue.get(0).id());
            } catch (RuntimeException | LinkageError e) { error = GuiSupport.tr("cnpcgun.editor.catalogueUnavailable"); }
        }
        categoryWidth = Math.max(44, Math.min(156, browse.width() / 2 - 6));
        search = addRenderableWidget(new EditorTextField(font, browse.x() + categoryWidth + 8, browse.y() + 6, browse.width() - categoryWidth - 12, 8, label("searchItems")));
        search.setHint(label("searchItems")); search.setValue(query);
        create = addRenderableWidget(new EditorButton(left.x(), left.y(), left.width(), 20, label("createItemPreset"), EditorButton.Kind.PRIMARY, this::createPreset));
        presetSearch = addRenderableWidget(new EditorTextField(font, left.x() + 4, left.y() + 28, left.width() - 8, 8, label("searchPresets")));
        presetSearch.setHint(label("searchPresets")); presetSearch.setValue(presetQuery);
        presets = addRenderableWidget(new EditorList(font, new Box(left.x(), left.y() + 44, left.width(), Math.max(18, left.height() - 64)), presetRows(), presetId, id -> {
            presetId = id; if (!session.creativeAllowed() || session.pending) return;
            JsonObject input = source(); input.addProperty("id", id);
            int limit = 64;
            for (JsonElement raw : itemPresets()) if (raw.isJsonObject()) {
                JsonObject item = raw.getAsJsonObject();
                if (id.equals(SafeJson.string(item, "id", ""))) { limit = SafeJson.integer(item, "stackLimit", 1); break; }
            }
            equipTemplate(Operation.EQUIP_ITEM_PRESET, input, limit);
        })); presets.filter(presetQuery);
        remove = addRenderableWidget(new EditorButton(left.x(), left.bottom() - 17, left.width(), 16, label("deleteItemPreset"), EditorButton.Kind.DANGER, () -> {
            if (presetId.isEmpty() || session.pending) return;
            EditorDialogs.confirm(host, label("deleteTitle"), GuiSupport.tr("cnpcgun.editor.deleteItemPresetMessage"), List.of(
                new EditorDialogs.Decision(label("deleteItemPreset"), EditorButton.Kind.DANGER, () -> { JsonObject input = new JsonObject(); input.addProperty("id", presetId); send.accept(Operation.DELETE_ITEM_PRESET, input); presetId = ""; }),
                new EditorDialogs.Decision(Component.translatable("gui.cancel"), EditorButton.Kind.NORMAL, () -> {})));
        }));
        int headerHeight = browserHeight < 70 ? 18 : 22, stripHeight = browserHeight < 70 ? 20 : 24;
        browser = addRenderableWidget(new Browser(new Box(browse.x(), browse.y() + headerHeight, browse.width(), Math.max(18, browse.height() - headerHeight - stripHeight - 2))));
        tabStrip = addRenderableWidget(new TabStrip(new Box(browse.x() + 21, browse.bottom() - stripHeight, Math.max(stripHeight, browse.width() - 42), stripHeight)));
        if (openedCatalogue) for (int i = 0; i < catalogue.size(); i++) if (catalogue.get(i).id().equals(tabId)) tabPage = i / tabStrip.capacity;
        previous = addRenderableWidget(new EditorButton(browse.x(), browse.bottom() - stripHeight + 1, 18, stripHeight - 2, Component.literal("‹"), EditorButton.Kind.TOOL, () -> tabPage = Math.max(0, tabPage - 1)));
        next = addRenderableWidget(new EditorButton(browse.right() - 18, browse.bottom() - stripHeight + 1, 18, stripHeight - 2, Component.literal("›"), EditorButton.Kind.TOOL, () -> tabPage = Math.min(tabStrip.pageCount() - 1, tabPage + 1)));
        search.setResponder(text -> { query = text; itemPage = 0; browser.refresh(); });
        presetSearch.setResponder(text -> { presetQuery = text; presets.filter(text); });
        boolean reserveButton = browserHeight >= 128;
        int compact = Math.max(18, Math.min(64, (reserve.height() - (reserveButton ? 28 : 0) - 4) / 3));
        int columns = slotBase == 6 ? 1 : 2;
        for (int i = 0; i < slotCount; i++) {
            int slot = slotBase + i; int sw = (reserve.width() - (columns - 1) * 4) / columns;
            Box spot = new Box(reserve.x() + i % columns * (sw + 4), reserve.y() + 1 + i / columns * (compact + 1), sw, compact);
            Component role = compact < 34 ? Component.empty() : Component.translatable("cnpcgun.editor.shortSlot" + slot);
            WeaponSlotButton button = addRenderableWidget(new WeaponSlotButton(spot, role, () -> session.slot(slot),
                () -> slotBase == 0 && SafeJson.integer(session.values(), "weapon.primarySlot", 0) == slot, () -> { targetSlot = slot; subtitle = GuiSupport.tr("cnpcgun.editor.equipmentTarget", GuiSupport.tr("cnpcgun.gui.slot" + slot)); slots.forEach(item -> item.selected = false); }));
            button.selected = targetSlot == slot; slots.add(button);
        }
        int start = main.x() + 2;
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            inventory(9 + row * 9 + column, new Box(start + column * (cell + 2), playerArea.y() + 10 + row * (cell + 2), cell, cell));
        int hotbar = playerArea.y() + 12 + 3 * (cell + 2);
        for (int i = 0; i < 9; i++) inventory(i, new Box(start + i * (cell + 2), hotbar, cell, cell));
        inventory(40, new Box(Math.min(playerArea.right() - cell, start + 9 * (cell + 2) + 2), hotbar, cell, cell));
        Box unloadBox = reserveButton ? new Box(reserve.x(), reserve.bottom() - 24, reserve.width(), 22)
            : new Box(box.x() + 10, box.bottom() - 28, (box.width() - 26) / 2, 20);
        unload = addRenderableWidget(new EditorButton(unloadBox, label("unequipItem"), EditorButton.Kind.DANGER,
            () -> { if (!session.pending && session.inventoryAllowed() && !session.slot(targetSlot).isEmpty()) send.accept(Operation.UNEQUIP, source()); }));
        if (reserveButton) button(0, 1, Component.translatable("gui.done"), EditorButton.Kind.NORMAL, host::closeDialog);
        else button(1, 2, Component.translatable("gui.done"), EditorButton.Kind.NORMAL, host::closeDialog);
        if (itemFocus) setFocused(search); else if (presetFocus) setFocused(presetSearch);
        browser.refresh(); presetHash = itemPresets().hashCode(); update();
    }
    private JsonObject source() { JsonObject input = new JsonObject(); input.addProperty("slot", targetSlot); return input; }
    private void equipTemplate(Operation operation, JsonObject input, int limit) {
        if (slotBase != 6) { send.accept(operation, input); return; }
        int maximum = Math.max(1, Math.min(64, limit));
        JsonObject initial = new JsonObject(); initial.addProperty("throw.count", Math.min(creativeCount, maximum));
        EditorDialogs.form(host, label("throwStackCount"), new Parameter[]{Parameter.integer("throw.count", 1, 1, maximum)}, initial, values -> {
            if (!session.creativeAllowed() || session.pending) return;
            creativeCount = values.get("throw.count").getAsInt(); input.addProperty("count", creativeCount); send.accept(operation, input);
        });
    }
    private JsonArray itemPresets() { return session.array("itemPresets"); }
    private List<EditorList.Row> presetRows() {
        List<EditorList.Row> rows = new ArrayList<>();
        for (JsonElement raw : itemPresets()) {
            if (!raw.isJsonObject()) continue;
            JsonObject item = raw.getAsJsonObject(); String id = SafeJson.string(item, "id", "");
            if (id.isEmpty() || slotBase == 6 && !SafeJson.bool(item, "throwable", false)) continue;
            rows.add(new EditorList.Row(id, SafeJson.string(item, "title", id), SafeJson.string(item, "itemName", ""), true));
        }
        return rows;
    }
    private CreativeCatalogue.Tab currentTab() {
        return catalogue.stream().filter(tab -> tab.id().equals(tabId)).findFirst().orElse(catalogue.isEmpty() ? null : catalogue.get(0));
    }
    private String tabTitle() { CreativeCatalogue.Tab tab = currentTab(); return tab == null ? GuiSupport.tr("cnpcgun.editor.creativeTabs") : tab.title(); }
    private void createPreset() {
        JsonObject initial = new JsonObject(); initial.addProperty("name", session.slot(targetSlot).getHoverName().getString());
        EditorDialogs.form(host, label("createItemPreset"), new Parameter[]{new Parameter("name", "string", "", null, null, null, "")}, initial, values -> {
            JsonObject input = new JsonObject(); input.add("name", values.get("name")); input.addProperty("npcSlot", targetSlot); send.accept(Operation.SAVE_ITEM_PRESET, input);
        });
    }
    private void inventory(int index, Box spot) {
        WeaponSlotButton button = addRenderableWidget(new WeaponSlotButton(spot, Component.empty(), () -> minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getInventory().getItem(index), () -> false, () -> {
            if (minecraft.player == null || session.pending || !session.inventoryAllowed() || slotBase == 6 && !throwable(minecraft.player.getInventory().getItem(index))) return;
            JsonObject input = source(); input.addProperty("inventorySlot", index); send.accept(Operation.EXCHANGE, input);
        })); slots.add(button);
    }
    private void update() {
        boolean available = minecraft.player != null && minecraft.level != null && !session.pending;
        for (int i = 0; i < slots.size(); i++) {
            slots.get(i).active = available && (i < slotCount || session.inventoryAllowed() && !slots.get(i).item().is(noppes.npcs.CustomItems.wand) && (slotBase == 0 || throwable(slots.get(i).item())));
            if (i < slotCount) slots.get(i).selected = slotBase + i == targetSlot;
        }
        create.active = available && session.creativeAllowed() && !session.slot(targetSlot).isEmpty();
        create.setTooltip(!session.creativeAllowed() ? Tooltip.create(label("creativePermission")) : null);
        remove.active = available && !presetId.isEmpty() && session.inventoryAllowed();
        presets.active = available; tabStrip.active = available && !catalogue.isEmpty(); browser.active = available && session.creativeAllowed();
        unload.active = available && session.inventoryAllowed() && !session.slot(targetSlot).isEmpty();
        previous.active = tabPage > 0; next.active = tabPage + 1 < tabStrip.pageCount();
    }
    @Override public void tick() {
        super.tick(); search.tick(); presetSearch.tick();
        if (session.status.startsWith("cnpcgun.error")) error = GuiSupport.tr(session.status); else if (!catalogue.isEmpty()) error = "";
        if (presetHash != itemPresets().hashCode()) { presetHash = itemPresets().hashCode(); presets.replaceRows(presetRows()); }
        update();
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        EditorStyle.text(g, font, tabTitle(), browseArea.x() + 2, browseArea.y() + 6, categoryWidth, EditorStyle.INK);
        EditorStyle.text(g, font, GuiSupport.tr("cnpcgun.editor.playerInventory"), playerArea.x(), playerArea.y() + 1, playerArea.width(), EditorStyle.MUTED);
        if (unload.isMouseOver(mx, my)) g.renderTooltip(font, label("unequipHint"), mx, my);
        for (int i = 0; i < slots.size(); i++) {
            WeaponSlotButton slot = slots.get(i); if (!slot.isMouseOver(mx, my)) continue;
            if (i < slotCount) {
                String role = GuiSupport.tr("cnpcgun.gui.slot" + (slotBase + i)); String item = slot.item().isEmpty() ? GuiSupport.tr("cnpcgun.editor.emptySlot") : slot.item().getHoverName().getString();
                g.renderTooltip(font, Component.literal(role + " · " + item), mx, my);
            } else if (!slot.item().isEmpty()) g.renderTooltip(font, slot.item(), mx, my);
            else g.renderTooltip(font, label("exchangeEmpty"), mx, my);
        }
        browser.tooltip(g, mx, my);
        tabStrip.tooltip(g, mx, my);
    }
    private final class TabStrip extends AbstractWidget {
        private final int capacity, size;
        private final Map<String, ItemStack> icons = new HashMap<>();
        TabStrip(Box spot) { super(spot.x(), spot.y(), spot.width(), spot.height(), label("creativeTabs")); size = spot.height(); capacity = Math.max(1, spot.width() / size); }
        int pageCount() { return Math.max(1, (catalogue.size() + capacity - 1) / capacity); }
        private CreativeCatalogue.Tab at(double x, double y) {
            if (!isMouseOver(x, y)) return null;
            int column = (int)(x - getX()) / size, index = tabPage * capacity + column;
            return column >= 0 && column < capacity && index < catalogue.size() ? catalogue.get(index) : null;
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
            tabPage = Math.max(0, Math.min(tabPage, pageCount() - 1));
            for (int i = 0; i < capacity && tabPage * capacity + i < catalogue.size(); i++) {
                CreativeCatalogue.Tab tab = catalogue.get(tabPage * capacity + i); int x = getX() + i * size;
                EditorStyle.slot(g, x, getY(), size - 1, tab.id().equals(tabId) || at(mx, my) == tab);
                ItemStack icon = icons.computeIfAbsent(tab.id(), ignored -> slotBase == 6
                    ? tab.items().stream().map(ItemStack::copy).filter(InventoryDialog::throwable).findFirst().orElse(ItemStack.EMPTY)
                    : tab.items().isEmpty() ? ItemStack.EMPTY : tab.items().get(0).copy());
                if (!icon.isEmpty()) g.renderItem(icon, x + (size - 16) / 2, getY() + (size - 16) / 2);
                if (tab.id().equals(tabId)) g.hLine(x + 2, x + size - 4, getY() + size - 3, EditorStyle.GREEN);
            }
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            CreativeCatalogue.Tab tab = at(x, y); if (!active || button != 0 || tab == null) return false;
            tabId = tab.id(); itemPage = 0; query = ""; search.setValue(""); browser.refresh(); return true;
        }
        void tooltip(GuiGraphics g, int x, int y) { CreativeCatalogue.Tab tab = at(x, y); if (tab != null) g.renderTooltip(font, Component.literal(tab.title()), x, y); }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
    private final class Browser extends AbstractWidget {
        private record Entry(CreativeCatalogue.Tab tab, int index, int fingerprint, ItemStack item) {}
        private List<Entry> shown = List.of();
        private final int columns, rows, size;
        Browser(Box spot) {
            super(spot.x(), spot.y(), spot.width(), spot.height(), label("creativeTabs"));
            size = Math.min(24, Math.max(18, spot.height())); columns = Math.max(1, (spot.width() - 4) / size); rows = Math.max(1, (spot.height() - 2) / size);
        }
        int pageCount() { return Math.max(1, (shown.size() + columns * rows - 1) / (columns * rows)); }
        void refresh() {
            List<Entry> result = new ArrayList<>(); CreativeCatalogue.Tab tab = currentTab(); String needle = query.toLowerCase(Locale.ROOT).trim();
            if (tab != null) for (int i = 0; i < tab.items().size(); i++) {
                ItemStack template = tab.items().get(i);
                // Freeze the source hash before any native name, tooltip or rendering call.
                int fingerprint = CreativeCatalogue.fingerprint(template);
                ItemStack item = template.copy();
                String text = item.getHoverName().getString() + " " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem());
                if (!item.isEmpty() && (slotBase == 0 || throwable(item)) && text.toLowerCase(Locale.ROOT).contains(needle)) result.add(new Entry(tab, i, fingerprint, item));
            }
            shown = List.copyOf(result); itemPage = Math.max(0, Math.min(itemPage, pageCount() - 1));
        }
        private Entry at(double x, double y) {
            if (!isMouseOver(x, y)) return null;
            int column = (int)(x - getX() - 2) / size, row = (int)(y - getY() - 1) / size;
            if (column < 0 || column >= columns || row < 0 || row >= rows) return null;
            int index = itemPage * columns * rows + row * columns + column; return index < shown.size() ? shown.get(index) : null;
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
            EditorStyle.panel(g, new Box(getX(), getY(), width, height), EditorStyle.PAPER);
            g.enableScissor(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1);
            int first = itemPage * columns * rows;
            for (int i = 0; i < columns * rows && first + i < shown.size(); i++) {
                Entry entry = shown.get(first + i); int x = getX() + 2 + i % columns * size, y = getY() + 1 + i / columns * size;
                EditorStyle.slot(g, x, y, size - 1, at(mx, my) == entry); g.renderItem(entry.item, x + (size - 16) / 2, y + (size - 16) / 2);
            }
            g.disableScissor();
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            Entry entry = at(x, y); if (button != 0 || entry == null || !active) return false;
            JsonObject input = source(); input.addProperty("tab", entry.tab.id()); input.addProperty("itemIndex", entry.index);
            input.addProperty("fingerprint", entry.fingerprint); equipTemplate(Operation.EQUIP_CREATIVE, input, entry.item.getMaxStackSize()); return true;
        }
        @Override public boolean mouseScrolled(double x, double y, double delta) {
            if (!isMouseOver(x, y)) return false; itemPage = Math.max(0, Math.min(pageCount() - 1, itemPage - (int)Math.signum(delta))); return true;
        }
        void tooltip(GuiGraphics g, int x, int y) { Entry entry = at(x, y); if (entry != null) g.renderTooltip(font, entry.item, x, y); }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
}
