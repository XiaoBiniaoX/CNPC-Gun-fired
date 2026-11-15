package bincnpc.gun.compat.lr;

import bincnpc.gun.common.api.*;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.script.EventBridge;
import bincnpc.gun.gun.adapter.GunAdapter;
import me.xjqsh.lrtactical.item.ThrowableItem;
import me.xjqsh.lrtactical.api.item.IThrowable;
import me.xjqsh.lrtactical.capability.CustomItemCoolDownsProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class LrAdapter implements GunAdapter {
    private static final class State { boolean preparing; ItemStack stack; }
    private State state(CombatContext ctx) { return (State)ctx.runtime.adapterState.computeIfAbsent(id(), key -> new State()); }
    @Override public String id() { return "lrtactical"; }
    @Override public boolean matches(ItemStack stack) {
        return stack.getItem() instanceof ThrowableItem item && item.getThrowableIndex(stack).isPresent();
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
        ThrowableItem item = (ThrowableItem)ctx.weapon.getItem();
        var index = item.getThrowableIndex(ctx.weapon).orElseThrow();
        State state = state(ctx);
        if (state.preparing) return Result.fail("BUSY");
        boolean cooldown = ctx.npc.getCapability(CustomItemCoolDownsProvider.CAPABILITY)
            .map(cap -> cap.isOnCooldown(index.getData().getCooldownCategory())).orElse(false);
        if (cooldown) return Result.fail("COOLDOWN");
        if (ctx.weapon.isEmpty()) return Result.fail("NO_AMMO");
        ctx.npc.startUsingItem(InteractionHand.MAIN_HAND);
        state.preparing = true; state.stack = ctx.weapon;
        ctx.runtime.scheduler.channel = "THROW"; ctx.runtime.scheduler.until = ctx.now + item.getMaxUsingTick(ctx.weapon) + 4;
        ctx.runtime.scheduler.interruptible = false;
        return Result.success("PENDING");
    }
    @Override public void tick(CombatContext ctx) {
        State state = state(ctx);
        if (!state.preparing) return;
        ThrowableItem item = (ThrowableItem)state.stack.getItem();
        if (ctx.npc.getMainHandItem() != state.stack || !ctx.npc.isUsingItem()) { state.preparing = false; ctx.runtime.scheduler.release(); return; }
        if (ctx.npc.getTicksUsingItem() >= item.getMaxUsingTick(state.stack)) {
            int count = state.stack.getCount();
            ctx.npc.releaseUsingItem(); // Native release -> onThrow -> native entity, cooldown and stack consumption.
            if (ctx.config.bool("ammo.infinite")) {
                state.stack.setCount(count); ctx.npc.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, state.stack);
            }
            state.preparing = false; ctx.runtime.scheduler.release();
            if (state.stack.getCount() < count || ctx.npc.getMainHandItem() != state.stack) {
                EventBridge.emit(ctx, "gun.fired", "", Result.success());
                bincnpc.gun.common.network.GunNetwork.animation(ctx, "FIRE", 8);
            }
            ctx.npc.updateClient = true;
        }
    }
    @Override public Result reload(CombatContext ctx) { return Result.fail("UNSUPPORTED"); }
    @Override public void cancel(CombatContext ctx) { ctx.npc.stopUsingItem(); }
}
