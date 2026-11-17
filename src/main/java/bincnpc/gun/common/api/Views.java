package bincnpc.gun.common.api;

import bincnpc.gun.common.config.Parameter;

public final class Views {
    private Views() {}
    public static final class WeaponInfo {
        public final String id, type, registryName, displayName, adapterId;
        public final boolean available;
        public WeaponInfo(String id, String type, String registryName, String displayName, String adapterId, boolean available) {
            this.id = id; this.type = type; this.registryName = registryName;
            this.displayName = displayName; this.adapterId = adapterId; this.available = available;
        }
    }
    public static final class GunCaps {
        public boolean canFire, canControlFireRate, canOverrideNativeRate, hasMagazine, canReload,
            canReadAmmo, canConsumeAmmo, hasReserveAmmo, hasRecoil, canControlRecoil,
            hasAimState, canAim, hasSpread, canControlSpread, canControlSpreadLimit, canControlRange,
            supportsBurst, supportsAutomaticFire, supportsSingleShot, supportsSpecialMechanics,
            canMoveAttack = true, canJumpFire = true, canBlindFire, supportsPartialReload;
        public String projectileModel = "UNKNOWN";
        public String[] firingModes = {"NATIVE"};
        public String[] specialMechanics = {};
        public GunCaps copy() { return SafeJson.GSON.fromJson(SafeJson.GSON.toJson(this), GunCaps.class); }
        public GunCaps withNpcControls() {
            canControlRecoil = true; canControlSpread = true; canControlSpreadLimit = true;
            canControlRange = true; canBlindFire = true;
            return this;
        }
        public boolean has(String key) {
            if (key == null) return false;
            return switch (key) {
                case "gun.fire" -> canFire;
                case "gun.rate" -> canControlFireRate;
                case "gun.override_rate" -> canOverrideNativeRate;
                case "gun.reload" -> canReload;
                case "gun.partial_reload" -> supportsPartialReload;
                case "gun.ammo" -> canReadAmmo;
                case "gun.reserve" -> hasReserveAmmo;
                case "gun.recoil" -> hasRecoil;
                case "gun.recoil_control" -> canControlRecoil;
                case "gun.spread" -> canControlSpread;
                case "gun.spread_limit" -> canControlSpreadLimit;
                case "gun.range" -> canControlRange;
                case "gun.aim" -> canAim;
                case "gun.burst" -> supportsBurst;
                case "gun.jump_fire" -> canJumpFire;
                case "gun.blind_fire" -> canBlindFire;
                default -> false;
            };
        }
    }
    public static final class BladeCaps {
        public boolean canAttack, specialAttack, phantomSword, lockOn, defense, counter, movement, canMoveAttack = true;
        public String[] actionTypes = {};
        public boolean has(String id) {
            if (id == null) return false;
            return switch (id) {
                case "slashblade.attack" -> canAttack;
                case "slashblade.special_attack" -> specialAttack;
                case "slashblade.phantom_sword" -> phantomSword;
                case "slashblade.lock_on" -> lockOn;
                case "slashblade.defense" -> defense;
                case "slashblade.counter" -> counter;
                case "slashblade.movement" -> movement;
                default -> false;
            };
        }
    }
    public static final class Capabilities {
        public final String domain;
        public final boolean canAttack, canMoveAttack, canSwitchAway;
        public final GunCaps gun;
        public final BladeCaps slashBlade;
        public Capabilities(String domain, GunCaps gun, BladeCaps blade, boolean canSwitchAway) {
            this.domain = domain; this.gun = gun; this.slashBlade = blade; this.canSwitchAway = canSwitchAway;
            this.canAttack = gun != null ? gun.canFire : blade != null && blade.canAttack;
            this.canMoveAttack = gun != null ? gun.canMoveAttack : blade != null && blade.canMoveAttack;
        }
    }
    public static final class AmmoState {
        public final boolean hasMagazine, hasReserve, isReloading, canReload;
        public final Integer magazineAmmo, magazineCapacity, reserveAmmo;
        public AmmoState(boolean magazine, Integer count, Integer capacity, boolean reserve, Integer reserveCount, boolean reload, boolean canReload) {
            this.hasMagazine = magazine; this.magazineAmmo = count; this.magazineCapacity = capacity;
            this.hasReserve = reserve; this.reserveAmmo = reserveCount; this.isReloading = reload; this.canReload = canReload;
        }
    }
    public static final class ActionInfo {
        public final String id, type, source, displayName, description, channel;
        public final boolean available, interruptible, parallel, targetRequired;
        public final int cooldown, duration;
        public final Integer resourceCost;
        public final String unavailableReason;
        public final Parameter[] parameters;
        public ActionInfo(String id, String type, String source, String displayName, String description,
                int cooldown, int duration, Integer cost, Parameter[] parameters, boolean available,
                String reason, boolean interruptible, boolean parallel, boolean targetRequired) {
            this.id = id; this.type = type; this.source = source; this.displayName = displayName;
            this.description = description; this.cooldown = cooldown; this.duration = duration; this.resourceCost = cost;
            this.parameters = parameters.clone(); this.available = available; this.unavailableReason = reason;
            this.interruptible = interruptible; this.parallel = parallel; this.channel = parallel ? "AUXILIARY" : "MAIN";
            this.targetRequired = targetRequired;
        }
    }
}
