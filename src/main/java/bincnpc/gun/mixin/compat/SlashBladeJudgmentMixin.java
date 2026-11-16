package bincnpc.gun.mixin.compat;

import bincnpc.gun.compat.slashblade.LegacyBladeCombat;
import mods.flammpfeil.slashblade.entity.selector.EntitySelectorAttackable;
import net.minecraft.entity.*;
import net.minecraft.util.EntityDamageSource;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = mods.flammpfeil.slashblade.entity.EntityJudgmentCutManager.class, remap = false)
public abstract class SlashBladeJudgmentMixin {
    @Inject(method = {"func_70071_h_", "onUpdate"}, at = @At("HEAD"))
    private void cnpcgun$judgment(CallbackInfo ci) {
        Entity source = (Entity)(Object)this;
        EntityNPCInterface npc = LegacyBladeCombat.owner(source);
        if (npc == null || source.ticksExisted != 25 || !LegacyBladeCombat.active(npc)) return;
        for (Entity target : LegacyBladeCombat.targets(source, source.world, npc,
                source.getEntityBoundingBox().expand(0, -source.height / 2, 0), EntitySelectorAttackable.getInstance()))
            LegacyBladeCombat.damage(source, target, new EntityDamageSource("directMagic", npc).setMagicDamage().setDamageBypassesArmor().setDamageIsAbsolute(),
                4 * LegacyBladeCombat.strength(npc, npc.getHeldItemMainhand(), LegacyBladeCombat.living(target)), false);
    }
}
