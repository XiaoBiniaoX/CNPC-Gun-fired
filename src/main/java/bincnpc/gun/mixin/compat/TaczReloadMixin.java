package bincnpc.gun.mixin.compat;

import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.compat.tacz.TaczAdapter;
import bincnpc.gun.gun.controller.ReloadTiming;
import com.tacz.guns.entity.shooter.LivingEntityReload;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.api.entity.ReloadState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = LivingEntityReload.class, remap = false)
public abstract class TaczReloadMixin {
    @Shadow @Final private LivingEntity shooter;
    @Shadow @Final private ShooterDataHolder data;
    @Unique private ReloadTiming.Clock cnpcgun$clock;
    @Unique private ItemStack cnpcgun$stack;
    @Unique private long cnpcgun$weaponEpoch;
    @Unique private double cnpcgun$speed;

    @Inject(method = "reload", at = @At("RETURN"))
    private void cnpcgun$beginReload(CallbackInfo ci) {
        cnpcgun$clock = null; cnpcgun$stack = null;
        if (!(shooter instanceof EntityNPCInterface npc) || !CombatService.serverThread(npc) || !CombatService.validNpc(npc)
                || data.reloadTimestamp == -1 || data.currentGunItem == null) return;
        CombatContext ctx = new CombatContext(npc);
        if (!(ctx.runtime.adapter instanceof TaczAdapter) || ctx.runtime.reloadTargetTicks <= 0 || ctx.runtime.editorPaused) return;
        Object nativeTicks = ctx.runtime.adapterState.get("tacz.reloadNativeTicks");
        if (!(nativeTicks instanceof Number number)) return;
        cnpcgun$stack = data.currentGunItem.get();
        if (cnpcgun$stack == null || cnpcgun$stack != ctx.weapon) { cnpcgun$stack = null; return; }
        cnpcgun$weaponEpoch = ctx.runtime.weaponEpoch;
        cnpcgun$speed = ReloadTiming.speed(number.doubleValue(), ctx.runtime.reloadTargetTicks);
        cnpcgun$clock = new ReloadTiming.Clock(ctx.now, System.currentTimeMillis());
    }
    @Inject(method = "tickReloadState", at = @At("HEAD"))
    private void cnpcgun$advanceReload(CallbackInfoReturnable<ReloadState> cir) {
        if (cnpcgun$clock == null) return;
        if (!(shooter instanceof EntityNPCInterface npc) || !CombatService.serverThread(npc) || !CombatService.validNpc(npc)
                || data.reloadTimestamp == -1 || data.currentGunItem == null || data.currentGunItem.get() != cnpcgun$stack) {
            cnpcgun$clock = null; cnpcgun$stack = null; return;
        }
        CombatContext ctx = new CombatContext(npc);
        if (!(ctx.runtime.adapter instanceof TaczAdapter) || ctx.runtime.weaponEpoch != cnpcgun$weaponEpoch || ctx.runtime.editorPaused) {
            cnpcgun$clock = null; cnpcgun$stack = null; return;
        }
        data.reloadTimestamp = cnpcgun$clock.adjust(data.reloadTimestamp, ctx.now, System.currentTimeMillis(), cnpcgun$speed);
    }
}
