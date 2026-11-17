package bincnpc.gun.compat.tacz;

import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatService;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.pojo.data.gun.ChargeData;
import com.tacz.guns.resource.pojo.data.gun.ChargeType;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;

/** One server-side trigger per NPC. Native shoot validates the charge and performs the shot and ammo cost. */
final class TaczCharging {
    private static final String KEY = "tacz.charge";
    private static final class State {
        final ItemStack weapon;
        final UUID target;
        final long revision, weaponEpoch;
        final FireMode mode;
        final ChargeData data;
        long tick;
        float progress;
        State(CombatContext ctx, FireMode mode, ChargeData data) {
            weapon = ctx.weapon; target = ctx.target.getUUID(); revision = ctx.config.revision;
            weaponEpoch = ctx.runtime.weaponEpoch; tick = ctx.now; this.mode = mode; this.data = data;
        }
    }
    private TaczCharging() {}
    static void cancel(CombatContext ctx) { ctx.runtime.adapterState.remove(KEY); }
    static boolean valid(ChargeData data) {
        return data != null && data.getChargeType() != null && Float.isFinite(data.getMaxCharge()) && data.getMaxCharge() > 0
            && Float.isFinite(data.getIncreasePerTick()) && data.getIncreasePerTick() > 0
            && Float.isFinite(data.getDecreasePerTick()) && data.getDecreasePerTick() >= 0
            && Float.isFinite(data.getDecreaseOnFire()) && data.getDecreaseOnFire() >= 0
            && Float.isFinite(data.getFireThreshold()) && data.getFireThreshold() >= 0 && data.getFireThreshold() <= data.getMaxCharge();
    }
    private static boolean allowed(CombatContext ctx) {
        return CombatService.serverThread(ctx.npc) && CombatService.validNpc(ctx.npc) && !ctx.runtime.editorPaused
            && ctx.config.bool("combat.enabled") && ctx.config.bool("gun.enabled") && !ctx.runtime.fireStopped
            && ctx.runtime.command.equals("AUTO") && ctx.validTarget()
            && ctx.weapon == ctx.npc.getMainHandItem() && !ctx.runtime.reloading
            && ctx.distance >= ctx.config.number("gun.minRange")
            && ctx.distance <= Math.min(ctx.config.number("gun.range"), ctx.config.number("combat.attackRange"))
            && (ctx.npc.getSensing().hasLineOfSight(ctx.target) || ctx.canFireWithoutSight());
    }
    private static boolean matches(State state, CombatContext ctx, FireMode mode, ChargeData data) {
        return state.weapon == ctx.weapon && state.target.equals(ctx.target.getUUID()) && state.revision == ctx.config.revision
            && state.weaponEpoch == ctx.runtime.weaponEpoch && state.mode == mode && state.data == data;
    }
    private static boolean nativeLocked(IGunOperator operator) {
        var reload = operator.getSynReloadState();
        return reload != null && reload.getStateType() != null && reload.getStateType().isReloading() || operator.getSynDrawCoolDown() > 0
            || operator.getSynIsBolting() || operator.getSynMeleeCoolDown() > 0 || operator.getSynSprintTime() > 0;
    }
    static void tick(CombatContext ctx) {
        if (!CombatService.serverThread(ctx.npc) || !(ctx.runtime.adapterState.get(KEY) instanceof State state)) return;
        if (!allowed(ctx) || !(ctx.weapon.getItem() instanceof IGun gun)) { cancel(ctx); return; }
        var nativeData = TimelessAPI.getCommonGunIndex(gun.getGunId(ctx.weapon)).map(index -> index.getGunData()).orElse(null);
        ChargeData data = nativeData == null ? null : nativeData.getChargeData(gun.getFireMode(ctx.weapon));
        IGunOperator operator = IGunOperator.fromLivingEntity(ctx.npc);
        if (!valid(data) || !matches(state, ctx, gun.getFireMode(ctx.weapon), data) || nativeLocked(operator)
                || ctx.now < state.tick || ctx.now - state.tick > 1) { cancel(ctx); return; }
        if (ctx.data.throwing.blocksAttack(ctx.now)) {
            if (ctx.data.throwing.pausesCharge()) state.tick = ctx.now;
            else cancel(ctx);
            return;
        }
        advance(state, ctx.now, operator);
    }
    private static void advance(State state, long now, IGunOperator operator) {
        if (now == state.tick) return;
        if (state.data.isChargeDuringCooldown() || operator.getSynShootCoolDown() < 50)
            state.progress = (float)Math.min(state.data.getMaxCharge(), (double)state.progress + state.data.getIncreasePerTick());
        else state.progress = Math.max(0, state.progress - state.data.getDecreasePerTick());
        state.tick = now;
    }
    static Float ready(CombatContext ctx, FireMode mode, ChargeData data, IGunOperator operator) {
        if (!allowed(ctx) || nativeLocked(operator)) { cancel(ctx); return null; }
        if (ctx.data.throwing.blocksAttack(ctx.now)) {
            if (!ctx.data.throwing.pausesCharge()) cancel(ctx);
            return null;
        }
        State state = ctx.runtime.adapterState.get(KEY) instanceof State previous ? previous : null;
        if (state == null || !matches(state, ctx, mode, data) || ctx.now < state.tick || ctx.now - state.tick > 1) {
            state = new State(ctx, mode, data); ctx.runtime.adapterState.put(KEY, state);
        }
        advance(state, ctx.now, operator);
        // HOLD releases at full charge; AUTO and DELAY also fire at the native maximum.
        return state.progress >= data.getMaxCharge() ? state.progress : null;
    }
    static void fired(CombatContext ctx, ChargeData data) {
        if (ctx.runtime.adapterState.get(KEY) instanceof State state && state.weapon == ctx.weapon && state.data == data)
            state.progress = data.getChargeType() == ChargeType.DELAY ? 0 : Math.max(0, state.progress - data.getDecreaseOnFire());
    }
}
