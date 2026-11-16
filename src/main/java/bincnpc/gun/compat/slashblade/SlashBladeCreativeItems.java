package bincnpc.gun.compat.slashblade;

import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.ItemSlashBladeNamed;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import java.util.*;

final class SlashBladeCreativeItems {
    private SlashBladeCreativeItems() {}
    static List<ItemStack> items() {
        Map<NBTTagCompound,ItemStack> result = new LinkedHashMap<>();
        add(result, SlashBlade.findItemStack("flammpfeil.slashblade", "slashbladeNamed", 1));
        List<String> names = new ArrayList<>(ItemSlashBladeNamed.NamedBlades);
        Collections.sort(names);
        for (String name : names) {
            add(result, SlashBlade.getCustomBlade(name));
        }
        return new ArrayList<>(result.values());
    }
    private static void add(Map<NBTTagCompound,ItemStack> result, ItemStack template) {
        if(template==null||template.isEmpty())return;
        ItemStack item=template.copy();item.setCount(1);
        if(item.getItemDamage()==32767)item.setItemDamage(0);
        ItemSlashBlade.ProudSoul.set(bincnpc.gun.common.legacy.Platform.tag(item),1000);
        result.putIfAbsent(item.writeToNBT(new NBTTagCompound()),item);
    }
}
