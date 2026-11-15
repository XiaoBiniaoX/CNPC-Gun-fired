package bincnpc.gun.slashblade.strategy;

import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.slashblade.action.ActionRegistry;
import com.google.gson.JsonObject;

public interface BladeStrategy {
    record Decision(String action, JsonObject parameters, int delay) {}
    Decision decide(CombatContext context);
}
