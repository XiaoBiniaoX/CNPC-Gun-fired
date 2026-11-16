package bincnpc.gun.client.animation;

import java.util.*;
import mods.flammpfeil.slashblade.client.renderer.entity.layers.LayerSlashBlade;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import noppes.npcs.entity.EntityNPCInterface;

/** The original SlashBlade handler installs its layer only on players. */
public final class BladeNativeAnimations {
    private final Set<RenderLivingBase<?>> installed=Collections.newSetFromMap(new WeakHashMap<RenderLivingBase<?>,Boolean>());
    public static void install(){MinecraftForge.EVENT_BUS.register(new BladeNativeAnimations());}
    @SuppressWarnings({"rawtypes","unchecked"})
    @SubscribeEvent public void before(RenderLivingEvent.Pre<?> event){
        if(!(event.getEntity() instanceof EntityNPCInterface)||!installed.add(event.getRenderer()))return;
        final LayerSlashBlade blade=new LayerSlashBlade(event.getRenderer());
        ((RenderLivingBase)event.getRenderer()).addLayer(new LayerRenderer<EntityLivingBase>(){
            @Override public void doRenderLayer(EntityLivingBase entity,float limb,float amount,float partial,float age,float yaw,float pitch,float scale){
                if(entity instanceof EntityNPCInterface&&entity.getHeldItemMainhand().getItem() instanceof ItemSlashBlade)
                    ((LayerRenderer)blade).doRenderLayer(entity,limb,amount,partial,age,yaw,pitch,scale);
            }
            @Override public boolean shouldCombineTextures(){return true;}
        });
    }
    @SubscribeEvent public void after(RenderLivingEvent.Post<?> event){
        if(event.getEntity() instanceof EntityNPCInterface)BladePreviewEffects.render((EntityNPCInterface)event.getEntity(),event.getX(),event.getY(),event.getZ(),event.getPartialRenderTick());
    }
}
