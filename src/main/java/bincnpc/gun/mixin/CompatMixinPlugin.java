package bincnpc.gun.mixin;

import java.util.*;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import org.spongepowered.asm.service.MixinService;

public final class CompatMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        // Forge's ModLauncher provider only supports transformed bytecode.
        if (mixin.endsWith("ExternalStoreInvoker") || mixin.endsWith("ExternalPortableMixin")) {
            try {
                ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(target, true);
                return mixin.endsWith("ExternalPortableMixin") ? node.methods.stream().anyMatch(method -> method.name.equals("makeAllPortable"))
                    : node.methods.stream().anyMatch(method -> method.name.equals("<init>") && method.desc.contains("Ljava/util/function/Predicate;Ljava/util/function/Predicate;"));
            } catch (ClassNotFoundException | java.io.IOException e) { return false; }
        }
        if (!mixin.contains(".compat.")) return true;
        try {
            MixinService.getService().getBytecodeProvider().getClassNode(target, true);
            return true;
        } catch (ClassNotFoundException | java.io.IOException e) { return false; }
    }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
