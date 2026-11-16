package bincnpc.gun.common;

import bincnpc.gun.common.network.GunNetwork;

/** Dedicated-server side never references Minecraft client classes. */
public class CommonProxy {
    public void init() {}
    public void receive(GunNetwork.Response packet) {}
    public void receive(GunNetwork.Animation packet) {}
}
