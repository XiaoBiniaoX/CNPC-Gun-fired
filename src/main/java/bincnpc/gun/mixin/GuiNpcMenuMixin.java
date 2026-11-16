package bincnpc.gun.mixin;

import bincnpc.gun.client.gui.GuiWeapons;
import bincnpc.gun.client.gui.editor.MenuTabLayout;
import bincnpc.gun.client.gui.editor.WeaponTab;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import noppes.npcs.client.gui.util.GuiNpcMenu;
import noppes.npcs.client.gui.util.GuiMenuTopButton;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Resolve addon tab positions before drawing and clicking, after all init callbacks. */
@Mixin(value = GuiNpcMenu.class, remap = false, priority = 900)
public abstract class GuiNpcMenuMixin {
    @Shadow private GuiScreen parent;
    @Shadow private GuiMenuTopButton[] topButtons;
    @Shadow private EntityNPCInterface npc;
    @Unique private int cnpcgun$left, cnpcgun$top, cnpcgun$width;

    @Inject(method = "initGui", at = @At("TAIL"))
    private void cnpcgun$tab(int left, int top, int width, CallbackInfo ci) {
        cnpcgun$left = left; cnpcgun$top = top; cnpcgun$width = width;
        cnpcgun$ensureLayout();
    }
    @Inject(method = "drawElements", at = @At("HEAD"))
    private void cnpcgun$draw(FontRenderer font, int x, int y, Minecraft minecraft, float partialTick, CallbackInfo ci) {
        cnpcgun$ensureLayout();
    }
    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void cnpcgun$click(int x, int y, int button, CallbackInfo ci) {
        cnpcgun$ensureLayout();
        for (GuiMenuTopButton tab : topButtons)
            tab.hover = x >= tab.x && y >= tab.y && x < tab.x + tab.getWidth() && y < tab.y + (tab.active ? 20 : 18);
    }
    @Unique private void cnpcgun$ensureLayout() {
        if (cnpcgun$width <= 0 || topButtons.length == 0) return;
        boolean present = false;
        for (GuiMenuTopButton button : topButtons) if (button.field_146127_k == GuiWeapons.TAB_ID) present = true;
        if (!present) {
            GuiMenuTopButton[] extended = Arrays.copyOf(topButtons, topButtons.length + 1);
            extended[topButtons.length] = WeaponTab.create(parent, npc, cnpcgun$left + 4, cnpcgun$top - 17);
            topButtons = extended;
        }
        List<MenuTabLayout.Tab> tabs = new ArrayList<>();
        for (GuiMenuTopButton button : topButtons) tabs.add(new MenuTabLayout.Tab(button.field_146127_k, button.getWidth()));
        for (MenuTabLayout.Position position : MenuTabLayout.arrange(tabs, GuiWeapons.TAB_ID, cnpcgun$left, cnpcgun$top, cnpcgun$width))
            for (GuiMenuTopButton button : topButtons) if (button.field_146127_k == position.id()) { button.x = position.x(); button.y = position.y(); }
    }
}
