package bincnpc.gun.client.animation;

import dev.kosmx.playerAnim.api.layered.IAnimation;
import mods.flammpfeil.slashblade.compat.playerAnim.PlayerAnimationOverrider;
import mods.flammpfeil.slashblade.compat.playerAnim.VmdAnimation;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.entity.EntityNPCInterface;

/** Loaded only on a SlashBlade client. Each NPC owns a clone of the native VMD. */
final class BladeNativeAnimations {
    private BladeNativeAnimations() {}
    static IAnimation create(String action) {
        if (!action.startsWith("combo:")) return null;
        ResourceLocation stage = ResourceLocation.tryParse(action.substring(6)); if (stage == null) return null;
        var animations = PlayerAnimationOverrider.getInstance().getAnimation();
        VmdAnimation original = animations.get(stage);
        if (original == null && stage.getNamespace().equals("slashblade")) {
            String path = stage.getPath();
            if (path.startsWith("judgement_cut")) original = animations.get(ComboStateRegistry.JUDGEMENT_CUT_SLASH_JUST.getId());
            else if (path.startsWith("rapid_slash")) original = animations.get(ComboStateRegistry.RAPID_SLASH.getId());
            else if (path.contains("aerial_cleave")) original = animations.get(ComboStateRegistry.AERIAL_CLEAVE.getId());
            else if (path.startsWith("aerial_rave_a")) original = animations.get(ComboStateRegistry.AERIAL_RAVE_A1.getId());
            else if (path.startsWith("aerial_rave_b")) original = animations.get(ComboStateRegistry.AERIAL_RAVE_B3.getId());
        }
        if (original == null) return null;
        VmdAnimation clone = original.getClone(); clone.play(); return clone;
    }
    static void synchronizeBlade(EntityNPCInterface npc, String action) {
        if (!action.startsWith("combo:") || !(npc.getMainHandItem().getItem() instanceof ItemSlashBlade)) return;
        ResourceLocation stage = ResourceLocation.tryParse(action.substring(6));
        if (stage == null || !ComboStateRegistry.REGISTRY.get().containsKey(stage)) return;
        npc.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            state.setComboSeq(stage); state.setLastActionTime(npc.level().getGameTime());
        });
    }
}
