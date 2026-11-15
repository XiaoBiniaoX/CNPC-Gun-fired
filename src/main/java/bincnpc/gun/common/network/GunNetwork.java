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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import noppes.npcs.*;
import noppes.npcs.entity.EntityNPCInterface;

public final class GunNetwork {
    public static final UUID NO_TOKEN = new UUID(0, 0);
    public static final int MAX_SNAPSHOT = 524288;
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(CNPCGun.MOD_ID, "editor"), () -> "4", "4"::equals, "4"::equals);
    public enum Operation { OPEN, CLOSE, REFRESH, SET, BATCH, ACTION_CONFIG, GRAPH, FETCH_GRAPH, DELETE_GRAPH,
        SELECT_GRAPH, SWITCH, EXCHANGE, SUPPLY, RELOAD, PRESET, COPY, SAVE_PRESET, START, STOP, EXECUTE_ACTION, FIRE, COMMAND,
        PRIMARY_WEAPON, EQUIP_CREATIVE, SAVE_ITEM_PRESET, EQUIP_ITEM_PRESET, DELETE_ITEM_PRESET, DELETE_PRESET, HEARTBEAT, UNEQUIP }
    public record Request(UUID npcId, UUID token, long revision, int requestId, Operation operation, String json) {
        public static Request decode(FriendlyByteBuf buf) {
            return new Request(buf.readUUID(), buf.readUUID(), buf.readLong(), buf.readVarInt(), buf.readEnum(Operation.class), buf.readUtf(SafeJson.MAX_BYTES));
        }
        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(npcId); buf.writeUUID(token); buf.writeLong(revision); buf.writeVarInt(requestId);
            buf.writeEnum(operation); buf.writeUtf(json, SafeJson.MAX_BYTES);
        }
    }
    public record Response(UUID npcId, UUID token, long revision, int requestId, String code, String message, String json, ItemStack[] slots) {
        public static Response decode(FriendlyByteBuf buf) {
            UUID npc = buf.readUUID(), token = buf.readUUID(); long revision = buf.readLong(); int request = buf.readVarInt();
            String code = buf.readUtf(64), message = buf.readUtf(512), json = buf.readUtf(MAX_SNAPSHOT);
            ItemStack[] slots = new ItemStack[Equipment.SIZE]; for (int i = 0; i < Equipment.SIZE; i++) slots[i] = buf.readItem();
            return new Response(npc, token, revision, request, code, message, json, slots);
        }
        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(npcId); buf.writeUUID(token); buf.writeLong(revision); buf.writeVarInt(requestId);
            buf.writeUtf(code, 64); buf.writeUtf(message, 512); buf.writeUtf(json, MAX_SNAPSHOT);
            for (ItemStack slot : slots) buf.writeItem(slot);
        }
    }
    public record Animation(int entityId, UUID npcId, String domain, String pose, String actionId, int duration, boolean aiming) {
        public static Animation decode(FriendlyByteBuf buf) {
            return new Animation(buf.readVarInt(), buf.readUUID(), buf.readUtf(24), buf.readUtf(32), buf.readUtf(192), buf.readVarInt(), buf.readBoolean());
        }
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId); buf.writeUUID(npcId); buf.writeUtf(domain, 24); buf.writeUtf(pose, 32);
            buf.writeUtf(actionId, 192); buf.writeVarInt(duration); buf.writeBoolean(aiming);
        }
    }
    private static final class Session {
        final UUID npcId, token = UUID.randomUUID();
        int lastRequest, pending;
        long expires;
        Session(UUID npcId, long now) { this.npcId = npcId; expires = now + 1200; }
    }
    private static final class Rate { long window; int count; }
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Rate> RATES = new HashMap<>();
    private static ThreadPoolExecutor worker;
    private GunNetwork() {}
    public static void init() {
        CHANNEL.registerMessage(0, Request.class, Request::encode, Request::decode, GunNetwork::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Response.class, Response::encode, Response::decode, (packet, supplier) -> {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> bincnpc.gun.client.ClientPackets.receive(packet)));
            context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, Animation.class, Animation::encode, Animation::decode, (packet, supplier) -> {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> bincnpc.gun.client.animation.NpcAnimations.receive(packet)));
            context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
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
    public static void forget(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID()); RATES.remove(player.getUUID()); finishEditing(player, session);
    }
    private static void finishEditing(ServerPlayer player, Session ended) {
        if (ended == null || player.getServer() == null) return;
        for (var level : player.getServer().getAllLevels()) {
            var entity = level.getEntity(ended.npcId);
            if (!(entity instanceof EntityNPCInterface npc) || !CombatService.validNpc(npc)) continue;
            long remaining = SESSIONS.values().stream().filter(session -> session.npcId.equals(ended.npcId))
                .mapToLong(session -> session.expires).max().orElse(0);
            if (remaining > level.getGameTime()) CombatService.data(npc).runtime.editorUntil = remaining;
            else CombatService.endEditing(new CombatContext(npc));
            break;
        }
    }
    private static void handle(Request request, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get(); ServerPlayer player = context.getSender();
        if (player != null) context.enqueueWork(() -> accept(player, request));
        context.setPacketHandled(true);
    }
    private static EntityNPCInterface editing(ServerPlayer player, UUID id, Operation operation) {
        EntityNPCInterface npc = NoppesUtilServer.getEditingNpc(player);
        if (npc == null || !id.equals(npc.getUUID()) || !CombatService.validNpc(npc) || !player.isAlive()
                || npc.level() != player.level() || player.distanceToSqr(npc) > 256
                || !(player.getMainHandItem().is(CustomItems.wand) || player.getOffhandItem().is(CustomItems.wand))
                || !CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_GUI)
                || !CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_ADVANCED)) return null;
        if (Set.of(Operation.EXCHANGE, Operation.UNEQUIP, Operation.SUPPLY, Operation.SWITCH, Operation.PRIMARY_WEAPON, Operation.EQUIP_CREATIVE,
                Operation.SAVE_ITEM_PRESET, Operation.EQUIP_ITEM_PRESET, Operation.DELETE_ITEM_PRESET).contains(operation)
                && !CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_INVENTORY)) return null;
        return npc;
    }
    private static boolean rate(ServerPlayer player, long now) {
        Rate rate = RATES.computeIfAbsent(player.getUUID(), key -> new Rate());
        if (now - rate.window >= 20 || now < rate.window) { rate.window = now; rate.count = 0; }
        return ++rate.count <= 8;
    }
    private static void accept(ServerPlayer player, Request request) {
        long now = player.level().getGameTime();
        if (request.requestId <= 0 || request.operation != Operation.CLOSE && !rate(player, now)) return;
        EntityNPCInterface npc = editing(player, request.npcId, request.operation);
        if (npc == null) { denied(player, request, "PERMISSION_DENIED"); return; }
        Session session = SESSIONS.get(player.getUUID());
        if (request.operation == Operation.OPEN) {
            Session previous = SESSIONS.remove(player.getUUID()); finishEditing(player, previous);
            session = new Session(npc.getUUID(), now); SESSIONS.put(player.getUUID(), session);
        } else if (session == null || !session.npcId.equals(request.npcId) || !session.token.equals(request.token)
                || session.expires < now) { denied(player, request, "PERMISSION_DENIED"); return; }
        if (request.requestId <= session.lastRequest) { denied(player, request, "CONFLICT"); return; }
        if (request.operation == Operation.CLOSE) {
            SESSIONS.remove(player.getUUID()); finishEditing(player, session); return;
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
                validateAsync(player.getServer(), player.getUUID(), session, request, input); return;
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
            session.pending = 0; ServerPlayer player = server.getPlayerList().getPlayer(playerId);
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
                server.execute(() -> {
                    ServerPlayer current = server.getPlayerList().getPlayer(playerId);
                    Session active = SESSIONS.get(playerId);
                    if (current == null || active == null || !active.token.equals(token) || active.pending != request.requestId) return;
                    active.pending = 0;
                    EntityNPCInterface npc = editing(current, request.npcId, request.operation);
                    if (npc == null || active.expires < current.level().getGameTime()) { denied(current, request, "PERMISSION_DENIED"); return; }
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
            session.pending = 0; ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) denied(player, request, "BUSY");
        }
    }
    private static Result apply(ServerPlayer player, CombatContext ctx, Operation operation, JsonObject input) {
        String id = string(input, "id", "");
        return switch (operation) {
            case OPEN, REFRESH -> Result.success();
            case SET -> {
                String key = string(input, "key", "");
                if (inventoryConfig(key) && !CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_INVENTORY)) yield Result.fail("PERMISSION_DENIED");
                yield key.equals("slashblade.preset") ? Presets.apply(ctx, ConfigSchema.get(key).validate(input.get("value")).getAsString()) : CombatService.setConfig(ctx, key, input.get("value"));
            }
            case BATCH -> {
                JsonObject values = object(input, "values", true);
                if (values.keySet().stream().anyMatch(GunNetwork::inventoryConfig)
                    && !CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_INVENTORY)) yield Result.fail("PERMISSION_DENIED");
                yield CombatService.setConfigs(ctx, values);
            }
            case FETCH_GRAPH, SELECT_GRAPH -> ctx.config.selectGraph(id);
            case DELETE_GRAPH -> ctx.data.blade.comboState(ctx).get("running").getAsBoolean() ? Result.fail("BUSY") : ctx.config.removeGraph(id);
            case SWITCH -> ctx.data.armory.switchTo(ctx, integer(input, "slot", 0, Armory.SIZE - 1));
            case EXCHANGE -> Equipment.exchange(ctx, player, integer(input, "slot", 0, Equipment.SIZE - 1), integer(input, "inventorySlot", 0, 40));
            case UNEQUIP -> Equipment.unequip(ctx, player, integer(input, "slot", 0, Equipment.SIZE - 1));
            case PRIMARY_WEAPON -> {
                int slot = integer(input, "slot", 0, Armory.SIZE - 1);
                if (ctx.data.armory.get(ctx.npc, slot).isEmpty()) yield Result.fail("NO_WEAPON");
                ctx.config.set("weapon.primarySlot", new JsonPrimitive(slot)); yield Result.success();
            }
            case EQUIP_CREATIVE -> player.isCreative() ? Equipment.creative(ctx, player,
                integer(input, "slot", 0, Equipment.SIZE - 1), CreativeCatalogue.resolve(player, input), input.has("count") ? integer(input, "count", 1, 64) : 1) : Result.fail("PERMISSION_DENIED");
            case SAVE_ITEM_PRESET -> {
                if (!player.isCreative()) yield Result.fail("PERMISSION_DENIED");
                ItemStack item;
                if (input.has("npcSlot")) item = Equipment.get(ctx, integer(input, "npcSlot", 0, Equipment.SIZE - 1)).copy();
                else if (input.has("inventorySlot")) {
                    int index = integer(input, "inventorySlot", 0, 40);
                    if (index > 35 && index != 40) yield Result.fail("INVALID_ARGUMENT");
                    item = player.getInventory().getItem(index).copy();
                } else item = CreativeCatalogue.resolve(player, input);
                yield ItemPresetLibrary.get(player).create(player, string(input, "name", ""), item);
            }
            case EQUIP_ITEM_PRESET -> {
                if (!player.isCreative()) yield Result.fail("PERMISSION_DENIED");
                ItemStack item = ItemPresetLibrary.get(player).find(player.getUUID(), id);
                yield item.isEmpty() ? Result.fail("NOT_FOUND") : Equipment.creative(ctx, player, integer(input, "slot", 0, Equipment.SIZE - 1), item, input.has("count") ? integer(input, "count", 1, 64) : 1);
            }
            case DELETE_ITEM_PRESET -> ItemPresetLibrary.get(player).remove(player.getUUID(), id);
            case SUPPLY -> ctx.runtime.adapter instanceof GunAdapter gun && ctx.runtime.gunCaps != null && ctx.runtime.gunCaps.hasReserveAmmo
                ? ctx.data.ammo.supply(ctx, gun, integer(input, "count", 0, 100000), input.has("replace") && Parameter.bool("replace", false).validate(input.get("replace")).getAsBoolean(), false) : Result.fail("UNSUPPORTED");
            case RELOAD -> ctx.data.gun.reload(ctx);
            case PRESET -> Presets.apply(ctx, id);
            case COPY -> Presets.copy(ctx, string(input, "source", ""), id);
            case SAVE_PRESET -> PresetLibrary.get(ctx).saveAs(id, ctx.config.snapshot());
            case DELETE_PRESET -> Presets.remove(ctx, id);
            case START -> ctx.data.blade.startCombo(ctx, id);
            case STOP -> ctx.data.blade.stopCombo(ctx, "editor");
            case EXECUTE_ACTION -> ctx.data.blade.execute(ctx, id, object(input, "parameters", false));
            case FIRE -> ctx.data.gun.fire(ctx);
            case COMMAND -> CombatService.command(ctx, id);
            default -> Result.fail("INVALID_ARGUMENT");
        };
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
    private static void respond(ServerPlayer player, Session session, int request, CombatContext ctx, Result result) {
        CombatService.refresh(ctx);
        JsonObject state = EditorSnapshot.create(ctx);
        state.addProperty("inventoryAllowed", CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_INVENTORY));
        state.addProperty("creativeAllowed", player.isCreative() && CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_INVENTORY));
        state.add("itemPresets", ItemPresetLibrary.get(player).snapshot(player.getUUID()));
        state.add("resultData", SafeJson.GSON.toJsonTree(result.data));
        String json = SafeJson.GSON.toJson(state);
        if (json.length() > MAX_SNAPSHOT) { denied(player, new Request(session.npcId, session.token, ctx.config.revision, request, Operation.REFRESH, "{}"), "INTERNAL_ERROR"); return; }
        ItemStack[] slots = new ItemStack[Equipment.SIZE]; for (int i = 0; i < Equipment.SIZE; i++) slots[i] = Equipment.get(ctx, i).copy();
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Response(session.npcId, session.token, ctx.config.revision,
            request, result.code, result.message.length() > 512 ? result.message.substring(0, 512) : result.message, json, slots));
        ctx.npc.updateClient = true;
    }
    private static void denied(ServerPlayer player, Request request, String code) {
        ItemStack[] empty = Equipment.empty();
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Response(request.npcId, request.token, request.revision, request.requestId,
            code, "cnpcgun.error." + code.toLowerCase(Locale.ROOT), "{}", empty));
    }
    public static void animation(CombatContext ctx, String pose, int duration) {
        animation(ctx, pose, duration, ctx.runtime.actionId);
    }
    public static void animation(CombatContext ctx, String pose, int duration, String actionId) {
        if (!CombatService.serverThread(ctx.npc)) return;
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> ctx.npc), new Animation(ctx.npc.getId(), ctx.npc.getUUID(),
            ctx.runtime.domain, pose, actionId, Math.max(1, Math.min(2400, duration)), ctx.runtime.aiming));
    }
    public static void summary(CombatContext ctx) {
        String key = ctx.runtime.domain + ":" + ctx.runtime.aiming + ":" + ctx.runtime.combatState;
        if (ctx.now < ctx.runtime.nextAnimationSummary && key.equals(ctx.runtime.animationSummary)) return;
        ctx.runtime.animationSummary = key; ctx.runtime.nextAnimationSummary = ctx.now + 20;
        animation(ctx, "STATE", 25);
    }
}
