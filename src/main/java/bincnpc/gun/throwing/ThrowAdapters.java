package bincnpc.gun.throwing;

import bincnpc.gun.common.legacy.Platform;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.*;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.common.Loader;
import noppes.npcs.entity.EntityNPCInterface;
import java.util.*;

/** Class names keep every weapon dependency optional on a dedicated server. */
public final class ThrowAdapters {
    private static final ThrowAdapter VANILLA = new ThrowAdapter() {
        @Override public boolean matches(ItemStack item) {
            return bincnpc.gun.common.legacy.Platform.is(item,Items.SNOWBALL)||bincnpc.gun.common.legacy.Platform.is(item,Items.FIRE_CHARGE)||bincnpc.gun.common.legacy.Platform.is(item,Items.EGG)
                || item.getItem() instanceof ItemSplashPotion || item.getItem() instanceof ItemLingeringPotion;
        }
        @Override public Entity create(WorldServer level, EntityNPCInterface owner, ItemStack item) {
            if (bincnpc.gun.common.legacy.Platform.is(item,Items.FIRE_CHARGE)) return new EntitySmallFireball(level,owner,0,0,0);
            if (bincnpc.gun.common.legacy.Platform.is(item,Items.SNOWBALL)) return new EntitySnowball(level,owner);
            if (bincnpc.gun.common.legacy.Platform.is(item,Items.EGG)) return new EntityEgg(level,owner);
            EntityPotion potion=new EntityPotion(level,owner,Platform.copy(item,1)); return potion;
        }
        @Override public Profile profile(Entity projectile) {
            return new Profile(1.5,projectile instanceof EntityPotion?0.05:0.03,projectile instanceof EntitySmallFireball?0.95:0.99,
                Math.min(2,projectile.width*0.5),projectile instanceof EntitySmallFireball?1:projectile instanceof EntityPotion?4:0,600,projectile instanceof EntitySmallFireball);
        }
        @Override public void detonate(Entity projectile) {
            if (projectile.isDead || !(projectile.world instanceof WorldServer)) return;
            RayTraceResult hit=new RayTraceResult(projectile.getPositionVector(),EnumFacing.UP,projectile.getPosition().down());
            if(projectile instanceof EntityFireball)((bincnpc.gun.mixin.ProjectileFactoryAccess)projectile).cnpcgun$impact(hit);
            else ((bincnpc.gun.mixin.ProjectileHitAccess)projectile).cnpcgun$impact(hit);
            if(!projectile.isDead)projectile.setDead();
        }
    };
    private static final Map<String,ThrowAdapter> optional=new LinkedHashMap<>();
    private static final Set<String> reported=new HashSet<>();
    private static final String[][] MODULES={
        {"cgm","bincnpc.gun.compat.cgm.CgmThrowAdapter"},
        {"techguns","bincnpc.gun.compat.techguns.TechgunsThrowAdapter"},
        {"flansmod","bincnpc.gun.compat.flan.FlanThrowAdapter"}
    };
    private ThrowAdapters(){}
    public static ThrowAdapter find(ItemStack item){
        if(item==null||item.isEmpty())return null;
        if(VANILLA.matches(item))return VANILLA;
        for(String[] module:MODULES)try{ThrowAdapter adapter=optional(module);if(adapter!=null&&adapter.matches(item))return adapter;}
        catch(RuntimeException|LinkageError error){report(module[0],error);}
        return null;
    }
    private static synchronized ThrowAdapter optional(String[] module){
        if(!Loader.isModLoaded(module[0]))return null;
        if(optional.containsKey(module[0]))return optional.get(module[0]);
        try{
            ThrowAdapter adapter=(ThrowAdapter)Class.forName(module[1],true,ThrowAdapters.class.getClassLoader()).getDeclaredConstructor().newInstance();
            optional.put(module[0],adapter);return adapter;
        }catch(ReflectiveOperationException|RuntimeException|LinkageError error){optional.put(module[0],null);report(module[0],error);return null;}
    }
    private static void report(String mod,Throwable error){
        String key=mod+":"+error.getClass().getName();
        if(reported.size()<8&&reported.add(key))bincnpc.gun.CNPCGun.LOGGER.warn("Optional throwable adapter {} unavailable",mod,error);
    }
    public static List<ItemStack> catalogue(){
        List<ItemStack> items=new ArrayList<>();
        for(Item item:net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS){
            ItemStack stack=new ItemStack(item);if(find(stack)!=null&&items.size()<4096)items.add(stack);
        }
        for(String[] module:MODULES)try{ThrowAdapter adapter=optional(module);if(adapter!=null)for(ItemStack stack:adapter.catalogue())
            if(stack!=null&&!stack.isEmpty()&&items.size()<4096)items.add(Platform.copy(stack,1));}
        catch(RuntimeException|LinkageError error){report(module[0],error);}
        return Collections.unmodifiableList(items);
    }
    public static void clear(){optional.clear();reported.clear();}
}
