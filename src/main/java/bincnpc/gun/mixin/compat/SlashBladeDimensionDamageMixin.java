package bincnpc.gun.mixin.compat;

import bincnpc.gun.compat.slashblade.LegacyBladeCombat;
import mods.flammpfeil.slashblade.ability.ArmorPiercing;
import mods.flammpfeil.slashblade.entity.EntitySlashDimension;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.entity.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EntityDamageSource;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value = EntitySlashDimension.class, remap = false)
public abstract class SlashBladeDimensionDamageMixin {
    @Shadow protected float AttackLevel;
    @Redirect(method = {"func_70071_h_", "onUpdate"}, at = @At(value = "INVOKE",
        target = "Lmods/flammpfeil/slashblade/ability/ArmorPiercing;doAPAttack(Lnet/minecraft/entity/Entity;F)F"))
    private float cnpcgun$armorPiercing(Entity target, float amount) {
        return LegacyBladeCombat.owner((Entity)(Object)this) == null ? ArmorPiercing.doAPAttack(target, amount) : 0;
    }
    @Redirect(method = {"func_70071_h_", "onUpdate"}, at = @At(value = "INVOKE", remap = false,
        target = "Lmods/flammpfeil/slashblade/item/ItemSlashBlade;func_77644_a(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/entity/EntityLivingBase;)Z"))
    private boolean cnpcgun$dimensionDamage(ItemSlashBlade blade, ItemStack stack, EntityLivingBase target, EntityLivingBase user) {
        Entity source = (Entity)(Object)this;
        if (LegacyBladeCombat.owner(source) != null && !LegacyBladeCombat.damage(source, target,
                new EntityDamageSource("directMagic", user).setMagicDamage().setDamageBypassesArmor().setDamageIsAbsolute(),
                Math.max(1, AttackLevel), false)) return false;
        return blade.hitEntity(stack, target, user);
    }
}
