package bincnpc.gun.slashblade.controller;

import bincnpc.gun.common.adapter.AdapterRegistry;
import bincnpc.gun.common.api.*;
import bincnpc.gun.common.combat.*;
import bincnpc.gun.common.script.EventBridge;
import bincnpc.gun.slashblade.action.*;
import bincnpc.gun.slashblade.adapter.BladeAdapter;
import bincnpc.gun.slashblade.combo.*;
import bincnpc.gun.slashblade.strategy.*;
import com.google.gson.*;
import java.util.*;

public final class BladeController {
    private final BladeStrategy beginner = new NormalStrategy(true), normal = new NormalStrategy(false);
    private final MaidStrategy maid = new MaidStrategy();
    private final Map<String, Long> finishing = new LinkedHashMap<>();
    private ComboGraph graph;
    private ComboGraph.Node node;
    private long graphStart, nodeStart, nodeWaitUntil, nextDecision;
    private boolean nodeExecuted;

    public void reset() { graph = null; node = null; finishing.clear(); nextDecision = 0; }

    public JsonObject state(CombatContext ctx) {
Object cnpcgun$value0; BladeAdapter cnpcgun$match0;

        return ((cnpcgun$value0 = ctx.runtime.adapter) instanceof BladeAdapter && (cnpcgun$match0 = (BladeAdapter)cnpcgun$value0) != null)
            ? AdapterRegistry.query(cnpcgun$match0, () -> cnpcgun$match0.state(ctx), new JsonObject()) : new JsonObject();
    }
    public Result suspendForThrow(CombatContext ctx) {
Object cnpcgun$value1; BladeAdapter cnpcgun$match1;

        if (!CombatService.serverThread(ctx.npc) || !CombatService.validNpc(ctx.npc)) return Result.fail("INVALID_NPC");
        if (!(((cnpcgun$value1 = ctx.runtime.adapter) instanceof BladeAdapter && (cnpcgun$match1 = (BladeAdapter)cnpcgun$value1) != null))) return Result.fail("NO_COMPATIBLE_WEAPON");
        Scheduler scheduler = ctx.runtime.scheduler;
        if (scheduler.busy(ctx.now) && !scheduler.channel.equals("BLADE")) return Result.fail("BUSY");
        if (scheduler.busy(ctx.now) && !scheduler.interruptible) return Result.fail("BUSY");
        ActionRegistry.Definition current = ActionRegistry.INSTANCE.get(scheduler.action);
        if (scheduler.busy(ctx.now) && current != null && current.info.type.equals("SPECIAL_ATTACK")) return Result.fail("BUSY");
        Result stopped = AdapterRegistry.call(cnpcgun$match1, () -> cnpcgun$match1.interruptForThrow(ctx));
        if (!stopped.ok) return stopped;
        if (graph != null) EventBridge.emit(ctx, "slashblade.combo_interrupted", ctx.runtime.actionId, Result.success("throw"));
        graph = null; node = null; ctx.runtime.comboId = ""; ctx.runtime.comboNode = ""; ctx.runtime.comboTarget = null;
        ctx.runtime.interrupted = "throw";
        if (!ctx.runtime.actionId.isEmpty() && finishing.remove(ctx.runtime.actionId) != null)
            EventBridge.emit(ctx, "slashblade.action_finished", ctx.runtime.actionId, Result.success());
        ctx.runtime.actionId = ""; ctx.runtime.actionEnd = ctx.now; nextDecision = ctx.now + 1;
        if (scheduler.channel.equals("BLADE")) scheduler.release();
        return Result.success();
    }
    public boolean ready(CombatContext ctx, String id) {
        ActionRegistry.Definition def = ActionRegistry.INSTANCE.get(id);
        return def != null && def.info.available && ctx.runtime.cooldowns.getOrDefault(id, 0L) <= ctx.now;
    }

    public Result execute(CombatContext ctx, String id, JsonObject input) {
Object cnpcgun$value4; BladeAdapter.Execution cnpcgun$match4;

Object cnpcgun$value3; BladeAdapter.Execution cnpcgun$match3;

Object cnpcgun$value2; BladeAdapter cnpcgun$match2;

        if (ctx == null || id == null) return Result.fail("INVALID_ARGUMENT");
        if (input == null) input = new JsonObject();
        if (!CombatService.serverThread(ctx.npc) || !CombatService.validNpc(ctx.npc) || ctx.runtime.editorPaused) return Result.fail("PERMISSION_DENIED");
        if (ctx.data.throwing.blocksAttack(ctx.now)) return Result.fail("BUSY");
        if (!(((cnpcgun$value2 = ctx.runtime.adapter) instanceof BladeAdapter && (cnpcgun$match2 = (BladeAdapter)cnpcgun$value2) != null))) return Result.fail("NO_COMPATIBLE_WEAPON");
        if (!ctx.config.bool("combat.enabled") || !ctx.config.bool("slashblade.enabled")) return Result.fail("PERMISSION_DENIED");
        ActionRegistry.Definition def = ActionRegistry.INSTANCE.get(id);
        if (def == null) return Result.fail("NOT_FOUND");
        if (!def.adapterId.equals(cnpcgun$match2.id()) || !def.info.available) return Result.fail("UNSUPPORTED");
        if (def.info.targetRequired && !ctx.validTarget()) return Result.fail("INVALID_TARGET");
        if (!ready(ctx, id)) return Result.fail("COOLDOWN");
        JsonObject parameters, options;
        try {
            JsonObject action = Presets.action(ctx, def);
            JsonObject merged = action.has("parameters") ? bincnpc.gun.common.legacy.Legacy.copy(action.getAsJsonObject("parameters")) : new JsonObject();
            input.entrySet().forEach(entry -> merged.add(entry.getKey(), bincnpc.gun.common.legacy.Legacy.copy(entry.getValue())));
            parameters = GraphValidator.parameters(merged, def.info.parameters);
            options = ActionConfig.validate(action, def);
        } catch (IllegalArgumentException | IllegalStateException e) { return Result.fail("INVALID_ARGUMENT", e.getMessage()); }
        if (ctx.validTarget() && (ctx.distance < options.get("minDistance").getAsDouble()
                || ctx.distance > Math.min(options.get("maxDistance").getAsDouble(), ctx.config.number("combat.attackRange"))))
            return Result.fail("OUT_OF_RANGE");
        if (!Conditions.all(options.getAsJsonArray("conditions"), ctx)) return Result.fail("CONDITION_NOT_MET");
        if (ctx.validTarget()) {
            String targetType = options.get("targetType").getAsString();
            if (targetType.equals("PLAYER") && !(ctx.target instanceof net.minecraft.entity.player.EntityPlayer)
                || targetType.equals("NPC") && !(ctx.target instanceof noppes.npcs.entity.EntityNPCInterface)
                || targetType.equals("MOB") && !(ctx.target instanceof net.minecraft.entity.EntityLiving)) return Result.fail("CONDITION_NOT_MET");
            if (options.get("targetCount").getAsInt() > 1 && ctx.targets().size() < options.get("targetCount").getAsInt())
                return Result.fail("CONDITION_NOT_MET");
        }
        JsonArray stages = options.getAsJsonArray("stages");
        if (!(stages.size() == 0)) {
            String phase = SafeJson.string(state(ctx), "combo", "");
            boolean match = false;
            for (JsonElement stage : stages) if (stage.getAsString().equals(phase)) match = true;
            if (!match) return Result.fail("CONDITION_NOT_MET");
        }
        boolean highest = ctx.config.text("slashblade.preset").equals("MAID");
        if (ctx.runtime.scheduler.busy(ctx.now) && ctx.runtime.scheduler.channel.equals("BLADE") && highest
                && !def.info.parallel && options.get("interruptNormal").getAsBoolean()
                && AdapterRegistry.query(cnpcgun$match2, () -> cnpcgun$match2.canChain(ctx, def, parameters), false)) {
            ctx.runtime.scheduler.release();
        } else if (ctx.runtime.scheduler.busy(ctx.now) && ctx.runtime.scheduler.channel.equals("BLADE")
                && ctx.runtime.scheduler.interruptible && !def.info.parallel && options.get("interruptNormal").getAsBoolean()) {
            JsonObject phase = state(ctx);
            if (phase.has("canInterrupt") && !phase.get("canInterrupt").getAsBoolean()
                    && !(phase.has("perfectCharge") && phase.get("perfectCharge").getAsBoolean())) return Result.fail("BUSY");
            ActionRegistry.Definition current = ActionRegistry.INSTANCE.get(ctx.runtime.scheduler.action);
            if (current != null && current.info.type.equals("SPECIAL_ATTACK")) return Result.fail("BUSY");
            ctx.runtime.scheduler.release();
        }
        Result lock = ctx.runtime.scheduler.acquire("BLADE", id, ctx.now, def.info.duration, def.info.interruptible, def.info.parallel);
        if (!lock.ok) return lock;
        Result result = AdapterRegistry.call(cnpcgun$match2, () -> cnpcgun$match2.execute(ctx, def, parameters));
        ctx.runtime.lastResult = result;
        if (!result.ok) {
            if (!def.info.parallel) ctx.runtime.scheduler.release();
            else ctx.runtime.scheduler.releaseAuxiliary(id);
            EventBridge.emit(ctx, "slashblade.action_failed", id, result); return result;
        }
        if (highest && bincnpc.gun.common.legacy.Legacy.set("combo:slashblade:combo_a1", "combo:slashblade:combo_b1", "combo:slashblade:combo_c").contains(def.backend))
            ctx.runtime.maid.comboCycle = (ctx.runtime.maid.comboCycle + 1) % 3;
        int duration = ((cnpcgun$value3 = result.data) instanceof BladeAdapter.Execution && (cnpcgun$match3 = (BladeAdapter.Execution)cnpcgun$value3) != null) ? cnpcgun$match3.duration() : def.info.duration;
        if (def.backend.equals("guard") && parameters.has("duration")) duration = parameters.get("duration").getAsInt();
        duration = Math.max(1, Math.min(2400, duration));
        if (!def.info.parallel) ctx.runtime.scheduler.until = ctx.now + duration;
        int delay = Math.max(highest ? 4 : duration, (int)Math.ceil(def.info.cooldown * options.get("cooldownMultiplier").getAsDouble()));
        ctx.runtime.cooldowns.put(id, ctx.now + delay);
        if (!def.info.parallel && !ctx.runtime.actionId.isEmpty() && finishing.remove(ctx.runtime.actionId) != null)
            EventBridge.emit(ctx, "slashblade.action_finished", ctx.runtime.actionId, Result.success());
        finishing.put(id, ctx.now + duration);
        if (!def.info.parallel) {
            ctx.runtime.actionId = id; ctx.runtime.actionEnd = ctx.now + duration; ctx.runtime.lastBladeAction = id;
            CombatService.setState(ctx, "EXECUTING_ACTION");
            if (highest && def.info.type.equals("SPECIAL_ATTACK") && !def.tags.contains("super")) {
                ctx.runtime.maid.specialAction = id; ctx.runtime.maid.lastSpecial = ctx.now;
                if (++ctx.runtime.maid.specialBurst >= 5) { ctx.runtime.maid.specialCooldown = ctx.now + 120; ctx.runtime.maid.specialCursor++; }
            } else if (highest && def.tags.contains("super")) ctx.runtime.maid.superCooldown = ctx.now + 400;
            else if (highest && (def.info.type.equals("NORMAL_ATTACK") || def.info.type.equals("MOVEMENT"))) ctx.runtime.maid.specialBurst = 0;
        }
        EventBridge.emit(ctx, "slashblade.action_started", id, result);
        if (def.info.type.equals("SPECIAL_ATTACK")) EventBridge.emit(ctx, "slashblade.special_attack_used", id, result);
        if (def.info.type.equals("PHANTOM_SWORD")) EventBridge.emit(ctx, "slashblade.phantom_action_used", id, result);
        String animation = ((cnpcgun$value4 = result.data) instanceof BladeAdapter.Execution && (cnpcgun$match4 = (BladeAdapter.Execution)cnpcgun$value4) != null) ? "combo:" + cnpcgun$match4.stage() : def.info.id;
        int animationDuration = highest && !def.tags.contains("super") && !def.info.parallel ? Math.min(4, duration) : duration;
        bincnpc.gun.common.network.GunNetwork.animation(ctx, def.info.type, animationDuration, animation);
        ctx.npc.updateClient = true;
        if (def.info.type.equals("SPECIAL_ATTACK") && options.get("linkPhantom").getAsBoolean()) {
            String phantom = selectEnabled(ctx, "PHANTOM_SWORD");
            if (!phantom.isEmpty()) execute(ctx, phantom, new JsonObject());
        }
        return result;
    }

    public void tick(CombatContext ctx) {
        Iterator<Map.Entry<String, Long>> iterator = finishing.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (ctx.now >= entry.getValue()) {
                EventBridge.emit(ctx, "slashblade.action_finished", entry.getKey(), Result.success());
                if (ctx.runtime.actionId.equals(entry.getKey())) ctx.runtime.actionId = "";
                iterator.remove();
            }
        }
        if (!ctx.runtime.command.equals("AUTO") || ctx.data.throwing.blocksAttack(ctx.now)) return;
        if (ctx.runtime.adapter instanceof BladeAdapter && ctx.config.bool("slashblade.enabled") && ctx.config.bool("slashblade.autoAttack")
                && ctx.config.text("slashblade.preset").equals("MAID") && ctx.validTarget() && ctx.distance <= ctx.config.number("combat.attackRange"))
            maid.mirage(ctx);
        if (graph != null) { tickGraph(ctx); return; }
        if (!(ctx.runtime.adapter instanceof BladeAdapter) || !ctx.config.bool("slashblade.enabled")
                || !ctx.config.bool("slashblade.autoAttack") || !ctx.validTarget()) return;
        if (ctx.now < nextDecision) return;
        if (ctx.distance > ctx.config.number("combat.attackRange")) return;
        String preset = ctx.config.text("slashblade.preset");
        if (preset.equals("CUSTOM")) {
            String selected = ctx.config.selectedGraph();
            if (!selected.isEmpty()) startCombo(ctx, selected);
            return;
        }BladeStrategy strategy;
switch (preset) {
case "NORMAL":
strategy = normal; break;
case "MAID":
strategy = maid; break;
default:
strategy = beginner; break;
};
        if (ctx.runtime.scheduler.busy(ctx.now) && !preset.equals("MAID")) return;
        BladeStrategy.Decision decision = strategy.decide(ctx);
        nextDecision = ctx.now + (preset.equals("MAID") ? 4 : ctx.config.integer("slashblade.attackInterval"));
        if (decision == null) return;
        Result result = execute(ctx, decision.action(), decision.parameters());
        if (result.ok) nextDecision = ctx.now + decision.delay();
        if (ctx.distance > CombatMovement.meleeReach(ctx)) ctx.npc.getNavigator().tryMoveToEntityLiving(ctx.target, 1.0);
        else ctx.npc.getNavigator().clearPath();
    }

    public String selectEnabled(CombatContext ctx, String type) {
        ActionRegistry.Definition best = null;
        int priority = -1;
        for (ActionRegistry.Definition def : ActionRegistry.INSTANCE.all()) {
            if (ctx.runtime.adapter == null || !def.adapterId.equals(ctx.runtime.adapter.id()) || !ready(ctx, def.info.id)) continue;
            if (type != null && !def.info.type.equals(type)) continue;
            if (type == null && !bincnpc.gun.common.legacy.Legacy.set("SPECIAL_ATTACK", "PHANTOM_SWORD").contains(def.info.type)) continue;
            if (def.info.type.equals("SPECIAL_ATTACK") && !ctx.config.bool("slashblade.useSpecialAttacks")
                || def.info.type.equals("PHANTOM_SWORD") && !ctx.config.bool("slashblade.usePhantoms")) continue;
            JsonObject raw = Presets.action(ctx, def);
            if (!ActionConfig.value(raw, "enabled").getAsBoolean() || !ActionConfig.value(raw, "auto").getAsBoolean()) continue;
            if (!ActionConfig.value(raw, "consecutive").getAsBoolean() && ctx.runtime.lastBladeAction.equals(def.info.id)) continue;
            JsonObject options;
            try { options = ActionConfig.validate(raw, def); } catch (IllegalArgumentException | IllegalStateException e) { continue; }
            if (ctx.distance < options.get("minDistance").getAsDouble() || ctx.distance > options.get("maxDistance").getAsDouble()) continue;
            if (!Conditions.all(options.getAsJsonArray("conditions"), ctx)) continue;
            if (!eligible(ctx, def.info.id)) continue;
            int rank = options.get("priority").getAsInt();
            if (rank > priority) { best = def; priority = rank; }
        }
        return best == null ? "" : best.info.id;
    }
    public boolean autoEnabled(CombatContext ctx, String id) {
        ActionRegistry.Definition def = ActionRegistry.INSTANCE.get(id);
        if (def == null || ctx.runtime.adapter == null || !def.adapterId.equals(ctx.runtime.adapter.id()) || !def.info.available) return false;
        if (def.info.type.equals("SPECIAL_ATTACK") && !ctx.config.bool("slashblade.useSpecialAttacks")
                || def.info.type.equals("PHANTOM_SWORD") && !ctx.config.bool("slashblade.usePhantoms")
                || bincnpc.gun.common.legacy.Legacy.set("COUNTER", "DEFENSE").contains(def.info.type) && !ctx.config.bool("slashblade.counter")) return false;
        JsonObject options = Presets.action(ctx, def);
        return ActionConfig.value(options, "enabled").getAsBoolean() && ActionConfig.value(options, "auto").getAsBoolean();
    }
    public boolean eligible(CombatContext ctx, String id) {
        ActionRegistry.Definition def = ActionRegistry.INSTANCE.get(id);
        if (!autoEnabled(ctx, id) || !ready(ctx, id) || def.info.targetRequired && !ctx.validTarget()) return false;
        JsonObject raw = Presets.action(ctx, def);
        try {
            JsonObject options = ActionConfig.validate(raw, def);
            JsonObject phase = state(ctx);
            if (!ctx.config.bool("slashblade.ignoreRequirements")
                    && (phase.has("broken") && phase.get("broken").getAsBoolean()
                    || phase.has("sealed") && phase.get("sealed").getAsBoolean())) return false;
            int cost = (int)Math.ceil((def.info.resourceCost == null ? 0 : def.info.resourceCost) * options.get("resourceMultiplier").getAsDouble());
            int reserve = Math.max(ctx.config.integer("slashblade.resourceReserve"), options.get("resourceReserve").getAsInt());
            if (!ctx.config.bool("slashblade.ignoreRequirements") && phase.has("resource") && phase.get("resource").getAsInt() < cost + reserve) return false;
            if (!options.get("consecutive").getAsBoolean() && !def.info.parallel && ctx.runtime.lastBladeAction.equals(id)) return false;
            JsonArray stages = options.getAsJsonArray("stages");
            if (!(stages.size() == 0) && !bincnpc.gun.common.legacy.Legacy.jsonList(stages).contains(new JsonPrimitive(SafeJson.string(phase, "combo", "")))) return false;
            if (ctx.validTarget()) {
                String kind = options.get("targetType").getAsString();
                if (kind.equals("PLAYER") && !(ctx.target instanceof net.minecraft.entity.player.EntityPlayer)
                    || kind.equals("NPC") && !(ctx.target instanceof noppes.npcs.entity.EntityNPCInterface)
                    || kind.equals("MOB") && !(ctx.target instanceof net.minecraft.entity.EntityLiving)) return false;
                if (options.get("targetCount").getAsInt() > 1 && ctx.targets().size() < options.get("targetCount").getAsInt()) return false;
            }
            return options.get("enabled").getAsBoolean() && options.get("auto").getAsBoolean()
                && (!ctx.validTarget() || ctx.distance >= options.get("minDistance").getAsDouble()
                    && ctx.distance <= Math.min(ctx.config.number("combat.attackRange"), options.get("maxDistance").getAsDouble()))
                && Conditions.all(options.getAsJsonArray("conditions"), ctx);
        } catch (IllegalArgumentException | IllegalStateException e) { return false; }
    }

    public Result setCombo(CombatContext ctx, JsonObject input) {
        try {
            ComboGraph checked = GraphValidator.validate(input, ActionRegistry.INSTANCE.snapshot(), false);
            if (ctx.config.graphs().size() >= 32 && ctx.config.graph(checked.id) == null) return Result.fail("CONFLICT");
            ctx.config.putGraph(checked.id, checked.original);
            return Result.success();
        } catch (IllegalArgumentException | IllegalStateException e) { return Result.fail("INVALID_ARGUMENT", e.getMessage()); }
    }
    public Result startCombo(CombatContext ctx, String id) {
        if (ctx.data.throwing.blocksAttack(ctx.now)) return Result.fail("BUSY");
        if (!(ctx.runtime.adapter instanceof BladeAdapter)) return Result.fail("NO_COMPATIBLE_WEAPON");
        if (graph != null || ctx.runtime.scheduler.busy(ctx.now)) return Result.fail("BUSY");
        JsonObject source = ctx.config.graph(id);
        if (source == null) return Result.fail("NOT_FOUND");
        try {
            for (JsonElement raw : source.getAsJsonArray("nodes")) {
                JsonObject candidate = raw.getAsJsonObject();
                ActionRegistry.Definition def = ActionRegistry.INSTANCE.get(candidate.get("action").getAsString());
                if (def == null) continue;
                String jump = ActionConfig.value(Presets.action(ctx, def), "successNode").getAsString();
                if (!jump.isEmpty()) candidate.addProperty("onSuccess", jump);
            }
            graph = GraphValidator.validate(source, ActionRegistry.INSTANCE.snapshot(), true);
        }
        catch (IllegalArgumentException | IllegalStateException e) { return Result.fail("INVALID_ARGUMENT", e.getMessage()); }
        graphStart = ctx.now; ctx.runtime.comboId = id; ctx.runtime.comboTarget = ctx.runtime.targetId;
        enter(ctx, graph.entryNode);
        EventBridge.emit(ctx, "slashblade.combo_started", "", Result.success(id));
        return Result.success();
    }
    private void enter(CombatContext ctx, String id) {
        if (id.equals("end")) {
            EventBridge.emit(ctx, "slashblade.combo_finished", "", Result.success());
            graph = null; node = null; ctx.runtime.comboId = ""; ctx.runtime.comboNode = ""; return;
        }
        node = graph.nodes.get(id); nodeStart = ctx.now; nodeExecuted = false;
        ctx.runtime.comboNode = id;
        EventBridge.emit(ctx, "slashblade.combo_node", node.action, Result.success(id));
    }
    private void tickGraph(CombatContext ctx) {
        if (!ctx.validTarget() && ctx.config.bool("combat.stopOnInvalidTarget")
                || ctx.config.text("slashblade.targetChange").equals("INTERRUPT") && !Objects.equals(ctx.runtime.comboTarget, ctx.runtime.targetId)
                || Conditions.any(node.interrupts, ctx) || ctx.now - graphStart > ctx.config.integer("slashblade.combo.maxTicks")) {
            stopCombo(ctx, "condition"); return;
        }
        if (ctx.now - nodeStart >= node.timeout) { enter(ctx, node.onTimeout); return; }
        if (nodeExecuted) {
            if (ctx.now >= nodeWaitUntil) enter(ctx, node.onSuccess);
            return;
        }
        if (!Conditions.all(node.conditions, ctx)) { enter(ctx, node.onFailure); return; }
        if (ActionRegistry.INSTANCE.get(node.action) == null) {
            if (ctx.config.text("slashblade.missingAction").equals("STOP")) stopCombo(ctx, "missing_action");
            else enter(ctx, node.onFailure);
            return;
        }
        Result result = execute(ctx, node.action, node.parameters);
        if (result.ok) {
Object cnpcgun$value5; BladeAdapter.Execution cnpcgun$match5;

            nodeExecuted = true;
            nodeWaitUntil = ((cnpcgun$value5 = result.data) instanceof BladeAdapter.Execution && (cnpcgun$match5 = (BladeAdapter.Execution)cnpcgun$value5) != null) ? ctx.now + cnpcgun$match5.duration()
                : ctx.now + ActionRegistry.INSTANCE.get(node.action).info.duration;
        } else if (!bincnpc.gun.common.legacy.Legacy.set("BUSY", "COOLDOWN").contains(result.code)) {
            ActionRegistry.Definition def = ActionRegistry.INSTANCE.get(node.action);
            if (def != null && ActionConfig.value(Presets.action(ctx, def), "failure").getAsString().equals("STOP")) stopCombo(ctx, result.code);
            else enter(ctx, node.onFailure);
        }
    }
    public Result stopCombo(CombatContext ctx, String reason) {
        if (ctx.runtime.scheduler.busy(ctx.now) && !ctx.runtime.scheduler.interruptible) return Result.fail("BUSY");
        if (graph != null) EventBridge.emit(ctx, "slashblade.combo_interrupted", ctx.runtime.actionId, Result.success(reason));
        graph = null; node = null; ctx.runtime.comboId = ""; ctx.runtime.comboNode = ""; ctx.runtime.interrupted = reason;
        if (ctx.runtime.scheduler.channel.equals("BLADE")) {
            if (ctx.runtime.adapter != null) AdapterRegistry.run(ctx.runtime.adapter, () -> ctx.runtime.adapter.cancel(ctx));
            ctx.runtime.scheduler.clear(); finishing.clear(); ctx.runtime.actionId = "";
        }
        return Result.success();
    }
    public JsonObject comboState(CombatContext ctx) {
        JsonObject out = new JsonObject();
        out.addProperty("comboId", ctx.runtime.comboId); out.addProperty("nodeId", ctx.runtime.comboNode);
        out.addProperty("actionId", ctx.runtime.actionId); out.addProperty("running", graph != null);
        out.addProperty("elapsed", graph == null ? 0 : ctx.now - graphStart); out.addProperty("interruptReason", ctx.runtime.interrupted);
        return out;
    }
}
