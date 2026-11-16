package bincnpc.gun.client;

import bincnpc.gun.common.CommonProxy;
import bincnpc.gun.common.network.GunNetwork;
import bincnpc.gun.client.animation.NpcAnimations;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;

public final class ClientProxy extends CommonProxy {
    @Override public void init(){
        bincnpc.gun.common.adapter.AdapterRegistry.initialize();
        MinecraftForge.EVENT_BUS.register(NpcAnimations.INSTANCE);
        bincnpc.gun.client.animation.BipedAnimationHooks.bind(NpcAnimations.INSTANCE);
        if(Loader.isModLoaded("flammpfeil.slashblade"))try{
            Class.forName("bincnpc.gun.client.animation.BladeNativeAnimations").getMethod("install").invoke(null);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError error){
            bincnpc.gun.CNPCGun.LOGGER.warn("Optional NPC blade renderer unavailable",error);
        }
    }
    @Override public void receive(GunNetwork.Response packet){
        Minecraft.getMinecraft().addScheduledTask(()->ClientPackets.receive(packet));
    }
    @Override public void receive(GunNetwork.Animation packet){
        Minecraft.getMinecraft().addScheduledTask(()->NpcAnimations.receive(packet));
    }
}
