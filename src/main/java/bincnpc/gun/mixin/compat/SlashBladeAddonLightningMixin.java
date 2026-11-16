package bincnpc.gun.mixin.compat;

import bincnpc.gun.compat.slashblade.LegacyBladeCombat;
import net.minecraft.entity.*;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.util.EntityDamageSource;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "cn.mmf.slashblade_addon.entity.EntityLightningSword", remap = false)
public abstract class SlashBladeAddonLightningMixin {
    @Inject(method = "attackToEntity", at = @At("HEAD"), cancellable = true)
    private void cnpcgun$lightning(Entity target, CallbackInfo ci) {
        Entity source = (Entity)(Object)this;
        EntityNPCInterface npc = LegacyBladeCombat.owner(source);
        if (npc == null) return;
        // A real owned damage event and an effect-only bolt avoid unaffiliated weather damage.
        if (LegacyBladeCombat.damage(source, target, new EntityDamageSource("directMagic", npc).setMagicDamage().setDamageBypassesArmor(),
                LegacyBladeCombat.strength(npc, npc.getHeldItemMainhand(), LegacyBladeCombat.living(target)), false))
            source.world.addWeatherEffect(new EntityLightningBolt(source.world, target.posX, target.posY, target.posZ, true));
        source.setDead();ci.cancel();
    }
}
