package bincnpc.gun;

import java.util.*;
import net.minecraftforge.fml.common.Loader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;

public final class CompatMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage){}
    @Override public String getRefMapperConfig(){return null;}
    @Override public boolean shouldApplyMixin(String target,String mixin){
        if(target.startsWith("mods.flammpfeil.slashblade."))return Loader.isModLoaded("flammpfeil.slashblade");
        if(target.startsWith("cn.mmf.slashblade_addon."))return Loader.isModLoaded("flammpfeil.slashblade")&&Loader.isModLoaded("slashblade_addon");
        if(target.startsWith("com.mrcrayfish.guns."))return Loader.isModLoaded("cgm");
        if(target.startsWith("techguns."))return Loader.isModLoaded("techguns");
        if(target.startsWith("com.flansmod."))return Loader.isModLoaded("flansmod");
        return true;
    }
    @Override public void acceptTargets(Set<String> mine,Set<String> others){}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
    @Override public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){}
}
