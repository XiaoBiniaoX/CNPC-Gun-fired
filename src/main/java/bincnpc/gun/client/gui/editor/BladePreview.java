package bincnpc.gun.client.gui.editor;

import com.google.gson.JsonObject;
import bincnpc.gun.common.api.SafeJson;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Only the detached client's blade pose is changed. Native attack callbacks are never called. */
final class BladePreview {
    private BladePreview() {}
    static String pose(ItemStack blade, JsonObject action, JsonObject parameters, long now) {
        if (blade == null || blade.isEmpty() || action == null) return "";
        String type = SafeJson.string(parameters, "artsType", SafeJson.string(action, "defaultArtsType", "Success"));
        String id = SafeJson.string(SafeJson.objectField(action, "previewStages"), type, SafeJson.string(action, "stageId", ""));
        ResourceLocation stage = ResourceLocation.tryParse(id); if (stage == null) return "";
        blade.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> { state.setComboSeq(stage); state.setLastActionTime(now); });
        return "combo:" + stage;
    }
    static void clear(ItemStack blade, long now) {
        if (blade == null || blade.isEmpty()) return;
        blade.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            state.setComboSeq(mods.flammpfeil.slashblade.registry.ComboStateRegistry.NONE.getId()); state.setLastActionTime(now);
        });
    }
}
