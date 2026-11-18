package bincnpc.gun.client.gui.editor;

import bincnpc.gun.api.client.LeaveHandler;
import bincnpc.gun.client.gui.legacy.GuiCompat.*;
import bincnpc.gun.client.gui.legacy.EmbeddedInput;
import bincnpc.gun.mixin.client.GuiScreenAccess;
import java.io.IOException;
import net.minecraft.client.gui.GuiScreen;

/** Wrap a native 1.12 GuiScreen while keeping the editor as Minecraft.currentScreen. */
public final class NativePage extends Screen implements LeaveHandler {
    private final GuiScreen page;
    private final Runnable close;
    private int key;
    private NativePage(GuiScreen page, Runnable close) { super(Component.empty()); this.page = page; this.close = close; }
    public static Screen wrap(GuiScreen page, Runnable close) { return page instanceof Screen ? (Screen)page : new NativePage(page, close); }
    @Override protected void init() { page.setWorldAndResolution(minecraft, width, height); }
    @Override public void render(GuiGraphics g, int x, int y, float partial) { g.prepare2D(); page.drawScreen(x, y, partial); g.prepare2D(); }
    @Override public void tick() { page.updateScreen(); }
    @Override public void removed() { page.onGuiClosed(); }
    @Override public void requestLeave(Runnable after) { if (page instanceof LeaveHandler) ((LeaveHandler)page).requestLeave(after); else after.run(); }
    @Override public void setDragging(boolean dragging) {
        super.setDragging(dragging);
        if (!dragging) ((GuiScreenAccess)page).cnpcgun$button(-1);
    }
    private boolean mouse(double x, double y, boolean wheel) {
        EmbeddedInput.screen = page; EmbeddedInput.x = (int)x; EmbeddedInput.y = (int)y; EmbeddedInput.wheel = wheel;
        try { page.handleMouseInput(); }
        catch (IOException failed) { throw new IllegalStateException("addon page input", failed); }
        finally { EmbeddedInput.screen = null; EmbeddedInput.wheel = false; }
        return true;
    }
    @Override public boolean mouseClicked(double x, double y, int button) { return mouse(x, y, false); }
    @Override public boolean mouseReleased(double x, double y, int button) { return mouse(x, y, false); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return mouse(x, y, false); }
    @Override public boolean mouseScrolled(double x, double y, double delta) { return mouse(x, y, true); }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == Keys.KEY_ESCAPE) { close.run(); return true; }
        this.key = key; return false;
    }
    @Override public boolean charTyped(char letter, int modifiers) {
        try { ((GuiScreenAccess)page).cnpcgun$key(letter, key); }
        catch (IOException failed) { throw new IllegalStateException("addon page keyboard", failed); }
        return true;
    }
}
