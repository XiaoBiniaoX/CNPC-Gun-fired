package bincnpc.gun.client;

import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.network.GunNetwork;
import com.google.gson.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.item.ItemStack;

public final class EditorSession {
    public final UUID npcId;
    public UUID token = GunNetwork.NO_TOKEN;
    public long revision;
    public JsonObject snapshot = new JsonObject();
    public ItemStack[] slots = bincnpc.gun.common.network.Equipment.empty();
    public String status = "cnpcgun.gui.loading";
    public boolean pending;
    private int requestId, waitingTicks, idleTicks, heartbeatTicks;
    private Consumer<GunNetwork.Response> callback;
    private GunNetwork.Operation waitingOperation;
    public GunNetwork.Operation completedOperation;
    public EditorSession(UUID npcId) { this.npcId = npcId; }
    public boolean ready() { return snapshot != null && snapshot.has("values") && snapshot.get("values").isJsonObject(); }
    public boolean send(GunNetwork.Operation operation, JsonObject input) { return send(operation, input, null); }
    public boolean send(GunNetwork.Operation operation, JsonObject input, Consumer<GunNetwork.Response> completed) {
        if (pending || operation == null || net.minecraft.client.Minecraft.getMinecraft().getConnection() == null) return false;
        try {
            String json = SafeJson.GSON.toJson(SafeJson.object(input));
            pending = true; waitingTicks = 0; idleTicks = 0; callback = completed;
            waitingOperation = operation;
            status = operation == GunNetwork.Operation.OPEN || operation == GunNetwork.Operation.REFRESH ? "cnpcgun.gui.loading" : "cnpcgun.gui.saving";
            GunNetwork.CHANNEL.sendToServer(new GunNetwork.Request(npcId, token, revision, ++requestId, operation, json));
            return true;
        } catch (IllegalArgumentException | IllegalStateException e) { pending = false; callback = null; status = "cnpcgun.error.invalid_argument"; return false; }
    }
    public boolean receive(GunNetwork.Response packet) {
        if (packet == null || !pending || !Objects.equals(packet.npcId(), npcId) || packet.requestId() != requestId) return false;
        pending = false; idleTicks = 0;
        completedOperation = waitingOperation;
        JsonObject data;
        try { data = SafeJson.persistedObject(packet.json()); }
        catch (IllegalArgumentException invalid) { callback = null; status = "cnpcgun.error.invalid_argument"; return true; }
        if (data.has("values") && data.get("values").isJsonObject()) {
            snapshot = data; token = packet.token() == null ? GunNetwork.NO_TOKEN : packet.token(); revision = packet.revision();
            slots = bincnpc.gun.common.network.Equipment.empty();
            if (packet.slots() != null) for (int i = 0; i < Math.min(slots.length, packet.slots().length); i++)
                if (packet.slots()[i] != null) slots[i] = packet.slots()[i].copy();
        }
        if ("SESSION_EXPIRED".equals(packet.code())) token = GunNetwork.NO_TOKEN;
        status = "SUCCESS".equals(packet.code()) ? "cnpcgun.gui.saved" : "cnpcgun.error." + (packet.code() == null ? "internal_error" : packet.code().toLowerCase(Locale.ROOT));
        Consumer<GunNetwork.Response> completed = callback; callback = null;
        if (completed != null) completed.accept(packet);
        return true;
    }
    public void tick() { tick(true); }
    public void tick(boolean allowRefresh) {
        if (net.minecraft.client.Minecraft.getMinecraft().getConnection() == null) { pending = false; callback = null; return; }
        if (pending && ++waitingTicks > 200) { pending = false; callback = null; status = "cnpcgun.gui.timeout"; }
        if (ready() && ++heartbeatTicks >= 600 && !pending && !token.equals(GunNetwork.NO_TOKEN)) {
            heartbeatTicks = 0;
            GunNetwork.CHANNEL.sendToServer(new GunNetwork.Request(npcId, token, revision, ++requestId, GunNetwork.Operation.HEARTBEAT, "{}"));
        }
        if (!allowRefresh) idleTicks = 0;
        if (allowRefresh && !pending && ready() && ++idleTicks >= 400) send(GunNetwork.Operation.REFRESH, new JsonObject());
    }
    public void close() {
        if (!token.equals(GunNetwork.NO_TOKEN) && net.minecraft.client.Minecraft.getMinecraft().getConnection() != null)
            GunNetwork.CHANNEL.sendToServer(new GunNetwork.Request(npcId, token, revision, ++requestId, GunNetwork.Operation.CLOSE, "{}"));
        snapshot = new JsonObject(); slots = bincnpc.gun.common.network.Equipment.empty();
        callback = null; pending = false; token = GunNetwork.NO_TOKEN;
    }
    public JsonObject values() { return ready() ? snapshot.getAsJsonObject("values") : new JsonObject(); }
    public JsonArray array(String key) { return SafeJson.arrayField(snapshot, key); }
    public JsonArray actions() {
        JsonArray valid = new JsonArray();
        for (JsonElement item : array("actions")) if (item.isJsonObject()) {
            JsonObject action = item.getAsJsonObject();
            if (!SafeJson.string(action, "id", "").isEmpty() && !SafeJson.string(action, "type", "").isEmpty()) valid.add(action);
        }
        return valid;
    }
    public JsonObject action(String id) {
        if (id == null) return new JsonObject();
        JsonObject actions = SafeJson.objectField(snapshot, "actionConfig");
        return actions.has(id) && actions.get(id).isJsonObject() ? bincnpc.gun.common.legacy.Legacy.copy(actions.getAsJsonObject(id)) : new JsonObject();
    }
    public JsonObject actionInfo(String id) {
        for (JsonElement item : actions()) if (SafeJson.string(item.getAsJsonObject(), "id", "").equals(id)) return bincnpc.gun.common.legacy.Legacy.copy(item.getAsJsonObject());
        return null;
    }
    public boolean has(String capability) {
        if (!ready() || capability == null) return false;
        for (JsonElement item : array("editableWeapons")) {
            if (!item.isJsonObject()) continue;
            JsonObject weapon = item.getAsJsonObject();
            if (capability(weapon, capability)) return true;
        }
        return capability(SafeJson.objectField(snapshot, "capabilities"), capability);
    }
    private static boolean capability(JsonObject weapon, String key) {
        try {
            bincnpc.gun.common.api.Views.GunCaps gun = weapon.has("gun") && weapon.get("gun").isJsonObject()
                ? SafeJson.GSON.fromJson(weapon.get("gun"), bincnpc.gun.common.api.Views.GunCaps.class) : null;
            bincnpc.gun.common.api.Views.BladeCaps blade = weapon.has("slashBlade") && weapon.get("slashBlade").isJsonObject()
                ? SafeJson.GSON.fromJson(weapon.get("slashBlade"), bincnpc.gun.common.api.Views.BladeCaps.class) : null;
            return gun != null && gun.has(key) || blade != null && blade.has(key);
        } catch (JsonParseException | IllegalStateException invalid) { return false; }
    }
    public static bincnpc.gun.common.config.Parameter[] parameters(JsonObject info) {
        List<bincnpc.gun.common.config.Parameter> valid = new ArrayList<>();
        for (JsonElement value : SafeJson.arrayField(info, "parameters")) {
            if (!value.isJsonObject()) continue;
            try {
                bincnpc.gun.common.config.Parameter raw = SafeJson.GSON.fromJson(value, bincnpc.gun.common.config.Parameter.class);
                if (raw == null || raw.id == null || raw.type == null) continue;
                bincnpc.gun.common.config.Parameter parameter = new bincnpc.gun.common.config.Parameter(raw.id, raw.type, raw.defaultValue, raw.min, raw.max, raw.values, raw.capability);
                parameter.validate(SafeJson.copy(parameter.defaultValue)); valid.add(parameter);
            } catch (JsonParseException | IllegalArgumentException | IllegalStateException invalid) { }
        }
        return bincnpc.gun.common.legacy.Legacy.array(valid, bincnpc.gun.common.config.Parameter[]::new);
    }
    public boolean inventoryAllowed() { return ready() && SafeJson.bool(snapshot, "inventoryAllowed", false); }
    public boolean creativeAllowed() { return ready() && SafeJson.bool(snapshot, "creativeAllowed", false); }
    public ItemStack slot(int slot) { return slots == null || slot < 0 || slot >= slots.length || slots[slot] == null ? ItemStack.EMPTY : slots[slot]; }
    public ItemStack previewWeapon(String domain) {
        for (JsonElement item : array("editableWeapons")) {
            if (!item.isJsonObject()) continue;
            JsonObject weapon = item.getAsJsonObject();
            String key = "GUN".equals(domain) ? "gun" : "SLASHBLADE".equals(domain) ? "slashBlade" : "";
            if (!key.isEmpty() && weapon.has(key) && weapon.get(key).isJsonObject()) return slot(SafeJson.integer(weapon, "slot", -1)).copy();
        }
        return ItemStack.EMPTY;
    }
}
