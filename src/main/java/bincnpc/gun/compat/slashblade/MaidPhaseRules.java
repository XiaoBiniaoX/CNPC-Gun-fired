package bincnpc.gun.compat.slashblade;

import mods.flammpfeil.slashblade.item.ItemSlashBlade.ComboSequence;
import com.google.gson.JsonObject;

/** Equivalent timing windows expressed in r33's actual combos. */
final class MaidPhaseRules {
    private MaidPhaseRules(){}
    static void describe(ComboSequence combo,long elapsed,boolean highest,JsonObject out) {
        boolean idle=combo==ComboSequence.None || combo==ComboSequence.Noutou;
        boolean charged=combo.isCharged || combo==ComboSequence.Kiriage || combo==ComboSequence.RisingStar
            || combo==ComboSequence.Kiriorosi || combo==ComboSequence.SlashDim || combo==ComboSequence.RapidSlashEnd;
        boolean protectedStage=combo==ComboSequence.SlashDim || combo==ComboSequence.RisingStar || combo==ComboSequence.HelmBraker;
        out.addProperty("canCharge",charged || !idle);
        out.addProperty("chargePending",!highest && charged && elapsed<15);
        out.addProperty("perfectCharge",!idle && (highest?elapsed>=4:charged && elapsed>=15 && elapsed<=18));
        out.addProperty("canInterrupt",idle || elapsed>=4 && (!protectedStage || elapsed>=LegacyBladeActions.duration(combo)));
        out.addProperty("idle",idle);out.addProperty("nextOfTimeout","slashblade:none");
    }
}
