package bincnpc.gun.mixin.compat;

import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.gun.controller.FireTiming;
import bincnpc.gun.compat.tacz.TaczAdapter;
import com.tacz.guns.entity.shooter.LivingEntityShoot;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only replaces managed NPC shot cooldown; native ammo, charge, bolt, reload and network checks remain. */
@Mixin(value = LivingEntityShoot.class, remap = false)
public abstract class TaczFireRateMixin {
    @Shadow @Final private LivingEntity shooter;
    @Shadow @Final private ShooterDataHolder data;

    @Inject(method = "getShootCoolDown(J)J", at = @At("HEAD"), cancellable = true)
    private void cnpcgun$configuredShotCooldown(long timestamp, CallbackInfoReturnable<Long> cir) {
        if (!(shooter instanceof EntityNPCInterface npc) || !CombatService.serverThread(npc) || !CombatService.validNpc(npc)
                || data.currentGunItem == null) return;
        ItemStack current = data.currentGunItem.get();
        CombatContext ctx = TaczAdapter.rateContext(shooter, current);
        if (ctx != null) cir.setReturnValue(FireTiming.remainingMillis(ctx.runtime.nextShot, ctx.now));
    }
}
