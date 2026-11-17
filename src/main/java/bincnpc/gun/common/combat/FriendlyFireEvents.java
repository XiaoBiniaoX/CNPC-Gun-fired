package bincnpc.gun.common.combat;

import bincnpc.gun.CNPCGun;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CNPCGun.MOD_ID)
public final class FriendlyFireEvents {
    private FriendlyFireEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(LivingAttackEvent event) {
        if (FriendlyFire.blocksDamage(event.getSource(), event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hurt(LivingHurtEvent event) {
        if (FriendlyFire.blocksDamage(event.getSource(), event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void explosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide || event.getLevel().getServer() == null || !event.getLevel().getServer().isSameThread()) return;
        // CGM stores the shooter in the damage source; other native explosives expose their owner.
        Entity source = event.getExplosion().getExploder();
        event.getAffectedEntities().removeIf(victim -> FriendlyFire.isProtected(source, victim)
            || FriendlyFire.blocksDamage(event.getExplosion().getDamageSource(), victim));
    }
}
