package bincnpc.gun.common.config;

import static bincnpc.gun.common.config.Parameter.*;
import java.util.*;

public final class ConfigSchema {
    private static final Map<String, Parameter> FIELDS = new LinkedHashMap<>();
    static {
        add(bool("combat.enabled", true), bool("combat.disableFriendlyFire", true),
            bool("combat.movement", true), bool("combat.pathfinding", true),
            bool("combat.stopOnInvalidTarget", true), bool("combat.allowTargetChange", true),
            integer("combat.decisionInterval", 10, 4, 100),
            integer("weapon.primarySlot", 0, 0, 5), bool("weapon.autoSwitch", true), bool("weapon.returnPrimary", true), number("weapon.meleeDistance", 4, 0, 32),
            number("weapon.lowHealth", 0.3, 0, 1), integer("weapon.switchCooldown", 20, 1, 200),
            bool("gun.enabled", true), bool("gun.autoAttack", true),
            number("gun.minRange", 0, 0, 2048), integer("gun.range", 15, 0, 2048),
            bool("gun.requireAim", true).requires("gun.aim"), bool("gun.moveFire", true),
            bool("gun.jumpFire", false).requires("gun.jump_fire"),
            bool("gun.blindFire", false).requires("gun.blind_fire"),
            number("gun.accuracy", 85, 0, 100), bool("gun.predictiveAim", false),
            choice("gun.prefire", "NONE", "TRACK", "SWEEP", "NONE"),
            choice("gun.lostSight", "WAIT", "WAIT", "APPROACH", "STOP"),
            choice("gun.firingMode", "NATIVE", "NATIVE", "SINGLE", "SEMI", "AUTO", "BURST", "CUSTOM").requires("gun.fire"),
            integer("gun.fireInterval", 4, 1, 1200).requires("gun.rate"),
            number("gun.fireRateMultiplier", 1, 0.05, 20).requires("gun.rate"),
            bool("gun.overrideNativeRate", false).requires("gun.override_rate"),
            bool("gun.reloadSpeedEnabled", false).requires("gun.reload"),
            integer("gun.reloadMinTicks", 30, 1, 1200).requires("gun.reload"),
            integer("gun.reloadMaxTicks", 60, 1, 1200).requires("gun.reload"),
            integer("gun.burstCount", 3, 1, 30).requires("gun.burst"),
            integer("gun.burstInterval", 2, 1, 200).requires("gun.burst"),
            integer("gun.burstCooldown", 12, 1, 1200).requires("gun.burst"),
            integer("gun.firstDelay", 4, 0, 1200), integer("gun.windup", 0, 0, 200),
            integer("gun.recovery", 0, 0, 200),
            bool("gun.randomInterval", false).requires("gun.rate"),
            integer("gun.minInterval", 2, 1, 1200).requires("gun.rate"),
            integer("gun.maxInterval", 8, 1, 1200).requires("gun.rate"),
            bool("gun.applyRecoil", true).requires("gun.recoil_control"),
            number("gun.recoilMultiplier", 1, 0, 10).requires("gun.recoil_control"),
            number("gun.spreadMultiplier", 1, 0, 10).requires("gun.spread"),
            number("gun.moveSpread", 1, 0, 10).requires("gun.spread"),
            number("gun.jumpSpread", 1, 0, 10).requires("gun.spread"),
            number("gun.continuousSpread", 1, 0, 5).requires("gun.spread"),
            number("gun.aimSpread", 1, 0, 5).requires("gun.spread"),
            number("gun.maxSpread", 180, 0, 180).requires("gun.spread_limit"),
            bool("ammo.autoReload", true).requires("gun.reload"),
            integer("ammo.reloadThreshold", 0, 0, 1000).requires("gun.reload"),
            bool("ammo.combatReload", true).requires("gun.reload"),
            bool("ammo.partialReload", true).requires("gun.partial_reload"),
            choice("ammo.reloadTiming", "EMPTY", "EMPTY", "THRESHOLD", "IDLE").requires("gun.reload"),
            choice("ammo.source", "MIXED", "INDEPENDENT", "NPC_ITEMS", "MIXED"),
            choice("ammo.priority", "ITEMS_FIRST", "ITEMS_FIRST", "RESERVE_FIRST"),
            choice("ammo.searchRule", "EXACT", "EXACT"),
            integer("ammo.reserveLimit", 300, 0, 100000),
            integer("ammo.minimumReserve", 0, 0, 100000),
            integer("ammo.supplyCooldown", 20, 0, 1200),
            bool("ammo.allowExternalSupply", true), bool("ammo.allowScriptSupply", true),
            bool("ammo.infinite", false), bool("gun.disableIdleAnimation", false), bool("combat.disablePlayerAnimation", false), integer("ammo.defaultReserve", 300, 0, 100000),
            bool("ammo.killSupply", false), integer("ammo.killReward", 30, 0, 100000), bool("ammo.dropSupply", false),
            choice("ammo.noAmmoBehavior", "SWITCH_WEAPON", "SWITCH_WEAPON", "SWITCH_TO_MELEE", "RETREAT", "HOLD_POSITION", "DELEGATE"),
            choice("slashblade.preset", "BEGINNER", "BEGINNER", "NORMAL", "CUSTOM", "MAID").requires("slashblade.attack"),
            bool("slashblade.enabled", true), bool("slashblade.protectDurability", true).requires("slashblade.attack"),
            bool("slashblade.ignoreRequirements", true).requires("slashblade.attack"),
            bool("slashblade.autoAttack", true), integer("slashblade.attackInterval", 12, 4, 200),
            bool("slashblade.useSpecialAttacks", true).requires("slashblade.special_attack"),
            bool("slashblade.usePhantoms", true).requires("slashblade.phantom_sword"),
            bool("slashblade.counter", true).requires("slashblade.defense"),
            choice("slashblade.missingAction", "SKIP", "SKIP", "STOP"),
            choice("slashblade.targetChange", "INTERRUPT", "INTERRUPT", "CONTINUE"),
            integer("slashblade.resourceReserve", 0, 0, 100000),
            integer("slashblade.combo.maxTicks", 600, 20, 2400));
        for (int i = 0; i < 6; i++) {
            add(integer("weapon.slot" + i + ".priority", 100 - i * 10, 0, 1000),
                bool("weapon.slot" + i + ".minRangeEnabled", true),
                number("weapon.slot" + i + ".minRange", i < 2 ? 12 : 0, 0, 2048),
                bool("weapon.slot" + i + ".maxRangeEnabled", true),
                number("weapon.slot" + i + ".maxRange", i >= 4 ? 6 : i >= 2 ? 20 : 64, 0, 2048),
                bool("weapon.slot" + i + ".maxHealthEnabled", false),
                number("weapon.slot" + i + ".maxHealth", 1, 0, 1));
        }
        add(bool("throw.enabled", true), integer("throw.cooldown", 30, 0, 1200));
        for (int i = 0; i < 3; i++) {
            String key = "throw.slot" + i + ".";
            add(bool(key + "once", true), choice(key + "timing", "SAFE", "RELOAD", "SAFE", "SHOOT", "RANDOM"),
                choice(key + "trajectory", "AUTO", "STRAIGHT", "CURVE", "AUTO", "TRACKING"),
                integer(key + "windup", 30, 0, 200), integer(key + "recovery", 15, 0, 200),
                number(key + "speed", 1, 0.01, 20), integer(key + "priority", 100 - i * 10, 0, 1000),
                number(key + "minRange", 6, 0, 128), number(key + "maxRange", 32, 1, 128),
                choice(key + "area", "SINGLE", "SINGLE", "AREA"), number(key + "deviation", 1.5, 0, 16),
                choice(key + "lostTarget", "CANCEL", "CANCEL", "CONTINUE", "RETARGET"),
                number(key + "maxElevation", 80, 0, 89), number(key + "maxDepression", 70, 0, 89),
                number(key + "calculationRange", 64, 1, 128), bool(key + "prediction", true));
        }
    }
    private ConfigSchema() {}
    /** Settings can be prepared for any weapon; native capabilities only gate actual combat actions. */
    public static boolean isGunSetting(String key) { return key != null && (key.startsWith("gun.") || key.startsWith("ammo.")); }
    private static void add(Parameter... parameters) {
        for (Parameter p : parameters) FIELDS.put(p.id, isGunSetting(p.id) ? p.requires("") : p);
    }
    public static Parameter get(String key) { return FIELDS.get(key); }
    public static Parameter[] all() { return FIELDS.values().toArray(Parameter[]::new); }
}
