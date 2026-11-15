package bincnpc.gun.compat.slashblade;

import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import com.google.gson.JsonObject;
import java.util.Set;

/** Native Power tables from the authorized maid reference, scoped to NPC decisions. */
final class MaidPhaseRules {
    private static final Set<String> CHARGE = Set.of("combo_c", "combo_a4", "combo_a5", "combo_b7", "combo_b_end2",
        "aerial_rave_a3", "aerial_rave_b4", "upperslash", "upperslash_jump", "aerial_cleave_landing", "rapid_slash_end", "rising_star");
    private static final Set<String> NATIVE_CHARGE = Set.of("judgement_cut_slash", "judgement_cut_slash_air",
        "judgement_cut_slash_just", "judgement_cut_slash_just2", "judgement_cut_end");
    private static final Set<String> QUICK = Set.of("combo_a1_end", "combo_a2_end", "combo_c_end", "combo_a3_end3",
        "combo_a4_end", "combo_a4_ex_end2", "combo_a5_end", "combo_b7_end3", "combo_b_end3", "aerial_rave_a1_end",
        "aerial_rave_a2_end2", "aerial_rave_a3_end", "aerial_rave_b3_end", "aerial_rave_b4_end", "upperslash_end",
        "upperslash_jump_end", "aerial_cleave_end", "rapid_slash_quick", "rapid_slash_end2", "rising_star_end",
        "judgement_cut_sheath", "judgement_cut_sheath_air", "judgement_cut_sheath_just");
    private static final Set<String> NATIVE_UNINTERRUPTIBLE = Set.of("combo_c", "combo_a4_ex", "combo_a5", "combo_b7",
        "combo_b_end2", "aerial_rave_b3", "upperslash", "aerial_cleave", "rising_star", "judgement_cut", "judgement_cut_end");
    private static final Set<String> UNINTERRUPTIBLE = Set.of("combo_a1", "combo_a2", "combo_c", "combo_a3", "combo_a4",
        "combo_a4_ex", "combo_a5", "combo_b1", "combo_b7", "combo_b_end2", "aerial_rave_a1", "aerial_rave_a2",
        "aerial_rave_a3", "aerial_rave_b3", "aerial_rave_b4", "upperslash", "upperslash_jump", "aerial_cleave",
        "rising_star", "judgement_cut", "judgement_cut_slash", "judgement_cut_slash_air",
        "judgement_cut_slash_just", "judgement_cut_slash_just2", "judgement_cut_end");
    private static boolean nativeId(ResourceLocation id, Set<String> paths) { return id != null && id.getNamespace().equals("slashblade") && paths.contains(id.getPath()); }
    static void describe(ISlashBladeState state, LivingEntity npc, ResourceLocation id, boolean highest, JsonObject out) {
        ComboState combo = ComboStateRegistry.REGISTRY.get().getValue(id);
        ResourceLocation timeout = combo == null ? ComboStateRegistry.NONE.getId() : combo.getNextOfTimeout(npc);
        long elapsed = Math.max(0, state.getElapsedTime(npc));
        boolean quick = nativeId(id, QUICK) || nativeId(timeout, QUICK);
        boolean charge = nativeId(id, CHARGE) || highest && nativeId(id, NATIVE_CHARGE);
        boolean released = combo != null && combo.releaseAction(npc, (int)Math.min(200, elapsed)) == SlashArts.ArtsType.Jackpot;
        Set<String> protectedStages = highest ? NATIVE_UNINTERRUPTIBLE : UNINTERRUPTIBLE;
        boolean interrupt = !nativeId(id, protectedStages) && !nativeId(timeout, protectedStages);
        out.addProperty("canCharge", charge || quick || released);
        out.addProperty("chargePending", !highest && charge && elapsed < SlashArts.ChargeTicks);
        out.addProperty("perfectCharge", highest && (charge || quick) && elapsed >= 4 || released
            || charge && elapsed >= SlashArts.ChargeTicks && elapsed <= SlashArts.ChargeTicks + SlashArts.getJustReceptionSpan(npc));
        out.addProperty("canInterrupt", interrupt);
        out.addProperty("idle", id.equals(ComboStateRegistry.NONE.getId()));
        out.addProperty("nextOfTimeout", timeout == null ? "" : timeout.toString());
    }
}
