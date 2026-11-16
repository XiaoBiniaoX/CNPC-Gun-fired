package bincnpc.gun.common.network;

import java.util.*;
import net.minecraft.entity.EntityList;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.util.ResourceLocation;

/** Vanilla's actual inventory subtypes, without invoking client-only getSubItems on a server. */
final class VanillaCreativeItems {
    private static final Map<String,int[]> METADATA=new HashMap<>();
    static {
        range(7,"stone");
        range(3,"dirt","sandstone","red_sandstone","prismarine","quartz_block","anvil");
        range(6,"planks","sapling","wooden_slab","double_plant","monster_egg","skull");
        range(4,"log","leaves","stonebrick","fish");
        range(2,"sand","sponge","log2","leaves2","cobblestone_wall","coal","golden_apple","cooked_fish");
        range(8,"stone_slab");
        range(9,"red_flower");
        range(16,"wool","stained_glass","stained_glass_pane","stained_hardened_clay",
            "carpet","concrete","concrete_powder","dye","banner","bed");
        METADATA.put("tallgrass",new int[]{1,2});
    }
    private VanillaCreativeItems() {}
    private static void range(int count,String... names) {
        int[] variants=new int[count];for(int i=0;i<count;i++)variants[i]=i;
        for(String name:names)METADATA.put(name,variants);
    }
    static boolean hasDefault(Item item) {
        if(item==Items.POTIONITEM||item==Items.SPLASH_POTION||item==Items.LINGERING_POTION
                ||item==Items.TIPPED_ARROW||item==Items.ENCHANTED_BOOK||item==Items.SPAWN_EGG)return false;
        ResourceLocation id=item.getRegistryName();
        if(id!=null&&"minecraft".equals(id.getNamespace())) {
            int[] variants=METADATA.get(id.getPath());
            return variants==null||variants.length==0||variants[0]==0;
        }
        return true;
    }
    static List<ItemStack> variants(Item item) {
        List<ItemStack> result=new ArrayList<>();ResourceLocation id=item.getRegistryName();
        if(id==null||!"minecraft".equals(id.getNamespace()))return result;
        int[] variants=METADATA.get(id.getPath());
        if(variants!=null)for(int meta:variants)if(meta!=0)result.add(new ItemStack(item,1,meta));
        if(item==Items.SPAWN_EGG) {
            List<ResourceLocation> entities=new ArrayList<>(EntityList.ENTITY_EGGS.keySet());
            entities.sort(Comparator.comparing(ResourceLocation::toString));
            for(ResourceLocation entity:entities) {
                ItemStack egg=new ItemStack(item);ItemMonsterPlacer.applyEntityIdToItemStack(egg,entity);result.add(egg);
            }
        }
        return result;
    }
}
