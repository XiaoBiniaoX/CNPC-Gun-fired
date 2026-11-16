package bincnpc.gun;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import zone.rong.mixinbooter.IEarlyMixinLoader;

/** Register vanilla hooks before FML discovers ordinary mods. No game or NPC classes here. */
@IFMLLoadingPlugin.Name("CNPCGunEarly")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
public final class EarlyMixinLoader implements IFMLLoadingPlugin, IEarlyMixinLoader {
    @Override public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.cnpcgun.early.json");
    }
    @Override public String[] getASMTransformerClass() { return new String[0]; }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public void injectData(Map<String, Object> data) {}
    @Override public String getAccessTransformerClass() { return null; }
}
