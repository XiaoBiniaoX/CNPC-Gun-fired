package bincnpc.gun.compat.slashblade;

import bincnpc.gun.common.api.*;
import bincnpc.gun.common.adapter.AdapterRegistry;
import bincnpc.gun.common.combat.*;
import bincnpc.gun.common.config.Parameter;
import bincnpc.gun.slashblade.action.*;
import bincnpc.gun.slashblade.adapter.BladeAdapter;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.SlashBladeConfig;
import mods.flammpfeil.slashblade.item.*;
import mods.flammpfeil.slashblade.capability.slashblade.*;
import mods.flammpfeil.slashblade.capability.concentrationrank.*;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.util.InputCommand;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.KnockBacks;
import mods.flammpfeil.slashblade.registry.*;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import mods.flammpfeil.slashblade.entity.*;
import mods.flammpfeil.slashblade.event.*;
import mods.flammpfeil.slashblade.ability.*;
import mods.flammpfeil.slashblade.util.TimeValueHelper;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import noppes.npcs.entity.EntityNPCInterface;
import java.util.*;
import java.util.function.Consumer;
import static bincnpc.gun.common.config.Parameter.*;

public final class SlashBladeAdapter implements BladeAdapter {
    private final Map<ResourceLocation, String> combos = new HashMap<>();
    private final Map<ResourceLocation, String> arts = new HashMap<>();
    private final Consumer<LivingAttackEvent> guardListener = this::guard;
    private final Consumer<SlashBladeEvent.DoSlashEvent> slashListener = this::slashed;
    public SlashBladeAdapter() { MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, guardListener); MinecraftForge.EVENT_BUS.addListener(slashListener); }
    @Override public void close() { MinecraftForge.EVENT_BUS.unregister(guardListener); MinecraftForge.EVENT_BUS.unregister(slashListener); }
    @Override public String id() { return "slashblade"; }
    @Override public boolean matches(ItemStack stack) { return stack != null && stack.getItem() instanceof ItemSlashBlade && stack.getCapability(ItemSlashBlade.BLADESTATE).isPresent(); }
    private ISlashBladeState blade(ItemStack stack) { return stack.getCapability(ItemSlashBlade.BLADESTATE).orElseThrow(() -> new IllegalStateException("blade state absent")); }
    @Override public Views.BladeCaps capabilities(ItemStack stack) {
        Views.BladeCaps caps = new Views.BladeCaps(); caps.canAttack = true;
        caps.specialAttack = !arts.isEmpty(); caps.phantomSword = SlashBlade.RegistryEvents.SummonedSword != null;
        caps.lockOn = true; caps.defense = true; caps.counter = true; caps.movement = true;
        caps.actionTypes = new String[]{"NORMAL_ATTACK", "COMBO_ATTACK", "SPECIAL_ATTACK", "PHANTOM_SWORD", "LOCK_ON", "DEFENSE", "COUNTER", "MOVEMENT"};
        return caps;
    }
    private static String actionId(ResourceLocation key, String prefix) { return "slashblade:" + key.getNamespace() + "/" + prefix + key.getPath(); }
    private void add(ActionRegistry registry, String action, String type, String source, String label, String backend,
            int duration, int cooldown, Integer cost, boolean parallel, boolean target, Parameter[] schema, String... tags) {
        registry.register(new ActionRegistry.Definition(new Views.ActionInfo(action, type, source, label,
            "cnpcgun.action.description." + type.toLowerCase(Locale.ROOT), cooldown, duration, cost, schema, true, "",
            !type.equals("SPECIAL_ATTACK"), parallel, target), id(), backend, tags));
    }
    @Override public void registerActions(ActionRegistry registry) {
        combos.clear(); arts.clear();
        for (ResourceLocation key : ComboStateRegistry.REGISTRY.get().getKeys()) {
            ComboState combo = ComboStateRegistry.REGISTRY.get().getValue(key);
            if (combo == null || key.getPath().equals("none") || key.getPath().equals("standby")) continue;
            String id = actionId(key, "combo/");
            int duration = Math.max(1, Math.min(200, (int)Math.ceil(TimeValueHelper.getTicksFromFrames(
                Math.max(1, Math.abs(combo.getEndFrame() - combo.getStartFrame()))) / Math.max(0.1, combo.getSpeed()))));
            Set<String> tags = new LinkedHashSet<>();
            tags.add(combo.isAerial() ? "air" : "ground");
            String path = key.getPath();
            boolean movement = path.contains("rapid_slash") || path.contains("rising_star");
            if (movement) tags.add("dash");
            if (path.contains("cleave")) tags.add("fall");
            if (path.contains("judgement")) tags.add("dimension");
            if (path.contains("_end") || path.contains("sheath")) tags.add("recovery");
            String type = movement ? "MOVEMENT" : path.contains("combo_") ? "NORMAL_ATTACK" : "COMBO_ATTACK";
            add(registry, id, type, key.getNamespace(), "combo." + key.getNamespace() + "." + path,
                "combo:" + key, duration, duration, 0, false, true, new Parameter[0], tags.toArray(String[]::new));
            combos.put(key, id);
        }
        for (ResourceLocation key : SlashArtsRegistry.REGISTRY.get().getKeys()) {
            SlashArts art = SlashArtsRegistry.REGISTRY.get().getValue(key);
            if (art == null || key.getPath().equals("none")) continue;
            String id = actionId(key, "sa/");
            add(registry, id, "SPECIAL_ATTACK", key.getNamespace(), art.getDescriptionId(), "sa:" + key,
                12, 40, art.getProudSoulCost(), false, true, new Parameter[]{choice("artsType", "Success", "Success", "Jackpot", "Super")}, "dimension");
            arts.put(key, id);
        }
        if (SlashArtsRegistry.REGISTRY.get().containsKey(SlashArtsRegistry.JUDGEMENT_CUT.getId()))
            add(registry, "slashblade:cnpcgun/sa/native_super", "SPECIAL_ATTACK", "cnpcgun", "cnpcgun.action.native_super",
                "sa:" + SlashArtsRegistry.JUDGEMENT_CUT.getId(), duration(ComboStateRegistry.JUDGEMENT_CUT_END.getId()), 400, 0,
                false, true, new Parameter[]{choice("artsType", "Super", "Success", "Jackpot", "Super")}, "dimension", "super");
        phantom(registry, "summoned_sword", SlashBlade.RegistryEvents.SummonedSword, "basic", 1, false);
        phantom(registry, "spiral_swords", SlashBlade.RegistryEvents.SpiralSwords, "spiral", 6, false);
        if (SlashBlade.RegistryEvents.SpiralSwords != null) add(registry, "slashblade:slashblade/phantom/spiral_fire",
            "PHANTOM_SWORD", "slashblade", "cnpcgun.action.spiral_fire", "phantom:spiral_fire", 2, 10, 0, true, true, new Parameter[0], "phantom");
        phantom(registry, "storm_swords", SlashBlade.RegistryEvents.StormSwords, "storm", 6, true);
        phantom(registry, "blistering_swords", SlashBlade.RegistryEvents.BlisteringSwords, "blistering", 6, false);
        phantom(registry, "heavy_rain_swords", SlashBlade.RegistryEvents.HeavyRainSwords, "rain", 18, true);
        add(registry, "slashblade:cnpcgun/lock_on", "LOCK_ON", "cnpcgun", "cnpcgun.action.lock_on", "lock", 1, 1, 0, true, true, new Parameter[0], "lock");
        add(registry, "slashblade:cnpcgun/spin_guard", "DEFENSE", "cnpcgun", "cnpcgun.action.spin_guard", "guard", 12, 30, 0, true, false,
            new Parameter[]{integer("duration", 12, 1, 100)}, "counter", "guard");
        add(registry, "slashblade:cnpcgun/air_trick", "MOVEMENT", "cnpcgun", "cnpcgun.action.air_trick", "teleport", 8, 40, 0, false, true,
            new Parameter[]{number("distance", 2, 1, 4)}, "teleport");
        if (combos.containsKey(ComboStateRegistry.COMBO_A1.getId())) add(registry, "slashblade:cnpcgun/counter", "COUNTER", "cnpcgun",
            "cnpcgun.action.counter", "counter", 8, 20, 0, false, true, new Parameter[0], "counter");
    }
    private void phantom(ActionRegistry registry, String fallback, net.minecraft.world.entity.EntityType<?> entity,
            String kind, int count, boolean multi) {
        if (entity == null) return;
        ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity);
        if (key == null) return;
        List<Parameter> schema = new ArrayList<>();
        schema.add(integer("count", count, 1, kind.equals("rain") ? 32 : 16));
        if (!kind.equals("basic")) schema.add(integer("delay", 0, 0, 100));
        if (multi) schema.add(integer("targetCount", 1, 1, 16));
        add(registry, actionId(key, "phantom/"), "PHANTOM_SWORD", key.getNamespace(),
            "cnpcgun.action." + kind, "phantom:" + kind, 4, kind.equals("basic") ? 8 : 40,
            kind.equals("basic") ? SlashBladeConfig.SUMMON_SWORD_COST.get() : SlashBladeConfig.SUMMON_SWORD_ART_COST.get(),
            true, !kind.equals("spiral"), schema.toArray(Parameter[]::new), "phantom", kind);
    }
    @Override public String nextNormal(CombatContext ctx) {
        ISlashBladeState state = blade(ctx.weapon);
        ResourceLocation current = resolve(ctx, state);
        ComboState combo = ComboStateRegistry.REGISTRY.get().getValue(current);
        ResourceLocation next = combo == null ? null : combo.getNext(ctx.npc);
        if (current.equals(ComboStateRegistry.NONE.getId()) || next == null || next.equals(ComboStateRegistry.NONE.getId())) {
            if (ctx.config.text("slashblade.preset").equals("MAID")) {
                if (!ctx.npc.onGround()) next = ComboStateRegistry.AERIAL_RAVE_A1.getId();
                else if (ctx.validTarget() && !ctx.target.onGround()) next = ComboStateRegistry.UPPERSLASH.getId();
                else next = ComboStateRegistry.COMBO_A1.getId();
            } else {
            ResourceLocation root = state.getComboRoot();
            ComboState rootState = root == null ? null : ComboStateRegistry.REGISTRY.get().getValue(root);
            next = rootState == null ? ComboStateRegistry.COMBO_A1.getId() : rootState.getNext(ctx.npc);
            }
        }
        if (next != null && next.equals(current)) return "";
        return combos.getOrDefault(next, combos.getOrDefault(ComboStateRegistry.COMBO_A1.getId(), ""));
    }
    private static int duration(ResourceLocation stage) {
        if (stage == null) return 12;
        ComboState combo = ComboStateRegistry.REGISTRY.get().getValue(stage);
        return combo == null ? 12 : Math.max(1, Math.min(200, (int)Math.ceil(TimeValueHelper.getTicksFromFrames(
            Math.max(1, Math.abs(combo.getEndFrame() - combo.getStartFrame()))) / Math.max(0.1, combo.getSpeed()))));
    }
    @Override public JsonObject presentation(CombatContext ctx, ActionRegistry.Definition action) {
        JsonObject out = BladeAdapter.super.presentation(ctx, action);
        for (Parameter parameter : action.info.parameters) if (parameter.id.equals("artsType")) out.addProperty("defaultArtsType", String.valueOf(parameter.defaultValue));
        if (action.backend.startsWith("sa:")) {
            SlashArts art = SlashArtsRegistry.REGISTRY.get().getValue(new ResourceLocation(action.backend.substring(3)));
            if (art != null) {
                JsonObject stages = new JsonObject(), durations = new JsonObject();
                for (SlashArts.ArtsType type : new SlashArts.ArtsType[]{SlashArts.ArtsType.Success, SlashArts.ArtsType.Jackpot, SlashArts.ArtsType.Super}) {
                    ResourceLocation stage = art.doArts(type, ctx.npc);
                    if (stage != null && !stage.equals(ComboStateRegistry.NONE.getId()) && ComboStateRegistry.REGISTRY.get().containsKey(stage)) {
                        stages.addProperty(type.name(), stage.toString()); durations.addProperty(type.name(), duration(stage));
                    }
                }
                out.add("previewStages", stages); out.add("previewDurations", durations);
            }
        } else if (action.backend.equals("guard")) out.addProperty("stageId", ComboStateRegistry.COMBO_A1_END2.getId().toString());
        else if (action.backend.equals("counter")) out.addProperty("stageId", ComboStateRegistry.COMBO_A1.getId().toString());
        return out;
    }
    @Override public boolean canChain(CombatContext ctx, ActionRegistry.Definition action, JsonObject params) {
        ISlashBladeState state = blade(ctx.weapon); ResourceLocation current = resolve(ctx, state);
        ComboState nativeCombo = ComboStateRegistry.REGISTRY.get().getValue(current);
        if (nativeCombo == null || state.getElapsedTime(ctx.npc) < 4) return false;
        if (action.backend.startsWith("combo:") && action.backend.substring(6).equals(String.valueOf(nativeCombo.getNext(ctx.npc)))
                && !current.toString().equals(action.backend.substring(6))) return true;
        JsonObject phase = new JsonObject(); MaidPhaseRules.describe(state, ctx.npc, current, true, phase);
        return phase.get("canInterrupt").getAsBoolean() || action.info.type.equals("SPECIAL_ATTACK") && phase.get("perfectCharge").getAsBoolean();
    }
    private ResourceLocation resolve(CombatContext ctx, ISlashBladeState state) {
        ResourceLocation key = state.getComboSeq();
        if (key == null) {
            key = ComboStateRegistry.NONE.getId(); state.setComboSeq(key); state.setLastActionTime(ctx.now);
        }
        ComboState combo = ComboStateRegistry.REGISTRY.get().getValue(key);
        long elapsed = Math.max(0, state.getElapsedTime(ctx.npc)) * 50;
        if (combo == null || combo.getTimeoutMS() <= 0 && !key.equals(ComboStateRegistry.NONE.getId())) {
            state.setComboSeq(ComboStateRegistry.NONE.getId()); state.setLastActionTime(ctx.now);
            return ComboStateRegistry.NONE.getId();
        }
        // A single native timeout transition per tick avoids malformed addon cycles.
        if (combo != null && !key.equals(ComboStateRegistry.NONE.getId()) && elapsed > combo.getTimeoutMS()) {
            ResourceLocation next = combo.getNextOfTimeout(ctx.npc);
            var event = new SlashBladeEvent.NextOfTimeOutComboEvent(ctx.weapon, state, ctx.npc, next);
            MinecraftForge.EVENT_BUS.post(event);
            next = event.isCanceled() ? ComboStateRegistry.NONE.getId() : event.getNextCombo();
            if (next == null || next.equals(key) || !ComboStateRegistry.REGISTRY.get().containsKey(next)) next = ComboStateRegistry.NONE.getId();
            state.updateComboSeq(ctx.npc, next); key = state.getComboSeq();
            if (key == null) { key = ComboStateRegistry.NONE.getId(); state.setComboSeq(key); }
            state.setLastActionTime(ctx.now);
            if (!key.equals(ComboStateRegistry.NONE.getId())) {
                bincnpc.gun.common.network.GunNetwork.animation(ctx, "COMBO_ATTACK", duration(key), "combo:" + key);
                ctx.npc.updateClient = true;
            }
            ComboState resolved = ComboStateRegistry.REGISTRY.get().getValue(key);
            if (resolved == null || resolved.getTimeoutMS() <= 0 && !key.equals(ComboStateRegistry.NONE.getId())) {
                key = ComboStateRegistry.NONE.getId(); state.setComboSeq(key);
            }
        }
        return key;
    }
    @Override public JsonObject state(CombatContext ctx) {
        ISlashBladeState state = blade(ctx.weapon); ResourceLocation combo = resolve(ctx, state);
        JsonObject out = new JsonObject();
        out.addProperty("combo", combo.toString()); out.addProperty("resource", state.getProudSoulCount());
        out.addProperty("broken", state.isBroken()); out.addProperty("sealed", state.isSealed());
        out.addProperty("elapsed", state.getElapsedTime(ctx.npc)); out.addProperty("specialAttack", arts.getOrDefault(state.getSlashArtsKey(), ""));
        MaidPhaseRules.describe(state, ctx.npc, combo, ctx.config.text("slashblade.preset").equals("MAID"), out);
        return out;
    }
    @Override public void tick(CombatContext ctx) {
        if (!CombatService.serverThread(ctx.npc)) return;
        ISlashBladeState state = blade(ctx.weapon);
        var maid = ctx.runtime.maid;
        if (maid.lastTick != ctx.now) {
            long elapsed = maid.lastTick == Long.MIN_VALUE ? 1 : Math.min(100, Math.max(1, ctx.now - maid.lastTick));
            maid.lastTick = ctx.now; maid.pressure = Math.max(0, maid.pressure - 2 * elapsed);
            if (maid.guardEnd <= ctx.now) maid.guardBudget = 0;
            if (maid.escapeAt > 0 && ctx.now >= maid.escapeAt) {
                maid.escapeAt = 0;
                if (ctx.config.text("slashblade.preset").equals("MAID")) maid.escapeUntil = ctx.now + 20;
                else CombatService.retreat(ctx);
            }
        }
        ctx.npc.getCapability(CapabilityInputState.INPUT_STATE).ifPresent(input -> {
            input.getCommands().clear();
            if (ctx.validTarget() && ctx.runtime.command.equals("AUTO") && ctx.config.bool("slashblade.autoAttack")
                    && !ctx.data.throwing.blocksAttack(ctx.now)) {
                input.getCommands().add(InputCommand.R_DOWN); input.getCommands().add(InputCommand.SNEAK);
                if (ctx.distance > CombatMovement.meleeReach(ctx) && ctx.now >= maid.escapeUntil && !ctx.data.throwing.movementLocked()) input.getCommands().add(InputCommand.FORWARD);
            }
        });
        state.setTargetEntityId(ctx.validTarget() ? ctx.target : null);
        resolve(ctx, state);
        // CNPC's mob tick does not run held ItemStack.inventoryTick.
        ((ItemSlashBlade)ctx.weapon.getItem()).inventoryTick(ctx.weapon, ctx.npc.level(), ctx.npc, 0, true);
    }
    @Override public Result execute(CombatContext ctx, ActionRegistry.Definition action, JsonObject params) {
        if (!CombatService.serverThread(ctx.npc) || ctx.runtime.editorPaused) return Result.fail("PERMISSION_DENIED");
        ISlashBladeState state = blade(ctx.weapon);
        if (state.isBroken() || state.isSealed()) return Result.fail("CONDITION_NOT_MET");
        state.setTargetEntityId(ctx.validTarget() ? ctx.target : null);
        ctx.npc.setYRot(ctx.yaw()); ctx.npc.setXRot(ctx.pitch()); ctx.npc.yHeadRot = ctx.yaw();
        JsonObject options = ctx.config.action(action.info.id);
        int cost = action.info.resourceCost == null ? 0 : action.info.resourceCost;
        cost = (int)Math.ceil(cost * ActionConfig.value(options, "resourceMultiplier").getAsDouble());
        int reserve = Math.max(ctx.config.integer("slashblade.resourceReserve"), ActionConfig.value(options, "resourceReserve").getAsInt());
        if (ctx.config.bool("slashblade.ignoreRequirements")) { cost = 0; reserve = 0; }
        if (action.backend.startsWith("phantom:")) return phantom(ctx, state, action.backend.substring(8), params, cost, reserve);
        if (state.getProudSoulCount() - cost < reserve) return Result.fail("INSUFFICIENT_RESOURCE");
        if (action.backend.startsWith("combo:")) return combo(ctx, state, new ResourceLocation(action.backend.substring(6)), 0);
        if (action.backend.startsWith("sa:")) {
            ResourceLocation key = new ResourceLocation(action.backend.substring(3));
            SlashArts art = SlashArtsRegistry.REGISTRY.get().getValue(key);
            if (art == null) return Result.fail("NOT_FOUND");
            SlashArts.ArtsType type = SlashArts.ArtsType.valueOf(params.get("artsType").getAsString());
            ResourceLocation combo = art.doArts(type, ctx.npc);
            var event = new SlashBladeEvent.PerformSlashArtEvent(ctx.npc, (int)state.getElapsedTime(ctx.npc), state, combo, type);
            if (MinecraftForge.EVENT_BUS.post(event)) return Result.fail("CONDITION_NOT_MET");
            return combo(ctx, state, event.getComboState(), cost);
        }
        return switch (action.backend) {
            case "lock" -> Result.success();
            case "guard" -> {
                if (!ctx.npc.onGround() || !ctx.config.bool("slashblade.ignoreRequirements") && ctx.weapon.getEnchantmentLevel(Enchantments.THORNS) <= 0) yield Result.fail("CONDITION_NOT_MET");
                ctx.runtime.adapterState.put("guardStart", ctx.now);
                ctx.runtime.adapterState.put("guardEnd", ctx.now + params.get("duration").getAsInt());
                ctx.runtime.maid.guardStart = ctx.now; ctx.runtime.maid.guardEnd = ctx.now + params.get("duration").getAsInt();
                ctx.runtime.maid.guardBudget = ctx.npc.getMaxHealth() * (ctx.config.text("slashblade.preset").equals("MAID") ? 1 : 0.2);
                yield Result.success();
            }
            case "counter" -> {
                if (ctx.now - ctx.runtime.lastHurt > 12) yield Result.fail("CONDITION_NOT_MET");
                yield combo(ctx, state, ComboStateRegistry.COMBO_A1.getId(), 0);
            }
            case "teleport" -> {
                if (!ctx.validTarget() || ctx.distance > 32) yield Result.fail("OUT_OF_RANGE");
                Vec3 offset = ctx.npc.position().subtract(ctx.target.position()).normalize().scale(params.get("distance").getAsDouble());
                Vec3 next = ctx.target.position().add(offset);
                if (!ctx.npc.level().hasChunkAt(net.minecraft.core.BlockPos.containing(next))) yield Result.fail("CONDITION_NOT_MET");
                boolean moved = ctx.npc.randomTeleport(next.x, next.y, next.z, true);
                if (moved) { Untouchable.setUntouchable(ctx.npc, SlayerStyleArts.TRICK_ACTION_UNTOUCHABLE_TIME); ctx.npc.getNavigation().stop(); }
                yield moved ? Result.success() : Result.fail("CONDITION_NOT_MET");
            }
            default -> Result.fail("UNSUPPORTED");
        };
    }
    private Result combo(CombatContext ctx, ISlashBladeState state, ResourceLocation combo, int cost) {
        if (combo == null || combo.equals(ComboStateRegistry.NONE.getId()) || !ComboStateRegistry.REGISTRY.get().containsKey(combo))
            return Result.fail("UNSUPPORTED");
        int before = state.getProudSoulCount();
        ResourceLocation current = state.getComboSeq(); ComboState phase = ComboStateRegistry.REGISTRY.get().getValue(current);
        if (current.equals(ComboStateRegistry.RAPID_SLASH.getId()) && combo.equals(ComboStateRegistry.RISING_STAR.getId())
                && ctx.validTarget() && AttackManager.areaAttack(ctx.npc, KnockBacks.toss.action, 0.44F, true, true, true).isEmpty())
            return Result.fail("CONDITION_NOT_MET");
        state.setProudSoulCount(before - cost);
        if (phase != null && !current.equals(combo) && combo.equals(phase.getNext(ctx.npc))) state.progressCombo(ctx.npc);
        else state.updateComboSeq(ctx.npc, combo);
        if (state.getLastActionTime() != ctx.now || state.getComboSeq() == null || state.getComboSeq().equals(ComboStateRegistry.NONE.getId())) {
            state.setProudSoulCount(before); return Result.fail("CONDITION_NOT_MET");
        }
        return Result.success(new BladeAdapter.Execution(state.getComboSeq().toString(), duration(state.getComboSeq())));
    }
    private Result phantom(CombatContext ctx, ISlashBladeState state, String kind, JsonObject params, int baseCost, int reserve) {
        if (kind.equals("spiral_fire")) {
            boolean fired = false;
            for (Entity entity : ctx.npc.getPassengers()) if (entity instanceof EntitySpiralSwords sword) { sword.doFire(); fired = true; }
            return fired ? Result.success() : Result.fail("CONDITION_NOT_MET");
        }
        var entityType = switch (kind) {
            case "spiral" -> SlashBlade.RegistryEvents.SpiralSwords;
            case "storm" -> SlashBlade.RegistryEvents.StormSwords;
            case "blistering" -> SlashBlade.RegistryEvents.BlisteringSwords;
            case "rain" -> SlashBlade.RegistryEvents.HeavyRainSwords;
            default -> SlashBlade.RegistryEvents.SummonedSword;
        };
        if (entityType == null) return Result.fail("UNSUPPORTED");
        boolean unrestricted = ctx.config.bool("slashblade.ignoreRequirements");
        if (!unrestricted && !SwordType.from(ctx.weapon).contains(SwordType.BEWITCHED)) return Result.fail("CONDITION_NOT_MET");
        int power = ctx.weapon.getEnchantmentLevel(Enchantments.POWER_ARROWS);
        if (!unrestricted && power <= 0) return Result.fail("CONDITION_NOT_MET");
        if (unrestricted) power = ctx.config.text("slashblade.preset").equals("MAID") ? (Math.max(0, power) + 4) * 2 : Math.max(1, power);
        int count = params.get("count").getAsInt();
        int baseline = kind.equals("basic") ? 1 : kind.equals("rain") ? 18 : 6;
        int cost = (int)Math.ceil(baseCost * Math.max(1, count / (double)baseline));
        if (state.getProudSoulCount() - cost < reserve) return Result.fail("INSUFFICIENT_RESOURCE");
        long existing = ctx.npc.level().getEntitiesOfClass(EntityAbstractSummonedSword.class, ctx.npc.getBoundingBox().inflate(40),
            sword -> sword.getOwner() == ctx.npc).stream().limit(65).count();
        if (existing + count > 64) return Result.fail("BUSY");
        List<LivingEntity> targets = new ArrayList<>();
        if (ctx.validTarget()) targets.add(ctx.target);
        if (params.has("targetCount")) for (LivingEntity target : ctx.targets())
            if (!targets.contains(target) && targets.size() < params.get("targetCount").getAsInt()) targets.add(target);
        if (kind.equals("basic") && ctx.config.text("slashblade.preset").equals("MAID"))
            targets.removeIf(target -> Math.abs(target.getY() - ctx.npc.getY()) >= 7);
        if (targets.isEmpty() && !kind.equals("spiral")) return Result.fail("INVALID_TARGET");
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            EntityAbstractSummonedSword sword = switch (kind) {
                case "spiral" -> new EntitySpiralSwords(SlashBlade.RegistryEvents.SpiralSwords, ctx.npc.level());
                case "storm" -> new EntityStormSwords(SlashBlade.RegistryEvents.StormSwords, ctx.npc.level());
                case "blistering" -> new EntityBlisteringSwords(SlashBlade.RegistryEvents.BlisteringSwords, ctx.npc.level());
                case "rain" -> new EntityHeavyRainSwords(SlashBlade.RegistryEvents.HeavyRainSwords, ctx.npc.level());
                default -> new EntityAbstractSummonedSword(SlashBlade.RegistryEvents.SummonedSword, ctx.npc.level());
            };
            LivingEntity target = targets.isEmpty() ? null : targets.get(i % targets.size());
            sword.setOwner(ctx.npc); sword.setColor(state.getColorCode()); sword.setDamage(power); sword.setRoll(0);
            sword.setPos(ctx.npc.position());
            int delay = params.has("delay") ? params.get("delay").getAsInt() : 0;
            if (kind.equals("basic")) {
                Vec3 side = ctx.shotDirection().cross(new Vec3(0, 1, 0)).normalize().scale((i % 2 == 0 ? 1 : -1) * 0.5);
                Vec3 pos = ctx.npc.getEyePosition().add(side);
                sword.setPos(pos);
                Vec3 direction = target.getEyePosition().subtract(pos).normalize();
                sword.shoot(direction.x, direction.y, direction.z, 3, 0);
                sword.setRoll(ctx.npc.getRandom().nextFloat() * 360);
            } else if (kind.equals("storm")) { sword.startRiding(target, true); sword.setDelay(delay + 360 / count * i); }
            else if (kind.equals("rain")) {
                sword.startRiding(ctx.npc, true); sword.setDelay(delay + i);
                ((EntityHeavyRainSwords)sword).setSpread(target.position().add(0, 7, 0)); sword.setXRot(-90);
            } else { sword.startRiding(ctx.npc, true); sword.setDelay(delay + (kind.equals("spiral") ? 360 / count * i : i)); }
            if (ctx.npc.level().addFreshEntity(sword)) {
                if (spawned++ == 0) state.setProudSoulCount(state.getProudSoulCount() - cost);
            } else sword.stopRiding();
        }
        if (spawned == 0) return Result.fail("INTERNAL_ERROR");
        ctx.npc.level().playSound(null, ctx.npc.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.HOSTILE, 0.2F, 1.45F);
        return Result.success(spawned);
    }
    private void slashed(SlashBladeEvent.DoSlashEvent event) {
        if (event.getUser() instanceof EntityNPCInterface npc && CombatService.serverThread(npc) && CombatService.validNpc(npc)
                && CombatService.data(npc).runtime.adapter == this) CombatService.data(npc).runtime.maid.lastSlash = npc.level().getGameTime();
    }
    private void guard(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof EntityNPCInterface npc) || !CombatService.serverThread(npc) || !CombatService.validNpc(npc)) return;
        CombatContext ctx = new CombatContext(npc);
        if (ctx.runtime.adapter != this || !AdapterRegistry.loaded(id())) return;
        AdapterRegistry.run(this, () -> guardNpc(event, ctx));
    }
    private void guardNpc(LivingAttackEvent event, CombatContext ctx) {
        EntityNPCInterface npc = ctx.npc;
        if (!ctx.config.bool("combat.enabled") || !ctx.config.bool("slashblade.enabled") || !ctx.config.bool("slashblade.counter")
                || ctx.runtime.editorPaused || !ctx.runtime.command.equals("AUTO")
                || event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        ISlashBladeState state = blade(ctx.weapon); if (state.isBroken() || state.isSealed()) return;
        boolean highest = ctx.config.text("slashblade.preset").equals("MAID");
        String guardId = "slashblade:cnpcgun/spin_guard";
        if (highest && !ctx.data.blade.autoEnabled(ctx, guardId)) return;
        if (!npc.onGround() || !ctx.config.bool("slashblade.ignoreRequirements") && ctx.weapon.getEnchantmentLevel(Enchantments.THORNS) <= 0) return;
        Entity source = event.getSource().getEntity();
        if (!(source instanceof LivingEntity attacker) || !CombatService.validTarget(npc, attacker)) return;
        var maid = ctx.runtime.maid;
        if (highest && ctx.now < maid.guardJustEnd) { event.setCanceled(true); return; }
        if (highest && maid.guardBudget <= 0) {
            maid.pressure += 150; maid.accumulatedDamage += Math.max(0, event.getAmount());
            if ((maid.pressure > 500 || maid.accumulatedDamage > npc.getMaxHealth() * 0.2) && ctx.now >= maid.guardCooldown) {
                if (ctx.data.blade.eligible(ctx, guardId) && ctx.data.blade.execute(ctx, guardId, new JsonObject()).ok) {
                    maid.pressure = 0; maid.accumulatedDamage = 0;
                }
            }
            return;
        }
        if (!(ctx.runtime.adapterState.get("guardEnd") instanceof Long until) || until <= ctx.now || maid.guardBudget <= 0) return;
        if (!highest && !Guard.getInstance().isInsideGuardableRange(event.getSource(), npc)) return;
        long start = (Long)ctx.runtime.adapterState.getOrDefault("guardStart", ctx.now);
        boolean just = ctx.now - (highest ? maid.lastSlash : start) < 3 + ctx.weapon.getEnchantmentLevel(Enchantments.SOUL_SPEED);
        Entity direct = event.getSource().getDirectEntity();
        if (!highest && !just && !(direct instanceof net.minecraft.world.entity.projectile.Projectile)) return;
        if (!highest && event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR) && !just) return;
        event.setCanceled(true);
        if (!just) {
            maid.guardBudget = Math.max(0, maid.guardBudget - Math.max(0, event.getAmount()));
            state.updateComboSeq(npc, maid.guardBudget > 0 ? ComboStateRegistry.COMBO_A1_END2.getId() : ComboStateRegistry.COMBO_A1.getId());
            if (maid.guardBudget <= 0) { maid.guardCooldown = ctx.now + 300; maid.guardEnd = ctx.now; maid.escapeAt = ctx.now + 5; }
        }
        if (direct instanceof net.minecraft.world.entity.projectile.Projectile projectile) {
            projectile.setOwner(npc); ArrowReflector.doReflect(projectile, npc);
        } else if (direct instanceof LivingEntity living) living.knockback(0.5, living.getX() - npc.getX(), living.getZ() - npc.getZ());
        if (just) {
            maid.guardJustEnd = ctx.now + 10;
            maid.specialBurst = 0; maid.specialCooldown = 0; maid.nextPhantom = 0;
            for (ActionRegistry.Definition def : ActionRegistry.INSTANCE.all()) if (def.adapterId.equals(id()) && def.info.type.equals("PHANTOM_SWORD")) ctx.runtime.cooldowns.remove(def.info.id);
            ctx.runtime.cooldowns.remove(maid.specialAction);
        }
        bincnpc.gun.common.network.GunNetwork.animation(ctx, "DEFENSE", 8, "combo:" + state.getComboSeq());
        ctx.runtime.lastHurt = ctx.now;
    }
    @Override public Result interruptForThrow(CombatContext ctx) {
        if (!matches(ctx.weapon)) return Result.fail("NO_COMPATIBLE_WEAPON");
        JsonObject phase = state(ctx);
        if (!phase.get("idle").getAsBoolean() && !phase.get("canInterrupt").getAsBoolean()) return Result.fail("BUSY");
        ctx.npc.getCapability(CapabilityInputState.INPUT_STATE).ifPresent(input -> input.getCommands().clear());
        ISlashBladeState state = blade(ctx.weapon);
        state.setComboSeq(ComboStateRegistry.NONE.getId()); state.setLastActionTime(ctx.now); state.setTargetEntityId(null);
        ctx.npc.updateClient = true;
        return Result.success();
    }
    @Override public void cancel(CombatContext ctx) {
        ctx.runtime.adapterState.remove("guardStart"); ctx.runtime.adapterState.remove("guardEnd");
        ctx.runtime.maid.reset();
        ctx.npc.getCapability(CapabilityInputState.INPUT_STATE).ifPresent(input -> input.getCommands().clear());
        if (matches(ctx.weapon)) {
            ISlashBladeState state = blade(ctx.weapon);
            state.setComboSeq(ComboStateRegistry.NONE.getId()); state.setLastActionTime(ctx.now);
            state.setTargetEntityId(null);
        }
    }
}
