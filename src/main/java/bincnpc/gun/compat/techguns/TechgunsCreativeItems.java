package bincnpc.gun.compat.techguns;

import techguns.items.guns.GenericGun;
import techguns.items.guns.ammo.*;
import net.minecraft.item.*;
import java.util.*;

final class TechgunsCreativeItems {
    private TechgunsCreativeItems() {}
    static List<ItemStack> items() {
        List<ItemStack> result = new ArrayList<>();
        for (Item item : Item.REGISTRY) if (item instanceof GenericGun) {
            GenericGun gun = (GenericGun)item; AmmoType type = gun.getAmmoType();
            if (type == null) continue;
            for (int variant = 0; variant < type.getVariants().size(); variant++) {
                AmmoVariant ammo = type.getVariants().get(variant);
                for (int camo = 0; camo < Math.max(1, gun.getCamoCount()); camo++) {
                    ItemStack stack = new ItemStack(item);
                    gun.getCurrentAmmo(stack); gun.setCurrentAmmoVariant(stack, ammo.getKey());
                    stack.getTagCompound().setByte("camo", (byte)camo); result.add(stack);
                }
                for (ItemStack stack : type.getAmmo(variant)) if (!stack.isEmpty()) result.add(stack.copy());
                for (ItemStack stack : type.getBullet(variant)) if (!stack.isEmpty()) result.add(stack.copy());
            }
            for (ItemStack stack : type.getEmptyMag()) if (!stack.isEmpty()) result.add(stack.copy());
        }
        return result;
    }
}
