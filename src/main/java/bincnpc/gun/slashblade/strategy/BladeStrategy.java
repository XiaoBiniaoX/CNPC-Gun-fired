package bincnpc.gun.slashblade.strategy;

import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.slashblade.action.ActionRegistry;
import com.google.gson.JsonObject;

public interface BladeStrategy {
    public static final class Decision  {
private final String action;
private final JsonObject parameters;
private final int delay;
public Decision(String action, JsonObject parameters, int delay) {
this.action = action; this.parameters = parameters; this.delay = delay;}
public String action() { return action; }
public JsonObject parameters() { return parameters; }
public int delay() { return delay; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof Decision))return false; Decision that=(Decision)value; return java.util.Objects.equals(action, that.action) && java.util.Objects.equals(parameters, that.parameters) && java.util.Objects.equals(delay, that.delay); }
@Override public int hashCode() { return java.util.Objects.hash(action, parameters, delay); }

}
    Decision decide(CombatContext context);
}
