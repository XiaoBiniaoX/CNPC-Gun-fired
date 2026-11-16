package bincnpc.gun.gun.ammo;

import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;

/** The legacy IInventory facade points at CNPC's real ammunition slots. */
public final class NativeAmmoInventory extends InventoryBasic {
    private final AmmoInventory inventory;
    public NativeAmmoInventory(AmmoInventory inventory) { super("CNPC ammunition", false, inventory.getSlots()); this.inventory = inventory; }
    @Override public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }
    @Override public void setInventorySlotContents(int slot, ItemStack stack) { inventory.setStackInSlot(slot, stack); }
    @Override public ItemStack decrStackSize(int slot, int count) { return inventory.extractItem(slot, count, false); }
    @Override public ItemStack removeStackFromSlot(int slot) { return inventory.extractItem(slot, Integer.MAX_VALUE, false); }
    @Override public boolean isEmpty() { for (int i=0;i<inventory.getSlots();i++) if (!inventory.getStackInSlot(i).isEmpty()) return false; return true; }
    @Override public void markDirty() { if (inventory != null) for (int i=0;i<inventory.getSlots();i++) inventory.changed(i); }
}
