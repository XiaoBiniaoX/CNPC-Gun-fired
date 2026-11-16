package bincnpc.gun.slashblade.adapter;

import bincnpc.gun.common.adapter.WeaponAdapter;
import bincnpc.gun.common.api.*;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.slashblade.action.ActionRegistry;
import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;

public interface BladeAdapter extends WeaponAdapter {
    public static final class Execution  {
private final String stage;
private final int duration;
public Execution(String stage, int duration) {
this.stage = stage; this.duration = duration;}
public String stage() { return stage; }
public int duration() { return duration; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof Execution))return false; Execution that=(Execution)value; return java.util.Objects.equals(stage, that.stage) && java.util.Objects.equals(duration, that.duration); }
@Override public int hashCode() { return java.util.Objects.hash(stage, duration); }

}
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
