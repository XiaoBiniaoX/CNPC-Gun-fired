package bincnpc.gun.client.animation;

import bincnpc.gun.mixin.compat.TaczAnimationAccess;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.compat.playeranimator.AnimationName;
import com.tacz.guns.compat.playeranimator.animation.PlayerAnimatorAssetManager;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import noppes.npcs.entity.EntityNPCInterface;

/** Client-only native gun-pack clips. Animation players are created independently for every NPC. */
final class TaczNativeAnimations {
    record Clip(IAnimation animation, int duration) {}
    private TaczNativeAnimations() {}
    private static Clip clip(EntityNPCInterface npc, String name) {
        var display = TimelessAPI.getGunDisplay(npc.getMainHandItem()).orElse(null);
        if (display == null || display.getPlayerAnimator3rd() == null) return null;
        var data = ((TaczAnimationAccess)PlayerAnimatorAssetManager.get()).cnpcgun$animation(display.getPlayerAnimator3rd(), name).orElse(null);
        return data == null ? null : new Clip(new KeyframeAnimationPlayer(data), Math.max(1, Math.min(2400, data.endTick)));
    }
    static Clip action(EntityNPCInterface npc, String pose, boolean aiming) {
        return switch (pose) {
            case "RELOAD" -> clip(npc, AnimationName.RELOAD_UPPER);
            case "FIRE" -> clip(npc, aiming ? AnimationName.AIM_FIRE_UPPER : AnimationName.NORMAL_FIRE_UPPER);
            case "MELEE" -> clip(npc, AnimationName.MELEE_UPPER);
            default -> null;
        };
    }
    static String loopKey(EntityNPCInterface npc, boolean aiming) {
        var display = TimelessAPI.getGunDisplay(npc.getMainHandItem()).orElse(null);
        return display == null ? "" : display.getPlayerAnimator3rd() + ":" + loopName(npc, aiming);
    }
    private static String loopName(EntityNPCInterface npc, boolean aiming) {
        return aiming ? AnimationName.AIM_UPPER : npc.getDeltaMovement().horizontalDistanceSqr() > 0.0025 ? AnimationName.WALK_UPPER : AnimationName.HOLD_UPPER;
    }
    static IAnimation loop(EntityNPCInterface npc, boolean aiming) { var clip = clip(npc, loopName(npc, aiming)); return clip == null ? null : clip.animation(); }
}
