package bincnpc.gun.compat.lr;

import bincnpc.gun.common.api.*;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.common.script.EventBridge;
import bincnpc.gun.gun.adapter.GunAdapter;
import me.xjqsh.lrtactical.item.ThrowableItem;
import me.xjqsh.lrtactical.api.item.IThrowable;
import me.xjqsh.lrtactical.capability.CustomItemCoolDownsProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class LrAdapter implements GunAdapter {
    private static final class State {
        boolean preparing;
        ItemStack stack;
        java.util.UUID target;
        long revision, weaponEpoch, expires;
        int prepareTicks;
    }
    private State state(CombatContext ctx) { return (State)ctx.runtime.adapterState.computeIfAbsent(id(), key -> new State()); }
    @Override public String id() { return "lrtactical"; }
    @Override public boolean matches(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof ThrowableItem item
            && item.getThrowableIndex(stack).filter(index -> index.getData() != null && index.getData().getEntityData() != null
                && index.getType() != null && index.getType().factory() != null).isPresent();
    }
    @Override public String weaponId(ItemStack stack) { return "lrtactical:" + ((IThrowable)stack.getItem()).getId(stack); }
    @Override public Views.GunCaps capabilities(ItemStack stack) {
        Views.GunCaps c = new Views.GunCaps(); c.canFire = true;
        c.supportsSingleShot = true; c.firingModes = new String[]{"NATIVE", "SINGLE"};
        c.canConsumeAmmo = true; c.projectileModel = "ENTITY_PROJECTILE";
        c.supportsSpecialMechanics = true; c.specialMechanics = new String[]{"PREPARE", "COOK", "REMOTE_DETONATION"};
        return c;
    }
    @Override public Views.AmmoState ammo(CombatContext ctx) {
        return new Views.AmmoState(false, null, null, false, null, false, false);
    }
    @Override public Result fire(CombatContext ctx) {
        if (!CombatService.serverThread(ctx.npc) || !CombatService.validNpc(ctx.npc) || ctx.runtime.editorPaused || !ctx.validTarget())
            return Result.fail("PERMISSION_DENIED");
        if (!matches(ctx.weapon)) return Result.fail("NO_COMPATIBLE_WEAPON");
        ThrowableItem item = (ThrowableItem)ctx.weapon.getItem();
        var index = item.getThrowableIndex(ctx.weapon).orElse(null);
        if (index == null || index.getData() == null) return Result.fail("NO_COMPATIBLE_WEAPON");
        State state = state(ctx);
        if (state.preparing) return Result.fail("BUSY");
        var category = index.getData().getCooldownCategory();
        boolean cooldown = category != null && ctx.npc.getCapability(CustomItemCoolDownsProvider.CAPABILITY)
            .map(cap -> cap.isOnCooldown(category)).orElse(false);
        if (cooldown) return Result.fail("COOLDOWN");
        if (ctx.weapon.isEmpty()) return Result.fail("NO_AMMO");
        int prepareTicks = item.getMaxUsingTick(ctx.weapon);
        if (prepareTicks < 0 || prepareTicks > 1200) return Result.fail("CONDITION_NOT_MET");
        ctx.npc.startUsingItem(InteractionHand.MAIN_HAND);
        if (!ctx.npc.isUsingItem() || ctx.npc.getUseItem() != ctx.weapon) return Result.fail("BUSY");
        state.preparing = true; state.stack = ctx.weapon;
        state.target = ctx.target.getUUID(); state.revision = ctx.config.revision; state.weaponEpoch = ctx.runtime.weaponEpoch;
        state.prepareTicks = prepareTicks; state.expires = ctx.now + prepareTicks + 4;
        ctx.runtime.scheduler.channel = "THROW"; ctx.runtime.scheduler.until = state.expires;
        ctx.runtime.scheduler.interruptible = false;
        return Result.success("PENDING");
    }
    @Override public void tick(CombatContext ctx) {
        if (!CombatService.serverThread(ctx.npc) || !(ctx.runtime.adapterState.get(id()) instanceof State state) || !state.preparing) return;
        if (!CombatService.validNpc(ctx.npc) || ctx.runtime.editorPaused || !ctx.config.bool("combat.enabled") || !ctx.config.bool("gun.enabled")
                || !ctx.runtime.command.equals("AUTO") || !ctx.validTarget() || !ctx.target.getUUID().equals(state.target)
                || ctx.config.revision != state.revision || ctx.runtime.weaponEpoch != state.weaponEpoch || ctx.now > state.expires
                || state.stack == null || !matches(state.stack) || ctx.npc.getMainHandItem() != state.stack || ctx.weapon != state.stack
                || !ctx.npc.isUsingItem() || ctx.npc.getUseItem() != state.stack
                || ctx.distance > ctx.gunRange()
                || ctx.distance < ctx.config.number("gun.minRange")
                || !ctx.npc.getSensing().hasLineOfSight(ctx.target) && !ctx.canFireWithoutSight()) { cancel(ctx); return; }
        ThrowableItem item = (ThrowableItem)state.stack.getItem();
        if (item.getMaxUsingTick(state.stack) != state.prepareTicks) { cancel(ctx); return; }
        if (ctx.npc.getTicksUsingItem() >= state.prepareTicks) {
            ItemStack released = state.stack; int count = released.getCount(); state.preparing = false;
            ctx.npc.releaseUsingItem(); // Native release -> onThrow -> native entity, cooldown and stack consumption.
            boolean fired = released.getCount() < count;
            if (fired && ctx.config.bool("ammo.infinite") && CombatService.validNpc(ctx.npc) && !ctx.runtime.editorPaused
                    && ctx.config.revision == state.revision && ctx.runtime.weaponEpoch == state.weaponEpoch
                    && (ctx.npc.getMainHandItem() == released || ctx.npc.getMainHandItem().isEmpty())) {
                released.setCount(count); ctx.npc.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, released);
            }
            state.stack = null; ctx.runtime.adapterState.remove(id(), state);
            if (ctx.runtime.scheduler.channel.equals("THROW")) ctx.runtime.scheduler.release();
            if (fired) {
                ctx.runtime.lastFired = ctx.now;
                EventBridge.emit(ctx, "gun.fired", "", Result.success());
                bincnpc.gun.common.network.GunNetwork.animation(ctx, "FIRE", 8);
            }
            ctx.npc.updateClient = true;
        }
    }
    @Override public Result reload(CombatContext ctx) { return Result.fail("UNSUPPORTED"); }
    @Override public void stopFire(CombatContext ctx) { cancel(ctx); }
    @Override public void cancel(CombatContext ctx) {
        if (!CombatService.serverThread(ctx.npc) || !(ctx.runtime.adapterState.remove(id()) instanceof State state)) return;
        state.preparing = false;
        if (state.stack != null && ctx.npc.isUsingItem() && ctx.npc.getUseItem() == state.stack) ctx.npc.stopUsingItem();
        state.stack = null;
        if (ctx.runtime.scheduler.channel.equals("THROW")) ctx.runtime.scheduler.release();
    }
}
