package bincnpc.gun.common.network;

import bincnpc.gun.common.adapter.*;
import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.config.Parameter;
import bincnpc.gun.common.legacy.Platform;
import bincnpc.gun.mixin.CreativeTabAccess;
import bincnpc.gun.throwing.ThrowAdapters;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.*;
import net.minecraft.init.Items;
import net.minecraft.init.PotionTypes;
import net.minecraft.item.*;
import net.minecraft.item.crafting.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.oredict.OreDictionary;

/** The same common catalogue supplies client indices and authoritative server templates. */
public final class CreativeCatalogue {
    public static final class Tab {
        private final String id, title;
        private final List<ItemStack> items;
        private final CreativeTabs nativeTab;
        Tab(String id, String title, List<ItemStack> items) { this(id, title, items, null); }
        Tab(String id, String title, List<ItemStack> items, CreativeTabs nativeTab) { this.id=id;this.title=title;this.items=Collections.unmodifiableList(items);this.nativeTab=nativeTab; }
        public String id() { return id; }
        public String title() { return title; }
        public List<ItemStack> items() { return items; }
        public CreativeTabs nativeTab() { return nativeTab; }
    }
    public static final class Changed extends IllegalArgumentException {
        private Changed(String reason) { super(reason); }
    }
    private static final class Bucket {
        final CreativeTabs tab;
        final Map<NBTTagCompound,ItemStack> items = new LinkedHashMap<>();
        Bucket(CreativeTabs tab) { this.tab=tab; }
        void add(ItemStack item) {
            if (item == null || item.isEmpty() || item.getItem()==noppes.npcs.CustomItems.wand
                    || item.getItemDamage()==OreDictionary.WILDCARD_VALUE || items.size()>=100000) return;
            ItemStack copy=Platform.copy(item,1);
            NBTTagCompound key=copy.writeToNBT(new NBTTagCompound());
            if (key.toString().length()<=32768) items.putIfAbsent(key,copy);
        }
    }
    private static final String THROWABLES="cnpcgun:throwable_registry";
    private static List<Tab> cached=Collections.emptyList();
    private static List<Object> revisions=Collections.emptyList();
    private CreativeCatalogue() {}
    public static synchronized void clear() { cached=Collections.emptyList();revisions=Collections.emptyList(); }
    public static synchronized List<Tab> tabs() {
        List<Object> current=new ArrayList<>();
        current.add(Item.REGISTRY.getKeys().size());
        for(String id:AdapterRegistry.ids()) {
            WeaponAdapter adapter=AdapterRegistry.get(id);current.add(id);
            current.add(AdapterRegistry.query(adapter,adapter::catalogueRevision,id));
        }
        if(cached.isEmpty()||!current.equals(revisions)) {
            Map<CreativeTabs,Bucket> buckets=new IdentityHashMap<>();
            // Native templates are authoritative for their item types. A blank gun/attachment
            // is not another creative variant of the correctly initialized weapon.
            Set<Item> nativeItems=Collections.newSetFromMap(new IdentityHashMap<Item,Boolean>());
            for(String id:AdapterRegistry.ids()) {
                WeaponAdapter adapter=AdapterRegistry.get(id);
                for(ItemStack item:AdapterRegistry.query(adapter,adapter::catalogueItems,Collections.<ItemStack>emptyList())) {
                    if(item==null||item.isEmpty())continue;
                    nativeItems.add(item.getItem());add(buckets,item);
                }
            }
            List<Item> items=new ArrayList<>();for(Item item:Item.REGISTRY)items.add(item);
            items.sort(Comparator.comparing(item->String.valueOf(item.getRegistryName())));
            for(Item item:items) {
                if(nativeItems.contains(item))continue;
                if(VanillaCreativeItems.hasDefault(item))add(buckets,new ItemStack(item));
                for(ItemStack variant:VanillaCreativeItems.variants(item))add(buckets,variant);
            }
            // Only real subtype outputs supplement other mods. Block states and matching
            // ingredients include growth/rotation/wildcard metadata that is not creative content.
            for(IRecipe recipe:CraftingManager.REGISTRY) {
                ItemStack output=recipe.getRecipeOutput();
                if(output!=null&&!output.isEmpty()&&!nativeItems.contains(output.getItem())
                        && output.getItem().getHasSubtypes()&&output.getItemDamage()>0
                        && output.getItemDamage()!=OreDictionary.WILDCARD_VALUE
                        && !"minecraft".equals(output.getItem().getRegistryName().getNamespace()))add(buckets,output);
            }
            for(PotionType type:PotionType.REGISTRY)if(type!=PotionTypes.EMPTY) {
                for(Item potion:new Item[]{Items.POTIONITEM,Items.SPLASH_POTION,Items.LINGERING_POTION,Items.TIPPED_ARROW})
                    add(buckets,PotionUtils.addPotionToItemStack(new ItemStack(potion),type));
            }
            for(Enchantment enchantment:Enchantment.REGISTRY)
                add(buckets,ItemEnchantedBook.getEnchantedItemStack(new EnchantmentData(enchantment,enchantment.getMaxLevel())));
            List<Bucket> ordered=new ArrayList<>(buckets.values());
            ordered.sort(Comparator.comparingInt(bucket->((CreativeTabAccess)bucket.tab).cnpcgun$index()));
            List<Tab> built=new ArrayList<>();
            for(Bucket bucket:ordered) {
                if(bucket.items.isEmpty())continue;
                CreativeTabAccess tab=(CreativeTabAccess)bucket.tab;int index=tab.cnpcgun$index();
                String label=tab.cnpcgun$label();
                String id=(index<=12?"minecraft":"cnpcgun")+":creative_"+index+"_"+label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]","_");
                List<ItemStack> variants=new ArrayList<>(bucket.items.values());
                variants.sort(Comparator.comparing(item->item.writeToNBT(new NBTTagCompound()).toString()));
                built.add(new Tab(id,"itemGroup."+label,variants,bucket.tab));
            }
            cached=Collections.unmodifiableList(built);revisions=current;
        }
        return copyTabs(cached);
    }
    private static void add(Map<CreativeTabs,Bucket> buckets,ItemStack item) {
        if(item==null||item.isEmpty())return;
        for(CreativeTabs tab:item.getItem().getCreativeTabs())
            if(tab!=null&&tab!=CreativeTabs.SEARCH&&tab!=CreativeTabs.INVENTORY)
                buckets.computeIfAbsent(tab,Bucket::new).add(item);
    }
    private static List<Tab> copyTabs(List<Tab> tabs) {
        List<Tab> result=new ArrayList<>();
        for(Tab tab:tabs) {
            List<ItemStack> items=new ArrayList<>();for(ItemStack item:tab.items)items.add(item.copy());
            result.add(new Tab(tab.id,tab.title,items,tab.nativeTab));
        }
        return Collections.unmodifiableList(result);
    }
    /** Filtering preserves each native index for server resolution. */
    public static synchronized List<Tab> throwingTabs() {
        List<Tab> result=new ArrayList<>();Set<NBTTagCompound> shown=new HashSet<>();
        for(Tab tab:tabs()) {
            boolean compatible=false;
            for(ItemStack item:tab.items)if(ThrowAdapters.find(item.copy())!=null) {
                compatible=true;shown.add(item.writeToNBT(new NBTTagCompound()));
            }
            if(compatible)result.add(tab);
        }
        Map<NBTTagCompound,ItemStack> missing=new LinkedHashMap<>();
        for(ItemStack item:ThrowAdapters.catalogue()) {
            ItemStack copy=Platform.copy(item,1);NBTTagCompound key=copy.writeToNBT(new NBTTagCompound());
            if(!shown.contains(key)&&key.toString().length()<=32768)missing.putIfAbsent(key,copy);
        }
        if(!missing.isEmpty()) {
            List<ItemStack> items=new ArrayList<>(missing.values());
            items.sort(Comparator.comparing(item->item.writeToNBT(new NBTTagCompound()).toString()));
            result.add(new Tab(THROWABLES,"cnpcgun.editor.throwRegistry",items));
        }
        return Collections.unmodifiableList(result);
    }
    public static int fingerprint(ItemStack stack) { return Platform.copy(stack,1).writeToNBT(new NBTTagCompound()).hashCode(); }
    public static synchronized ItemStack resolve(EntityPlayerMP player,JsonObject input) {
        if(player==null||input==null||!player.capabilities.isCreativeMode)throw new IllegalArgumentException("creative editor required");
        String id=SafeJson.string(input,"tab","");
        if(Platform.id(id)==null)throw new IllegalArgumentException("tab");
        int index=Parameter.integer("itemIndex",0,0,100000).validate(input.get("itemIndex")).getAsInt();
        int expected=Parameter.integer("fingerprint",0,Integer.MIN_VALUE,Integer.MAX_VALUE).validate(input.get("fingerprint")).getAsInt();
        List<Tab> all=tabs();
        if(THROWABLES.equals(id)) {
            Parameter.integer("slot",6,6,8).validate(input.get("slot"));all=throwingTabs();
        }
        for(Tab tab:all)if(tab.id.equals(id)) {
            if(index>=tab.items.size())throw new Changed("creative catalogue index changed");
            ItemStack item=tab.items.get(index);
            if(item.isEmpty()||item.getItem()==noppes.npcs.CustomItems.wand)throw new IllegalArgumentException("unavailable catalogue item");
            if(fingerprint(item)!=expected)throw new Changed("creative template changed for "+item.getItem().getRegistryName());
            return Platform.copy(item,1);
        }
        throw new Changed("creative catalogue tab changed");
    }
}
