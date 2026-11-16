package bincnpc.gun.common.npc;

import bincnpc.gun.CNPCGun;
import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.config.NpcConfig;
import bincnpc.gun.gun.ammo.AmmoController;
import bincnpc.gun.gun.controller.GunController;
import bincnpc.gun.gun.switching.Armory;
import bincnpc.gun.slashblade.controller.BladeController;
import com.google.gson.*;
import net.minecraft.nbt.NBTTagCompound;

public final class NpcData {
    public final NpcConfig config = new NpcConfig();
    public final Armory armory = new Armory();
    public final AmmoController ammo = new AmmoController();
    public final GunController gun = new GunController();
    public final BladeController blade = new BladeController();
    public final bincnpc.gun.throwing.ThrowInventory throwItems = new bincnpc.gun.throwing.ThrowInventory();
    public final bincnpc.gun.throwing.ThrowController throwing = new bincnpc.gun.throwing.ThrowController();
    public boolean persistenceAvailable = true;
    public NBTTagCompound unrestoredReference;
    public RuntimeState runtime = new RuntimeState();
    private NBTTagCompound preserved = new NBTTagCompound();

    public void read(NBTTagCompound tag) {
        preserved = tag.copy();
        config.replace(new JsonObject());
        String raw = tag.getString("Config");
        if (!raw.isEmpty()) {
            try {
                config.replace(SafeJson.persistedObject(raw));
                int schema = config.snapshot().get("schemaVersion").getAsInt();
                if (schema != NpcConfig.SCHEMA) CNPCGun.LOGGER.warn("CNPC Gun configuration schema {} retained; known fields use validated defaults", schema);
            } catch (IllegalArgumentException | IllegalStateException e) {
                preserved.setString("UnparsedConfig", raw);
                CNPCGun.LOGGER.warn("Invalid CNPC Gun configuration retained for recovery: {}", e.getMessage());
            }
        }
        armory.load(tag.getCompoundTag("Armory"));
        ammo.load(tag.getCompoundTag("Reserve"));
        throwItems.load(tag.getCompoundTag("Throwables"));
        throwing.cancel();
        blade.reset();
        runtime = new RuntimeState();
        runtime.restoreSupplies = !tag.hasKey("Reserve", net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND);
        runtime.movement.meleeFallback = tag.getBoolean("MeleeFallback");
        if (tag.hasKey("NavigationModeBeforeCombat", net.minecraftforge.common.util.Constants.NBT.TAG_INT)) runtime.movement.priorNavigationMode = Math.max(-1, Math.min(7, tag.getInteger("NavigationModeBeforeCombat")));
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = preserved.copy();
        tag.setString("Config", SafeJson.GSON.toJson(config.snapshot()));
        tag.setTag("Armory", armory.save());
        tag.setTag("Reserve", ammo.save());
        tag.setTag("Throwables", throwItems.save());
        tag.setBoolean("MeleeFallback", runtime.movement.meleeFallback);
        if (runtime.movement.priorNavigationMode >= 0) tag.setInteger("NavigationModeBeforeCombat", runtime.movement.priorNavigationMode);
        else tag.removeTag("NavigationModeBeforeCombat");
        tag.setInteger("SchemaVersion", NpcConfig.SCHEMA);
        return tag;
    }
}
