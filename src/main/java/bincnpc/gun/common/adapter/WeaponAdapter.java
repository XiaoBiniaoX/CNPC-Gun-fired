package bincnpc.gun.common.adapter;

import net.minecraft.world.item.ItemStack;
import bincnpc.gun.common.combat.CombatContext;

public interface WeaponAdapter {
    String id();
    String domain();
    boolean matches(ItemStack stack);
    default String weaponId(ItemStack stack) {
        return id() + ":" + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
    }
    default void onEquip(CombatContext context) {}
    default void tick(CombatContext context) {}
    default void cancel(CombatContext context) {}
    default void close() {}
}
