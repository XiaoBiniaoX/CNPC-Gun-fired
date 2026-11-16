package bincnpc.gun.mixin.compat;

import bincnpc.gun.throwing.ThrowFlights;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value=techguns.entities.projectiles.GrenadeProjectile.class,remap=false)
public abstract class TechgunsGrenadeMixin {
    @Redirect(method="hitBlock",at=@At(value="INVOKE",target="Lnet/minecraft/world/World;func_72838_d(Lnet/minecraft/entity/Entity;)Z",remap=false))
    private boolean cnpcgun$bounce(World world,Entity bounce){
        boolean spawned=world.spawnEntity(bounce);
        if(spawned)ThrowFlights.followBounce((Entity)(Object)this,bounce);
        return spawned;
    }
}
