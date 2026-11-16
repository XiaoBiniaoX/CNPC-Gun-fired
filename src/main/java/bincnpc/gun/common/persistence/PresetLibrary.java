package bincnpc.gun.common.persistence;

import bincnpc.gun.common.api.*;
import bincnpc.gun.common.combat.*;
import com.google.gson.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.storage.WorldSavedData;

/** Reusable configuration templates are server WorldSavedData; NPC blobs stay in CNPCplus. */
public final class PresetLibrary extends WorldSavedData {
    public PresetLibrary() { super("cnpcgun_presets"); }
    public PresetLibrary(String name) { super(name); }
    private final Map<String, JsonObject> templates = new LinkedHashMap<>();
    private final Map<String, String> unparsed = new LinkedHashMap<>();
    public static PresetLibrary get(CombatContext ctx) {
        net.minecraft.world.storage.MapStorage storage = ctx.npc.getServer().getWorld(0).getMapStorage();
        PresetLibrary library = (PresetLibrary)storage.getOrLoadData(PresetLibrary.class, "cnpcgun_presets");
        if (library == null) { library = new PresetLibrary(); storage.setData("cnpcgun_presets", library); }
        return library;
    }
    public static PresetLibrary load(NBTTagCompound tag) {
        PresetLibrary library = new PresetLibrary();
        NBTTagCompound entries = tag.getCompoundTag("Templates");
        for (String id : entries.getKeySet()) if (library.templates.size() < 64) {
            try { library.templates.put(id, SafeJson.persistedObject(entries.getString(id))); }
            catch (IllegalArgumentException | IllegalStateException e) {
                library.unparsed.put(id, entries.getString(id));
                bincnpc.gun.CNPCGun.LOGGER.warn("Preset {} retained as unavailable: {}", id, e.getMessage());
            }
        }
        return library;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        PresetLibrary restored = load(tag); templates.clear(); unparsed.clear();
        templates.putAll(restored.templates); unparsed.putAll(restored.unparsed);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagCompound entries = new NBTTagCompound();
        templates.forEach((id, config) -> entries.setString(id, SafeJson.GSON.toJson(config)));
        unparsed.forEach(entries::setString);
        tag.setTag("Templates", entries); return tag;
    }
    public String[] ids() { return bincnpc.gun.common.legacy.Legacy.keys(templates).toArray(new String[0]); }
    public JsonObject find(String id) { return templates.containsKey(id) ? bincnpc.gun.common.legacy.Legacy.copy(templates.get(id)) : null; }
    public Result remove(String id) {
        if (!templates.containsKey(id)) return Result.fail("NOT_FOUND");
        templates.remove(id); markDirty(); return Result.success();
    }
    public Result saveAs(String id, JsonObject config) {
        if (!id.matches("user:[a-z0-9_.-]{1,48}")) return Result.fail("INVALID_ARGUMENT");
        if (templates.containsKey(id) || unparsed.containsKey(id) || templates.size() + unparsed.size() >= 64) return Result.fail("CONFLICT");
        JsonObject saved = bincnpc.gun.common.legacy.Legacy.copy(config); saved.remove("presetCopies");
        templates.put(id, SafeJson.persistedObject(SafeJson.GSON.toJson(saved))); markDirty(); return Result.success(id);
    }
}
