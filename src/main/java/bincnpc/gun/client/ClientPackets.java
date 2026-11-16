package bincnpc.gun.client;

import bincnpc.gun.client.gui.GuiWeapons;
import bincnpc.gun.common.network.GunNetwork;
import net.minecraft.client.Minecraft;

public final class ClientPackets {
    private ClientPackets() {}
    public static void receive(GunNetwork.Response packet) {
Object cnpcgun$value0; GuiWeapons cnpcgun$match0;

        if (((cnpcgun$value0 = Minecraft.getMinecraft().currentScreen) instanceof GuiWeapons && (cnpcgun$match0 = (GuiWeapons)cnpcgun$value0) != null)) cnpcgun$match0.receive(packet);
    }
}
