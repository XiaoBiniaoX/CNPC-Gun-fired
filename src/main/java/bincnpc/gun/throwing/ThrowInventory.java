package bincnpc.gun.throwing;

import bincnpc.gun.common.combat.CombatContext;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;

/** Three server-owned templates. Spent one-shot quotas survive chunk unload; reset/respawn restores them. */
public final class ThrowInventory {
    public static final int SIZE = 3;
    private final ItemStack[] items = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private final int[] spent = new int[SIZE];
    private boolean resetAfterDeath;
    public long epoch;
    public ItemStack get(int slot) { return slot < 0 || slot >= SIZE ? ItemStack.EMPTY : items[slot]; }
    public int remaining(CombatContext ctx, int slot) {
        return get(slot).isEmpty() ? 0 : ctx.config.bool("throw.slot" + slot + ".once") ? Math.max(0, get(slot).getCount() - spent[slot]) : -1;
    }
    public void put(int slot, ItemStack item) { if (slot < 0 || slot >= SIZE || item == null) throw new IllegalArgumentException("throw slot"); items[slot] = item; spent[slot] = 0; epoch++; }
    public ItemStack take(CombatContext ctx, int slot) {
        if (slot < 0 || slot >= SIZE) return ItemStack.EMPTY;
        ItemStack item = get(slot);
        if (!item.isEmpty()) item.setCount(Math.max(0, item.getCount() - spent[slot]));
        items[slot] = ItemStack.EMPTY; spent[slot] = 0; epoch++;
        return item.isEmpty() ? ItemStack.EMPTY : item;
    }
    public void used(CombatContext ctx, int slot) { if (ctx == null || slot < 0 || slot >= SIZE) return; if (ctx.config.bool("throw.slot" + slot + ".once")) spent[slot]++; epoch++; }
    public void reset() { java.util.Arrays.fill(spent, 0); resetAfterDeath = false; epoch++; }
    public void died() { resetAfterDeath = true; }
    public void onAlive() { if (resetAfterDeath) reset(); }
    public CompoundTag save() {
        CompoundTag result = new CompoundTag(); ListTag list = new ListTag();
        for (int i = 0; i < SIZE; i++) if (!items[i].isEmpty()) {
            CompoundTag item = items[i].save(new CompoundTag()); item.putInt("Slot", i); item.putInt("Spent", spent[i]); list.add(item);
        }
        result.put("Items", list); result.putBoolean("ResetAfterDeath", resetAfterDeath); return result;
    }
    public void load(CompoundTag tag) {
        if (tag == null) tag = new CompoundTag();
        java.util.Arrays.fill(items, ItemStack.EMPTY); java.util.Arrays.fill(spent, 0); epoch++;
        resetAfterDeath = tag.getBoolean("ResetAfterDeath");
        for (Tag raw : tag.getList("Items", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag)raw; int slot = item.getInt("Slot");
            if (slot < 0 || slot >= SIZE || !items[slot].isEmpty()) continue;
            items[slot] = ItemStack.of(item);
            if (!items[slot].isEmpty()) items[slot].setCount(Math.max(1, Math.min(64, Math.min(items[slot].getMaxStackSize(), items[slot].getCount()))));
            spent[slot] = Math.max(0, Math.min(items[slot].getCount(), item.getInt("Spent")));
        }
    }
}
