package bincnpc.gun.client.gui.editor;

import bincnpc.gun.client.animation.NpcAnimations;
import bincnpc.gun.common.api.SafeJson;
import com.google.gson.*;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.entity.EntityNPCInterface;

/** Detached model and a bounded, one-shot visual timeline. No entity is added to the world or ticked. */
public final class NpcPreview extends AbstractWidget {
    private record Step(JsonObject info, JsonObject parameters, int ticks) {}
    private final EntityNPCInterface source;
    private final Deque<Step> timeline = new ArrayDeque<>();
    private EntityNPCInterface model;
    private ItemStack weapon = ItemStack.EMPTY;
    private int left;
    private String action = "";
    private boolean unavailable;
    public NpcPreview(EntityNPCInterface source, EditorLayout.Box box, ItemStack weapon) {
        super(box.x(), box.y(), box.width(), box.height(), Component.translatable("cnpcgun.editor.npcPreview"));
        this.source = source; this.weapon = weapon == null ? ItemStack.EMPTY : weapon.copy(); this.unavailable = source == null;
    }
    public void bounds(EditorLayout.Box box) { setX(box.x()); setY(box.y()); setWidth(box.width()); height = box.height(); }
    private EntityNPCInterface model() {
        if (model != null || unavailable) return model;
        try {
            if (!(source.getType().create(source.level()) instanceof EntityNPCInterface copy)) { unavailable = true; return null; }
            copy.readSpawnData(source.writeSpawnData().copy()); copy.setUUID(UUID.randomUUID()); copy.setId(-1);
            copy.setNoAi(true); copy.setPos(source.getX(), source.getY(), source.getZ());
            copy.display.setShowName(1); copy.display.setName(""); copy.textureLocation = source.textureLocation; copy.textureGlowLocation = source.textureGlowLocation;
            copy.textureCloakLocation = source.textureCloakLocation; copy.setHealth(Math.max(1, copy.getMaxHealth()));
            copy.inventory.setRightHand(NpcAPI.Instance().getIItemStack(weapon.copy())); model = copy;
        } catch (RuntimeException | LinkageError e) { unavailable = true; }
        return model;
    }
    public void dummy() {
        EntityNPCInterface copy = model(); if (copy == null) return;
        for (EquipmentSlot slot : EquipmentSlot.values()) copy.setItemSlot(slot, ItemStack.EMPTY);
        copy.display.setName("");
    }
    public boolean playing() { return left > 0 || !timeline.isEmpty(); }
    public void stop() {
        timeline.clear(); left = 0; action = "";
        if (model != null) {
            NpcAnimations.clearPreview(model);
            if (ModList.get().isLoaded("slashblade")) {
                try { bincnpc.gun.client.animation.BladePreviewEffects.clear(model); BladePreview.clear(model.getMainHandItem(), model.level().getGameTime()); }
                catch (RuntimeException | LinkageError ignored) { }
            }
        }
    }
    public void dispose() { stop(); model = null; }
    public void fire(int duration, boolean aim) {
        EntityNPCInterface copy = model(); if (copy == null) return;
        NpcAnimations.preview(copy, "GUN", "FIRE", "preview", duration, aim); action = "";
    }
    public void hit() { EntityNPCInterface copy = model(); if (copy != null) copy.hurtTime = 6; }
    public void playAction(JsonObject info, JsonObject parameters) {
        stop(); if (info == null) return;
        if (parameters == null) parameters = new JsonObject();
        int ticks = duration(info, parameters);
        int delay = Mth.clamp(SafeJson.integer(parameters, "delay", 0), 0, 100);
        timeline.add(new Step(info.deepCopy(), parameters.deepCopy(), Mth.clamp(ticks + delay, 1, 2400))); advance();
    }
    public void playGraph(JsonObject graph, Function<String, JsonObject> actions) {
        stop(); Map<String, JsonObject> nodes = new LinkedHashMap<>();
        if (graph == null || actions == null) return;
        for (JsonElement raw : SafeJson.arrayField(graph, "nodes")) {
            if (!raw.isJsonObject()) continue;
            JsonObject node = raw.getAsJsonObject(); String nodeId = SafeJson.string(node, "id", "");
            if (!nodeId.isEmpty()) nodes.put(nodeId, node);
        }
        String id = SafeJson.string(graph, "entryNode", "end"); Set<String> seen = new HashSet<>(); int duration = 0;
        while (!id.equals("end") && seen.size() < 128 && seen.add(id)) {
            JsonObject node = nodes.get(id); if (node == null) break;
            JsonObject info = actions.apply(SafeJson.string(node, "action", ""));
            if (info != null && SafeJson.bool(info, "available", false)) {
                JsonObject params = SafeJson.objectField(node, "parameters").deepCopy();
                int ticks = duration(info, params); if (duration + ticks > 2400) break;
                timeline.add(new Step(info.deepCopy(), params, ticks));
                duration += ticks; id = SafeJson.string(node, "onSuccess", "end");
            } else id = SafeJson.string(node, "onFailure", "end");
        }
        advance();
    }
    private void advance() {
        Step next = timeline.poll(); if (next == null) { left = 0; action = ""; return; }
        EntityNPCInterface copy = model(); if (copy == null) { stop(); return; }
        left = next.ticks; action = bincnpc.gun.client.gui.GuiSupport.actionName(next.info);
        String animation = SafeJson.string(next.info, "id", "");
        if (ModList.get().isLoaded("slashblade")) {
            try {
                String stage = BladePreview.pose(copy.getMainHandItem(), next.info, next.parameters, copy.level().getGameTime());
                if (!stage.isEmpty()) animation = stage;
                bincnpc.gun.client.animation.BladePreviewEffects.start(copy, next.info, next.parameters, left);
            } catch (RuntimeException | LinkageError ignored) { }
        }
        NpcAnimations.preview(copy, "SLASHBLADE", SafeJson.string(next.info, "type", ""), animation, left, false);
    }
    private static int duration(JsonObject info, JsonObject parameters) {
        int ticks = SafeJson.integer(info, "duration", 12);
        String type = SafeJson.string(parameters, "artsType", SafeJson.string(info, "defaultArtsType", "Success"));
        ticks = SafeJson.integer(SafeJson.objectField(info, "previewDurations"), type, ticks);
        ticks = SafeJson.integer(parameters, "duration", ticks);
        if (SafeJson.string(info, "type", "").equals("PHANTOM_SWORD")) ticks = Math.max(24, ticks);
        return Mth.clamp(ticks, 1, 2400);
    }
    public void tick() {
        if (model != null && model.hurtTime > 0) model.hurtTime--;
        if (model != null && ModList.get().isLoaded("slashblade")) {
            try { bincnpc.gun.client.animation.BladePreviewEffects.tick(model); } catch (RuntimeException | LinkageError ignored) { }
        }
        if (left > 0 && --left == 0) advance();
    }
    private int modelScale(EntityNPCInterface copy) {
        int scale = Math.max(12, Math.min(width / 3, (height - 40) / 2));
        float npcSize = Math.max(0.5F, copy.getBbHeight() / 1.8F);
        return Math.max(8, (int)(scale / npcSize));
    }
    public EditorLayout.Box hitArea() {
        EntityNPCInterface copy = model();
        int scale = copy == null ? Math.max(8, width / 3) : modelScale(copy);
        int bodyWidth = Math.max(8, Math.min(width - 16, (int)Math.ceil(scale * (copy == null ? 0.6F : copy.getBbWidth()))));
        int bodyHeight = Math.max(12, Math.min(height - 42, (int)Math.ceil(scale * (copy == null ? 1.8F : copy.getBbHeight()))));
        return new EditorLayout.Box(getX() + (width - bodyWidth) / 2, getY() + height - 22 - bodyHeight, bodyWidth, bodyHeight);
    }
    @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        EditorStyle.panel(g, new EditorLayout.Box(getX(), getY(), width, height), 0xFF111511);
        g.fill(getX() + 3, getY() + height - 18, getX() + width - 3, getY() + height - 3, 0xFF202820);
        EditorStyle.text(g, font, getMessage().getString(), getX() + 7, getY() + 7, width - 14, 0xFFCCD4C5);
        EntityNPCInterface copy = model();
        if (copy == null) { EditorStyle.text(g, font, Component.translatable("cnpcgun.editor.previewUnavailable").getString(), getX() + 6, getY() + 28, width - 12, EditorStyle.WHITE); return; }
        int scale = modelScale(copy);
        int x = getX() + width / 2, y = getY() + height - 22;
        g.flush(); g.enableScissor(getX() + 2, getY() + 19, getX() + width - 2, getY() + height - 19);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, x, y, scale, (x - mx) * 0.15F, (getY() + height / 2 - my) * 0.15F, copy);
        g.disableScissor(); g.flush();
        EditorStyle.text(g, font, action.isEmpty() ? Component.translatable(playing() ? "cnpcgun.editor.previewPlaying" : "cnpcgun.editor.previewReady").getString() : action,
            getX() + 6, getY() + height - 13, width - 12, 0xFFB6C7A1);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
