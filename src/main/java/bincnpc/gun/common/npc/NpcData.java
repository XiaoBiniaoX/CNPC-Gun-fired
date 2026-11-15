package bincnpc.gun.common.npc;

import bincnpc.gun.CNPCGun;
import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.config.NpcConfig;
import bincnpc.gun.gun.ammo.AmmoController;
import bincnpc.gun.gun.controller.GunController;
import bincnpc.gun.gun.switching.Armory;
import bincnpc.gun.slashblade.controller.BladeController;
import com.google.gson.*;
import net.minecraft.nbt.CompoundTag;

public final class NpcData {
    public final NpcConfig config = new NpcConfig();
    public final Armory armory = new Armory();
    public final AmmoController ammo = new AmmoController();
    public final GunController gun = new GunController();
    public final BladeController blade = new BladeController();
    public final bincnpc.gun.throwing.ThrowInventory throwItems = new bincnpc.gun.throwing.ThrowInventory();
    public final bincnpc.gun.throwing.ThrowController throwing = new bincnpc.gun.throwing.ThrowController();
    public RuntimeState runtime = new RuntimeState();
    private CompoundTag preserved = new CompoundTag();

    public void read(CompoundTag tag) {
        preserved = tag.copy();
        config.replace(new JsonObject());
        String raw = tag.getString("Config");
        if (!raw.isEmpty()) {
            try {
                config.replace(SafeJson.persistedObject(raw));
                int schema = config.snapshot().get("schemaVersion").getAsInt();
                if (schema != NpcConfig.SCHEMA) CNPCGun.LOGGER.warn("CNPC Gun configuration schema {} retained; known fields use validated defaults", schema);
            } catch (IllegalArgumentException | IllegalStateException e) {
                preserved.putString("UnparsedConfig", raw);
                CNPCGun.LOGGER.warn("Invalid CNPC Gun configuration retained for recovery: {}", e.getMessage());
            }
        }
        armory.load(tag.getCompound("Armory"));
        ammo.load(tag.getCompound("Reserve"));
        throwItems.load(tag.getCompound("Throwables"));
        throwing.cancel();
        blade.reset();
        runtime = new RuntimeState();
        runtime.restoreSupplies = !tag.contains("Reserve", net.minecraft.nbt.Tag.TAG_COMPOUND);
        runtime.movement.meleeFallback = tag.getBoolean("MeleeFallback");
        if (tag.contains("NavigationModeBeforeCombat", net.minecraft.nbt.Tag.TAG_INT)) runtime.movement.priorNavigationMode = Math.max(-1, Math.min(7, tag.getInt("NavigationModeBeforeCombat")));
    }

    public CompoundTag write() {
        CompoundTag tag = preserved.copy();
        tag.putString("Config", SafeJson.GSON.toJson(config.snapshot()));
        tag.put("Armory", armory.save());
        tag.put("Reserve", ammo.save());
        tag.put("Throwables", throwItems.save());
        tag.putBoolean("MeleeFallback", runtime.movement.meleeFallback);
        if (runtime.movement.priorNavigationMode >= 0) tag.putInt("NavigationModeBeforeCombat", runtime.movement.priorNavigationMode);
        else tag.remove("NavigationModeBeforeCombat");
        tag.putInt("SchemaVersion", NpcConfig.SCHEMA);
        return tag;
    }
}
