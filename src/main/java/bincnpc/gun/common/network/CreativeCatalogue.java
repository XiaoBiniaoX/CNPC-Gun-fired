package bincnpc.gun.common.network;

import bincnpc.gun.common.config.Parameter;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.*;

/** Common-side catalogue. Requests select an index and hash, never supply an ItemStack. */
public final class CreativeCatalogue {
    public record Tab(String id, String title, List<ItemStack> items) {}
    public static final class Changed extends IllegalArgumentException {
        private Changed(String reason) { super(reason); }
    }
    private static final String THROWABLES = "cnpcgun:throwable_registry";
    private CreativeCatalogue() {}
    public static synchronized List<Tab> tabs(FeatureFlagSet features, boolean operator, HolderLookup.Provider registries) {
        CreativeModeTabs.tryRebuildTabContents(features, operator, registries);
        List<Tab> result = new ArrayList<>();
        for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
            if (tab.getType() == CreativeModeTab.Type.INVENTORY || tab.getType() == CreativeModeTab.Type.HOTBAR || tab.getDisplayItems().isEmpty()) continue;
            ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (id != null) result.add(new Tab(id.toString(), tab.getDisplayName().getString(), tab.getDisplayItems().stream().map(ItemStack::copy).toList()));
        }
        return result;
    }
    /** Keep native item indices intact; filtering is local to the throwing picker. */
    public static synchronized List<Tab> throwingTabs(FeatureFlagSet features, boolean operator, HolderLookup.Provider registries) {
        List<Tab> nativeTabs = tabs(features, operator, registries);
        List<Tab> result = new ArrayList<>(); Set<CompoundTag> shown = new HashSet<>();
        for (Tab tab : nativeTabs) {
            boolean compatible = false;
            for (ItemStack item : tab.items()) if (bincnpc.gun.throwing.ThrowAdapters.find(item.copy()) != null) {
                compatible = true; shown.add(item.copyWithCount(1).save(new CompoundTag()));
            }
            if (compatible) result.add(tab);
        }
        Map<CompoundTag, ItemStack> missing = new LinkedHashMap<>();
        for (ItemStack item : bincnpc.gun.throwing.ThrowAdapters.catalogue()) {
            if (!item.isItemEnabled(features)) continue;
            ItemStack copy = item.copyWithCount(1); CompoundTag key = copy.save(new CompoundTag());
            if (!shown.contains(key) && key.toString().length() <= 32768) missing.putIfAbsent(key, copy);
        }
        if (!missing.isEmpty()) {
            List<ItemStack> items = new ArrayList<>(missing.values());
            items.sort(Comparator.comparing(item -> item.save(new CompoundTag()).toString()));
            result.add(new Tab(THROWABLES, net.minecraft.network.chat.Component.translatable("cnpcgun.editor.throwRegistry").getString(), List.copyOf(items)));
        }
        return List.copyOf(result);
    }
    public static int fingerprint(ItemStack stack) {
        CompoundTag saved = stack.save(new CompoundTag()).copy();
        // Native GunData reads create empty compounds; UUID identifies a runtime instance,
        // not a creative variant. Keep ammo selection, overrides, perks, attachments and Energy.
        if (superbGun(stack.getItem()) && saved.contains("tag", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag tag = saved.getCompound("tag");
            if (tag.contains("GunData", net.minecraft.nbt.Tag.TAG_COMPOUND)) tag.getCompound("GunData").remove("UUID");
            for (String key : new String[]{"GunData", "Perks", "Attachments"}) {
                if (tag.contains(key, net.minecraft.nbt.Tag.TAG_COMPOUND) && tag.getCompound(key).isEmpty()) tag.remove(key);
            }
            if (tag.isEmpty()) saved.remove("tag");
        }
        return saved.hashCode();
    }
    private static boolean superbGun(Item item) {
        // Inspect actual inheritance, including addon guns, without linking an optional mod.
        for (Class<?> type = item.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals("com.atsuishio.superbwarfare.item.gun.GunItem")) return true;
        }
        return false;
    }
    public static synchronized ItemStack resolve(ServerPlayer player, JsonObject input) {
        if (player == null || input == null || !player.isCreative()) throw new IllegalArgumentException("creative editor required");
        String tabId = bincnpc.gun.common.api.SafeJson.string(input, "tab", "");
        ResourceLocation id = ResourceLocation.tryParse(tabId); if (id == null) throw new IllegalArgumentException("tab");
        int index = Parameter.integer("itemIndex", 0, 0, 100000).validate(input.get("itemIndex")).getAsInt();
        int expected = Parameter.integer("fingerprint", 0, Integer.MIN_VALUE, Integer.MAX_VALUE).validate(input.get("fingerprint")).getAsInt();
        if (THROWABLES.equals(tabId)) {
            int slot = Parameter.integer("slot", 0, 6, 8).validate(input.get("slot")).getAsInt();
            Tab tab = throwingTabs(player.level().enabledFeatures(), player.hasPermissions(2), player.level().registryAccess()).stream()
                .filter(entry -> entry.id().equals(THROWABLES)).findFirst().orElseThrow(() -> new Changed("throwable catalogue unavailable"));
            if (index >= tab.items().size()) throw new Changed("throwable catalogue index changed");
            return checked(player, tab.items().get(index), expected);
        }
        CreativeModeTabs.tryRebuildTabContents(player.level().enabledFeatures(), player.hasPermissions(2), player.level().registryAccess());
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getOptional(id).orElseThrow(() -> new IllegalArgumentException("tab"));
        if (tab.getType() == CreativeModeTab.Type.INVENTORY || tab.getType() == CreativeModeTab.Type.HOTBAR) throw new IllegalArgumentException("tab");
        int cursor = 0;
        for (ItemStack item : tab.getDisplayItems()) {
            if (cursor++ != index) continue;
            return checked(player, item, expected);
        }
        throw new Changed("creative catalogue index changed");
    }
    private static ItemStack checked(ServerPlayer player, ItemStack item, int expected) {
        if (item == null || item.isEmpty() || item.is(noppes.npcs.CustomItems.wand)
                || !item.isItemEnabled(player.level().enabledFeatures()) || item.save(new CompoundTag()).toString().length() > 32768)
            throw new IllegalArgumentException("unavailable catalogue item");
        int actual = fingerprint(item);
        if (actual != expected) throw new Changed("creative template changed for " + BuiltInRegistries.ITEM.getKey(item.getItem())
            + " (expected " + expected + ", actual " + actual + ")");
        return item.copyWithCount(1);
    }
}
