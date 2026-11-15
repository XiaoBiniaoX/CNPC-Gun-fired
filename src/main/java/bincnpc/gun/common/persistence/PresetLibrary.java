package bincnpc.gun.common.persistence;

import bincnpc.gun.common.api.*;
import bincnpc.gun.common.combat.*;
import com.google.gson.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.saveddata.SavedData;

/** Reusable configuration templates are server SavedData; NPC blobs stay in CNPCplus. */
public final class PresetLibrary extends SavedData {
    private final Map<String, JsonObject> templates = new LinkedHashMap<>();
    private final Map<String, String> unparsed = new LinkedHashMap<>();
    public static PresetLibrary get(CombatContext ctx) {
        return ctx.npc.getServer().overworld().getDataStorage().computeIfAbsent(PresetLibrary::load, PresetLibrary::new, "cnpcgun_presets");
    }
    public static PresetLibrary load(CompoundTag tag) {
        PresetLibrary library = new PresetLibrary();
        CompoundTag entries = tag.getCompound("Templates");
        for (String id : entries.getAllKeys()) if (library.templates.size() < 64) {
            try { library.templates.put(id, SafeJson.persistedObject(entries.getString(id))); }
            catch (IllegalArgumentException | IllegalStateException e) {
                library.unparsed.put(id, entries.getString(id));
                bincnpc.gun.CNPCGun.LOGGER.warn("Preset {} retained as unavailable: {}", id, e.getMessage());
            }
        }
        return library;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        CompoundTag entries = new CompoundTag();
        templates.forEach((id, config) -> entries.putString(id, SafeJson.GSON.toJson(config)));
        unparsed.forEach(entries::putString);
        tag.put("Templates", entries); return tag;
    }
    public String[] ids() { return templates.keySet().toArray(String[]::new); }
    public JsonObject find(String id) { return templates.containsKey(id) ? templates.get(id).deepCopy() : null; }
    public Result remove(String id) {
        if (!templates.containsKey(id)) return Result.fail("NOT_FOUND");
        templates.remove(id); setDirty(); return Result.success();
    }
    public Result saveAs(String id, JsonObject config) {
        if (!id.matches("user:[a-z0-9_.-]{1,48}")) return Result.fail("INVALID_ARGUMENT");
        if (templates.containsKey(id) || unparsed.containsKey(id) || templates.size() + unparsed.size() >= 64) return Result.fail("CONFLICT");
        JsonObject saved = config.deepCopy(); saved.remove("presetCopies");
        templates.put(id, SafeJson.persistedObject(SafeJson.GSON.toJson(saved))); setDirty(); return Result.success(id);
    }
}
