package bincnpc.gun.common.persistence;

import bincnpc.gun.common.api.Result;
import com.google.gson.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.storage.WorldSavedData;

/** Private player-owned item templates made from server-resolved inventory/catalogue stacks. */
public final class ItemPresetLibrary extends WorldSavedData {
    private static final class Entry  {
private final UUID owner;
private final String title;
private final ItemStack item;
public Entry(UUID owner, String title, ItemStack item) {
this.owner = owner; this.title = title; this.item = item;}
public UUID owner() { return owner; }
public String title() { return title; }
public ItemStack item() { return item; }
@Override public boolean equals(Object value) { if(this==value)return true; if(!(value instanceof Entry))return false; Entry that=(Entry)value; return java.util.Objects.equals(owner, that.owner) && java.util.Objects.equals(title, that.title) && java.util.Objects.equals(item, that.item); }
@Override public int hashCode() { return java.util.Objects.hash(owner, title, item); }

}
    public ItemPresetLibrary() { super("cnpcgun_item_presets"); }
    public ItemPresetLibrary(String name) { super(name); }
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    public static ItemPresetLibrary get(EntityPlayerMP player) {
        if (player == null || player.getServer() == null) throw new IllegalArgumentException("item presets require a server player");
        net.minecraft.world.storage.MapStorage storage = player.getServer().getWorld(0).getMapStorage();
        ItemPresetLibrary library = (ItemPresetLibrary)storage.getOrLoadData(ItemPresetLibrary.class, "cnpcgun_item_presets");
        if (library == null) { library = new ItemPresetLibrary(); storage.setData("cnpcgun_item_presets", library); }
        return library;
    }
    public static ItemPresetLibrary load(NBTTagCompound tag) {
        ItemPresetLibrary library = new ItemPresetLibrary();
        if (tag == null) return library;
        for (NBTBase raw : bincnpc.gun.common.legacy.Platform.tags(tag.getTagList("Items", net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND))) {
            if (library.entries.size() >= 256) break;
            NBTTagCompound item = (NBTTagCompound)raw;
            if (!item.hasUniqueId("Owner") || item.getString("Id").length() > 64 || item.getString("Title").length() > 48) continue;
            ItemStack stack = new ItemStack(item.getCompoundTag("Item"));
            if (!stack.isEmpty() && stack.writeToNBT(new NBTTagCompound()).toString().length() <= 32768) {
                stack.setCount(1); library.entries.put(item.getString("Id"), new Entry(item.getUniqueId("Owner"), item.getString("Title"), stack));
            }
        }
        return library;
    }
    @Override public void readFromNBT(NBTTagCompound tag) { entries.clear(); entries.putAll(load(tag).entries); }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        entries.forEach((id, entry) -> {
            NBTTagCompound item = new NBTTagCompound(); item.setString("Id", id); item.setUniqueId("Owner", entry.owner); item.setString("Title", entry.title);
            item.setTag("Item", entry.item.writeToNBT(new NBTTagCompound())); list.appendTag(item);
        });
        tag.setTag("Items", list); return tag;
    }
    public JsonArray snapshot(UUID owner) {
        JsonArray list = new JsonArray();
        entries.forEach((id, entry) -> {
            if (!entry.owner.equals(owner)) return;
            JsonObject item = new JsonObject(); item.addProperty("id", id); item.addProperty("title", entry.title);
            item.addProperty("itemName", entry.item.getDisplayName()); item.addProperty("throwable", bincnpc.gun.throwing.ThrowAdapters.find(entry.item) != null);
            item.addProperty("stackLimit", Math.max(1, Math.min(64, entry.item.getMaxStackSize()))); list.add(item);
        });
        return list;
    }
    public Result create(EntityPlayerMP player, String title, ItemStack stack) {
        if (player == null || title == null || stack == null) return Result.fail("INVALID_ARGUMENT");
        title = title.trim();
        if (!player.capabilities.isCreativeMode) return Result.fail("PERMISSION_DENIED");
        if (title.isEmpty() || title.length() > 48 || title.chars().anyMatch(Character::isISOControl) || stack.isEmpty()
            || bincnpc.gun.common.legacy.Platform.is(stack, noppes.npcs.CustomItems.wand) || stack.writeToNBT(new NBTTagCompound()).toString().length() > 32768) return Result.fail("INVALID_ARGUMENT");
        long owned = entries.values().stream().filter(entry -> entry.owner.equals(player.getUniqueID())).count();
        if (owned >= 64 || entries.size() >= 256) return Result.fail("CONFLICT");
        String id = UUID.randomUUID().toString(); ItemStack copy = stack.copy(); copy.setCount(1);
        entries.put(id, new Entry(player.getUniqueID(), title, copy)); markDirty(); return Result.success(id);
    }
    public ItemStack find(UUID owner, String id) {
        Entry entry = entries.get(id); return entry != null && entry.owner.equals(owner) ? entry.item.copy() : ItemStack.EMPTY;
    }
    public Result remove(UUID owner, String id) {
        Entry entry = entries.get(id); if (entry == null || !entry.owner.equals(owner)) return Result.fail("NOT_FOUND");
        entries.remove(id); markDirty(); return Result.success();
    }
}
