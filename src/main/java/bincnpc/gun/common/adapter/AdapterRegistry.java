package bincnpc.gun.common.adapter;

import bincnpc.gun.CNPCGun;
import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.slashblade.action.ActionRegistry;
import bincnpc.gun.slashblade.adapter.BladeAdapter;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/** The descriptor is data; optional class loading happens only after the mod check. */
public final class AdapterRegistry {
    private static final Map<String, WeaponAdapter> ACTIVE = new LinkedHashMap<>();
    private static final Map<String, String> DISABLED = new LinkedHashMap<>();
    private static boolean ready;
    private AdapterRegistry() {}

    public static void initialize() {
        clear();
        try (InputStream in = AdapterRegistry.class.getResourceAsStream("/cnpcgun/adapters.json")) {
            if (in == null) throw new IllegalStateException("missing adapter descriptors");
            JsonArray descriptors = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement element : descriptors) {
                JsonObject d = element.getAsJsonObject();
                String id = d.get("id").getAsString();
                if (!ModList.get().isLoaded(d.get("mod").getAsString())) continue;
                WeaponAdapter adapter = null;
                try {
                    adapter = (WeaponAdapter)Class.forName(d.get("class").getAsString(), true,
                        AdapterRegistry.class.getClassLoader()).getDeclaredConstructor().newInstance();
                    ACTIVE.put(id, adapter);
                    if (adapter instanceof BladeAdapter blade) blade.registerActions(ActionRegistry.INSTANCE);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                    DISABLED.put(id, e.getClass().getSimpleName() + ": " + e.getMessage());
                    CNPCGun.LOGGER.warn("CNPC Gun adapter {} disabled: {}", id, DISABLED.get(id));
                    ActionRegistry.INSTANCE.removeAdapter(id);
                    if (adapter != null) close(adapter);
                }
            }
            ready = true;
        } catch (IOException | RuntimeException e) {
            CNPCGun.LOGGER.error("Cannot load CNPC Gun adapter descriptors", e);
        }
    }

    public static boolean ready() { return ready; }
    public static boolean loaded(String id) { return ACTIVE.containsKey(id) && !DISABLED.containsKey(id); }
    public static String[] ids() { return ACTIVE.keySet().stream().filter(AdapterRegistry::loaded).toArray(String[]::new); }
    public static JsonObject status() {
        JsonObject out = new JsonObject();
        ACTIVE.forEach((id, adapter) -> out.addProperty(id, loaded(id) ? "AVAILABLE" : DISABLED.get(id)));
        DISABLED.forEach(out::addProperty);
        return out;
    }
    public static WeaponAdapter find(ItemStack stack) {
        if (!ready || stack == null || stack.isEmpty()) return null;
        for (WeaponAdapter adapter : ACTIVE.values()) {
            if (!loaded(adapter.id())) continue;
            Boolean matches = query(adapter, () -> adapter.matches(stack), false);
            if (matches) return adapter;
        }
        return null;
    }
    public static <T> T query(WeaponAdapter adapter, Supplier<T> work, T fallback) {
        if (adapter == null || !loaded(adapter.id())) return fallback;
        try { T value = work.get(); return value == null ? fallback : value; }
        catch (RuntimeException | LinkageError e) {
            disable(adapter, e);
            return fallback;
        }
    }
    public static Result call(WeaponAdapter adapter, Supplier<Result> work) {
        return query(adapter, work, Result.fail("ADAPTER_UNAVAILABLE"));
    }
    public static void run(WeaponAdapter adapter, Runnable work) {
        query(adapter, () -> { work.run(); return true; }, false);
    }
    public static void cancel(WeaponAdapter adapter, bincnpc.gun.common.combat.CombatContext context) {
        if (adapter == null) return;
        try { adapter.cancel(context); }
        catch (RuntimeException | LinkageError e) { disable(adapter, e); }
    }
    private static void disable(WeaponAdapter adapter, Throwable error) {
        if (DISABLED.putIfAbsent(adapter.id(), error.getClass().getSimpleName() + ": " + error.getMessage()) == null) {
            CNPCGun.LOGGER.warn("CNPC Gun adapter {} failed and was disabled", adapter.id(), error);
            ActionRegistry.INSTANCE.removeAdapter(adapter.id());
            close(adapter);
        }
    }
    private static void close(WeaponAdapter adapter) {
        try { adapter.close(); }
        catch (RuntimeException | LinkageError e) { CNPCGun.LOGGER.warn("Cannot release CNPC Gun adapter {}", adapter.id(), e); }
    }
    public static void clear() {
        ready = false;
        ACTIVE.values().forEach(AdapterRegistry::close);
        ACTIVE.clear(); DISABLED.clear(); ActionRegistry.INSTANCE.clear();
    }
}
