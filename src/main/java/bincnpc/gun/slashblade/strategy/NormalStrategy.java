package bincnpc.gun.slashblade.strategy;

import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.slashblade.adapter.BladeAdapter;
import bincnpc.gun.slashblade.action.*;
import com.google.gson.JsonObject;

public final class NormalStrategy implements BladeStrategy {
    private final boolean beginner;
    public NormalStrategy(boolean beginner) { this.beginner = beginner; }
    @Override public Decision decide(CombatContext ctx) {
Object cnpcgun$value0; BladeAdapter cnpcgun$match0;

        if (!(((cnpcgun$value0 = ctx.runtime.adapter) instanceof BladeAdapter && (cnpcgun$match0 = (BladeAdapter)cnpcgun$value0) != null))) return null;
        String preferred = ctx.data.blade.selectEnabled(ctx, null);
        if (!preferred.isEmpty()) return new Decision(preferred, new JsonObject(), ctx.config.integer("slashblade.attackInterval"));
        if (!beginner) {
            String tag = ctx.now - ctx.runtime.lastHurt < 10 && ctx.config.bool("slashblade.counter") ? "counter"
                : ctx.distance > 4 ? "dash" : "";
            if (!tag.isEmpty()) for (ActionRegistry.Definition def : ActionRegistry.INSTANCE.all()) {
                if (def.adapterId.equals(cnpcgun$match0.id()) && def.tags.contains(tag) && ctx.data.blade.eligible(ctx, def.info.id))
                    return new Decision(def.info.id, new JsonObject(), 12);
            }
        }
        String normal = cnpcgun$match0.nextNormal(ctx);
        if (normal.isEmpty() || !ctx.data.blade.eligible(ctx, normal)) {
            normal = "";
            for (ActionRegistry.Definition def : ActionRegistry.INSTANCE.all()) {
                if (def.adapterId.equals(cnpcgun$match0.id())
                        && (def.info.type.equals("NORMAL_ATTACK") || def.info.type.equals("COMBO_ATTACK"))
                        && !def.tags.contains("recovery") && def.tags.contains(ctx.npc.onGround ? "ground" : "air")
                        && ctx.data.blade.eligible(ctx, def.info.id)) { normal = def.info.id; break; }
            }
        }
        return normal.isEmpty() ? null : new Decision(normal, new JsonObject(), beginner ? Math.max(12, ctx.config.integer("slashblade.attackInterval")) : ctx.config.integer("slashblade.attackInterval"));
    }
}
