package bincnpc.gun.api.client;

import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Client-only editor extensions. Register once on the client thread during client setup. */
public final class DrawerApi {
    public static final int VERSION = 2;
    public static final ResourceLocation INVENTORY = new ResourceLocation("cnpcgun", "inventory");
    public static final ResourceLocation GUN = new ResourceLocation("cnpcgun", "gun");
    public static final ResourceLocation BLADE = new ResourceLocation("cnpcgun", "blade");
    public record Drawer(ResourceLocation id, Component title) {}
    public record Page(ResourceLocation id, ResourceLocation drawer, Component title, Function<Context, Screen> factory, boolean dialog) {}
    /** Entry-time summary only. closePage leaves the content page; closeDialog pops only the top dialog. */
    public record Context(UUID npcId, JsonObject snapshot, Consumer<Screen> openDialog, Runnable closeDialog, Runnable closePage) {
        public Context { snapshot = snapshot.deepCopy(); }
        @Override public JsonObject snapshot() { return snapshot.deepCopy(); }
    }
    private static final Map<ResourceLocation, Drawer> DRAWERS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Page> PAGES = new LinkedHashMap<>();
    private DrawerApi() {}
    public static void registerDrawer(ResourceLocation id, Component title) {
        Objects.requireNonNull(id); Objects.requireNonNull(title);
        if (builtin(id) || DRAWERS.containsKey(id)) throw new IllegalArgumentException("duplicate drawer: " + id);
        DRAWERS.put(id, new Drawer(id, title.copy()));
    }
    /** Embed a normal Screen using work-area-local dimensions, drawing and input coordinates. */
    public static void registerPage(ResourceLocation id, ResourceLocation drawer, Component title, Function<Context, Screen> factory) {
        registerPage(id, drawer, title, factory, false);
    }
    /** Explicit full-editor modal; registerPage never opens a modal. */
    public static void registerDialogPage(ResourceLocation id, ResourceLocation drawer, Component title, Function<Context, Screen> factory) {
        registerPage(id, drawer, title, factory, true);
    }
    private static void registerPage(ResourceLocation id, ResourceLocation drawer, Component title, Function<Context, Screen> factory, boolean dialog) {
        Objects.requireNonNull(id); Objects.requireNonNull(drawer); Objects.requireNonNull(title); Objects.requireNonNull(factory);
        if (!builtin(drawer) && !DRAWERS.containsKey(drawer)) throw new IllegalArgumentException("unknown drawer: " + drawer);
        if (PAGES.containsKey(id)) throw new IllegalArgumentException("duplicate page: " + id);
        PAGES.put(id, new Page(id, drawer, title.copy(), factory, dialog));
    }
    private static boolean builtin(ResourceLocation id) { return INVENTORY.equals(id) || GUN.equals(id) || BLADE.equals(id); }
    public static List<Drawer> drawers() { return List.copyOf(DRAWERS.values()); }
    public static List<Page> pages(ResourceLocation drawer) { return PAGES.values().stream().filter(page -> page.drawer().equals(drawer)).toList(); }
}
