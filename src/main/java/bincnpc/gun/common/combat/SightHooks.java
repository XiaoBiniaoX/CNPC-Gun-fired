package bincnpc.gun.common.combat;

import java.util.function.BiFunction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

/** Early vanilla hook; NPC dependencies are bound only after mod initialization. */
public final class SightHooks {
    public static BiFunction<EntityLivingBase, Entity, Boolean> handler;
    private SightHooks() {}
}
