package bincnpc.gun.common.network;

import bincnpc.gun.CNPCGun;
import bincnpc.gun.common.adapter.AdapterRegistry;
import bincnpc.gun.common.api.*;
import bincnpc.gun.common.combat.*;
import bincnpc.gun.common.config.*;
import bincnpc.gun.common.persistence.PresetLibrary;
import bincnpc.gun.common.persistence.ItemPresetLibrary;
import bincnpc.gun.gun.adapter.GunAdapter;
import bincnpc.gun.gun.switching.Armory;
import bincnpc.gun.slashblade.action.*;
import bincnpc.gun.slashblade.combo.GraphValidator;
import bincnpc.gun.slashblade.strategy.Presets;
import com.google.gson.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import noppes.npcs.*;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;

public final class GunNetwork {
    public static final UUID NO_TOKEN = new UUID(0, 0);
    public static final int MAX_SNAPSHOT = 524288;
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(CNPCGun.MOD_ID);
    public enum Operation { OPEN, CLOSE, REFRESH, SET, BATCH, ACTION_CONFIG, GRAPH, FETCH_GRAPH, DELETE_GRAPH,
        SELECT_GRAPH, SWITCH, EXCHANGE, SUPPLY, RELOAD, PRESET, COPY, SAVE_PRESET, START, STOP, EXECUTE_ACTION, FIRE, COMMAND,
        PRIMARY_WEAPON, EQUIP_CREATIVE, SAVE_ITEM_PRESET, EQUIP_ITEM_PRESET, DELETE_ITEM_PRESET, DELETE_PRESET, HEARTBEAT, UNEQUIP }
    public static final class Request implements IMessage {
private UUID npcId;
private UUID token;
private long revision;
private int requestId;
private Operation operation;
private String json;
public Request() {}
@Override public void fromBytes(ByteBuf bytes) { Request packet = decode(new LegacyBuffer(bytes)); this.npcId = packet.npcId; this.token = packet.token; this.revision = packet.revision; this.requestId = packet.requestId; this.operation = packet.operation; this.json = packet.json; }
@Override public void toBytes(ByteBuf bytes) { encode(new LegacyBuffer(bytes)); }
public Request(UUID npcId, UUID token, long revision, int requestId, Operation operation, String json) {
this.npcId = npcId; this.token = token; this.revision = revision; this.requestId = requestId; this.operation = operation; this.json = json;}
public UUID npcId() { return npcId; }
public UUID token() { return token; }
public long revision() { return revision; }
public int requestId() { return requestId; }
public Operation operation() { return operation; }
public String json() { return json; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof Request))return false; Request that=(Request)value; return java.util.Objects.equals(npcId, that.npcId) && java.util.Objects.equals(token, that.token) && java.util.Objects.equals(revision, that.revision) && java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(operation, that.operation) && java.util.Objects.equals(json, that.json); }
@Override public int hashCode() { return java.util.Objects.hash(npcId, token, revision, requestId, operation, json); }

        public static Request decode(LegacyBuffer buf) {
            return new Request(buf.readUUID(), buf.readUUID(), buf.readLong(), buf.readVarInt(), buf.readEnum(Operation.class), buf.readUtf(SafeJson.MAX_BYTES));
        }
        public void encode(LegacyBuffer buf) {
            buf.writeUUID(npcId); buf.writeUUID(token); buf.writeLong(revision); buf.writeVarInt(requestId);
            buf.writeEnum(operation); buf.writeUtf(json, SafeJson.MAX_BYTES);
        }
    
}
    public static final class Response implements IMessage {
private UUID npcId;
private UUID token;
private long revision;
private int requestId;
private String code;
private String message;
private String json;
private ItemStack[] slots;
public Response() {}
@Override public void fromBytes(ByteBuf bytes) { Response packet = decode(new LegacyBuffer(bytes)); this.npcId = packet.npcId; this.token = packet.token; this.revision = packet.revision; this.requestId = packet.requestId; this.code = packet.code; this.message = packet.message; this.json = packet.json; this.slots = packet.slots; }
@Override public void toBytes(ByteBuf bytes) { encode(new LegacyBuffer(bytes)); }
public Response(UUID npcId, UUID token, long revision, int requestId, String code, String message, String json, ItemStack[] slots) {
this.npcId = npcId; this.token = token; this.revision = revision; this.requestId = requestId; this.code = code; this.message = message; this.json = json; this.slots = slots;}
public UUID npcId() { return npcId; }
public UUID token() { return token; }
public long revision() { return revision; }
public int requestId() { return requestId; }
public String code() { return code; }
public String message() { return message; }
public String json() { return json; }
public ItemStack[] slots() { return slots; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof Response))return false; Response that=(Response)value; return java.util.Objects.equals(npcId, that.npcId) && java.util.Objects.equals(token, that.token) && java.util.Objects.equals(revision, that.revision) && java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(code, that.code) && java.util.Objects.equals(message, that.message) && java.util.Objects.equals(json, that.json) && java.util.Objects.equals(slots, that.slots); }
@Override public int hashCode() { return java.util.Objects.hash(npcId, token, revision, requestId, code, message, json, slots); }

        public static Response decode(LegacyBuffer buf) {
            UUID npc = buf.readUUID(), token = buf.readUUID(); long revision = buf.readLong(); int request = buf.readVarInt();
            String code = buf.readUtf(64), message = buf.readUtf(512), json = buf.readUtf(MAX_SNAPSHOT);
            ItemStack[] slots = new ItemStack[Equipment.SIZE]; for (int i = 0; i < Equipment.SIZE; i++) slots[i] = buf.readItem();
            return new Response(npc, token, revision, request, code, message, json, slots);
        }
        public void encode(LegacyBuffer buf) {
            buf.writeUUID(npcId); buf.writeUUID(token); buf.writeLong(revision); buf.writeVarInt(requestId);
            buf.writeUtf(code, 64); buf.writeUtf(message, 512); buf.writeUtf(json, MAX_SNAPSHOT);
            for (ItemStack slot : slots) buf.writeItem(slot);
        }
    
}
    public static final class Animation implements IMessage {
private int entityId;
private UUID npcId;
private String domain;
private String pose;
private String actionId;
private int duration;
private boolean aiming;
public Animation() {}
@Override public void fromBytes(ByteBuf bytes) { Animation packet = decode(new LegacyBuffer(bytes)); this.entityId = packet.entityId; this.npcId = packet.npcId; this.domain = packet.domain; this.pose = packet.pose; this.actionId = packet.actionId; this.duration = packet.duration; this.aiming = packet.aiming; }
@Override public void toBytes(ByteBuf bytes) { encode(new LegacyBuffer(bytes)); }
public Animation(int entityId, UUID npcId, String domain, String pose, String actionId, int duration, boolean aiming) {
this.entityId = entityId; this.npcId = npcId; this.domain = domain; this.pose = pose; this.actionId = actionId; this.duration = duration; this.aiming = aiming;}
public int entityId() { return entityId; }
public UUID npcId() { return npcId; }
public String domain() { return domain; }
public String pose() { return pose; }
public String actionId() { return actionId; }
public int duration() { return duration; }
public boolean aiming() { return aiming; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof Animation))return false; Animation that=(Animation)value; return java.util.Objects.equals(entityId, that.entityId) && java.util.Objects.equals(npcId, that.npcId) && java.util.Objects.equals(domain, that.domain) && java.util.Objects.equals(pose, that.pose) && java.util.Objects.equals(actionId, that.actionId) && java.util.Objects.equals(duration, that.duration) && java.util.Objects.equals(aiming, that.aiming); }
@Override public int hashCode() { return java.util.Objects.hash(entityId, npcId, domain, pose, actionId, duration, aiming); }

        public static Animation decode(LegacyBuffer buf) {
            return new Animation(buf.readVarInt(), buf.readUUID(), buf.readUtf(24), buf.readUtf(32), buf.readUtf(192), buf.readVarInt(), buf.readBoolean());
        }
        public void encode(LegacyBuffer buf) {
            buf.writeVarInt(entityId); buf.writeUUID(npcId); buf.writeUtf(domain, 24); buf.writeUtf(pose, 32);
            buf.writeUtf(actionId, 192); buf.writeVarInt(duration); buf.writeBoolean(aiming);
        }
    
}
    private static final class Session {
        final UUID npcId, token = UUID.randomUUID();
        final int dimension;
        int lastRequest, pending;
        long expires;
        Session(EntityNPCInterface npc, long now) { npcId = npc.getUniqueID(); dimension = npc.dimension; expires = now + 1200; }
    }
    private static final class Rate { long window; int count; }
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Rate> RATES = new HashMap<>();
    private static ThreadPoolExecutor worker;
    private GunNetwork() {}
    public static void init() {
        CHANNEL.registerMessage(new RequestHandler(), Request.class, 0, Side.SERVER);
        CHANNEL.registerMessage(new ResponseHandler(), Response.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(new AnimationHandler(), Animation.class, 2, Side.CLIENT);
    }
    public static final class RequestHandler implements IMessageHandler<Request, IMessage> {
        @Override public IMessage onMessage(Request packet, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> accept(player, packet)); return null;
        }
    }
    public static final class ResponseHandler implements IMessageHandler<Response, IMessage> {
        @Override public IMessage onMessage(Response packet, MessageContext context) { CNPCGun.proxy.receive(packet); return null; }
    }
    public static final class AnimationHandler implements IMessageHandler<Animation, IMessage> {
        @Override public IMessage onMessage(Animation packet, MessageContext context) { CNPCGun.proxy.receive(packet); return null; }
    }
    public static void startWorker() {
        stopWorker();
        worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(32), task -> {
            Thread thread = new Thread(task, "CNPCGun-configuration"); thread.setDaemon(true); return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }
    public static void stopWorker() {
        if (worker != null) worker.shutdownNow(); worker = null; SESSIONS.clear(); RATES.clear();
    }
    public static void forget(EntityPlayerMP player) {
        Session session = SESSIONS.remove(player.getUniqueID()); RATES.remove(player.getUniqueID()); finishEditing(player, session);
    }
    private static void finishEditing(EntityPlayerMP player, Session ended) {
        if (ended == null || player.getServer() == null) return;
        for (WorldServer level : player.getServer().worlds) {
            if (level.provider.getDimension() != ended.dimension) continue;
            Entity entity = level.getEntityFromUuid(ended.npcId);
            if (!(entity instanceof EntityNPCInterface)) continue;
            EntityNPCInterface npc = (EntityNPCInterface)entity;
            if (!CombatService.presentNpc(npc)) continue;
            long remaining = SESSIONS.values().stream().filter(session -> session.dimension == ended.dimension && session.npcId.equals(ended.npcId))
                .mapToLong(session -> session.expires).max().orElse(0);
            if (remaining > level.getTotalWorldTime()) CombatService.data(npc).runtime.editorUntil = remaining;
            else CombatService.endEditing(new CombatContext(npc));
            break;
        }
    }
    private static EntityNPCInterface editing(EntityPlayerMP player, UUID id, Operation operation) {
        // Native editingNpc only authorizes OPEN. An issued token binds later edits to the world UUID.
        if (operation == Operation.OPEN) {
            EntityNPCInterface selected = NoppesUtilServer.getEditingNpc(player);
            if (selected == null || !id.equals(selected.getUniqueID()) || selected.world != player.world) return null;
        }
        Entity entity = player.getServerWorld().getEntityFromUuid(id);
        return entity instanceof EntityNPCInterface ? (EntityNPCInterface)entity : null;
    }
    private static String editingError(EntityPlayerMP player, EntityNPCInterface npc, Operation operation) {
        if (!CombatService.presentNpc(npc)) return "INVALID_NPC";
        if (!player.isEntityAlive()) return "PERMISSION_DENIED";
        if (npc.world != player.world || player.getDistanceSq(npc) > 256) return "OUT_OF_RANGE";
        if (!(bincnpc.gun.common.legacy.Platform.is(player.getHeldItemMainhand(), CustomItems.wand)
                || bincnpc.gun.common.legacy.Platform.is(player.getHeldItemOffhand(), CustomItems.wand))) return "WAND_REQUIRED";
        if (!CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_GUI)
                || !CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_ADVANCED)) return "PERMISSION_DENIED";
        if (bincnpc.gun.common.legacy.Legacy.set(Operation.EXCHANGE, Operation.UNEQUIP, Operation.SUPPLY, Operation.SWITCH, Operation.PRIMARY_WEAPON, Operation.EQUIP_CREATIVE,
                Operation.SAVE_ITEM_PRESET, Operation.EQUIP_ITEM_PRESET, Operation.DELETE_ITEM_PRESET).contains(operation)
                && !CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_INVENTORY)) return "PERMISSION_DENIED";
        return null;
    }
    private static boolean rate(EntityPlayerMP player, long now) {
        Rate rate = RATES.computeIfAbsent(player.getUniqueID(), key -> new Rate());
        if (now - rate.window >= 20 || now < rate.window) { rate.window = now; rate.count = 0; }
        return ++rate.count <= 8;
    }
    private static void accept(EntityPlayerMP player, Request request) {
        long now = player.world.getTotalWorldTime();
        if (request.requestId <= 0 || request.operation != Operation.CLOSE && !rate(player, now)) return;
        Session session = SESSIONS.get(player.getUniqueID());
        if (request.operation != Operation.OPEN) {
            if (session == null || !session.npcId.equals(request.npcId) || !session.token.equals(request.token)) {
                denied(player, request, "SESSION_EXPIRED"); return;
            }
            if (request.requestId <= session.lastRequest) { denied(player, request, "CONFLICT"); return; }
            // Closing writes no settings and must still release a session after its wand/target changes.
            if (request.operation == Operation.CLOSE) {
                SESSIONS.remove(player.getUniqueID()); finishEditing(player, session); return;
            }
            if (session.expires < now) { denied(player, request, "SESSION_EXPIRED"); return; }
            if (session.dimension != player.dimension) { denied(player, request, "OUT_OF_RANGE"); return; }
        }
        EntityNPCInterface npc = editing(player, request.npcId, request.operation);
        String error = editingError(player, npc, request.operation);
        if (error != null) { denied(player, request, error); return; }
        if (request.operation == Operation.OPEN) {
            Session previous = SESSIONS.remove(player.getUniqueID()); finishEditing(player, previous);
            session = new Session(npc, now); SESSIONS.put(player.getUniqueID(), session);
        }
        if (session.pending != 0) { denied(player, request, "BUSY"); return; }
        session.lastRequest = request.requestId; session.expires = now + 1200;
        CombatContext ctx = new CombatContext(npc); CombatService.refresh(ctx);
        CombatService.beginEditing(ctx, session.expires);
        if (request.operation == Operation.HEARTBEAT) return;
        if (request.operation != Operation.OPEN && request.operation != Operation.REFRESH && request.revision != ctx.config.revision) {
            respond(player, session, request.requestId, ctx, Result.fail("CONFLICT")); return;
        }
        try {
            JsonObject input = SafeJson.parseObject(request.json);
            if (request.operation == Operation.GRAPH || request.operation == Operation.ACTION_CONFIG) {
                validateAsync(player.getServer(), player.getUniqueID(), session, request, input); return;
            }
            Result result = apply(player, ctx, request.operation, input);
            respond(player, session, request.requestId, new CombatContext(npc), result);
        } catch (CreativeCatalogue.Changed e) {
            CNPCGun.LOGGER.debug("CNPC Gun catalogue request {} rejected for NPC {}: {}", request.operation, request.npcId, e.getMessage());
            respond(player, session, request.requestId, ctx, Result.fail("CATALOGUE_CHANGED"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            respond(player, session, request.requestId, ctx, Result.fail("INVALID_ARGUMENT"));
        } catch (RuntimeException e) {
            CNPCGun.LOGGER.warn("CNPC Gun editor request {} failed for NPC {}", request.operation, request.npcId, e);
            denied(player, request, "INTERNAL_ERROR");
        }
    }
    private static void validateAsync(MinecraftServer server, UUID playerId, Session session, Request request, JsonObject input) {
        Map<String, ActionRegistry.Definition> actions = ActionRegistry.INSTANCE.snapshot();
        UUID token = session.token; session.pending = request.requestId;
        if (worker == null) {
            session.pending = 0; EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
            if (player != null) denied(player, request, "BUSY");
            return;
        }
        try {
            worker.execute(() -> {
                JsonObject checked = null; Result failure = null;
                try {
                    if (request.operation == Operation.GRAPH) checked = GraphValidator.validate(input, actions, false).original;
                    else {
                        String id = string(input, "id", ""); ActionRegistry.Definition def = actions.get(id);
                        if (def == null) failure = Result.fail("NOT_FOUND");
                        else { checked = new JsonObject(); checked.addProperty("id", id); checked.add("config", ActionConfig.validate(object(input, "config", true), def)); }
                    }
                } catch (RuntimeException e) { failure = Result.fail("INVALID_ARGUMENT"); }
                JsonObject validated = checked; Result error = failure;
                server.addScheduledTask(() -> {
                    EntityPlayerMP current = server.getPlayerList().getPlayerByUUID(playerId);
                    Session active = SESSIONS.get(playerId);
                    if (current == null || active == null || !active.token.equals(token) || active.pending != request.requestId) return;
                    active.pending = 0;
                    if (active.expires < current.world.getTotalWorldTime()) { denied(current, request, "SESSION_EXPIRED"); return; }
                    if (active.dimension != current.dimension) { denied(current, request, "OUT_OF_RANGE"); return; }
                    EntityNPCInterface npc = editing(current, request.npcId, request.operation);
                    String rejection = editingError(current, npc, request.operation);
                    if (rejection != null) { denied(current, request, rejection); return; }
                    CombatContext ctx = new CombatContext(npc); CombatService.refresh(ctx);
                    if (ctx.config.revision != request.revision) { respond(current, active, request.requestId, ctx, Result.fail("CONFLICT")); return; }
                    Result result = error;
                    if (result == null) {
                        if (request.operation == Operation.GRAPH) {
                            boolean allowed = EditorWeapons.has(ctx, "slashblade.attack");
                            for (JsonElement raw : validated.getAsJsonArray("nodes")) {
                                ActionRegistry.Definition definition = ActionRegistry.INSTANCE.get(raw.getAsJsonObject().get("action").getAsString());
                                if (definition != null && !EditorWeapons.adapter(ctx, definition.adapterId)) allowed = false;
                            }
                            result = allowed ? ctx.data.blade.setCombo(ctx, validated) : Result.fail("UNSUPPORTED");
                        }
                        else {
                            String id = validated.get("id").getAsString(); ActionRegistry.Definition def = ActionRegistry.INSTANCE.get(id);
                            if (def == null || !EditorWeapons.adapter(ctx, def.adapterId)) result = Result.fail("UNSUPPORTED");
                            else { ctx.config.putAction(id, validated.getAsJsonObject("config")); result = Result.success(); }
                        }
                    }
                    respond(current, active, request.requestId, ctx, result);
                });
            });
        } catch (RejectedExecutionException e) {
            session.pending = 0; EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
            if (player != null) denied(player, request, "BUSY");
        }
    }
    private static Result apply(EntityPlayerMP player, CombatContext ctx, Operation operation, JsonObject input) {
        String id = string(input, "id", "");
        Object cnpcgun$value1; GunAdapter cnpcgun$match1;
        switch (operation) {

case OPEN:
case REFRESH:
return Result.success();
case SET:
{
                String key = string(input, "key", "");
                if (inventoryConfig(key) && !CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_INVENTORY)) return Result.fail("PERMISSION_DENIED");
                return key.equals("slashblade.preset") ? Presets.apply(ctx, ConfigSchema.get(key).validate(input.get("value")).getAsString()) : CombatService.setConfig(ctx, key, input.get("value"));
            }
case BATCH:
{
                JsonObject values = object(input, "values", true);
                if (values.entrySet().stream().map(Map.Entry::getKey).anyMatch(GunNetwork::inventoryConfig)
                    && !CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_INVENTORY)) return Result.fail("PERMISSION_DENIED");
                return CombatService.setConfigs(ctx, values);
            }
case FETCH_GRAPH:
case SELECT_GRAPH:
return ctx.config.selectGraph(id);
case DELETE_GRAPH:
return ctx.data.blade.comboState(ctx).get("running").getAsBoolean() ? Result.fail("BUSY") : ctx.config.removeGraph(id);
case SWITCH:
return ctx.data.armory.switchTo(ctx, integer(input, "slot", 0, Armory.SIZE - 1));
case EXCHANGE:
return Equipment.exchange(ctx, player, integer(input, "slot", 0, Equipment.SIZE - 1), integer(input, "inventorySlot", 0, 40));
case UNEQUIP:
return Equipment.unequip(ctx, player, integer(input, "slot", 0, Equipment.SIZE - 1));
case PRIMARY_WEAPON:
{
                int slot = integer(input, "slot", 0, Armory.SIZE - 1);
                if (ctx.data.armory.get(ctx.npc, slot).isEmpty()) return Result.fail("NO_WEAPON");
                ctx.config.set("weapon.primarySlot", new JsonPrimitive(slot)); return Result.success();
            }
case EQUIP_CREATIVE:
return player.capabilities.isCreativeMode ? Equipment.creative(ctx, player,
                integer(input, "slot", 0, Equipment.SIZE - 1), CreativeCatalogue.resolve(player, input), input.has("count") ? integer(input, "count", 1, 64) : 1) : Result.fail("PERMISSION_DENIED");
case SAVE_ITEM_PRESET:
{
                if (!player.capabilities.isCreativeMode) return Result.fail("PERMISSION_DENIED");
                ItemStack item;
                if (input.has("npcSlot")) item = Equipment.get(ctx, integer(input, "npcSlot", 0, Equipment.SIZE - 1)).copy();
                else if (input.has("inventorySlot")) {
                    int index = integer(input, "inventorySlot", 0, 40);
                    if (index > 35 && index != 40) return Result.fail("INVALID_ARGUMENT");
                    item = player.inventory.getStackInSlot(index).copy();
                } else item = CreativeCatalogue.resolve(player, input);
                return ItemPresetLibrary.get(player).create(player, string(input, "name", ""), item);
            }
case EQUIP_ITEM_PRESET:
{
                if (!player.capabilities.isCreativeMode) return Result.fail("PERMISSION_DENIED");
                ItemStack item = ItemPresetLibrary.get(player).find(player.getUniqueID(), id);
                return item.isEmpty() ? Result.fail("NOT_FOUND") : Equipment.creative(ctx, player, integer(input, "slot", 0, Equipment.SIZE - 1), item, input.has("count") ? integer(input, "count", 1, 64) : 1);
            }
case DELETE_ITEM_PRESET:
return ItemPresetLibrary.get(player).remove(player.getUniqueID(), id);
case SUPPLY:
return ((cnpcgun$value1 = ctx.runtime.adapter) instanceof GunAdapter && (cnpcgun$match1 = (GunAdapter)cnpcgun$value1) != null) && ctx.runtime.gunCaps != null && ctx.runtime.gunCaps.hasReserveAmmo
                ? ctx.data.ammo.supply(ctx, cnpcgun$match1, integer(input, "count", 0, 100000), input.has("replace") && Parameter.bool("replace", false).validate(input.get("replace")).getAsBoolean(), false) : Result.fail("UNSUPPORTED");
case RELOAD:
return ctx.data.gun.reload(ctx);
case PRESET:
return Presets.apply(ctx, id);
case COPY:
return Presets.copy(ctx, string(input, "source", ""), id);
case SAVE_PRESET:
return PresetLibrary.get(ctx).saveAs(id, ctx.config.snapshot());
case DELETE_PRESET:
return Presets.remove(ctx, id);
case START:
return ctx.data.blade.startCombo(ctx, id);
case STOP:
return ctx.data.blade.stopCombo(ctx, "editor");
case EXECUTE_ACTION:
return ctx.data.blade.execute(ctx, id, object(input, "parameters", false));
case FIRE:
return ctx.data.gun.fire(ctx);
case COMMAND:
return CombatService.command(ctx, id);
default:
return Result.fail("INVALID_ARGUMENT");
}
    }
    private static boolean inventoryConfig(String key) { return key.startsWith("ammo.") || key.startsWith("throw.") || key.equals("weapon.primarySlot"); }
    private static int integer(JsonObject input, String key, int min, int max) {
        return Parameter.integer(key, min, min, max).validate(input.get(key)).getAsInt();
    }
    private static String string(JsonObject input, String key, String fallback) {
        if (!input.has(key)) return fallback;
        return new Parameter(key, "string", fallback, null, null, null, "").validate(input.get(key)).getAsString();
    }
    private static JsonObject object(JsonObject input, String key, boolean required) {
        JsonElement value = input.get(key);
        if (value == null && !required) return new JsonObject();
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(key);
        return value.getAsJsonObject();
    }
    private static void respond(EntityPlayerMP player, Session session, int request, CombatContext ctx, Result result) {
        CombatService.refresh(ctx);
        JsonObject state = EditorSnapshot.create(ctx);
        state.addProperty("inventoryAllowed", CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_INVENTORY));
        state.addProperty("creativeAllowed", player.capabilities.isCreativeMode && CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_INVENTORY));
        state.add("itemPresets", ItemPresetLibrary.get(player).snapshot(player.getUniqueID()));
        state.add("resultData", SafeJson.GSON.toJsonTree(result.data));
        String json = SafeJson.GSON.toJson(state);
        if (json.length() > MAX_SNAPSHOT) { denied(player, new Request(session.npcId, session.token, ctx.config.revision, request, Operation.REFRESH, "{}"), "INTERNAL_ERROR"); return; }
        ItemStack[] slots = new ItemStack[Equipment.SIZE]; for (int i = 0; i < Equipment.SIZE; i++) slots[i] = Equipment.get(ctx, i).copy();
        CHANNEL.sendTo(new Response(session.npcId, session.token, ctx.config.revision,
            request, result.code, result.message.length() > 512 ? result.message.substring(0, 512) : result.message, json, slots), player);
        ctx.npc.updateClient = true;
    }
    private static void denied(EntityPlayerMP player, Request request, String code) {
        ItemStack[] empty = Equipment.empty();
        CHANNEL.sendTo(new Response(request.npcId, request.token, request.revision, request.requestId,
            code, "cnpcgun.error." + code.toLowerCase(Locale.ROOT), "{}", empty), player);
    }
    public static void animation(CombatContext ctx, String pose, int duration) {
        animation(ctx, pose, duration, ctx.runtime.actionId);
    }
    public static void animation(CombatContext ctx, String pose, int duration, String actionId) {
        if (!CombatService.serverThread(ctx.npc)) return;
        CHANNEL.sendToAllTracking(new Animation(ctx.npc.getEntityId(), ctx.npc.getUniqueID(),
            ctx.runtime.domain, pose, actionId, Math.max(1, Math.min(2400, duration)), ctx.runtime.aiming), ctx.npc);
    }
    public static void summary(CombatContext ctx) {
        String key = ctx.runtime.domain + ":" + ctx.runtime.aiming + ":" + ctx.runtime.combatState;
        if (ctx.now < ctx.runtime.nextAnimationSummary && key.equals(ctx.runtime.animationSummary)) return;
        ctx.runtime.animationSummary = key; ctx.runtime.nextAnimationSummary = ctx.now + 20;
        animation(ctx, "STATE", 25);
    }
}
