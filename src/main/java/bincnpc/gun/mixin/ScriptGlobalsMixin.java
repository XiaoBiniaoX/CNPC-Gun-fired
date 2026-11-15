package bincnpc.gun.mixin;

import bincnpc.gun.common.script.ScriptApi;
import java.util.HashMap;
import noppes.npcs.controllers.ScriptContainer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ScriptContainer.class, remap = false)
public abstract class ScriptGlobalsMixin {
    @Shadow @Final public static HashMap<String, Object> Data;
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void cnpcgun$register(CallbackInfo ci) { Data.put("CNPCGun", new ScriptApi()); }
}
