package bincnpc.gun.common.persistence;

import bincnpc.gun.common.api.Result;
import com.google.gson.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** Private player-owned item templates made from server-resolved inventory/catalogue stacks. */
public final class ItemPresetLibrary extends SavedData {
    private record Entry(UUID owner, String title, ItemStack item) {}
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    public static ItemPresetLibrary get(ServerPlayer player) {
        if (player == null || player.getServer() == null) throw new IllegalArgumentException("item presets require a server player");
        return player.getServer().overworld().getDataStorage().computeIfAbsent(ItemPresetLibrary::load, ItemPresetLibrary::new, "cnpcgun_item_presets");
    }
    public static ItemPresetLibrary load(CompoundTag tag) {
        ItemPresetLibrary library = new ItemPresetLibrary();
        if (tag == null) return library;
        for (Tag raw : tag.getList("Items", Tag.TAG_COMPOUND)) {
            if (library.entries.size() >= 256) break;
            CompoundTag item = (CompoundTag)raw;
            if (!item.hasUUID("Owner") || item.getString("Id").length() > 64 || item.getString("Title").length() > 48) continue;
            ItemStack stack = ItemStack.of(item.getCompound("Item"));
            if (!stack.isEmpty() && stack.save(new CompoundTag()).toString().length() <= 32768) {
                stack.setCount(1); library.entries.put(item.getString("Id"), new Entry(item.getUUID("Owner"), item.getString("Title"), stack));
            }
        }
        return library;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        entries.forEach((id, entry) -> {
            CompoundTag item = new CompoundTag(); item.putString("Id", id); item.putUUID("Owner", entry.owner); item.putString("Title", entry.title);
            item.put("Item", entry.item.save(new CompoundTag())); list.add(item);
        });
        tag.put("Items", list); return tag;
    }
    public JsonArray snapshot(UUID owner) {
        JsonArray list = new JsonArray();
        entries.forEach((id, entry) -> {
            if (!entry.owner.equals(owner)) return;
            JsonObject item = new JsonObject(); item.addProperty("id", id); item.addProperty("title", entry.title);
            item.addProperty("itemName", entry.item.getHoverName().getString()); item.addProperty("throwable", bincnpc.gun.throwing.ThrowAdapters.find(entry.item) != null);
            item.addProperty("stackLimit", Math.max(1, Math.min(64, entry.item.getMaxStackSize()))); list.add(item);
        });
        return list;
    }
    public Result create(ServerPlayer player, String title, ItemStack stack) {
        if (player == null || title == null || stack == null) return Result.fail("INVALID_ARGUMENT");
        title = title.trim();
        if (!player.isCreative()) return Result.fail("PERMISSION_DENIED");
        if (title.isEmpty() || title.length() > 48 || title.chars().anyMatch(Character::isISOControl) || stack.isEmpty()
            || stack.is(noppes.npcs.CustomItems.wand) || stack.save(new CompoundTag()).toString().length() > 32768) return Result.fail("INVALID_ARGUMENT");
        long owned = entries.values().stream().filter(entry -> entry.owner.equals(player.getUUID())).count();
        if (owned >= 64 || entries.size() >= 256) return Result.fail("CONFLICT");
        String id = UUID.randomUUID().toString(); ItemStack copy = stack.copy(); copy.setCount(1);
        entries.put(id, new Entry(player.getUUID(), title, copy)); setDirty(); return Result.success(id);
    }
    public ItemStack find(UUID owner, String id) {
        Entry entry = entries.get(id); return entry != null && entry.owner.equals(owner) ? entry.item.copy() : ItemStack.EMPTY;
    }
    public Result remove(UUID owner, String id) {
        Entry entry = entries.get(id); if (entry == null || !entry.owner.equals(owner)) return Result.fail("NOT_FOUND");
        entries.remove(id); setDirty(); return Result.success();
    }
}
