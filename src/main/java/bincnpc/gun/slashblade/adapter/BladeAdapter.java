package bincnpc.gun.slashblade.adapter;

import bincnpc.gun.common.adapter.WeaponAdapter;
import bincnpc.gun.common.api.*;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.slashblade.action.ActionRegistry;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;

public interface BladeAdapter extends WeaponAdapter {
    record Execution(String stage, int duration) {}
    @Override default String domain() { return "SLASHBLADE"; }
    Views.BladeCaps capabilities(ItemStack stack);
    void registerActions(ActionRegistry registry);
    Result execute(CombatContext context, ActionRegistry.Definition action, JsonObject parameters);
    JsonObject state(CombatContext context);
    /** Ends only the current interruptible input/combo; resource and skill cooldowns are retained. */
    default Result interruptForThrow(CombatContext context) { return Result.fail("UNSUPPORTED"); }
    default String nextNormal(CombatContext context) { return ""; }
    /** Server-thread metadata only; clients never invoke native attack callbacks. */
    default JsonObject presentation(CombatContext context, ActionRegistry.Definition action) {
        JsonObject out = new JsonObject(); out.addProperty("backend", action.backend);
        if (action.backend.startsWith("combo:")) out.addProperty("stageId", action.backend.substring(6));
        return out;
    }
    default boolean canChain(CombatContext context, ActionRegistry.Definition action, JsonObject parameters) { return false; }
}
