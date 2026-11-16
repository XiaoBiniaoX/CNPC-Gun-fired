package bincnpc.gun.common.combat;

import bincnpc.gun.common.adapter.*;
import bincnpc.gun.common.api.*;
import bincnpc.gun.common.config.*;
import bincnpc.gun.common.npc.*;
import bincnpc.gun.common.script.EventBridge;
import bincnpc.gun.gun.adapter.GunAdapter;
import bincnpc.gun.gun.ammo.AmmoInventory;
import bincnpc.gun.gun.ammo.AmmoSupply;
import bincnpc.gun.gun.switching.Armory;
import bincnpc.gun.common.network.EditorWeapons;
import bincnpc.gun.slashblade.adapter.BladeAdapter;
import java.util.*;
import com.google.gson.*;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import noppes.npcs.entity.EntityNPCInterface;

public final class CombatService {
    private CombatService() {}
    public static NpcData data(EntityNPCInterface npc) { return ((NpcAccess)npc).cnpcgun$data(); }
    public static boolean validNpc(EntityNPCInterface npc) {
Object cnpcgun$value0; WorldServer cnpcgun$match0;

        return npc != null && data(npc).persistenceAvailable && npc.isEntityAlive() && !npc.isDead && !npc.isKilled()
            && ((cnpcgun$value0 = npc.world) instanceof WorldServer && (cnpcgun$match0 = (WorldServer)cnpcgun$value0) != null) && cnpcgun$match0.getEntityFromUuid(npc.getUniqueID()) == npc;
    }
    public static boolean serverThread(EntityNPCInterface npc) {
        return npc != null && npc.getServer() != null && npc.getServer().isCallingFromMinecraftThread() && !npc.world.isRemote;
    }
    public static boolean validTarget(EntityNPCInterface npc, EntityLivingBase target) {
Object cnpcgun$value2; WorldServer cnpcgun$match2;

Object cnpcgun$value1; net.minecraft.entity.player.EntityPlayer cnpcgun$match1;

        return npc != null && target != null && target != npc && target.isEntityAlive() && !target.isDead && !bincnpc.gun.common.legacy.Platform.spectator(target)
            && target.world == npc.world && !bincnpc.gun.common.legacy.Platform.allied(npc, target) && npc.canAttackClass(target.getClass())
            && (!(((cnpcgun$value1 = target) instanceof net.minecraft.entity.player.EntityPlayer && (cnpcgun$match1 = (net.minecraft.entity.player.EntityPlayer)cnpcgun$value1) != null)) || !cnpcgun$match1.capabilities.isCreativeMode)
            && (!(((cnpcgun$value2 = npc.world) instanceof WorldServer && (cnpcgun$match2 = (WorldServer)cnpcgun$value2) != null)) || cnpcgun$match2.getEntityFromUuid(target.getUniqueID()) == target);
    }
    public static boolean isControlled(EntityNPCInterface npc) {
        if (!(npc instanceof NpcAccess) || npc.world.isRemote) return false;
        RuntimeState runtime = data(npc).runtime;
        return runtime.adapter != null && AdapterRegistry.loaded(runtime.adapter.id()) || runtime.movement.meleeFallback
            || data(npc).throwing.blocksAttack(npc.world.getTotalWorldTime());
    }
    public static boolean blocksNative(EntityNPCInterface npc) {
        if (npc == null || npc.world.isRemote || !(npc instanceof NpcAccess)) return false;
        if (isControlled(npc)) return true;
        return AdapterRegistry.find(noppes.npcs.api.wrapper.ItemStackWrapper.MCItem(npc.inventory.getRightHand())) != null;
    }

    public static void tick(EntityNPCInterface npc) {
        if (!serverThread(npc) || !validNpc(npc) || !AdapterRegistry.ready() || npc.isRiding()) return;
        NpcData state = data(npc);
        state.throwItems.onAlive();
        if (state.runtime.editorUntil > npc.world.getTotalWorldTime()) return;
        if (state.runtime.editorPaused) endEditing(new CombatContext(npc));
        else if (state.runtime.restoreSupplies) restoreDefaults(new CombatContext(npc));
        acquirePrefireTarget(npc);
        if (!state.config.bool("combat.allowTargetChange") && state.runtime.targetId != null
                && (npc.getAttackTarget() == null || !state.runtime.targetId.equals(npc.getAttackTarget().getUniqueID()))) {
Object cnpcgun$value3; EntityLivingBase cnpcgun$match3;

            Entity previous = ((WorldServer)npc.world).getEntityFromUuid(state.runtime.targetId);
            if (((cnpcgun$value3 = previous) instanceof EntityLivingBase && (cnpcgun$match3 = (EntityLivingBase)cnpcgun$value3) != null) && validTarget(npc, cnpcgun$match3)) npc.setAttackTarget(cnpcgun$match3);
        }
        CombatContext ctx = new CombatContext(npc);
        if (ctx.runtime.lastTick == ctx.now) return;
        ctx.runtime.lastTick = ctx.now;
        refresh(ctx);
        ctx.runtime.scheduler.expire(ctx.now);
        AmmoSupply.pickup(ctx);
        CombatMovement.prepare(ctx);
        if (!ctx.config.bool("combat.enabled")) {
            ctx.data.throwing.cancel();
            if (!ctx.runtime.combatState.equals("DISABLED")) {
                if (ctx.runtime.adapter != null) AdapterRegistry.run(ctx.runtime.adapter, () -> ctx.runtime.adapter.cancel(ctx));
                ctx.data.blade.reset(); ctx.runtime.scheduler.clear(); ctx.npc.getNavigator().clearPath();
                ctx.runtime.reloading = false; ctx.runtime.reloadTargetTicks = 0; ctx.runtime.reloadObserved = false;
            }
            setState(ctx, "DISABLED");
            EventBridge.flush(ctx); return;
        }
        if (ctx.runtime.adapter != null) AdapterRegistry.run(ctx.runtime.adapter, () -> ctx.runtime.adapter.tick(ctx));
        if (ctx.runtime.adapter != null && !AdapterRegistry.loaded(ctx.runtime.adapter.id())) refresh(ctx);
        UUID target = ctx.validTarget() ? ctx.target.getUniqueID() : null;
        if (!Objects.equals(target, ctx.runtime.targetId)) {
            ctx.runtime.targetId = target; ctx.runtime.firstTargetTick = ctx.now;
            if (target != null) ctx.runtime.fireStopped = false;
            ctx.runtime.nextShot = ctx.now + ctx.config.integer("gun.firstDelay") + ctx.config.integer("gun.windup");
            EventBridge.emit(ctx, "combat.target_changed", "", Result.success());
        }
        if (ctx.target != null && !ctx.validTarget()) npc.setAttackTarget(null);
        if (!ctx.validTarget()) {
            setState(ctx, "IDLE");
            if (ctx.config.bool("combat.stopOnInvalidTarget") && ctx.runtime.scheduler.interruptible
                    && ctx.runtime.scheduler.channel.equals("GUN")) ctx.data.gun.stop(ctx);
        }
        if (ctx.now >= ctx.runtime.noAmmoRetry && ctx.runtime.noAmmoEvent) {
            long fingerprint = new AmmoInventory(npc).fingerprint();
            if (fingerprint != ctx.runtime.inventoryFingerprint) {
                ctx.runtime.noAmmoRetry = 0; ctx.runtime.inventoryFingerprint = fingerprint;
            }
        }
        if (!ctx.runtime.command.equals("AUTO") || !ctx.config.bool("weapon.autoSwitch")) {
            ctx.runtime.movement.ammoFallback = false; ctx.runtime.movement.pursueSlot = -1;
        }
        // Exhaustion must be resolved before a throwable can acquire the movement/attack lock.
        if (ctx.runtime.command.equals("AUTO") && ctx.config.bool("weapon.autoSwitch") && ctx.validTarget()
                && (ctx.runtime.movement.ammoFallback || ctx.runtime.movement.pursueSlot >= 0
                    || ctx.runtime.adapter instanceof GunAdapter && !currentGunUsable(ctx))) {
            int before = ctx.data.armory.active;
            pursueMelee(ctx);
            if (before != ctx.data.armory.active) { invalidateDecision(ctx); EventBridge.flush(ctx); return; }
        }
        ctx.data.throwing.tick(ctx);
        pollDecision(ctx);
        queueDecision(ctx);
        boolean decisionDue = ctx.now >= ctx.runtime.nextDecision;
        if (decisionDue && ctx.runtime.command.equals("AUTO") && ctx.config.bool("weapon.autoSwitch") && ctx.validTarget()
                && !ctx.runtime.movement.ammoFallback && !ctx.runtime.scheduler.busy(ctx.now) && !ctx.runtime.reloading && !ctx.data.throwing.blocksAttack(ctx.now)) {
            int before = ctx.data.armory.active;
            switchBest(ctx, false);
            if (before != ctx.data.armory.active) { invalidateDecision(ctx); EventBridge.flush(ctx); return; }
        }
        int beforeAction = ctx.data.armory.active;
        if (ctx.runtime.adapter instanceof GunAdapter) ctx.data.gun.tick(ctx);
        else if (ctx.runtime.adapter instanceof BladeAdapter) ctx.data.blade.tick(ctx);
        else CombatMovement.melee(ctx);
        if (beforeAction != ctx.data.armory.active) { invalidateDecision(ctx); EventBridge.flush(ctx); return; }
        if (ctx.data.throwing.blocksAttack(ctx.now)) setState(ctx, "THROWING");
        if (!ctx.runtime.command.equals("AUTO")) {
            if (ctx.runtime.command.equals("RETREAT")) {
                if (ctx.now >= ctx.runtime.nextDecision) retreat(ctx);
            } else { npc.getNavigator().clearPath(); setState(ctx, "WAITING"); }
            ctx.runtime.nextDecision = Math.max(ctx.runtime.nextDecision, ctx.now + ctx.config.integer("combat.decisionInterval"));
            EventBridge.flush(ctx); return;
        }
        if (decisionDue) ctx.runtime.nextDecision = ctx.now + decisionInterval(ctx);
        CombatMovement.tick(ctx);
        CombatMovement.face(ctx.npc);
        bincnpc.gun.common.network.GunNetwork.summary(ctx);
        EventBridge.flush(ctx);
    }

    public static void refresh(CombatContext ctx) {
        WeaponAdapter current = AdapterRegistry.find(ctx.weapon);
        String id = current == null ? "" : AdapterRegistry.query(current, () -> current.weaponId(ctx.weapon), "");
        if (current == ctx.runtime.adapter && id.equals(ctx.runtime.weaponId) && ctx.weapon == ctx.runtime.lastStack) {
            if (ctx.now >= ctx.runtime.nextCapabilityCheck) {
                String prior = SafeJson.GSON.toJson(capabilities(ctx));
                updateCapabilities(ctx, current);
                ctx.runtime.nextCapabilityCheck = ctx.now + ctx.config.integer("combat.decisionInterval");
                if (!prior.equals(SafeJson.GSON.toJson(capabilities(ctx)))) EventBridge.emit(ctx, "weapon.adapter_changed", "", Result.success());
            }
            return;
        }
        WeaponAdapter old = ctx.runtime.adapter;
        ctx.data.throwing.cancel();
        ctx.runtime.weaponEpoch++; invalidateDecision(ctx);
        if (old != null) AdapterRegistry.cancel(old, ctx.withWeapon(ctx.runtime.lastStack));
        ctx.data.blade.reset();
        ctx.runtime.adapterState.clear();
        if (!ctx.runtime.scheduler.channel.equals("SWITCH") || !ctx.runtime.scheduler.busy(ctx.now)) ctx.runtime.scheduler.clear();
        ctx.runtime.actionId = ""; ctx.runtime.comboId = ""; ctx.runtime.comboNode = ""; ctx.runtime.lastBladeAction = "";
        ctx.runtime.adapter = current; ctx.runtime.weaponId = id; ctx.runtime.lastStack = ctx.weapon;
        updateCapabilities(ctx, current);
        ctx.runtime.nextCapabilityCheck = ctx.now + ctx.config.integer("combat.decisionInterval");
        ctx.runtime.domain = current == null ? melee(ctx.weapon) ? "MELEE" : "UNKNOWN" : current.domain();
        if (current != null || !melee(ctx.weapon)) ctx.runtime.movement.meleeFallback = false;
        ctx.runtime.reloading = false; ctx.runtime.reloadTargetTicks = 0; ctx.runtime.reloadObserved = false; ctx.runtime.ammoEmpty = false; ctx.runtime.noAmmoEvent = false;
        ctx.runtime.noAmmoRetry = 0; ctx.runtime.fireStopped = false; ctx.runtime.shotStreak = 0;
        if (current != null) AdapterRegistry.run(current, () -> current.onEquip(ctx));
        EventBridge.emit(ctx, "weapon.changed", "", Result.success());
        if (old != current) EventBridge.emit(ctx, "weapon.adapter_changed", "", Result.success());
    }
    private static void updateCapabilities(CombatContext ctx, WeaponAdapter current) {
Object cnpcgun$value5; BladeAdapter cnpcgun$match5;

Object cnpcgun$value4; GunAdapter cnpcgun$match4;

        ctx.runtime.gunCaps = ((cnpcgun$value4 = current) instanceof GunAdapter && (cnpcgun$match4 = (GunAdapter)cnpcgun$value4) != null) ? AdapterRegistry.query(cnpcgun$match4, () -> cnpcgun$match4.capabilities(ctx.weapon), null) : null;
        ctx.runtime.bladeCaps = ((cnpcgun$value5 = current) instanceof BladeAdapter && (cnpcgun$match5 = (BladeAdapter)cnpcgun$value5) != null) ? AdapterRegistry.query(cnpcgun$match5, () -> cnpcgun$match5.capabilities(ctx.weapon), null) : null;
    }
    public static boolean melee(ItemStack stack) { return stack != null && (stack.getItem() instanceof ItemSword || stack.getItem() instanceof ItemAxe); }
    public static Views.WeaponInfo weaponInfo(ItemStack stack, String forcedId) {
        if (stack == null || stack.isEmpty()) return null;
        WeaponAdapter adapter = AdapterRegistry.find(stack);
        String registry = String.valueOf(stack.getItem().getRegistryName());
        String id = forcedId != null ? forcedId : adapter == null ? registry : AdapterRegistry.query(adapter, () -> adapter.weaponId(stack), registry);
        return new Views.WeaponInfo(id, adapter == null ? melee(stack) ? "MELEE" : "UNKNOWN" : adapter.domain(),
            registry, stack.getDisplayName(), adapter == null ? "" : adapter.id(), adapter != null || melee(stack));
    }
    public static boolean hasCapability(CombatContext ctx, String id) {
        return ctx.runtime.gunCaps != null && ctx.runtime.gunCaps.has(id) || ctx.runtime.bladeCaps != null && ctx.runtime.bladeCaps.has(id);
    }
    public static Views.Capabilities capabilities(CombatContext ctx) {
        return new Views.Capabilities(ctx.runtime.domain, ctx.runtime.gunCaps == null ? null : ctx.runtime.gunCaps.copy(),
            ctx.runtime.bladeCaps == null ? null : SafeJson.GSON.fromJson(SafeJson.GSON.toJson(ctx.runtime.bladeCaps), Views.BladeCaps.class),
            !ctx.runtime.scheduler.busy(ctx.now));
    }
    public static Result setConfig(CombatContext ctx, String key, Object value) {
        Parameter parameter = ConfigSchema.get(key);
        if (parameter == null) return Result.fail("NOT_FOUND");
        if (!parameter.capability.isEmpty() && !EditorWeapons.has(ctx, parameter.capability)) return Result.fail("UNSUPPORTED");
        try {
            JsonElement checked = parameter.validate(SafeJson.copy(value));
            if (key.equals("slashblade.preset")) return bincnpc.gun.slashblade.strategy.Presets.apply(ctx, checked.getAsString());
            if (key.equals("gun.firingMode") && !EditorWeapons.firingMode(ctx, checked.getAsString())) return Result.fail("UNSUPPORTED");
            NpcConfig prospective = new NpcConfig(ctx.config.snapshot());
            prospective.set(key, checked); validateRanges(prospective);
            ctx.config.set(key, checked);
            if (key.equals("gun.enabled") || key.equals("gun.autoAttack")) ctx.runtime.fireStopped = false;
            ctx.runtime.noAmmoRetry = 0;
            return Result.success(SafeJson.plain(checked));
        } catch (IllegalArgumentException | IllegalStateException e) { return Result.fail("INVALID_ARGUMENT", e.getMessage()); }
    }
    public static Result setConfigs(CombatContext ctx, JsonObject values) {
        if (values == null || values.size() > 32 || values.has("slashblade.preset")) return Result.fail("INVALID_ARGUMENT");
        NpcConfig candidate = new NpcConfig(ctx.config.snapshot());
        try {
            for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
                Parameter p = ConfigSchema.get(entry.getKey());
                if (p == null) return Result.fail("NOT_FOUND");
                if (!p.capability.isEmpty() && !EditorWeapons.has(ctx, p.capability)) return Result.fail("UNSUPPORTED");
                JsonElement value = p.validate(entry.getValue());
                if (p.id.equals("gun.firingMode") && !EditorWeapons.firingMode(ctx, value.getAsString())) return Result.fail("UNSUPPORTED");
                candidate.set(p.id, value);
            }
            validateRanges(candidate); ctx.config.replace(candidate.snapshot());
            if (values.has("gun.enabled") || values.has("gun.autoAttack")) ctx.runtime.fireStopped = false;
            ctx.runtime.noAmmoRetry = 0;
            return Result.success();
        } catch (IllegalArgumentException | IllegalStateException e) { return Result.fail("INVALID_ARGUMENT"); }
    }
    public static void validateRanges(NpcConfig config) {
        if (config.number("gun.minRange") > config.number("gun.range")
            || config.integer("gun.minInterval") > config.integer("gun.maxInterval")
            || config.integer("gun.reloadMinTicks") > config.integer("gun.reloadMaxTicks")
            || config.integer("ammo.minimumReserve") > config.integer("ammo.reserveLimit")) throw new IllegalArgumentException("invalid range");
        for (int i = 0; i < Armory.SIZE; i++) if (config.bool("weapon.slot" + i + ".minRangeEnabled") && config.bool("weapon.slot" + i + ".maxRangeEnabled")
            && config.number("weapon.slot" + i + ".minRange") > config.number("weapon.slot" + i + ".maxRange")) throw new IllegalArgumentException("slot range");
        for (int i = 0; i < 3; i++) if (config.number("throw.slot" + i + ".minRange") > config.number("throw.slot" + i + ".maxRange")) throw new IllegalArgumentException("throw range");
    }
    public static Result switchBest(CombatContext ctx, boolean onlyMelee) {
        return switchBest(ctx, onlyMelee, false);
    }
    public static boolean usableGun(CombatContext ctx) {
        for (int slot = 0; slot < Armory.SIZE; slot++) {
Object cnpcgun$value6; GunAdapter cnpcgun$match6;

            ItemStack stack = ctx.data.armory.get(ctx.npc, slot);
            if (!(((cnpcgun$value6 = AdapterRegistry.find(stack)) instanceof GunAdapter && (cnpcgun$match6 = (GunAdapter)cnpcgun$value6) != null))) continue;
            if (usableGun(ctx, cnpcgun$match6, stack)) return true;
        }
        return false;
    }
    public static boolean currentGunUsable(CombatContext ctx) {
Object cnpcgun$value7; GunAdapter cnpcgun$match7;

        return ((cnpcgun$value7 = ctx.runtime.adapter) instanceof GunAdapter && (cnpcgun$match7 = (GunAdapter)cnpcgun$value7) != null) && usableGun(ctx, cnpcgun$match7, ctx.weapon);
    }
    private static boolean usableGun(CombatContext ctx, GunAdapter gun, ItemStack stack) {
        Views.GunCaps caps = AdapterRegistry.query(gun, () -> gun.capabilities(stack), null);
        if (caps == null || !caps.canFire) return false;
        CombatContext view = ctx.withWeapon(stack);
        Views.AmmoState ammo = AdapterRegistry.query(gun, () -> gun.ammo(view), null);
        // Native reload may already own the available rounds; do not cancel it as "empty".
        if (stack == ctx.weapon && ammo != null && ammo.isReloading) return true;
        if (!AdapterRegistry.query(gun, () -> gun.canUse(view, stack), false)) return false;
        if (ctx.config.bool("ammo.infinite") || ammo == null || !ammo.hasMagazine || ammo.magazineAmmo == null || ammo.magazineAmmo > 0) return true;
        return ammo.canReload && (ctx.config.bool("ammo.partialReload") || ammo.reserveAmmo == null || ammo.magazineCapacity == null
            || ammo.reserveAmmo >= ammo.magazineCapacity - ammo.magazineAmmo);
    }
    public static Result pursueMelee(CombatContext ctx) {
        if (ctx == null || !serverThread(ctx.npc) || !validNpc(ctx.npc) || !ctx.validTarget() || ctx.runtime.editorPaused || !ctx.runtime.command.equals("AUTO")
                || !ctx.config.bool("weapon.autoSwitch")) return Result.fail("PERMISSION_DENIED");
        MovementState movement = ctx.runtime.movement;
        if (usableGun(ctx)) {
            if (movement.ammoFallback) invalidateDecision(ctx);
            movement.ammoFallback = false; movement.pursueSlot = -1; return Result.fail("CONDITION_NOT_MET");
        }
        List<CombatDecisions.Weapon> choices = new ArrayList<>();
        for (int slot = 0; slot < Armory.SIZE; slot++) {
Object cnpcgun$value8; BladeAdapter cnpcgun$match8;

            ItemStack stack = ctx.data.armory.get(ctx.npc, slot);
            WeaponAdapter adapter = AdapterRegistry.find(stack);
            if (stack.isEmpty() || adapter instanceof GunAdapter || !(adapter instanceof BladeAdapter) && !melee(stack)) continue;
            if (((cnpcgun$value8 = adapter) instanceof BladeAdapter && (cnpcgun$match8 = (BladeAdapter)cnpcgun$value8) != null)) {
                Views.BladeCaps caps = AdapterRegistry.query(cnpcgun$match8, () -> cnpcgun$match8.capabilities(stack), null);
                if (!ctx.config.bool("slashblade.enabled") || caps == null || !caps.canAttack) continue;
            }
            String key = weaponKey(stack, adapter); if (key.length() > 256) continue;
            choices.add(new CombatDecisions.Weapon(slot, key, true, false, -1, -1, ctx.config.integer("weapon.slot" + slot + ".priority")));
        }
        int best = CombatDecisions.meleeFallback(choices, ctx.data.armory.active);
        if (best < 0) { movement.ammoFallback = false; movement.pursueSlot = -1; return Result.fail("NO_WEAPON"); }
        if (!movement.ammoFallback) {
            invalidateDecision(ctx); movement.nextMove = ctx.now;
            if (ctx.runtime.adapter instanceof GunAdapter) {
                Views.AmmoState ammo = ctx.data.gun.ammo(ctx);
                if (!ctx.runtime.ammoEmpty && ammo != null && ammo.hasMagazine && ammo.magazineAmmo != null && ammo.magazineAmmo <= 0) {
                    ctx.runtime.ammoEmpty = true; EventBridge.emit(ctx, "gun.ammo_empty", "", Result.success());
                }
                if (!ctx.runtime.noAmmoEvent) { ctx.runtime.noAmmoEvent = true; EventBridge.emit(ctx, "gun.no_ammo", "", Result.fail("NO_AMMO")); }
            }
        }
        movement.ammoFallback = true;
        boolean approaching = ctx.distance > CombatMovement.meleeReach(ctx);
        movement.pursueSlot = approaching || best != ctx.data.armory.active ? best : -1;
        if (best != ctx.data.armory.active || approaching) ctx.data.throwing.cancel();
        Result switched = Result.success();
        if (best != ctx.data.armory.active) {
            if (ctx.runtime.scheduler.busy(ctx.now)) {
                if (!ctx.runtime.scheduler.channel.equals("GUN") || !ctx.runtime.scheduler.interruptible) return Result.fail("BUSY");
                ctx.runtime.scheduler.release();
            }
            // A depleted gun's normal switch cooldown must not delay the last usable weapon.
            ctx.runtime.nextSwitch = ctx.now;
            ctx.npc.getNavigator().clearPath(); ctx.npc.getMoveHelper().strafe(0, 0); ctx.npc.moveStrafing = 0; ctx.npc.moveForward = 0;
            switched = ctx.data.armory.switchTo(ctx, best);
            if (!switched.ok) return switched;
            movement.meleeFallback = !(AdapterRegistry.find(ctx.data.armory.get(ctx.npc, best)) instanceof BladeAdapter);
            movement.pursueSlot = approaching ? best : -1;
        }
        if (approaching) {
            if (ctx.now >= movement.nextMove) { CombatMovement.chase(ctx); movement.nextMove = ctx.now + 6; }
            setState(ctx, "APPROACHING");
        } else if (!ctx.data.throwing.blocksAttack(ctx.now) && ctx.runtime.combatState.equals("THROWING")) {
            setState(ctx, "WAITING");
        }
        return switched;
    }
    public static Result switchBest(CombatContext ctx, boolean onlyMelee, boolean requireLoaded) {
        if (!serverThread(ctx.npc) || !validNpc(ctx.npc) || ctx.runtime.editorPaused) return Result.fail("PERMISSION_DENIED");
        if (ctx.runtime.scheduler.busy(ctx.now) || ctx.data.throwing.blocksAttack(ctx.now)) return Result.fail("BUSY");
        if (!ctx.validTarget()) return Result.fail("INVALID_TARGET");
        CombatDecisions.Advice choice = !onlyMelee && !requireLoaded ? advice(ctx) : null;
        if (choice == null || !candidate(ctx, choice.slot(), onlyMelee, requireLoaded, choice.weapon()))
            choice = CombatDecisions.calculate(snapshot(ctx, onlyMelee, requireLoaded));
        if (choice.slot() < 0 || !candidate(ctx, choice.slot(), onlyMelee, requireLoaded, choice.weapon())) return Result.fail("NO_WEAPON");
        return ctx.data.armory.switchTo(ctx, choice.slot());
    }
    private static String weaponKey(ItemStack stack, WeaponAdapter adapter) {
        return adapter == null ? String.valueOf(stack.getItem().getRegistryName())
            : adapter.id() + ":" + AdapterRegistry.query(adapter, () -> adapter.weaponId(stack), "");
    }
    private static boolean candidate(CombatContext ctx, int slot, boolean onlyMelee, boolean requireLoaded, String key) {
Object cnpcgun$value9; GunAdapter cnpcgun$match9;

        if (slot < 0 || slot >= Armory.SIZE || !slotEligible(ctx, slot)) return false;
        ItemStack stack = ctx.data.armory.get(ctx.npc, slot); if (stack.isEmpty()) return false;
        WeaponAdapter adapter = AdapterRegistry.find(stack);
        boolean melee = !(adapter instanceof GunAdapter) && (adapter instanceof BladeAdapter || melee(stack));
        if (onlyMelee && !melee || adapter == null && !melee || !weaponKey(stack, adapter).equals(key)) return false;
        if (((cnpcgun$value9 = adapter) instanceof GunAdapter && (cnpcgun$match9 = (GunAdapter)cnpcgun$value9) != null)) {
            if (!usableGun(ctx, cnpcgun$match9, stack)) return false;
            Views.GunCaps caps = AdapterRegistry.query(cnpcgun$match9, () -> cnpcgun$match9.capabilities(stack), null);
            if (caps == null || !caps.canFire) return false;
            if (requireLoaded && caps.hasMagazine && caps.canReadAmmo && !ctx.config.bool("ammo.infinite")
                    && AdapterRegistry.query(cnpcgun$match9, () -> cnpcgun$match9.loadedRounds(stack), 0) <= 0) return false;
        }
        return !requireLoaded || !melee || ctx.distance <= Math.max(4, ctx.config.number("weapon.meleeDistance"));
    }
    private static CombatDecisions.Snapshot snapshot(CombatContext ctx, boolean onlyMelee, boolean requireLoaded) {
        List<CombatDecisions.Weapon> choices = new ArrayList<>();
        for (int i = 0; i < Armory.SIZE; i++) {
Object cnpcgun$value11; GunAdapter cnpcgun$match11;

Object cnpcgun$value10; GunAdapter cnpcgun$match10;

            ItemStack stack = ctx.data.armory.get(ctx.npc, i);
            if (stack.isEmpty()) continue;
            WeaponAdapter adapter = AdapterRegistry.find(stack);
            boolean melee = !(adapter instanceof GunAdapter) && (adapter instanceof BladeAdapter || melee(stack));
            if (onlyMelee && !melee) continue;
            if (adapter == null && !melee) continue;
            if (((cnpcgun$value10 = adapter) instanceof GunAdapter && (cnpcgun$match10 = (GunAdapter)cnpcgun$value10) != null) && !usableGun(ctx, cnpcgun$match10, stack)) continue;
            if (!slotEligible(ctx, i)) continue;
            int loaded = -1, capacity = -1;
            if (((cnpcgun$value11 = adapter) instanceof GunAdapter && (cnpcgun$match11 = (GunAdapter)cnpcgun$value11) != null)) {
                Views.GunCaps caps = AdapterRegistry.query(cnpcgun$match11, () -> cnpcgun$match11.capabilities(stack), null);
                if (caps == null || !caps.canFire) continue;
                if (caps.hasMagazine && caps.canReadAmmo) loaded = Math.max(-1, Math.min(100000, AdapterRegistry.query(cnpcgun$match11, () -> cnpcgun$match11.loadedRounds(stack), -1)));
                capacity = Math.max(-1, Math.min(100000, AdapterRegistry.query(cnpcgun$match11, () -> cnpcgun$match11.magazineCapacity(stack), -1)));
            }
            String key = weaponKey(stack, adapter); if (key.length() > 256) continue;
            choices.add(new CombatDecisions.Weapon(i, key, melee, adapter instanceof GunAdapter, loaded, capacity, ctx.config.integer("weapon.slot" + i + ".priority")));
        }
        return new CombatDecisions.Snapshot(ctx.npc.getUniqueID(), ctx.target.getUniqueID(), ctx.npc.world.provider.getDimension() + "",
            ctx.now, ctx.config.revision, ctx.runtime.weaponEpoch, ctx.data.armory.active, ctx.runtime.weaponId, ctx.distance,
            Math.max(0, Math.min(1, ctx.npc.getHealth() / Math.max(1, ctx.npc.getMaxHealth()))),
            Math.max(0, Math.min(1, ctx.target.getHealth() / Math.max(1, ctx.target.getMaxHealth()))), Math.min(16, Math.max(1, ctx.targets().size())),
            ctx.config.number("weapon.meleeDistance"), ctx.config.number("weapon.lowHealth"), ctx.now - ctx.runtime.lastHurt <= 8,
            ctx.config.bool("ammo.infinite"), onlyMelee, requireLoaded, choices);
    }
    private static int decisionInterval(CombatContext ctx) {
        return ctx.runtime.adapter instanceof BladeAdapter && ctx.config.text("slashblade.preset").equals("MAID")
            ? Math.min(4, ctx.config.integer("combat.decisionInterval")) : ctx.config.integer("combat.decisionInterval");
    }
    public static CombatDecisions.Advice advice(CombatContext ctx) {
        CombatDecisions.Advice advice = ctx.runtime.decisionAdvice;
        if (advice == null || !advice.currentGeneration() || !serverThread(ctx.npc) || !validNpc(ctx.npc) || !ctx.validTarget() || ctx.runtime.editorPaused
                || !ctx.runtime.command.equals("AUTO") || !ctx.config.bool("combat.enabled")
                || !advice.snapshot().matches(ctx.npc.getUniqueID(), ctx.target.getUniqueID(), ctx.npc.world.provider.getDimension() + "",
                    ctx.now, ctx.config.revision, ctx.runtime.weaponEpoch, ctx.data.armory.active, ctx.runtime.weaponId)
                || Math.abs(advice.snapshot().distance() - ctx.distance) > Math.max(2, ctx.distance * 0.25)) return null;
        return advice;
    }
    private static void pollDecision(CombatContext ctx) {
        if (ctx.runtime.decisionTicket == null || !ctx.runtime.decisionTicket.finished()) return;
        ctx.runtime.decisionAdvice = ctx.runtime.decisionTicket.advice(); ctx.runtime.decisionTicket = null;
        if (advice(ctx) == null) ctx.runtime.decisionAdvice = null;
    }
    private static void queueDecision(CombatContext ctx) {
        if (!ctx.runtime.command.equals("AUTO") || !ctx.validTarget() || ctx.runtime.decisionTicket != null
                || ctx.runtime.decisionCycle == ctx.runtime.nextDecision || ctx.now < ctx.runtime.nextDecision - 2) return;
        ctx.runtime.decisionCycle = ctx.runtime.nextDecision;
        if (!ctx.config.bool("weapon.autoSwitch") && !(ctx.runtime.adapter instanceof BladeAdapter && ctx.config.text("slashblade.preset").equals("MAID"))) return;
        CombatDecisions.Snapshot snapshot = snapshot(ctx, false, false);
        if (!snapshot.weapons().isEmpty()) ctx.runtime.decisionTicket = CombatDecisions.submit(snapshot);
    }
    private static void invalidateDecision(CombatContext ctx) {
        if (ctx.runtime.decisionTicket != null) ctx.runtime.decisionTicket.cancel();
        ctx.runtime.decisionTicket = null; ctx.runtime.decisionAdvice = null; ctx.runtime.decisionCycle = Long.MIN_VALUE;
    }
    public static boolean slotEligible(CombatContext ctx, int slot) {
        String key = "weapon.slot" + slot + ".";
        double health = ctx.npc.getHealth() / Math.max(1, ctx.npc.getMaxHealth());
        return (!ctx.config.bool(key + "maxHealthEnabled") || health <= ctx.config.number(key + "maxHealth"))
            && (!ctx.validTarget() || (!ctx.config.bool(key + "minRangeEnabled") || ctx.distance >= ctx.config.number(key + "minRange"))
                && (!ctx.config.bool(key + "maxRangeEnabled") || ctx.distance <= ctx.config.number(key + "maxRange")));
    }
    public static void beginEditing(CombatContext ctx, long until) {
        invalidateDecision(ctx);
        ctx.data.throwing.cancel();
        CombatMovement.release(ctx);
        if (!ctx.runtime.editorPaused) {
            if (ctx.runtime.adapter != null) AdapterRegistry.cancel(ctx.runtime.adapter, ctx);
            ctx.data.blade.reset(); ctx.runtime.scheduler.clear(); ctx.npc.getNavigator().clearPath();
            ctx.runtime.reloading = false; ctx.runtime.reloadTargetTicks = 0; ctx.runtime.reloadObserved = false; ctx.runtime.aiming = false; ctx.runtime.editorPaused = true;
        }
        ctx.runtime.editorUntil = until;
    }
    public static void endEditing(CombatContext ctx) {
        if (!serverThread(ctx.npc)) return;
        ctx.runtime.editorUntil = 0; ctx.runtime.editorPaused = false; restoreDefaults(ctx);
    }
    private static void restoreDefaults(CombatContext ctx) {
        ctx.data.throwing.cancel();
        if (ctx.runtime.adapter != null) AdapterRegistry.cancel(ctx.runtime.adapter, ctx);
        ctx.data.blade.reset(); ctx.runtime.scheduler.clear(); ctx.runtime.nextSwitch = 0;
        ctx.data.armory.restorePrimary(ctx);
        CombatContext current = new CombatContext(ctx.npc); refresh(current); AmmoSupply.defaults(current);
        ctx.runtime.fireStopped = false; ctx.runtime.command = "AUTO"; ctx.runtime.noAmmoCommand = ""; ctx.runtime.noAmmoRetry = 0;
    }
    public static boolean hostile(EntityNPCInterface npc, EntityLivingBase target) {
Object cnpcgun$value14; net.minecraft.entity.player.EntityPlayer cnpcgun$match14;

Object cnpcgun$value13; EntityNPCInterface cnpcgun$match13;

Object cnpcgun$value12; EntityLiving cnpcgun$match12;

        if (!validTarget(npc, target) || target.isInvisible() && !npc.ais.attackInvisible) return false;
        if (target == npc.getAttackTarget() || ((cnpcgun$value12 = target) instanceof EntityLiving && (cnpcgun$match12 = (EntityLiving)cnpcgun$value12) != null) && cnpcgun$match12.getAttackTarget() == npc) return true;
        if (npc.getFaction() == null) return false;
        if (((cnpcgun$value13 = target) instanceof EntityNPCInterface && (cnpcgun$match13 = (EntityNPCInterface)cnpcgun$value13) != null)) return npc.getFaction().isAggressiveToNpc(cnpcgun$match13);
        if (((cnpcgun$value14 = target) instanceof net.minecraft.entity.player.EntityPlayer && (cnpcgun$match14 = (net.minecraft.entity.player.EntityPlayer)cnpcgun$value14) != null)) return npc.getFaction().isAggressiveToPlayer(cnpcgun$match14);
        return npc.getFaction().getsAttacked && target instanceof net.minecraft.entity.monster.IMob;
    }
    private static void acquirePrefireTarget(EntityNPCInterface npc) {
        NpcData data = data(npc); long now = npc.world.getTotalWorldTime();
        if (!data.config.bool("combat.enabled") || !data.config.bool("gun.enabled") || !data.config.bool("gun.autoAttack")
            || data.config.text("gun.prefire").equals("NONE") || !data.runtime.command.equals("AUTO")
            || now < data.runtime.nextDecision || validTarget(npc, npc.getAttackTarget())) return;
        double range = Math.min(data.config.number("combat.attackRange"), data.config.number("gun.range"));
        EntityLivingBase nearest = null; double distance = range * range;
        for (EntityLivingBase target : npc.world.getEntitiesWithinAABB(EntityLivingBase.class, npc.getEntityBoundingBox().grow(range), entity -> hostile(npc, entity))) {
            double candidate = npc.getDistanceSq(target); if (candidate < distance) { nearest = target; distance = candidate; }
        }
        if (nearest != null) npc.setAttackTarget(nearest);
    }
    public static void rewardKill(EntityNPCInterface owner, EntityLivingBase victim) {
        if (!serverThread(owner) || !validNpc(owner) || victim == null || owner == victim || victim.world != owner.world) return;
        RuntimeState runtime = data(owner).runtime; long now = owner.world.getTotalWorldTime();
        if (Objects.equals(runtime.killAwards.get(victim.getUniqueID()), now)) return;
        runtime.killAwards.put(victim.getUniqueID(), now);
        if (runtime.killAwards.size() > 128) runtime.killAwards.remove(bincnpc.gun.common.legacy.Legacy.keys(runtime.killAwards).iterator().next());
        AmmoSupply.kill(new CombatContext(owner));
    }
    public static Result command(CombatContext ctx, String command) {
        if (!bincnpc.gun.common.legacy.Legacy.set("AUTO", "HOLD", "RETREAT").contains(command)) return Result.fail("INVALID_ARGUMENT");
        if (command.equals("RETREAT") && !ctx.validTarget()) return Result.fail("INVALID_TARGET");
        if (ctx.runtime.scheduler.busy(ctx.now) && !ctx.runtime.scheduler.interruptible) return Result.fail("BUSY");
        if (ctx.runtime.scheduler.busy(ctx.now) && ctx.runtime.adapter instanceof BladeAdapter) {
            JsonObject phase = ctx.data.blade.state(ctx);
            if (phase.has("canInterrupt") && !phase.get("canInterrupt").getAsBoolean()) return Result.fail("BUSY");
        }
        ctx.runtime.noAmmoCommand = "";
        if (!command.equals("AUTO")) {
            ctx.data.throwing.cancel();
            ctx.data.blade.stopCombo(ctx, "command");
            if (ctx.runtime.adapter != null) AdapterRegistry.run(ctx.runtime.adapter, () -> ctx.runtime.adapter.cancel(ctx));
            ctx.runtime.scheduler.clear();
        }
        ctx.runtime.command = command; ctx.runtime.fireStopped = !command.equals("AUTO");
        if (command.equals("RETREAT")) return retreat(ctx);
        if (command.equals("HOLD")) { ctx.npc.getNavigator().clearPath(); setState(ctx, "WAITING"); }
        return Result.success();
    }
    public static Result retreat(CombatContext ctx) {
        if (!ctx.validTarget()) return Result.fail("INVALID_TARGET");
        if (ctx.runtime.scheduler.busy(ctx.now) && !ctx.runtime.scheduler.interruptible) return Result.fail("BUSY");
        net.minecraft.util.math.Vec3d direction = ctx.npc.getPositionVector().subtract(ctx.target.getPositionVector()).normalize();
        net.minecraft.util.math.Vec3d position = ctx.npc.getPositionVector().add(direction.scale(8));
        ctx.npc.getNavigator().tryMoveToXYZ(position.x, position.y, position.z, 1.1);
        setState(ctx, "RETREATING"); return Result.success();
    }
    public static void setState(CombatContext ctx, String state) {
        if (!ctx.runtime.combatState.equals(state)) {
            ctx.runtime.combatState = state;
            EventBridge.emit(ctx, "combat.state_changed", "", Result.success(state));
        }
    }
}
