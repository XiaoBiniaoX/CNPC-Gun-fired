package bincnpc.gun.slashblade.strategy;

import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.common.combat.CombatMovement;
import bincnpc.gun.slashblade.adapter.BladeAdapter;
import bincnpc.gun.slashblade.action.*;
import com.google.gson.JsonObject;
import java.util.*;

/**
 * NPC port of the reference's highest Native Power configuration. Native combo
 * transitions stay on the server; mirage arts have an independent bounded lane.
 */
public final class MaidStrategy implements BladeStrategy {
    @Override public Decision decide(CombatContext ctx) {
Object cnpcgun$value0; BladeAdapter cnpcgun$match0;

        if (!(((cnpcgun$value0 = ctx.runtime.adapter) instanceof BladeAdapter && (cnpcgun$match0 = (BladeAdapter)cnpcgun$value0) != null)) || !ctx.validTarget()) return null;
        if (ctx.now < ctx.runtime.maid.escapeUntil) {
            double dx = ctx.npc.posX - ctx.target.posX, dz = ctx.npc.posZ - ctx.target.posZ;
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 0.01) { dx = 1; dz = 0; length = 1; }
            ctx.npc.getNavigator().tryMoveToXYZ(ctx.npc.posX + dx / length * 6, ctx.npc.posY, ctx.npc.posZ + dz / length * 6, 1.3);
            return null;
        }
        if (!ctx.npc.getEntitySenses().canSee(ctx.target)) { ctx.npc.getNavigator().tryMoveToEntityLiving(ctx.target, 1.15); return null; }
        bincnpc.gun.common.combat.CombatDecisions.Advice advice = CombatService.advice(ctx);
        double reach = CombatMovement.meleeReach(ctx);
        JsonObject phase = ctx.data.blade.state(ctx);
        boolean interrupt = flag(phase, "canInterrupt"), perfect = flag(phase, "perfectCharge"), idle = flag(phase, "idle");
        if (idle && ctx.now >= ctx.runtime.maid.specialCooldown) ctx.runtime.maid.specialBurst = 0;
        if (!ctx.npc.onGround && ctx.npc.posY - ctx.target.posY > 5 && interrupt) {
            String fall = tagged(ctx, "fall", "MOVEMENT", "COMBO_ATTACK"); if (!fall.isEmpty()) return decision(fall);
        }
        if (interrupt && ctx.now - ctx.runtime.lastHurt <= 8 && ctx.config.bool("slashblade.counter")) {
            String counter = tagged(ctx, "counter", "COUNTER"); if (!counter.isEmpty()) return decision(counter);
        }
        boolean release = perfect || !idle && interrupt && phase.has("elapsed") && phase.get("elapsed").getAsLong() >= 4;
        if (release && ctx.config.bool("slashblade.useSpecialAttacks")) {
            boolean pressure = advice != null ? advice.pressure() >= 0.6 : ctx.targets().size() >= 3 || ctx.now - ctx.runtime.lastHurt <= 8;
            String superArt = ctx.now >= ctx.runtime.maid.superCooldown && (ctx.targets().size() >= 2 || pressure) ? tagged(ctx, "super", "SPECIAL_ATTACK") : "";
            if (!superArt.isEmpty()) return decision(superArt);
            if (ctx.now >= ctx.runtime.maid.specialCooldown) {
                String special = special(ctx, phase); if (!special.isEmpty()) return decision(special);
            }
        }
        if (interrupt && ctx.distance > 12) {
            String trick = tagged(ctx, "teleport", "MOVEMENT"); if (!trick.isEmpty()) return decision(trick);
        }
        String current = phase.has("combo") ? phase.get("combo").getAsString() : "";
        if (interrupt && current.equals("slashblade:rapid_slash") && ctx.distance <= reach) {
            String rising = nativeAction(ctx, "rising_star"); if (!rising.isEmpty()) return decision(rising);
        }
        if (idle && ctx.distance > reach) {
            String dash = nativeAction(ctx, "rapid_slash"); if (!dash.isEmpty()) return decision(dash);
        }
        if (idle && ctx.distance <= reach) {
            if (!ctx.npc.onGround) { String air = nativeAction(ctx, "aerial_rave_a1"); if (!air.isEmpty()) return decision(air); }
            else if (!ctx.target.onGround) { String launcher = nativeAction(ctx, "upperslash"); if (!launcher.isEmpty()) return decision(launcher); }
            else {
                List<String> roots = bincnpc.gun.common.legacy.Legacy.list("combo_a1", "combo_b1", "combo_c");
                int start = ctx.targets().size() >= 3 ? 1 : ctx.runtime.maid.comboCycle;
                for (int offset = 0; offset < roots.size(); offset++) {
                    String root = nativeAction(ctx, roots.get((start + offset) % roots.size())); if (!root.isEmpty()) return decision(root);
                }
            }
        }
        String action = cnpcgun$match0.nextNormal(ctx);
        if (!action.isEmpty() && ctx.data.blade.eligible(ctx, action)) return decision(action);
        if (idle) {
            String start = tagged(ctx, ctx.npc.onGround ? "ground" : "air", "NORMAL_ATTACK", "COMBO_ATTACK");
            if (!start.isEmpty()) return decision(start);
        }
        return null;
    }
    private static Decision decision(String id) { return new Decision(id, new JsonObject(), 4); }
    private static boolean flag(JsonObject phase, String key) { return phase.has(key) && phase.get(key).getAsBoolean(); }
    private String nativeAction(CombatContext ctx, String path) {
        for (ActionRegistry.Definition def : ActionRegistry.INSTANCE.all()) if (def.backend.equals("combo:slashblade:" + path) && ctx.data.blade.eligible(ctx, def.info.id)) return def.info.id;
        return "";
    }
    private String tagged(CombatContext ctx, String tag, String... types) {
        for (ActionRegistry.Definition def : ActionRegistry.INSTANCE.all()) {
            if (def.tags.contains(tag) && Arrays.asList(types).contains(def.info.type) && ctx.data.blade.eligible(ctx, def.info.id)) return def.info.id;
        }
        return "";
    }
    private String special(CombatContext ctx, JsonObject phase) {
        String nativeId = phase.has("specialAttack") ? phase.get("specialAttack").getAsString() : "";
        if (ctx.runtime.maid.specialBurst > 0 && ctx.data.blade.eligible(ctx, ctx.runtime.maid.specialAction)) return ctx.runtime.maid.specialAction;
        List<ActionRegistry.Definition> choices = new ArrayList<>();
        for (ActionRegistry.Definition def : ActionRegistry.INSTANCE.all()) if (def.info.type.equals("SPECIAL_ATTACK") && !def.tags.contains("super")
                && ctx.data.blade.autoEnabled(ctx, def.info.id)) choices.add(def);
        choices.sort(Comparator.<ActionRegistry.Definition>comparingInt(def -> def.info.id.equals(nativeId) ? 10000
                : ActionConfig.value(Presets.action(ctx, def), "priority").getAsInt()).reversed().thenComparing(def -> def.info.id));
        for (int offset = 0; offset < choices.size(); offset++) {
            ActionRegistry.Definition def = choices.get(Math.floorMod(ctx.runtime.maid.specialCursor + offset, choices.size()));
            if (ctx.data.blade.eligible(ctx, def.info.id)) return def.info.id;
        }
        return "";
    }
    public void mirage(CombatContext ctx) {
        if (!ctx.validTarget() || !ctx.npc.getEntitySenses().canSee(ctx.target)) return;
        MaidCombatState state = ctx.runtime.maid;
        if (!ctx.config.bool("slashblade.usePhantoms") || ctx.now < state.nextPhantom) return;
        state.nextPhantom = ctx.now + 4;
        if (state.spiralReleaseAt > 0 && ctx.now >= state.spiralReleaseAt) {
            String fire = "slashblade:slashblade/phantom/spiral_fire";
            if (ctx.data.blade.eligible(ctx, fire) && ctx.data.blade.execute(ctx, fire, new JsonObject()).ok) state.spiralReleaseAt = 0;
        }
        for (String kind : bincnpc.gun.common.legacy.Legacy.list("rain", "blistering", "spiral", "storm", "basic")) {
            if (kind.equals("basic") && Math.abs(ctx.target.posY - ctx.npc.posY) >= 7) continue;
            String action = tagged(ctx, kind, "PHANTOM_SWORD");
            if (action.isEmpty()) continue;
            if (ctx.data.blade.execute(ctx, action, new JsonObject()).ok) {
                if (kind.equals("spiral")) state.spiralReleaseAt = ctx.now + 8;
                return;
            }
        }
    }
}
