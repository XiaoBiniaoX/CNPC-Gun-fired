package bincnpc.gun.common.network;

import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.gun.switching.Armory;
import bincnpc.gun.throwing.ThrowAdapters;
import bincnpc.gun.throwing.ThrowInventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Wire indices 0..5 are weapons, 6..8 are throwables; automatic weapon selection still has only six slots. */
public final class Equipment {
    public static final int SIZE = Armory.SIZE + ThrowInventory.SIZE;
    private Equipment() {}
    public static ItemStack[] empty() { ItemStack[] items = new ItemStack[SIZE]; java.util.Arrays.fill(items, ItemStack.EMPTY); return items; }
    public static ItemStack get(CombatContext ctx, int slot) { return ctx == null || slot < 0 || slot >= SIZE ? ItemStack.EMPTY : slot < Armory.SIZE ? ctx.data.armory.get(ctx.npc, slot) : ctx.data.throwItems.get(slot - Armory.SIZE); }
    private static boolean valid(CombatContext ctx, ServerPlayer player, int slot) {
        return ctx != null && player != null && CombatService.serverThread(ctx.npc) && CombatService.validNpc(ctx.npc) && player.level() == ctx.npc.level() && slot >= Armory.SIZE && slot < SIZE && !ctx.runtime.scheduler.busy(ctx.now) && !ctx.data.throwing.blocksAttack(ctx.now);
    }
    private static boolean supported(ItemStack item) { return item != null && (item.isEmpty() || !item.is(noppes.npcs.CustomItems.wand) && item.getCount() <= Math.min(64, item.getMaxStackSize()) && ThrowAdapters.find(item) != null); }
    public static Result exchange(CombatContext ctx, ServerPlayer player, int slot, int inventorySlot) {
        if (ctx == null || player == null || slot < 0 || slot >= SIZE) return Result.fail("INVALID_ARGUMENT");
        if (slot < Armory.SIZE) return ctx.data.armory.exchange(ctx, player, slot, inventorySlot);
        if (!valid(ctx, player, slot) || inventorySlot < 0 || inventorySlot > 35 && inventorySlot != 40) return Result.fail("INVALID_ARGUMENT");
        ItemStack incoming = player.getInventory().getItem(inventorySlot);
        if (!supported(incoming)) return Result.fail("UNSUPPORTED_THROWABLE");
        ItemStack old = ctx.data.throwItems.take(ctx, slot - Armory.SIZE);
        player.getInventory().setItem(inventorySlot, old); ctx.data.throwItems.put(slot - Armory.SIZE, incoming);
        changed(ctx, player); return Result.success();
    }
    public static Result creative(CombatContext ctx, ServerPlayer player, int slot, ItemStack canonical) {
        return creative(ctx, player, slot, canonical, 1);
    }
    public static Result creative(CombatContext ctx, ServerPlayer player, int slot, ItemStack canonical, int count) {
        if (ctx == null || player == null || slot < 0 || slot >= SIZE) return Result.fail("INVALID_ARGUMENT");
        if (slot < Armory.SIZE) return ctx.data.armory.equipCreative(ctx, player, slot, canonical);
        if (!valid(ctx, player, slot) || !player.isCreative()) return Result.fail("PERMISSION_DENIED");
        if (canonical == null || canonical.isEmpty() || !supported(canonical)) return Result.fail("UNSUPPORTED_THROWABLE");
        if (count < 1 || count > Math.min(64, canonical.getMaxStackSize())) return Result.fail("INVALID_ARGUMENT");
        ItemStack item = canonical.copyWithCount(count);
        ItemStack old = ctx.data.throwItems.take(ctx, slot - Armory.SIZE); ctx.data.throwItems.put(slot - Armory.SIZE, item);
        if (!old.isEmpty() && !player.getInventory().add(old)) player.drop(old, false);
        changed(ctx, player); return Result.success();
    }
    public static Result unequip(CombatContext ctx, ServerPlayer player, int slot) {
        if (ctx == null || player == null || slot < 0 || slot >= SIZE) return Result.fail("INVALID_ARGUMENT");
        if (slot < Armory.SIZE) return ctx.data.armory.unequip(ctx, player, slot);
        if (!valid(ctx, player, slot)) return Result.fail("INVALID_ARGUMENT");
        if (get(ctx, slot).isEmpty()) return Result.fail("NO_WEAPON");
        for (int i = 0; i < 36; i++) if (player.getInventory().getItem(i).isEmpty()) return exchange(ctx, player, slot, i);
        if (player.getInventory().getItem(40).isEmpty()) return exchange(ctx, player, slot, 40);
        return Result.fail("INVENTORY_FULL");
    }
    private static void changed(CombatContext ctx, ServerPlayer player) {
        player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges(); ctx.config.revision++; ctx.npc.updateClient = true;
    }
}
