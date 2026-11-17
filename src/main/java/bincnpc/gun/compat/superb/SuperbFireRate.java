package bincnpc.gun.compat.superb;

import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.gun.controller.GunController;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;

/** Synchronous scope for one NPC trigger; native property queries have no shooter argument. */
public final class SuperbFireRate {
    private static final ThreadLocal<CombatContext> ACTIVE = new ThreadLocal<>();
    private SuperbFireRate() {}
    static Result fire(CombatContext ctx, Supplier<Result> action) {
        if (!CombatService.serverThread(ctx.npc) || !CombatService.validNpc(ctx.npc)) return Result.fail("PERMISSION_DENIED");
        CombatContext previous = ACTIVE.get();
        ACTIVE.set(ctx);
        try { return action.get(); }
        finally { if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous); }
    }
    public static Integer configuredRpm(ItemStack stack) {
        CombatContext ctx = ACTIVE.get();
        if (ctx == null || !CombatService.serverThread(ctx.npc) || !CombatService.validNpc(ctx.npc)
                || !(ctx.runtime.adapter instanceof SuperbAdapter) || ctx.runtime.editorPaused
                || !ctx.config.bool("combat.enabled") || !ctx.config.bool("gun.enabled") || !ctx.config.bool("gun.overrideNativeRate")
                || ctx.runtime.gunCaps == null || !ctx.runtime.gunCaps.canOverrideNativeRate || ctx.runtime.shotInterval <= 0
                || ctx.now < ctx.runtime.nextShot || stack != ctx.weapon || stack != ctx.npc.getMainHandItem()) return null;
        return Math.max(1, (int)Math.ceil(1200.0 / GunController.configuredNativeInterval(ctx)));
    }
}
