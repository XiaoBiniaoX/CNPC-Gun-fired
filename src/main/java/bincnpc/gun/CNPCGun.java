package bincnpc.gun;

import bincnpc.gun.common.adapter.AdapterRegistry;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.common.network.GunNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;
import bincnpc.gun.common.combat.CombatContext;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import noppes.npcs.entity.EntityNPCInterface;

@Mod(CNPCGun.MOD_ID)
public final class CNPCGun {
    public static final String MOD_ID = "cnpcgun";
    public static final String VERSION = "1.0.0";
    public static final String API_VERSION = "1";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CNPCGun() {
        GunNetwork.init();
        MinecraftForge.EVENT_BUS.addListener(this::serverStarted);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::hurt);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, this::death);
        MinecraftForge.EVENT_BUS.addListener(this::serverStopping);
        MinecraftForge.EVENT_BUS.addListener(this::logout);
        MinecraftForge.EVENT_BUS.addListener(this::tracking);
        MinecraftForge.EVENT_BUS.addListener(this::serverTick);
    }

    private void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) GunNetwork.forget(player);
    }
    private void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof EntityNPCInterface npc && CombatService.validNpc(npc)) {
            CombatContext ctx = new CombatContext(npc);
            GunNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new GunNetwork.Animation(npc.getId(), npc.getUUID(), ctx.runtime.domain, "STATE", "", 25, ctx.runtime.aiming));
        }
    }
    private void serverStarted(ServerStartedEvent event) {
        AdapterRegistry.initialize();
        GunNetwork.startWorker();
        bincnpc.gun.common.combat.CombatDecisions.start();
        bincnpc.gun.throwing.ThrowPlanner.start();
        LOGGER.info("CNPC Gun {} initialized; adapters: {}", VERSION, AdapterRegistry.ids());
    }

    private void serverStopping(ServerStoppingEvent event) {
        for (var level : event.getServer().getAllLevels()) for (var entity : level.getAllEntities()) if (entity instanceof EntityNPCInterface npc) {
            CombatService.data(npc).throwing.cancel(); bincnpc.gun.common.combat.CombatMovement.release(new CombatContext(npc));
        }
        bincnpc.gun.throwing.ThrowFlights.stop(event.getServer());
        bincnpc.gun.throwing.ThrowPlanner.stop();
        bincnpc.gun.common.combat.CombatDecisions.stop();
        GunNetwork.stopWorker();
        AdapterRegistry.clear();
    }

    private void tick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof EntityNPCInterface npc && !npc.level().isClientSide) {
            CombatService.tick(npc);
        }
    }
    private void serverTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.START) bincnpc.gun.throwing.CollisionSnapshot.beginTick();
        else {
            var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) bincnpc.gun.throwing.ThrowFlights.tick(server);
        }
    }

    private void hurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof EntityNPCInterface npc && !npc.level().isClientSide) {
            CombatService.data(npc).runtime.lastHurt = npc.level().getGameTime();
        }
        // Native projectiles and area attacks still respect CNPC factions and ownership.
        if (event.getSource().getEntity() instanceof EntityNPCInterface owner
                && (CombatService.isControlled(owner) || bincnpc.gun.throwing.ThrowFlights.owned(event.getSource().getDirectEntity(), owner))
                && !CombatService.validTarget(owner, event.getEntity())) {
            event.setCanceled(true);
        }
    }
    private void death(LivingDeathEvent event) {
        if (event.isCanceled() || event.getEntity().level().isClientSide) return;
        if (event.getEntity() instanceof EntityNPCInterface npc) {
            var data = CombatService.data(npc); data.runtime.restoreSupplies = true; data.throwing.cancel(); data.throwItems.died();
        }
        if (event.getSource().getEntity() instanceof EntityNPCInterface owner) CombatService.rewardKill(owner, event.getEntity());
    }
}
