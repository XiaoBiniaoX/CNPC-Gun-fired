package bincnpc.gun.throwing;

import bincnpc.gun.common.combat.CombatContext;
import net.minecraft.nbt.*;
import net.minecraft.item.ItemStack;

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
    public NBTTagCompound save() {
        NBTTagCompound result = new NBTTagCompound(); NBTTagList list = new NBTTagList();
        for (int i = 0; i < SIZE; i++) if (!items[i].isEmpty()) {
            NBTTagCompound item = items[i].writeToNBT(new NBTTagCompound()); item.setInteger("Slot", i); item.setInteger("Spent", spent[i]); list.appendTag(item);
        }
        result.setTag("Items", list); result.setBoolean("ResetAfterDeath", resetAfterDeath); return result;
    }
    public void load(NBTTagCompound tag) {
        if (tag == null) tag = new NBTTagCompound();
        java.util.Arrays.fill(items, ItemStack.EMPTY); java.util.Arrays.fill(spent, 0); epoch++;
        resetAfterDeath = tag.getBoolean("ResetAfterDeath");
        for (NBTBase raw : bincnpc.gun.common.legacy.Platform.tags(tag.getTagList("Items", net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND))) {
            NBTTagCompound item = (NBTTagCompound)raw; int slot = item.getInteger("Slot");
            if (slot < 0 || slot >= SIZE || !items[slot].isEmpty()) continue;
            items[slot] = new ItemStack(item);
            if (!items[slot].isEmpty()) items[slot].setCount(Math.max(1, Math.min(64, Math.min(items[slot].getMaxStackSize(), items[slot].getCount()))));
            spent[slot] = Math.max(0, Math.min(items[slot].getCount(), item.getInteger("Spent")));
        }
    }
}
