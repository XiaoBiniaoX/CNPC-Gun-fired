package bincnpc.gun.client.gui.editor;

import com.google.gson.JsonObject;
import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.compat.slashblade.LegacyBladeActions;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import noppes.npcs.entity.EntityNPCInterface;

/** Detached client NBT only; no attack callback or world tick is invoked. */
public final class BladePreview {
    private BladePreview() {}
    public static String pose(ItemStack blade, JsonObject action, JsonObject parameters, long now) {
        if (blade == null || blade.isEmpty() || !(blade.getItem() instanceof ItemSlashBlade) || action == null) return "";
        String type = SafeJson.string(parameters, "artsType", SafeJson.string(action, "defaultArtsType", "Success"));
        String id = SafeJson.string(SafeJson.objectField(action, "previewStages"), type, SafeJson.string(action, "stageId", ""));
        if (id.isEmpty()) id = SafeJson.string(action, "legacyCombo", "");
        synchronize(blade, id, now);
        return "combo:" + id;
    }
    public static void synchronize(ItemStack blade, String stage, long now) {
        if (blade == null || blade.isEmpty() || !(blade.getItem() instanceof ItemSlashBlade)) return;
        NBTTagCompound tag = ItemSlashBlade.getItemTagCompound(blade);
        ItemSlashBlade.setComboSequence(tag, LegacyBladeActions.resolve(stage.startsWith("combo:") ? stage.substring(6) : stage));
        ItemSlashBlade.LastActionTime.set(tag, now);
    }
    public static void animate(EntityNPCInterface npc, String stage, long started, int age, int duration, boolean charged) {
        ItemStack blade = npc.getHeldItemMainhand();
        if (blade.isEmpty() || !(blade.getItem() instanceof ItemSlashBlade)) return;
        synchronize(blade, stage, started);
        ItemSlashBlade.IsCharged.set(ItemSlashBlade.getItemTagCompound(blade), charged);
        // CNPC equipment sync and its own swing timer must not erase the native blade progress.
        npc.swingingHand = EnumHand.MAIN_HAND;
        npc.isSwingInProgress = age < duration;
        npc.swingProgressInt = Math.min(age, duration);
        npc.prevSwingProgress = Math.max(0.0001F, Math.min(1, (age - 1F) / Math.max(1, duration)));
        npc.swingProgress = Math.max(0.0001F, Math.min(1, age / (float)Math.max(1, duration)));
    }
    public static void clear(ItemStack blade, long now) { synchronize(blade, "none", now); }
}
