package bincnpc.gun.common.script;

import bincnpc.gun.CNPCGun;
import bincnpc.gun.common.adapter.*;
import bincnpc.gun.common.api.*;
import bincnpc.gun.common.combat.*;
import bincnpc.gun.common.config.ConfigSchema;
import bincnpc.gun.gun.adapter.GunAdapter;
import bincnpc.gun.slashblade.adapter.BladeAdapter;
import bincnpc.gun.slashblade.action.*;
import bincnpc.gun.slashblade.combo.*;
import bincnpc.gun.slashblade.strategy.Presets;
import com.google.gson.*;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.world.entity.*;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.entity.EntityNPCInterface;

/** One global, stable ES5 facade; proxies contain only a weak NPC reference. */
public final class ScriptApi {
    public String getApiVersion() { return CNPCGun.API_VERSION; }
    public String[] getSupportedDomains() {
        Set<String> domains = new LinkedHashSet<>();
        for (String id : AdapterRegistry.ids()) {
            if (id.equals("slashblade")) domains.add("SLASHBLADE"); else domains.add("GUN");
        }
        return domains.toArray(String[]::new);
    }
    public boolean isAdapterLoaded(String id) { return AdapterRegistry.loaded(id); }
    public String[] getLoadedAdapters() { return AdapterRegistry.ids(); }
    public String[] getConditions() { return Conditions.ids(); }
    public NpcProxy get(Object npc) {
        Entity entity = unwrap(npc);
        return new NpcProxy(entity instanceof EntityNPCInterface cnpc ? cnpc : null);
    }
    public Result registerCondition(String id, Object callback) {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,64}")) return Result.fail("INVALID_ARGUMENT");
        return Result.fail("UNSUPPORTED", "cnpcgun.error.cross_script_condition");
    }
    private static Entity unwrap(Object object) {
        return object instanceof IEntity<?> wrapper ? wrapper.getMCEntity() : null;
    }

    public static final class NpcProxy {
        private final WeakReference<EntityNPCInterface> npc;
        private NpcProxy(EntityNPCInterface npc) { this.npc = new WeakReference<>(npc); }
        private CombatContext context() {
            EntityNPCInterface entity = npc.get();
            if (!CombatService.serverThread(entity) || !CombatService.validNpc(entity)) return null;
            CombatContext ctx = new CombatContext(entity); CombatService.refresh(ctx); return ctx;
        }
        private Result unavailable() {
            EntityNPCInterface entity = npc.get();
            return Result.fail(entity != null && !CombatService.serverThread(entity) ? "PERMISSION_DENIED" : "INVALID_NPC");
        }
        public String getVersion() { return CNPCGun.API_VERSION; }
        public boolean isAvailable() { return context() != null && AdapterRegistry.ready(); }
        public Views.WeaponInfo getCurrentWeapon() {
            CombatContext ctx = context(); return ctx == null ? null : CombatService.weaponInfo(ctx.weapon, null);
        }
        public String getWeaponType() { CombatContext ctx = context(); return ctx == null ? "UNKNOWN" : ctx.runtime.domain; }
        public Views.Capabilities getCapabilities() {
            CombatContext ctx = context(); return ctx == null ? new Views.Capabilities("UNKNOWN", null, null, false) : CombatService.capabilities(ctx);
        }
        public boolean hasCapability(String id) { CombatContext ctx = context(); return ctx != null && CombatService.hasCapability(ctx, id); }
        public GunProxy getGun() { return new GunProxy(this); }
        public BladeProxy getSlashBlade() { return new BladeProxy(this); }
        public CombatProxy getCombat() { return new CombatProxy(this); }
        public AmmoProxy getAmmo() { return new AmmoProxy(this); }
        public Object getState() {
            CombatContext ctx = context();
            if (ctx == null) return null;
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("weaponType", ctx.runtime.domain); out.put("combatState", ctx.runtime.combatState);
            out.put("actionId", ctx.runtime.actionId); out.put("comboId", ctx.runtime.comboId); out.put("nodeId", ctx.runtime.comboNode);
            out.put("reloading", ctx.runtime.reloading); out.put("lastResult", ctx.runtime.lastResult);
            out.put("targetId", ctx.runtime.targetId == null ? null : ctx.runtime.targetId.toString());
            return out;
        }
        public Object getConfig(String key) {
            CombatContext ctx = context();
            if (ctx == null) return unavailable();
            return ConfigSchema.get(key) == null ? Result.fail("NOT_FOUND") : SafeJson.plain(ctx.config.get(key));
        }
        public Result setConfig(String key, Object value) {
            CombatContext ctx = context();
            if (ctx == null) return unavailable();
            if ("slashblade.preset".equals(key) && value instanceof String id) return Presets.apply(ctx, id);
            return CombatService.setConfig(ctx, key, value);
        }
        public Views.WeaponInfo[] getAvailableWeapons() {
            CombatContext ctx = context(); if (ctx == null) return new Views.WeaponInfo[0];
            List<Views.WeaponInfo> weapons = new ArrayList<>();
            for (int i = 0; i < bincnpc.gun.gun.switching.Armory.SIZE; i++) {
                Views.WeaponInfo info = CombatService.weaponInfo(ctx.data.armory.get(ctx.npc, i), "slot:" + i);
                if (info != null) weapons.add(info);
            }
            return weapons.toArray(Views.WeaponInfo[]::new);
        }
        public Result switchWeapon(String id) {
            CombatContext ctx = context(); if (ctx == null) return unavailable();
            for (int i = 0; i < bincnpc.gun.gun.switching.Armory.SIZE; i++) {
                Views.WeaponInfo info = CombatService.weaponInfo(ctx.data.armory.get(ctx.npc, i), null);
                if (id != null && (id.equals("slot:" + i) || info != null && id.equals(info.id))) return ctx.data.armory.switchTo(ctx, i);
            }
            return Result.fail("NOT_FOUND");
        }
        public Result switchToBestWeapon() { CombatContext ctx = context(); return ctx == null ? unavailable() : CombatService.switchBest(ctx, false); }
    }

    public static final class GunProxy {
        private final NpcProxy parent;
        private GunProxy(NpcProxy parent) { this.parent = parent; }
        public boolean isGun() { return parent.getWeaponType().equals("GUN"); }
        public Views.WeaponInfo getWeaponInfo() { return isGun() ? parent.getCurrentWeapon() : null; }
        public Views.GunCaps getCapabilities() { return parent.getCapabilities().gun; }
        public String getFiringMode() { return isGun() ? (String)parent.getConfig("gun.firingMode") : null; }
        public Object getFireRate() {
            CombatContext ctx = parent.context();
            if (ctx == null || !(ctx.runtime.adapter instanceof GunAdapter adapter)) return null;
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("interval", ctx.config.integer("gun.fireInterval")); out.put("multiplier", ctx.config.number("gun.fireRateMultiplier"));
            out.put("nativeInterval", AdapterRegistry.query(adapter, () -> adapter.baseInterval(ctx.weapon), 4));
            out.put("overrideNativeRate", ctx.config.bool("gun.overrideNativeRate")); return out;
        }
        public Views.AmmoState getAmmoState() { return parent.getAmmo().getState(); }
        public boolean isReloading() { Views.AmmoState ammo = getAmmoState(); return ammo != null && ammo.isReloading; }
        public boolean isFiring() { CombatContext ctx = parent.context(); return ctx != null && ctx.runtime.scheduler.channel.equals("GUN"); }
        public Object getAimState() {
            CombatContext ctx = parent.context();
            return ctx != null && ctx.runtime.adapter instanceof GunAdapter adapter ? AdapterRegistry.query(adapter, () -> adapter.aimState(ctx), null) : null;
        }
        public Result fire() { CombatContext ctx = parent.context(); return ctx == null ? parent.unavailable() : ctx.data.gun.fire(ctx); }
        public Result stopFire() { CombatContext ctx = parent.context(); return ctx == null ? parent.unavailable() : ctx.data.gun.stop(ctx); }
        public Result reload() { return parent.getAmmo().reload(); }
        public Result setFiringEnabled(boolean enabled) { return parent.setConfig("gun.autoAttack", enabled); }
        public Result setFiringMode(String mode) { return parent.setConfig("gun.firingMode", mode); }
        public Result setFireInterval(int ticks) { return parent.setConfig("gun.fireInterval", ticks); }
        public Result setFireRateMultiplier(double multiplier) { return parent.setConfig("gun.fireRateMultiplier", multiplier); }
        public Result setBurstCount(int count) { return parent.setConfig("gun.burstCount", count); }
        public Result setBurstInterval(int ticks) { return parent.setConfig("gun.burstInterval", ticks); }
        public Result setBurstCooldown(int ticks) { return parent.setConfig("gun.burstCooldown", ticks); }
        public Result setAiming(boolean aim) {
            CombatContext ctx = parent.context(); if (ctx == null) return parent.unavailable();
            if (!(ctx.runtime.adapter instanceof GunAdapter adapter)) return Result.fail("NO_COMPATIBLE_WEAPON");
            Result result = AdapterRegistry.call(adapter, () -> adapter.aim(ctx, aim));
            if (result.ok) ctx.runtime.aiming = aim; return result;
        }
        public Result setSpreadMultiplier(double multiplier) { return parent.setConfig("gun.spreadMultiplier", multiplier); }
        public Double getSpread() {
            CombatContext ctx = parent.context();
            return ctx != null && ctx.runtime.adapter instanceof GunAdapter adapter
                ? AdapterRegistry.query(adapter, () -> adapter.spread(ctx.weapon), null) : null;
        }
        public boolean hasRecoil() { Views.GunCaps caps = getCapabilities(); return caps != null && caps.hasRecoil; }
        public Double getRecoil() {
            CombatContext ctx = parent.context();
            return ctx != null && ctx.runtime.gunCaps != null && ctx.runtime.gunCaps.hasRecoil && ctx.runtime.adapter instanceof GunAdapter adapter
                ? AdapterRegistry.query(adapter, () -> adapter.recoil(ctx.weapon), null) : null;
        }
        public Result setRecoilMultiplier(double multiplier) { return parent.setConfig("gun.recoilMultiplier", multiplier); }
    }

    public static final class AmmoProxy {
        private final NpcProxy parent;
        private AmmoProxy(NpcProxy parent) { this.parent = parent; }
        public Views.AmmoState getState() { CombatContext ctx = parent.context(); return ctx == null ? null : ctx.data.gun.ammo(ctx); }
        public Integer getMagazineAmmo() { Views.AmmoState state = getState(); return state == null ? null : state.magazineAmmo; }
        public Integer getReserveAmmo() { Views.AmmoState state = getState(); return state == null ? null : state.reserveAmmo; }
        public boolean canReload() { Views.AmmoState state = getState(); return state != null && state.canReload; }
        public Result reload() { CombatContext ctx = parent.context(); return ctx == null ? parent.unavailable() : ctx.data.gun.reload(ctx); }
        private Result supply(int count, boolean replace) {
            CombatContext ctx = parent.context(); if (ctx == null) return parent.unavailable();
            if (!(ctx.runtime.adapter instanceof GunAdapter adapter)) return Result.fail("NO_COMPATIBLE_WEAPON");
            if (ctx.runtime.gunCaps == null || !ctx.runtime.gunCaps.hasReserveAmmo) return Result.fail("UNSUPPORTED");
            return ctx.data.ammo.supply(ctx, adapter, count, replace, true);
        }
        public Result addReserveAmmo(int count) { return supply(count, false); }
        public Result setReserveAmmo(int count) { return supply(count, true); }
        public Result setReserveAmmoLimit(int count) { return parent.setConfig("ammo.reserveLimit", count); }
        public Result setNoAmmoBehavior(String behavior) { return parent.setConfig("ammo.noAmmoBehavior", behavior); }
    }

    public static final class BladeProxy {
        private final NpcProxy parent;
        private BladeProxy(NpcProxy parent) { this.parent = parent; }
        public boolean isSlashBlade() { return parent.getWeaponType().equals("SLASHBLADE"); }
        public Views.WeaponInfo getWeaponInfo() { return isSlashBlade() ? parent.getCurrentWeapon() : null; }
        public Views.BladeCaps getCapabilities() { return parent.getCapabilities().slashBlade; }
        public String getPreset() { CombatContext ctx = parent.context(); return ctx == null ? null : ctx.config.text("slashblade.preset"); }
        public Object getCombatState() { CombatContext ctx = parent.context(); return ctx == null ? null : SafeJson.plain(ctx.data.blade.state(ctx)); }
        private Views.ActionInfo[] actions(String filter, boolean enabledOnly) {
            CombatContext ctx = parent.context(); if (ctx == null || !(ctx.runtime.adapter instanceof BladeAdapter adapter)) return new Views.ActionInfo[0];
            List<Views.ActionInfo> out = new ArrayList<>();
            for (ActionRegistry.Definition def : ActionRegistry.INSTANCE.all()) {
                if (!def.adapterId.equals(adapter.id()) || filter != null && !filter.equals(def.info.type)) continue;
                if (enabledOnly && !ActionConfig.value(ctx.config.action(def.info.id), "enabled").getAsBoolean()) continue;
                out.add(SafeJson.GSON.fromJson(SafeJson.GSON.toJson(def.info), Views.ActionInfo.class));
            }
            return out.toArray(Views.ActionInfo[]::new);
        }
        public Views.ActionInfo[] getAvailableActions() { return actions(null, false); }
        public Views.ActionInfo[] getAvailableSpecialAttacks() { return actions("SPECIAL_ATTACK", false); }
        public Views.ActionInfo[] getAvailablePhantomActions() { return actions("PHANTOM_SWORD", false); }
        public Views.ActionInfo[] getEnabledSpecialAttacks() { return actions("SPECIAL_ATTACK", true); }
        public Views.ActionInfo[] getEnabledPhantomActions() { return actions("PHANTOM_SWORD", true); }
        public Views.ActionInfo[] getAvailableUtilityActions() {
            return Arrays.stream(actions(null, false)).filter(a -> !Set.of("NORMAL_ATTACK", "COMBO_ATTACK", "SPECIAL_ATTACK", "PHANTOM_SWORD").contains(a.type)).toArray(Views.ActionInfo[]::new);
        }
        public Views.ActionInfo getAction(String id) {
            CombatContext ctx = parent.context(); ActionRegistry.Definition def = ActionRegistry.INSTANCE.get(id);
            return ctx == null || def == null || ctx.runtime.adapter == null || !def.adapterId.equals(ctx.runtime.adapter.id())
                ? null : SafeJson.GSON.fromJson(SafeJson.GSON.toJson(def.info), Views.ActionInfo.class);
        }
        public Result executeAction(String id) { return executeAction(id, Collections.emptyMap()); }
        public Result executeAction(String id, Object data) {
            CombatContext ctx = parent.context(); if (ctx == null) return parent.unavailable();
            try {
                Object target = data instanceof Map<?, ?> map ? map.get("target") : null;
                if (target != null) {
                    Entity entity = unwrap(target);
                    if (!(entity instanceof LivingEntity living) || !CombatService.validTarget(ctx.npc, living)) return Result.fail("INVALID_TARGET");
                    ctx = new CombatContext(ctx.npc, living);
                }
                JsonObject parameters;
                if (data instanceof Map<?, ?> map && map.containsKey("target")) {
                    Map<String, Object> withoutTarget = new LinkedHashMap<>();
                    map.forEach((k, v) -> { if (!"target".equals(k)) withoutTarget.put(String.valueOf(k), v); });
                    parameters = SafeJson.object(withoutTarget);
                } else parameters = SafeJson.object(data);
                return ctx.data.blade.execute(ctx, id, parameters);
            } catch (IllegalArgumentException | IllegalStateException e) { return Result.fail("INVALID_ARGUMENT", e.getMessage()); }
        }
        private Result requireType(String id, String type, Object parameters) {
            Views.ActionInfo info = getAction(id);
            if (info == null) return Result.fail("NOT_FOUND");
            return info.type.equals(type) ? executeAction(id, parameters) : Result.fail("INVALID_ARGUMENT");
        }
        public Result useSpecialAttack(String id) { return requireType(id, "SPECIAL_ATTACK", Collections.emptyMap()); }
        public Result executePhantomAction(String id, Object parameters) { return requireType(id, "PHANTOM_SWORD", parameters); }
        public Result executePhantomAction(String id) { return executePhantomAction(id, Collections.emptyMap()); }
        public Result configureAction(String id, Object data) {
            CombatContext ctx = parent.context(); if (ctx == null) return parent.unavailable();
            ActionRegistry.Definition def = ActionRegistry.INSTANCE.get(id);
            if (def == null) return Result.fail("NOT_FOUND");
            if (ctx.runtime.adapter == null || !def.adapterId.equals(ctx.runtime.adapter.id())) return Result.fail("UNSUPPORTED");
            try { ctx.config.putAction(id, ActionConfig.validate(SafeJson.object(data), def)); return Result.success(); }
            catch (IllegalArgumentException | IllegalStateException e) { return Result.fail("INVALID_ARGUMENT", e.getMessage()); }
        }
        private Result edit(String id, String key, Object value, String type) {
            CombatContext ctx = parent.context(); if (ctx == null) return parent.unavailable();
            Views.ActionInfo info = getAction(id);
            if (info == null) return Result.fail("NOT_FOUND");
            if (type != null && !info.type.equals(type)) return Result.fail("INVALID_ARGUMENT");
            JsonObject action = ctx.config.action(id); action.add(key, SafeJson.copy(value));
            return configureAction(id, action);
        }
        public Result enableSpecialAttack(String id, boolean enabled) { return edit(id, "enabled", enabled, "SPECIAL_ATTACK"); }
        public Result setSpecialAttackPriority(String id, int priority) { return edit(id, "priority", priority, "SPECIAL_ATTACK"); }
        public Result setSpecialAttackCondition(String id, String condition) {
            if (!Conditions.IDS.contains(condition)) return Result.fail("NOT_FOUND");
            return edit(id, "conditions", new String[]{condition}, "SPECIAL_ATTACK");
        }
        public Result enablePhantomAction(String id, boolean enabled) { return edit(id, "enabled", enabled, "PHANTOM_SWORD"); }
        public Result setPreset(String id) { CombatContext ctx = parent.context(); return ctx == null ? parent.unavailable() : Presets.apply(ctx, id); }
        public Result copyPreset(String source, String destination) { CombatContext ctx = parent.context(); return ctx == null ? parent.unavailable() : Presets.copy(ctx, source, destination); }
        public Result resetPreset(String id) { return setPreset(id); }
        public Result savePreset(String id) {
            CombatContext ctx = parent.context(); return ctx == null ? parent.unavailable()
                : bincnpc.gun.common.persistence.PresetLibrary.get(ctx).saveAs(id, ctx.config.snapshot());
        }
        public String[] getPresets() {
            CombatContext ctx = parent.context();
            if (ctx == null) return Presets.BUILTINS.clone();
            List<String> ids = new ArrayList<>(Arrays.asList(Presets.BUILTINS));
            ids.addAll(Arrays.asList(bincnpc.gun.common.persistence.PresetLibrary.get(ctx).ids()));
            return ids.toArray(String[]::new);
        }
        public Object getCombo() {
            CombatContext ctx = parent.context(); return ctx == null ? null : SafeJson.plain(ctx.config.graph(ctx.config.selectedGraph()));
        }
        public Result setCombo(Object data) {
            CombatContext ctx = parent.context(); if (ctx == null) return parent.unavailable();
            try { return ctx.data.blade.setCombo(ctx, SafeJson.object(data)); }
            catch (IllegalArgumentException | IllegalStateException e) { return Result.fail("INVALID_ARGUMENT", e.getMessage()); }
        }
        public Result startCombo(String id) { CombatContext ctx = parent.context(); return ctx == null ? parent.unavailable() : ctx.data.blade.startCombo(ctx, id); }
        public Result stopCombo() { CombatContext ctx = parent.context(); return ctx == null ? parent.unavailable() : ctx.data.blade.stopCombo(ctx, "script"); }
        public Object getComboState() { CombatContext ctx = parent.context(); return ctx == null ? null : SafeJson.plain(ctx.data.blade.comboState(ctx)); }
    }

    public static final class CombatProxy {
        private final NpcProxy parent;
        private CombatProxy(NpcProxy parent) { this.parent = parent; }
        public Object getTarget() { CombatContext ctx = parent.context(); return ctx == null || !ctx.validTarget() ? null : NpcAPI.Instance().getIEntity(ctx.target); }
        public Result setTarget(Object target) {
            CombatContext ctx = parent.context(); if (ctx == null) return parent.unavailable();
            Entity entity = unwrap(target);
            if (!(entity instanceof LivingEntity living) || !CombatService.validTarget(ctx.npc, living)) return Result.fail("INVALID_TARGET");
            if (!ctx.config.bool("combat.allowTargetChange") && ctx.validTarget() && ctx.target != living) return Result.fail("CONFLICT");
            ctx.npc.setTarget(living); return Result.success();
        }
        public Result clearTarget() { CombatContext ctx = parent.context(); if (ctx == null) return parent.unavailable(); ctx.npc.setTarget(null); return Result.success(); }
        public Double getAttackRange() { CombatContext ctx = parent.context(); return ctx == null ? null : ctx.config.number("combat.attackRange"); }
        public Result setAttackRange(double range) { return parent.setConfig("combat.attackRange", range); }
        public boolean isInCombat() { CombatContext ctx = parent.context(); return ctx != null && ctx.validTarget(); }
        public String getCombatState() { CombatContext ctx = parent.context(); return ctx == null ? "DISABLED" : ctx.runtime.combatState; }
        public Result setCombatEnabled(boolean enabled) { return parent.setConfig("combat.enabled", enabled); }
        public Result requestRetreat() { CombatContext ctx = parent.context(); return ctx == null ? parent.unavailable() : CombatService.command(ctx, "RETREAT"); }
        public Result requestHoldPosition() {
            CombatContext ctx = parent.context(); if (ctx == null) return parent.unavailable();
            return CombatService.command(ctx, "HOLD");
        }
        public Result requestAttack() {
            CombatContext ctx = parent.context(); if (ctx == null) return parent.unavailable();
            ctx.runtime.command = "AUTO"; ctx.runtime.fireStopped = false;
            if (ctx.runtime.adapter instanceof GunAdapter) return ctx.data.gun.fire(ctx);
            if (ctx.runtime.adapter instanceof BladeAdapter blade) return ctx.data.blade.execute(ctx, blade.nextNormal(ctx), new JsonObject());
            return Result.fail("NO_COMPATIBLE_WEAPON");
        }
    }
}
