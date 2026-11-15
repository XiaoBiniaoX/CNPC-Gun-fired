package bincnpc.gun.client.animation;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import noppes.npcs.entity.EntityNPCInterface;
import java.util.Map;
import java.util.WeakHashMap;

/** Native renderers with detached, zero-damage visual instances. No spawn, tick or collision. */
public final class BladePreviewEffects {
    private static final Map<EntityNPCInterface, Visual> VISUALS = new WeakHashMap<>();
    private static final class Visual {
        final EntityAbstractSummonedSword sword;
        final EntitySlashEffect slash;
        final String kind;
        final int count, duration, delay;
        int age;
        Visual(EntityNPCInterface npc, JsonObject info, JsonObject parameters, int duration) {
            kind = info.has("backend") ? info.get("backend").getAsString() : "";
            this.duration = Mth.clamp(duration, 1, 2400); delay = parameters.has("delay") ? Mth.clamp(parameters.get("delay").getAsInt(), 0, 100) : 0;
            count = parameters.has("count") ? Mth.clamp(parameters.get("count").getAsInt(), 1, 32) : kind.contains("rain") ? 18 : kind.contains("basic") ? 1 : 8;
            int color = npc.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE).map(state -> state.getColorCode()).orElse(0x7799FF);
            sword = new EntityAbstractSummonedSword(SlashBlade.RegistryEvents.SummonedSword, npc.level());
            sword.setId(-2); sword.setColor(color); sword.setDamage(0); sword.setNoClip(true);
            slash = new EntitySlashEffect(SlashBlade.RegistryEvents.SlashEffect, npc.level());
            slash.setId(-3); slash.setColor(color); slash.setDamage(0); slash.setMute(true); slash.setNoClip(true); slash.setRank(5); slash.setLifetime(12); slash.setBaseSize(0.5F);
        }
    }
    private BladePreviewEffects() {}
    public static void start(EntityNPCInterface npc, JsonObject info, JsonObject parameters, int duration) {
        if (npc.getId() >= 0 || !npc.level().isClientSide) return;
        String type = info.get("type").getAsString();
        if (type.equals("SPECIAL_ATTACK") || type.equals("PHANTOM_SWORD")) VISUALS.put(npc, new Visual(npc, info, parameters, duration));
        else VISUALS.remove(npc);
    }
    public static void clear(EntityNPCInterface npc) { VISUALS.remove(npc); }
    public static void tick(EntityNPCInterface npc) { Visual visual = VISUALS.get(npc); if (visual != null && ++visual.age > visual.duration + visual.delay) VISUALS.remove(npc); }
    public static void render(EntityNPCInterface npc, PoseStack pose, MultiBufferSource buffers, int light, float partialTick) {
        Visual visual = VISUALS.get(npc); if (visual == null || visual.age < visual.delay) return;
        float elapsed = visual.age - visual.delay + partialTick;
        float progress = Mth.clamp(elapsed / Math.max(1, visual.duration - visual.delay), 0, 1);
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        pose.pushPose();
        try {
            pose.translate(0, 1.5, 0); pose.scale(1, -1, 1);
            if (visual.kind.startsWith("phantom:")) {
                var renderer = dispatcher.getRenderer(visual.sword);
                for (int i = 0; i < visual.count; i++) {
                    double angle = i * Math.PI * 2 / visual.count + elapsed * 0.16;
                    double x = Math.cos(angle) * 0.85, y = 1.1 + Math.sin(angle) * 0.25, z = Math.sin(angle) * 0.7;
                    float yaw = (float)Math.toDegrees(angle), pitch = 0;
                    if (visual.kind.contains("rain")) { x = (i % 6 - 2.5) * 0.3; y = 2.6 - progress * 2.0 + i / 6 * 0.15; z = -0.4; yaw = 0; pitch = -90; }
                    else if (visual.kind.contains("blistering")) { x = (i % 2 == 0 ? 1 : -1) * (0.7 - i / 2 * 0.05); y = 1.3 + i / 2 * 0.12; z = -progress * 1.5; yaw = 0; }
                    else if (visual.kind.contains("basic") || visual.kind.contains("spiral_fire")) { x = (i % 2 == 0 ? 1 : -1) * 0.5; y = 1.3; z = -progress * 2; yaw = 0; }
                    visual.sword.setYRot(yaw); visual.sword.yRotO = yaw; visual.sword.setXRot(pitch); visual.sword.xRotO = pitch;
                    visual.sword.setRoll(i * 360F / visual.count);
                    pose.pushPose(); pose.translate(x, y, z);
                    try { renderer.render(visual.sword, yaw, partialTick, pose, buffers, light); } finally { pose.popPose(); }
                }
            } else {
                var renderer = dispatcher.getRenderer(visual.slash);
                int cuts = visual.kind.contains("judgement") || visual.kind.contains("native_super") ? 3 : 1;
                visual.slash.tickCount = 2 + (int)elapsed % 9;
                for (int i = 0; i < cuts; i++) {
                    visual.slash.setRotationRoll(i * 60F - 30); visual.slash.setRotationOffset(i * 90F);
                    pose.pushPose(); pose.translate(0, 1.05 + i * 0.08, -0.35);
                    try { renderer.render(visual.slash, 0, partialTick, pose, buffers, light); } finally { pose.popPose(); }
                }
            }
        } finally { pose.popPose(); }
    }
}
