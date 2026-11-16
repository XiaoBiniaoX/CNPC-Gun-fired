package bincnpc.gun.common.adapter;

import net.minecraft.item.ItemStack;
import bincnpc.gun.common.combat.CombatContext;

public interface WeaponAdapter {
    String id();
    String domain();
    boolean matches(ItemStack stack);
    default String weaponId(ItemStack stack) {
        return id() + ":" + stack.getItem().getRegistryName();
    }
    default Object catalogueRevision() { return id(); }
    default java.util.List<ItemStack> catalogueItems() { return java.util.Collections.emptyList(); }
    default void onEquip(CombatContext context) {}
    default void tick(CombatContext context) {}
    default void cancel(CombatContext context) {}
    default void close() {}
}
