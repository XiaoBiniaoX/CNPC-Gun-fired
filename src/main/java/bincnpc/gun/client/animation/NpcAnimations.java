package bincnpc.gun.client.animation;

import bincnpc.gun.CNPCGun;
import bincnpc.gun.common.network.GunNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.impl.AnimationProcessor;
import dev.kosmx.playerAnim.core.util.Vec3f;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import noppes.npcs.entity.EntityNPCInterface;

/** Client presentation only. All attack execution and resource costs stay on the server. */
@Mod.EventBusSubscriber(modid = CNPCGun.MOD_ID, value = Dist.CLIENT)
public final class NpcAnimations {
    private static final Map<EntityNPCInterface, WeaponAnimation> ANIMATIONS = new WeakHashMap<>();
    private static final Map<HumanoidModel<?>, List<PartPose>> SAVED_POSES = new WeakHashMap<>();
    private record PartPose(ModelPart part, float x, float y, float z, float rx, float ry, float rz) {
        PartPose(ModelPart part) { this(part, part.x, part.y, part.z, part.xRot, part.yRot, part.zRot); }
        void restore() { part.x = x; part.y = y; part.z = z; part.xRot = rx; part.yRot = ry; part.zRot = rz; }
    }
    private NpcAnimations() {}
    public static void preview(EntityNPCInterface npc, String domain, String pose, String actionId, int duration, boolean aiming) {
        WeaponAnimation animation = ANIMATIONS.computeIfAbsent(npc, WeaponAnimation::new);
        animation.start(domain, pose, actionId, duration, aiming);
    }
    public static void clearPreview(EntityNPCInterface npc) { ANIMATIONS.remove(npc); }
    public static void receive(GunNetwork.Animation packet) {
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getEntity(packet.entityId()) instanceof EntityNPCInterface npc) || !npc.getUUID().equals(packet.npcId())) return;
        WeaponAnimation animation = ANIMATIONS.computeIfAbsent(npc, WeaponAnimation::new);
        if (packet.pose().equals("STATE")) {
            if (!animation.domain.equals(packet.domain())) animation.nativeBlade = null;
            animation.domain = packet.domain(); animation.aiming = packet.aiming(); animation.summaryAge = 0;
        } else {
            animation.start(packet.domain(), packet.pose(), packet.actionId(), packet.duration(), packet.aiming());
            if (packet.domain().equals("SLASHBLADE") && packet.actionId().startsWith("combo:") && ModList.get().isLoaded("slashblade")) {
                try { BladeNativeAnimations.synchronizeBlade(npc, packet.actionId()); } catch (RuntimeException | LinkageError ignored) { }
            }
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (Minecraft.getInstance().level == null) { ANIMATIONS.clear(); SAVED_POSES.values().forEach(poses -> poses.forEach(PartPose::restore)); SAVED_POSES.clear(); return; }
        Iterator<Map.Entry<EntityNPCInterface, WeaponAnimation>> iterator = ANIMATIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<EntityNPCInterface, WeaponAnimation> item = iterator.next();
            if (item.getKey().isRemoved() || !item.getKey().isAlive() || item.getKey().level() != Minecraft.getInstance().level) iterator.remove();
            else item.getValue().processor.tick();
        }
    }
    public static void apply(EntityNPCInterface npc, HumanoidModel<?> model, float partialTick) {
        WeaponAnimation animation = ANIMATIONS.get(npc); if (animation == null || !animation.processor.isActive()) return;
        restore(model);
        List<PartPose> saved = new ArrayList<>();
        for (ModelPart part : List.of(model.head, model.hat, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg)) saved.add(new PartPose(part));
        if (model instanceof PlayerModel<?> player) for (ModelPart part : List.of(player.rightSleeve, player.leftSleeve, player.jacket, player.rightPants, player.leftPants)) saved.add(new PartPose(part));
        SAVED_POSES.put(model, saved);
        animation.processor.setTickDelta(partialTick);
        transform(animation.processor, "rightArm", model.rightArm); transform(animation.processor, "leftArm", model.leftArm);
        transform(animation.processor, "rightLeg", model.rightLeg); transform(animation.processor, "leftLeg", model.leftLeg);
        transform(animation.processor, "head", model.head);
        if (!animation.nativeActive()) transform(animation.processor, "body", model.body);
        model.hat.copyFrom(model.head);
        if (model instanceof PlayerModel<?> player) {
            player.rightSleeve.copyFrom(player.rightArm); player.leftSleeve.copyFrom(player.leftArm); player.jacket.copyFrom(player.body);
            player.rightPants.copyFrom(player.rightLeg); player.leftPants.copyFrom(player.leftLeg);
        }
    }
    public static void restore(EntityModel<?> model) { if (model instanceof HumanoidModel<?> humanoid) { List<PartPose> saved = SAVED_POSES.remove(humanoid); if (saved != null) saved.forEach(PartPose::restore); } }
    public static boolean nativeRoot(EntityNPCInterface npc) { WeaponAnimation animation = ANIMATIONS.get(npc); return animation != null && animation.nativeActive(); }
    public static boolean nativeGun(EntityNPCInterface npc) { WeaponAnimation animation = ANIMATIONS.get(npc); return animation != null && animation.gunAnimation() != null; }
    public static void root(EntityNPCInterface npc, PoseStack pose, float partialTick) {
        WeaponAnimation animation = ANIMATIONS.get(npc); if (animation == null || !animation.nativeActive()) return;
        animation.processor.setTickDelta(partialTick);
        Vec3f position = animation.processor.get3DTransform("body", TransformType.POSITION, Vec3f.ZERO);
        Vec3f rotation = animation.processor.get3DTransform("body", TransformType.ROTATION, Vec3f.ZERO);
        pose.translate(-position.getX(), position.getY() + 0.7, -position.getZ());
        pose.mulPose(Axis.ZP.rotation(rotation.getZ())); pose.mulPose(Axis.YP.rotation(rotation.getY())); pose.mulPose(Axis.XP.rotation(rotation.getX()));
        pose.translate(0, -0.7, 0);
    }
    private static void transform(AnimationProcessor processor, String name, ModelPart part) {
        Vec3f value = processor.get3DTransform(name, TransformType.ROTATION, new Vec3f(part.xRot, part.yRot, part.zRot));
        part.xRot = value.getX(); part.yRot = value.getY(); part.zRot = value.getZ();
        Vec3f position = processor.get3DTransform(name, TransformType.POSITION, new Vec3f(part.x, part.y, part.z));
        part.x = position.getX(); part.y = position.getY(); part.z = position.getZ();
    }
    private static final class WeaponAnimation implements IAnimation {
        final WeakReference<EntityNPCInterface> npc;
        final AnimationProcessor processor = new AnimationProcessor(this);
        String domain = "UNKNOWN", pose = "";
        IAnimation nativeBlade;
        IAnimation nativeGunAction, nativeGunLoop;
        String gunLoopKey = "";
        boolean aiming;
        int age = 1000, duration = 1, summaryAge = 1000, direction = 1;
        int phantomAge = 1000, phantomDuration = 1, guardAge = 1000, guardDuration = 1;
        int throwAge = 1000, throwDuration = 1;
        boolean throwRelease;
        float delta;
        WeaponAnimation(EntityNPCInterface npc) { this.npc = new WeakReference<>(npc); }
        void start(String domain, String pose, String id, int duration, boolean aiming) {
            if (!this.domain.equals(domain)) { nativeBlade = null; nativeGunAction = nativeGunLoop = null; gunLoopKey = ""; phantomAge = guardAge = 1000; }
            this.domain = domain; this.aiming = aiming; summaryAge = 0;
            if (pose.equals("THROW_END")) { throwAge = 1000; return; }
            if (pose.equals("THROW") || pose.equals("THROW_RELEASE")) {
                throwAge = 0; throwDuration = Mth.clamp(duration, 1, 220); throwRelease = pose.equals("THROW_RELEASE"); return;
            }
            if (domain.equals("SLASHBLADE") && pose.equals("PHANTOM_SWORD")) { phantomAge = 0; phantomDuration = Mth.clamp(duration, 4, 100); return; }
            if (domain.equals("SLASHBLADE") && pose.equals("DEFENSE") && !id.startsWith("combo:")) { guardAge = 0; guardDuration = Mth.clamp(duration, 4, 100); return; }
            this.pose = pose; age = 0; this.duration = Mth.clamp(duration, 1, 2400); direction = (id.hashCode() & 1) == 0 ? 1 : -1;
            nativeBlade = null;
            nativeGunAction = null;
            if (domain.equals("GUN") && ModList.get().isLoaded("tacz") && npc.get() != null) {
                try {
                    var clip = TaczNativeAnimations.action(npc.get(), pose, aiming);
                    if (clip != null) {
                        nativeGunAction = pose.equals("RELOAD") && id.equals("reload_override")
                            ? new ModifierLayer<IAnimation>(clip.animation(), new SpeedModifier((float)clip.duration() / this.duration)) : clip.animation();
                        if (!id.equals("reload_override")) this.duration = Math.max(this.duration, clip.duration());
                    }
                } catch (RuntimeException | LinkageError ignored) { }
            }
            if (domain.equals("SLASHBLADE") && ModList.get().isLoaded("slashblade")) {
                try { nativeBlade = BladeNativeAnimations.create(id); } catch (RuntimeException | LinkageError ignored) { }
            }
        }
        boolean nativeActive() { return throwAge >= throwDuration + 4 && domain.equals("SLASHBLADE") && nativeBlade != null && nativeBlade.isActive() && age < duration + 4; }
        IAnimation gunAnimation() {
            if (!domain.equals("GUN") || summaryAge >= 45) return null;
            return nativeGunAction != null && nativeGunAction.isActive() ? nativeGunAction : nativeGunLoop != null && nativeGunLoop.isActive() ? nativeGunLoop : null;
        }
        @Override public void tick() {
            age++; summaryAge++; phantomAge++; guardAge++; throwAge++;
            if (nativeBlade != null) nativeBlade.tick();
            if (nativeGunAction != null) nativeGunAction.tick();
            if (domain.equals("GUN") && summaryAge < 45 && ModList.get().isLoaded("tacz") && npc.get() != null) {
                try {
                    String key = TaczNativeAnimations.loopKey(npc.get(), aiming);
                    if (!key.equals(gunLoopKey) || nativeGunLoop == null || !nativeGunLoop.isActive()) {
                        gunLoopKey = key; nativeGunLoop = TaczNativeAnimations.loop(npc.get(), aiming);
                    }
                } catch (RuntimeException | LinkageError ignored) { nativeGunLoop = null; }
            }
            if (nativeGunLoop != null) nativeGunLoop.tick();
        }
        @Override public boolean isActive() { return age < duration + 4 || domain.equals("GUN") && summaryAge < 45
            || throwAge < throwDuration + 4
            || domain.equals("SLASHBLADE") && (phantomAge < phantomDuration + 4 || guardAge < guardDuration + 4); }
        @Override public void setupAnim(float partialTick) { delta = partialTick; }
        @Override public Vec3f get3DTransform(String part, TransformType type, float partialTick, Vec3f original) {
            if (type == TransformType.ROTATION && throwAge + partialTick < throwDuration + 4 && (part.equals("leftArm") || part.equals("body"))) {
                float time = Mth.clamp((throwAge + partialTick) / Math.max(1, throwDuration), 0, 1);
                float blend = Mth.clamp((throwDuration + 4 - throwAge - partialTick) / 4, 0, 1);
                Vec3f target = part.equals("leftArm") ? new Vec3f(throwRelease ? -1.65F + time * 1.25F : -1.25F - time * 1.15F, 0.25F, -0.18F)
                    : new Vec3f(throwRelease ? -0.06F * (1 - time) : 0.06F * time, -0.08F * (1 - time), 0);
                return new Vec3f(Mth.lerp(blend, original.getX(), target.getX()), Mth.lerp(blend, original.getY(), target.getY()), Mth.lerp(blend, original.getZ(), target.getZ()));
            }
            IAnimation gun = gunAnimation();
            if (gun != null && (part.equals("rightArm") || part.equals("leftArm") || part.equals("body"))) {
                gun.setupAnim(partialTick);
                Vec3f value = gun.get3DTransform(part, type, partialTick, original);
                return Float.isFinite(value.getX()) && Float.isFinite(value.getY()) && Float.isFinite(value.getZ()) ? value : original;
            }
            float elapsed = age + delta, phase = Mth.clamp(elapsed / Math.max(1, duration), 0, 1);
            float fade = Mth.clamp((duration + 4 - elapsed) / 4, 0, 1);
            float pulse = (float)Math.sin(phase * Math.PI), pitch = npc.get() == null ? 0 : npc.get().getXRot() * Mth.DEG_TO_RAD;
            Vec3f target = original; float weight = fade;
            if (nativeActive()) {
                Vec3f value = nativeBlade.get3DTransform(part, type, partialTick, original);
                if (Float.isFinite(value.getX()) && Float.isFinite(value.getY()) && Float.isFinite(value.getZ())) target = value;
                if (type == TransformType.POSITION) target = new Vec3f(Mth.clamp(target.getX(), original.getX() - 32, original.getX() + 32),
                    Mth.clamp(target.getY(), original.getY() - 32, original.getY() + 32), Mth.clamp(target.getZ(), original.getZ() - 32, original.getZ() + 32));
                target = new Vec3f(Mth.lerp(fade, original.getX(), target.getX()), Mth.lerp(fade, original.getY(), target.getY()), Mth.lerp(fade, original.getZ(), target.getZ()));
                if (type != TransformType.ROTATION) return target;
                original = target; weight = 0;
            } else if (type != TransformType.ROTATION) return original;
            if (domain.equals("GUN")) {
                weight = summaryAge < 35 ? 1 : Mth.clamp((45 - summaryAge) / 10f, 0, 1);
                if (part.equals("rightArm")) target = new Vec3f((aiming ? -1.55F : -0.85F) + pitch - (pose.equals("FIRE") ? pulse * fade * 0.15F : 0), -0.12F, 0);
                if (part.equals("leftArm")) target = new Vec3f((aiming ? -1.45F : -0.55F) + pitch, 0.55F, -0.12F);
                if (pose.equals("RELOAD") && elapsed < duration + 4) {
                    if (part.equals("rightArm")) target = new Vec3f(-0.7F - 0.25F * pulse, -0.3F, 0.2F);
                    if (part.equals("leftArm")) target = new Vec3f(-0.8F - 0.3F * pulse, 0.6F, -0.25F);
                }
            } else if (domain.equals("SLASHBLADE") && elapsed < duration + 4 && !nativeActive()) {
                if (part.equals("rightArm")) target = new Vec3f(-1.5F + phase * 2.5F, direction * (0.4F - pulse), direction * (0.2F + pulse * 0.5F));
                if (part.equals("body")) target = new Vec3f(0.08F * pulse, direction * 0.3F * pulse, 0);
                if (pose.equals("PHANTOM_SWORD") && part.equals("leftArm")) target = new Vec3f(-1.2F, 0.3F, -0.5F * pulse);
                if (pose.equals("DEFENSE") && part.equals("rightArm")) target = new Vec3f(-1.2F, -0.6F, 0.6F);
            }
            Vec3f result = new Vec3f(Mth.lerp(weight, original.getX(), target.getX()), Mth.lerp(weight, original.getY(), target.getY()), Mth.lerp(weight, original.getZ(), target.getZ()));
            if (domain.equals("SLASHBLADE") && part.equals("leftArm") && phantomAge < phantomDuration + 4) {
                float overlay = Mth.clamp((phantomDuration + 4 - phantomAge - delta) / 4F, 0, 1);
                result = new Vec3f(Mth.lerp(overlay, result.getX(), -1.15F), Mth.lerp(overlay, result.getY(), 0.3F), Mth.lerp(overlay, result.getZ(), -0.3F));
            }
            if (domain.equals("SLASHBLADE") && part.equals("rightArm") && guardAge < guardDuration + 4) {
                float overlay = Mth.clamp((guardDuration + 4 - guardAge - delta) / 4F, 0, 1);
                result = new Vec3f(Mth.lerp(overlay, result.getX(), -1.2F), Mth.lerp(overlay, result.getY(), -0.6F), Mth.lerp(overlay, result.getZ(), 0.6F));
            }
            return result;
        }
    }
}
