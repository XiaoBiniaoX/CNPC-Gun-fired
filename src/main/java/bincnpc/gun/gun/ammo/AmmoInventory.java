package bincnpc.gun.gun.ammo;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.wrapper.ItemStackWrapper;
import noppes.npcs.entity.EntityNPCInterface;

/** Real CNPC drops/held ammunition, never a player inventory or generated items. */
public final class AmmoInventory implements IItemHandler {
    private final EntityNPCInterface npc;
    private final boolean enabled;
    public AmmoInventory(EntityNPCInterface npc) { this(npc, true); }
    public AmmoInventory(EntityNPCInterface npc, boolean enabled) {
        if (npc == null) throw new IllegalArgumentException("NPC ammunition inventory");
        this.npc = npc; this.enabled = enabled;
    }
    @Override public int getSlots() { return enabled && npc.inventory != null ? 23 : 0; }
    @Override public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= getSlots()) throw new IndexOutOfBoundsException(slot);
        var wrapper = slot < 21 ? npc.inventory.drops.get(slot) : slot == 21 ? npc.inventory.getLeftHand() : npc.inventory.getProjectile();
        if (wrapper == null) return ItemStack.EMPTY;
        ItemStack stack = ItemStackWrapper.MCItem(wrapper);
        return stack == null ? ItemStack.EMPTY : stack;
    }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack existing = getStackInSlot(slot);
        if (existing.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        int count = Math.min(amount, existing.getCount());
        ItemStack result = existing.copy(); result.setCount(count);
        if (!simulate) {
            existing.shrink(count);
            if (slot < 21) {
                if (existing.isEmpty()) npc.inventory.drops.remove(slot);
                else npc.inventory.drops.put(slot, NpcAPI.Instance().getIItemStack(existing));
            } else if (slot == 21) npc.inventory.setLeftHand(NpcAPI.Instance().getIItemStack(existing));
            else npc.inventory.setProjectile(NpcAPI.Instance().getIItemStack(existing));
            npc.updateClient = true;
        }
        return result;
    }
    @Override public int getSlotLimit(int slot) { return 64; }
    @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
    public void changed(int slot) {
        ItemStack stack = getStackInSlot(slot);
        if (slot < 21) {
            if (stack.isEmpty()) npc.inventory.drops.remove(slot);
            else npc.inventory.drops.put(slot, NpcAPI.Instance().getIItemStack(stack));
        } else if (slot == 21) npc.inventory.setLeftHand(NpcAPI.Instance().getIItemStack(stack));
        else npc.inventory.setProjectile(NpcAPI.Instance().getIItemStack(stack));
        npc.updateClient = true;
    }
    public long fingerprint() {
        long hash = 1;
        for (int i = 0; i < getSlots(); i++) {
            ItemStack s = getStackInSlot(i);
            hash = hash * 31 + s.getCount();
            hash = hash * 31 + (s.isEmpty() ? 0 : net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(s.getItem()));
            hash = hash * 31 + (s.hasTag() ? s.getTag().hashCode() : 0);
        }
        return hash;
    }
}
