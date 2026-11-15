package bincnpc.gun.gun.switching;

import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.combat.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.wrapper.ItemStackWrapper;
import noppes.npcs.entity.EntityNPCInterface;

/** Six logical slots. The active slot is owned solely by CNPC's actual right hand. */
public final class Armory {
    public static final int SIZE = 6;
    private final ItemStack[] slots = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    public int active;

    public ItemStack get(EntityNPCInterface npc, int index) {
        if (index < 0 || index >= SIZE) return ItemStack.EMPTY;
        if (index != active) return slots[index];
        if (npc == null || npc.inventory == null) return ItemStack.EMPTY;
        ItemStack item = ItemStackWrapper.MCItem(npc.inventory.getRightHand());
        return item == null ? ItemStack.EMPTY : item;
    }

    public Result switchTo(CombatContext ctx, int index) {
        if (ctx == null || !CombatService.serverThread(ctx.npc) || !CombatService.validNpc(ctx.npc)) return Result.fail("INVALID_NPC");
        if (index < 0 || index >= SIZE) return Result.fail("INVALID_ARGUMENT");
        if (index == active) return Result.success();
        if (ctx.runtime.scheduler.busy(ctx.now) || ctx.data.throwing.blocksAttack(ctx.now)) return Result.fail("BUSY");
        if (ctx.now < ctx.runtime.nextSwitch) return Result.fail("COOLDOWN");
        ItemStack next = slots[index];
        if (next.isEmpty()) return Result.fail("NO_WEAPON");
        Result lock = ctx.runtime.scheduler.acquire("SWITCH", "slot:" + index, ctx.now, 4, false, false);
        if (!lock.ok) return lock;
        ItemStack current = get(ctx.npc, active);
        // No copying: ownership moves between the real hand and one bounded reserve slot.
        slots[active] = current;
        slots[index] = ItemStack.EMPTY;
        active = index;
        ctx.runtime.movement.meleeFallback = bincnpc.gun.common.adapter.AdapterRegistry.find(next) == null && CombatService.melee(next)
            && (ctx.runtime.adapter != null || ctx.runtime.movement.meleeFallback);
        ctx.npc.inventory.setRightHand(NpcAPI.Instance().getIItemStack(next));
        ctx.npc.updateAI = true; ctx.npc.updateClient = true;
        ctx.data.config.revision++;
        ctx.runtime.nextSwitch = ctx.now + ctx.config.integer("weapon.switchCooldown");
        ctx.runtime.fireStopped = false; ctx.runtime.noAmmoRetry = 0; ctx.runtime.command = "AUTO";
        return Result.success("slot:" + index);
    }

    public Result exchange(CombatContext ctx, ServerPlayer player, int slot, int inventorySlot) {
        if (ctx == null || player == null || !CombatService.serverThread(ctx.npc) || player.level() != ctx.npc.level()) return Result.fail("PERMISSION_DENIED");
        if (slot < 0 || slot >= SIZE || (inventorySlot < 0 || inventorySlot > 35) && inventorySlot != 40)
            return Result.fail("INVALID_ARGUMENT");
        if (ctx.runtime.scheduler.busy(ctx.now) || ctx.data.throwing.blocksAttack(ctx.now)) return Result.fail("BUSY");
        ItemStack incoming = player.getInventory().getItem(inventorySlot);
        if (incoming.is(noppes.npcs.CustomItems.wand)) return Result.fail("INVALID_ARGUMENT");
        if (slot == active) {
            Result lock = ctx.runtime.scheduler.acquire("SWITCH", "exchange", ctx.now, 4, false, false);
            if (!lock.ok) return lock;
        }
        ItemStack old = get(ctx.npc, slot);
        // Both endpoints are server-owned inventories; the client only selects indices.
        player.getInventory().setItem(inventorySlot, old);
        if (slot == active) ctx.npc.inventory.setRightHand(NpcAPI.Instance().getIItemStack(incoming));
        else slots[slot] = incoming;
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        ctx.npc.updateClient = true; ctx.npc.updateAI = true; ctx.config.revision++;
        ctx.runtime.noAmmoRetry = 0;
        return Result.success();
    }

    public Result unequip(CombatContext ctx, ServerPlayer player, int slot) {
        if (!CombatService.serverThread(ctx.npc) || slot < 0 || slot >= SIZE) return Result.fail("INVALID_ARGUMENT");
        if (get(ctx.npc, slot).isEmpty()) return Result.fail("NO_WEAPON");
        for (int i = 0; i < 36; i++) if (player.getInventory().getItem(i).isEmpty()) return exchange(ctx, player, slot, i);
        if (player.getInventory().getItem(40).isEmpty()) return exchange(ctx, player, slot, 40);
        return Result.fail("INVENTORY_FULL");
    }

    /** Creative editor replacement accepts only a stack resolved by the server catalogue. */
    public Result equipCreative(CombatContext ctx, ServerPlayer player, int slot, ItemStack canonical) {
        if (ctx == null || player == null || !CombatService.serverThread(ctx.npc) || player.level() != ctx.npc.level() || !player.isCreative()) return Result.fail("PERMISSION_DENIED");
        if (slot < 0 || slot >= SIZE || canonical == null || canonical.isEmpty() || canonical.is(noppes.npcs.CustomItems.wand)) return Result.fail("INVALID_ARGUMENT");
        if (ctx.runtime.scheduler.busy(ctx.now) || ctx.data.throwing.blocksAttack(ctx.now)) return Result.fail("BUSY");
        ItemStack incoming = canonical.copy(); incoming.setCount(1);
        ItemStack old = get(ctx.npc, slot);
        if (slot == active) ctx.npc.inventory.setRightHand(NpcAPI.Instance().getIItemStack(incoming));
        else slots[slot] = incoming;
        if (!old.isEmpty() && !player.getInventory().add(old)) player.drop(old, false);
        player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges();
        ctx.npc.updateClient = true; ctx.npc.updateAI = true; ctx.config.revision++;
        return Result.success();
    }

    /** Called after an edit or NPC reset, with native actions already cancelled. */
    public void restorePrimary(CombatContext ctx) {
        int preferred = ctx.config.integer("weapon.primarySlot");
        if (preferred == active || get(ctx.npc, preferred).isEmpty()) return;
        slots[active] = get(ctx.npc, active);
        ItemStack next = slots[preferred]; slots[preferred] = ItemStack.EMPTY; active = preferred;
        ctx.npc.inventory.setRightHand(NpcAPI.Instance().getIItemStack(next));
        ctx.npc.updateClient = true; ctx.npc.updateAI = true;
    }

    public int slotOf(EntityNPCInterface npc, ItemStack weapon) {
        for (int i = 0; i < SIZE; i++) if (get(npc, i) == weapon) return i;
        return active;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Active", active);
        ListTag list = new ListTag();
        for (int i = 0; i < SIZE; i++) if (i != active && !slots[i].isEmpty()) {
            CompoundTag item = slots[i].save(new CompoundTag());
            item.putInt("Slot", i); list.add(item);
        }
        tag.put("Items", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag == null) tag = new CompoundTag();
        active = Math.max(0, Math.min(SIZE - 1, tag.getInt("Active")));
        java.util.Arrays.fill(slots, ItemStack.EMPTY);
        for (Tag raw : tag.getList("Items", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag)raw;
            int i = item.getInt("Slot");
            if (i >= 0 && i < SIZE && i != active && slots[i].isEmpty()) slots[i] = ItemStack.of(item);
        }
    }
}
