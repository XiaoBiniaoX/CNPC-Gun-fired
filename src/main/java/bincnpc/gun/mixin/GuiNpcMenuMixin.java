package bincnpc.gun.mixin;

import bincnpc.gun.client.gui.GuiWeapons;
import bincnpc.gun.client.gui.editor.MenuTabLayout;
import bincnpc.gun.client.gui.editor.WeaponTab;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.client.gui.util.GuiNpcMenu;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiMenuTopButton;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Final positioning happens after all addon init callbacks, for both rendering and hit testing. */
@Mixin(value = GuiNpcMenu.class, remap = false, priority = 900)
public abstract class GuiNpcMenuMixin {
    @Shadow private IGuiInterface parent;
    @Shadow private GuiMenuTopButton[] topButtons;
    @Shadow private EntityNPCInterface npc;
    @Unique private int cnpcgun$left, cnpcgun$top, cnpcgun$width;

    @Inject(method = "initGui", at = @At("TAIL"))
    private void cnpcgun$tab(int left, int top, int width, CallbackInfo ci) {
        cnpcgun$left = left; cnpcgun$top = top; cnpcgun$width = width;
        cnpcgun$ensureLayout();
    }
    @Inject(method = "drawElements", at = @At("HEAD"))
    private void cnpcgun$draw(GuiGraphics graphics, Font font, int x, int y, Minecraft minecraft, float partialTick, CallbackInfo ci) {
        cnpcgun$ensureLayout();
    }
    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void cnpcgun$click(double x, double y, int button, CallbackInfoReturnable<Boolean> ci) {
        cnpcgun$ensureLayout();
        for (GuiMenuTopButton tab : topButtons) tab.hover = x >= tab.getX() && y >= tab.getY() && x < tab.getX() + tab.getWidth() && y < tab.getY() + (tab.active ? 25 : 18);
    }
    @Unique private void cnpcgun$ensureLayout() {
        if (cnpcgun$width <= 0 || topButtons.length == 0) return;
        boolean present = false;
        for (GuiMenuTopButton button : topButtons) if (button.id == GuiWeapons.TAB_ID) present = true;
        if (!present) {
            GuiMenuTopButton[] extended = Arrays.copyOf(topButtons, topButtons.length + 1);
            extended[topButtons.length] = WeaponTab.create(parent, npc, cnpcgun$left + 4, cnpcgun$top - 17);
            topButtons = extended;
        }
        List<MenuTabLayout.Tab> tabs = new ArrayList<>();
        for (GuiMenuTopButton button : topButtons) tabs.add(new MenuTabLayout.Tab(button.id, button.getWidth()));
        for (MenuTabLayout.Position position : MenuTabLayout.arrange(tabs, GuiWeapons.TAB_ID, cnpcgun$left, cnpcgun$top, cnpcgun$width))
            for (GuiMenuTopButton button : topButtons) if (button.id == position.id()) { button.setX(position.x()); button.setY(position.y()); }
    }
}
