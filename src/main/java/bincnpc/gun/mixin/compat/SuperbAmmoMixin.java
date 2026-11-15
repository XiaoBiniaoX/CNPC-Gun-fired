package bincnpc.gun.mixin.compat;

import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.gun.ammo.AmmoController;
import bincnpc.gun.gun.ammo.AmmoInventory;
import bincnpc.gun.compat.superb.SuperbAmmo;
import com.atsuishio.superbwarfare.data.gun.GunData;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets = "com.atsuishio.superbwarfare.data.gun.GunData", remap = false)
public abstract class SuperbAmmoMixin {
    @Unique private static final LazyOptional<IEnergyStorage> cnpcgun$infiniteEnergy = LazyOptional.of(() -> new IEnergyStorage() {
        @Override public int receiveEnergy(int amount, boolean simulate) { return 0; }
        @Override public int extractEnergy(int amount, boolean simulate) { return Math.max(0, amount); }
        @Override public int getEnergyStored() { return Integer.MAX_VALUE; }
        @Override public int getMaxEnergyStored() { return Integer.MAX_VALUE; }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return false; }
    });
    @Inject(method = "getEnergyProvider(Lnet/minecraft/world/entity/Entity;)Lnet/minecraftforge/common/util/LazyOptional;",
        at = @At("HEAD"), cancellable = true, require = 0)
    private void cnpcgun$energy(Entity entity, CallbackInfoReturnable<LazyOptional<IEnergyStorage>> cir) {
        if (entity instanceof EntityNPCInterface npc && CombatService.serverThread(npc) && CombatService.isControlled(npc)
                && CombatService.data(npc).config.bool("ammo.infinite")) cir.setReturnValue(cnpcgun$infiniteEnergy);
    }
    @Inject(method = "countBackupAmmo(Lnet/minecraft/world/entity/Entity;)I", at = @At("HEAD"), cancellable = true, require = 0)
    private void cnpcgun$count(Entity entity, CallbackInfoReturnable<Integer> cir) {
        if (entity instanceof EntityNPCInterface npc && CombatService.serverThread(npc) && CombatService.isControlled(npc)) {
            CombatContext context = new CombatContext(npc);
            if (context.config.bool("ammo.infinite")) cir.setReturnValue(Integer.MAX_VALUE);
            else if (SuperbAmmo.inventory((GunData)(Object)this))
                cir.setReturnValue(((GunData)(Object)this).countBackupAmmo(new AmmoInventory(npc, AmmoController.allowsItems(context))));
        }
    }
    @Inject(method = "consumeBackupAmmo(Lnet/minecraft/world/entity/Entity;I)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void cnpcgun$consume(Entity entity, int amount, CallbackInfo ci) {
        if (entity instanceof EntityNPCInterface npc && CombatService.serverThread(npc) && CombatService.isControlled(npc)) {
            CombatContext context = new CombatContext(npc);
            if (context.config.bool("ammo.infinite")) ci.cancel();
            else if (SuperbAmmo.inventory((GunData)(Object)this)) {
                ((GunData)(Object)this).consumeBackupAmmo(new AmmoInventory(npc, AmmoController.allowsItems(context)), amount); ci.cancel();
            }
        }
    }
}
