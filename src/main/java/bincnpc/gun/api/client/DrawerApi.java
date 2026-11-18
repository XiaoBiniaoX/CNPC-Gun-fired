package bincnpc.gun.api.client;

import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.relauncher.*;

/** Client-only editor extensions. Register once on the client thread during initialization. */
@SideOnly(Side.CLIENT)
public final class DrawerApi {
    public static final int VERSION = 2;
    public static final ResourceLocation INVENTORY = new ResourceLocation("cnpcgun", "inventory");
    public static final ResourceLocation GUN = new ResourceLocation("cnpcgun", "gun");
    public static final ResourceLocation BLADE = new ResourceLocation("cnpcgun", "blade");
    public static final class Drawer {
        private final ResourceLocation id;
        private final ITextComponent title;
        public Drawer(ResourceLocation id, ITextComponent title) { this.id = id; this.title = title; }
        public ResourceLocation id() { return id; }
        public ITextComponent title() { return title; }
    }
    public static final class Page {
        private final ResourceLocation id, drawer;
        private final ITextComponent title;
        private final Function<Context, GuiScreen> factory;
        private final boolean dialog;
        public Page(ResourceLocation id, ResourceLocation drawer, ITextComponent title, Function<Context, GuiScreen> factory, boolean dialog) {
            this.id = id; this.drawer = drawer; this.title = title; this.factory = factory; this.dialog = dialog;
        }
        public ResourceLocation id() { return id; }
        public ResourceLocation drawer() { return drawer; }
        public ITextComponent title() { return title; }
        public Function<Context, GuiScreen> factory() { return factory; }
        public boolean dialog() { return dialog; }
    }
    /** Entry-time summary only. closePage consults LeaveHandler; closeDialog directly pops the top dialog. */
    public static final class Context {
        private final UUID npcId;
        private final JsonObject snapshot;
        private final Consumer<GuiScreen> openDialog;
        private final Runnable closeDialog, closePage;
        public Context(UUID npcId, JsonObject snapshot, Consumer<GuiScreen> openDialog, Runnable closeDialog, Runnable closePage) {
            this.npcId = npcId; this.snapshot = bincnpc.gun.common.legacy.Legacy.copy(snapshot);
            this.openDialog = openDialog; this.closeDialog = closeDialog; this.closePage = closePage;
        }
        public UUID npcId() { return npcId; }
        public JsonObject snapshot() { return bincnpc.gun.common.legacy.Legacy.copy(snapshot); }
        public Consumer<GuiScreen> openDialog() { return openDialog; }
        public Runnable closeDialog() { return closeDialog; }
        public Runnable closePage() { return closePage; }
    }
    private static final Map<ResourceLocation, Drawer> DRAWERS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Page> PAGES = new LinkedHashMap<>();
    private DrawerApi() {}
    public static void registerDrawer(ResourceLocation id, ITextComponent title) {
        Objects.requireNonNull(id); Objects.requireNonNull(title);
        if (builtin(id) || DRAWERS.containsKey(id)) throw new IllegalArgumentException("duplicate drawer: " + id);
        DRAWERS.put(id, new Drawer(id, title.createCopy()));
    }
    /** Embed a normal GuiScreen using work-area-local dimensions, drawing and input coordinates. */
    public static void registerPage(ResourceLocation id, ResourceLocation drawer, ITextComponent title, Function<Context, GuiScreen> factory) {
        registerPage(id, drawer, title, factory, false);
    }
    /** Explicit full-editor modal; registerPage never opens a modal. */
    public static void registerDialogPage(ResourceLocation id, ResourceLocation drawer, ITextComponent title, Function<Context, GuiScreen> factory) {
        registerPage(id, drawer, title, factory, true);
    }
    private static void registerPage(ResourceLocation id, ResourceLocation drawer, ITextComponent title, Function<Context, GuiScreen> factory, boolean dialog) {
        Objects.requireNonNull(id); Objects.requireNonNull(drawer); Objects.requireNonNull(title); Objects.requireNonNull(factory);
        if (!builtin(drawer) && !DRAWERS.containsKey(drawer)) throw new IllegalArgumentException("unknown drawer: " + drawer);
        if (PAGES.containsKey(id)) throw new IllegalArgumentException("duplicate page: " + id);
        PAGES.put(id, new Page(id, drawer, title.createCopy(), factory, dialog));
    }
    private static boolean builtin(ResourceLocation id) { return INVENTORY.equals(id) || GUN.equals(id) || BLADE.equals(id); }
    public static List<Drawer> drawers() { return Collections.unmodifiableList(new ArrayList<>(DRAWERS.values())); }
    public static List<Page> pages(ResourceLocation drawer) {
        return Collections.unmodifiableList(PAGES.values().stream().filter(page -> page.drawer().equals(drawer)).collect(Collectors.toList()));
    }
}
