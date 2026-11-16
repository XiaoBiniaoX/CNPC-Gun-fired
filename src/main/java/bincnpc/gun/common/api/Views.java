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
        public boolean has(String key) {
            if (key == null) return false;
            switch (key) {
case "gun.fire":
return canFire;
case "gun.rate":
return canControlFireRate;
case "gun.override_rate":
return canOverrideNativeRate;
case "gun.reload":
return canReload;
case "gun.partial_reload":
return supportsPartialReload;
case "gun.ammo":
return canReadAmmo;
case "gun.reserve":
return hasReserveAmmo;
case "gun.recoil":
return hasRecoil;
case "gun.recoil_control":
return canControlRecoil;
case "gun.spread":
return canControlSpread;
case "gun.spread_limit":
return canControlSpreadLimit;
case "gun.range":
return canControlRange;
case "gun.aim":
return canAim;
case "gun.burst":
return supportsBurst;
case "gun.jump_fire":
return canJumpFire;
case "gun.blind_fire":
return canBlindFire;
default:
return false;
}
        }
    }
    public static final class BladeCaps {
        public boolean canAttack, specialAttack, phantomSword, lockOn, defense, counter, movement, canMoveAttack = true;
        public String[] actionTypes = {};
        public boolean has(String id) {
            if (id == null) return false;
            switch (id) {
case "slashblade.attack":
return canAttack;
case "slashblade.special_attack":
return specialAttack;
case "slashblade.phantom_sword":
return phantomSword;
case "slashblade.lock_on":
return lockOn;
case "slashblade.defense":
return defense;
case "slashblade.counter":
return counter;
case "slashblade.movement":
return movement;
default:
return false;
}
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
