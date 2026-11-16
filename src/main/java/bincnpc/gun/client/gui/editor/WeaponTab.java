package bincnpc.gun.client.gui.editor;

import bincnpc.gun.client.gui.GuiWeapons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import noppes.npcs.client.gui.util.*;
import noppes.npcs.entity.EntityNPCInterface;

public final class WeaponTab {
    private WeaponTab() {}
    public static GuiMenuTopButton create(GuiScreen parent, EntityNPCInterface npc, int x, int y) {
        GuiMenuTopButton tab = new GuiMenuTopButton(GuiWeapons.TAB_ID, x, y, "cnpcgun.gui.tab");
        tab.listener = ignored -> {
            GuiNpcTextField.unfocus();
            if (parent instanceof GuiContainerNPCInterface2) ((GuiContainerNPCInterface2)parent).save();
            else if (parent instanceof GuiNPCInterface2) ((GuiNPCInterface2)parent).save();
            Minecraft.getMinecraft().displayGuiScreen(new GuiWeapons(npc));
        };
        return tab;
    }
}
