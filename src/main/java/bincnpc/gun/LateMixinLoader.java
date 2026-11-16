package bincnpc.gun;

import java.util.Collections;
import java.util.List;
import zone.rong.mixinbooter.ILateMixinLoader;

public final class LateMixinLoader implements ILateMixinLoader {
    @Override public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.cnpcgun.json");
    }
}
