package bincnpc.gun.slashblade.action;

import bincnpc.gun.common.api.Views;
import bincnpc.gun.common.config.Parameter;
import java.util.*;

public final class ActionRegistry {
    public static final ActionRegistry INSTANCE = new ActionRegistry();
    public static final class Definition {
        public final Views.ActionInfo info;
        public final String adapterId, backend;
        public final Set<String> tags;
        public Definition(Views.ActionInfo info, String adapterId, String backend, String... tags) {
            this.info = info; this.adapterId = adapterId; this.backend = backend; this.tags = Set.of(tags);
        }
    }
    private final Map<String, Definition> actions = new LinkedHashMap<>();
    public void register(Definition action) {
        if (!action.info.id.matches("slashblade:[a-z0-9_.-]+/[a-z0-9_./-]+")) throw new IllegalArgumentException("invalid action ID");
        if (actions.putIfAbsent(action.info.id, action) != null) throw new IllegalArgumentException("duplicate action ID");
    }
    public Definition get(String id) { return actions.get(id); }
    public Map<String, Definition> snapshot() { return Collections.unmodifiableMap(new LinkedHashMap<>(actions)); }
    public Definition[] all() { return actions.values().toArray(Definition[]::new); }
    public void removeAdapter(String id) { actions.values().removeIf(d -> d.adapterId.equals(id)); }
    public void clear() { actions.clear(); }
}
