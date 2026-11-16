package bincnpc.gun;

import bincnpc.gun.common.adapter.AdapterRegistry;
import bincnpc.gun.common.combat.*;
import bincnpc.gun.common.network.GunNetwork;
import bincnpc.gun.common.npc.NpcData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import noppes.npcs.entity.EntityNPCInterface;

@Mod(modid = CNPCGun.MOD_ID, name = "CNPC-Gun for 1.12.2 V1.0.0", version = CNPCGun.VERSION,
    acceptedMinecraftVersions = "[1.12.2]", acceptableRemoteVersions = "[1.0.0]",
    dependencies = "required-after:customnpcs;required-after:cnpcplus@[4.0.5,);after:cnpcmagic;after:npcpathfindinglegacy;after:tacz;after:cgm;after:techguns;after:flansmod;after:flammpfeil.slashblade")
public final class CNPCGun {
    public static final String MOD_ID = "cnpcgun", VERSION = "1.0.0", API_VERSION = "1";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    @SidedProxy(clientSide = "bincnpc.gun.client.ClientProxy", serverSide = "bincnpc.gun.common.CommonProxy")
    public static bincnpc.gun.common.CommonProxy proxy;

    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        GunNetwork.init();
        MinecraftForge.EVENT_BUS.register(this);
    }
    @Mod.EventHandler public void postInit(FMLPostInitializationEvent event) { proxy.init(); }
    @Mod.EventHandler public void serverStarted(FMLServerStartedEvent event) {
        AdapterRegistry.initialize();
        GunNetwork.startWorker();
        CombatDecisions.start();
        bincnpc.gun.throwing.ThrowPlanner.start();
        LOGGER.info("CNPC Gun {} initialized; adapters: {}", VERSION, AdapterRegistry.ids());
    }
    @Mod.EventHandler public void serverStopping(FMLServerStoppingEvent event) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null) {
            for (WorldServer world : server.worlds) for (Entity entity : world.loadedEntityList) if (entity instanceof EntityNPCInterface) {
                EntityNPCInterface npc = (EntityNPCInterface)entity;
                CombatService.data(npc).throwing.cancel();
                CombatMovement.release(new CombatContext(npc));
            }
            bincnpc.gun.throwing.ThrowFlights.stop(server);
        }
        bincnpc.gun.throwing.ThrowPlanner.stop();
        CombatDecisions.stop();
        GunNetwork.stopWorker();
        AdapterRegistry.clear();
        bincnpc.gun.throwing.ThrowAdapters.clear();
        bincnpc.gun.common.persistence.GunPersistence.clear();
    }
    @SubscribeEvent public void logout(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) GunNetwork.forget((EntityPlayerMP)event.player);
    }
    @SubscribeEvent public void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntityPlayer() instanceof EntityPlayerMP && event.getTarget() instanceof EntityNPCInterface) {
            EntityNPCInterface npc = (EntityNPCInterface)event.getTarget();
            if (CombatService.validNpc(npc)) {
                CombatContext ctx = new CombatContext(npc);
                GunNetwork.CHANNEL.sendTo(new GunNetwork.Animation(npc.getEntityId(), npc.getUniqueID(),
                    ctx.runtime.domain, "STATE", "", 25, ctx.runtime.aiming), (EntityPlayerMP)event.getEntityPlayer());
            }
        }
    }
    @SubscribeEvent public void tick(LivingEvent.LivingUpdateEvent event) {
        if (event.getEntityLiving() instanceof EntityNPCInterface && !event.getEntityLiving().world.isRemote)
            CombatService.tick((EntityNPCInterface)event.getEntityLiving());
    }
    @SubscribeEvent public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) bincnpc.gun.throwing.CollisionSnapshot.beginTick();
        else {
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server != null) bincnpc.gun.throwing.ThrowFlights.tick(server);
        }
    }
    @SubscribeEvent public void hurt(LivingHurtEvent event) {
        if (event.getEntityLiving() instanceof EntityNPCInterface && !event.getEntityLiving().world.isRemote)
            CombatService.data((EntityNPCInterface)event.getEntityLiving()).runtime.lastHurt = event.getEntityLiving().world.getTotalWorldTime();
        if (event.getSource().getTrueSource() instanceof EntityNPCInterface) {
            EntityNPCInterface shooter = (EntityNPCInterface)event.getSource().getTrueSource();
            if ((CombatService.isControlled(shooter) || bincnpc.gun.throwing.ThrowFlights.owned(event.getSource().getImmediateSource(), shooter))
                    && !CombatService.validTarget(shooter, event.getEntityLiving())) event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void death(LivingDeathEvent event) {
        if (event.isCanceled() || event.getEntityLiving().world.isRemote) return;
        if (event.getEntityLiving() instanceof EntityNPCInterface) {
            NpcData data = CombatService.data((EntityNPCInterface)event.getEntityLiving());
            data.runtime.restoreSupplies = true; data.throwing.cancel(); data.throwItems.died();
        }
        if (event.getSource().getTrueSource() instanceof EntityNPCInterface)
            CombatService.rewardKill((EntityNPCInterface)event.getSource().getTrueSource(), event.getEntityLiving());
    }
}
