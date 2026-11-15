package bincnpc.gun.compat.superb;

import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.item.ammo.AmmoBoxItem;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/** Native consumer strategies remain responsible for item matching and resource consumption. */
public final class SuperbAmmo {
    private SuperbAmmo() {}

    public static AmmoConsumer selected(GunData data) {
        AmmoConsumer consumer = data.selectedAmmoConsumer();
        if (!consumer.initialized()) consumer.init();
        return consumer;
    }

    public static boolean inventory(GunData data) { return inventory(selected(data)); }
    public static boolean inventory(AmmoConsumer consumer) {
        return consumer.getType() == AmmoConsumer.AmmoConsumeType.PLAYER_AMMO && consumer.getPlayerAmmoType() != null
            || consumer.getType() == AmmoConsumer.AmmoConsumeType.ITEM && !consumer.getStack().isEmpty();
    }

    public static String reserveKey(GunData data) {
        AmmoConsumer consumer = selected(data);
        if (!inventory(consumer) || consumer.getAmmo() == null) return null;
        String key = "superbwarfare:" + consumer.getAmmo();
        if (key.length() <= 224) return key;
        // SNBT ammunition variants may exceed the bank's bounded key length.
        try {
            return "superbwarfare:nbt/" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public static int itemUnits(GunData data, ItemStack item) {
        if (item == null || item.isEmpty()) return 0;
        AmmoConsumer consumer = selected(data);
        if (!inventory(consumer)) return 0;
        // Reusable, mixed-ammo boxes are consumed through the NPC's native inventory.
        // The generic ground-supply path discards the whole item, so never credit a box there.
        if (consumer.getType() == AmmoConsumer.AmmoConsumeType.PLAYER_AMMO && item.getItem() instanceof AmmoBoxItem) return 0;
        int nativeCount = consumer.count(data, new SingleItem(item.copy()));
        return (int)Math.min(100001L, (long)nativeCount * Math.max(1, consumer.getLoadAmount()));
    }

    public static int extractUnits(GunData data, ItemStack item, int requested) {
        int count = Math.min(Math.max(0, Math.min(100000, requested)), itemUnits(data, item));
        if (count == 0) return 0;
        AmmoConsumer consumer = selected(data);
        int load = Math.max(1, consumer.getLoadAmount());
        SingleItem inventory = new SingleItem(item);
        int before = consumer.count(data, inventory), remainder = Math.max(0, data.virtualAmmo.get());
        consumer.consume(data, inventory, (int)((count + load - 1L) / load));
        long removed = Math.max(0L, (long)before - consumer.count(data, inventory)) * load;
        int taken = (int)Math.min(count, removed);
        // Keep packed-ammo and loadAmount surplus exactly once in the native remainder.
        data.virtualAmmo.set((int)Math.min(Integer.MAX_VALUE, (long)remainder + removed - taken));
        data.cachedBackupAmmo = -1; data.save();
        return taken;
    }

    private static final class SingleItem implements IItemHandler {
        private final ItemStack item;
        private SingleItem(ItemStack item) { this.item = item; }
        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) { check(slot); return item; }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { check(slot); return stack; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            check(slot);
            int count = Math.min(item.getCount(), Math.max(0, amount));
            if (count == 0) return ItemStack.EMPTY;
            ItemStack extracted = item.copyWithCount(count);
            if (!simulate) item.shrink(count);
            return extracted;
        }
        @Override public int getSlotLimit(int slot) { check(slot); return item.getMaxStackSize(); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { check(slot); return false; }
        private static void check(int slot) { if (slot != 0) throw new IndexOutOfBoundsException(slot); }
    }
}
