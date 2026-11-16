package bincnpc.gun.common.network;

import bincnpc.gun.common.adapter.*;
import bincnpc.gun.common.api.*;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.gun.adapter.GunAdapter;
import bincnpc.gun.gun.switching.Armory;
import bincnpc.gun.slashblade.adapter.BladeAdapter;
import com.google.gson.*;
import java.util.*;
import net.minecraft.item.ItemStack;

/** Read-only editing capabilities from server-owned hand and reserve weapons. */
public final class EditorWeapons {
    private EditorWeapons() {}
    public static JsonObject presentation(CombatContext ctx, bincnpc.gun.slashblade.action.ActionRegistry.Definition action) {
        for (int i = 0; i < Armory.SIZE; i++) {
Object cnpcgun$value0; BladeAdapter cnpcgun$match0;

            ItemStack stack = ctx.data.armory.get(ctx.npc, i); WeaponAdapter adapter = AdapterRegistry.find(stack);
            if (((cnpcgun$value0 = adapter) instanceof BladeAdapter && (cnpcgun$match0 = (BladeAdapter)cnpcgun$value0) != null) && adapter.id().equals(action.adapterId))
                return AdapterRegistry.query(adapter, () -> cnpcgun$match0.presentation(ctx.withWeapon(stack), action), new JsonObject());
        }
        return new JsonObject();
    }
    public static boolean adapter(CombatContext ctx, String id) {
        for (int i = 0; i < Armory.SIZE; i++) {
            WeaponAdapter adapter = AdapterRegistry.find(ctx.data.armory.get(ctx.npc, i));
            if (adapter != null && adapter.id().equals(id)) return true;
        }
        return false;
    }
    public static boolean has(CombatContext ctx, String capability) {
        for (int i = 0; i < Armory.SIZE; i++) {
Object cnpcgun$value2; BladeAdapter cnpcgun$match2;

Object cnpcgun$value1; GunAdapter cnpcgun$match1;

            ItemStack stack = ctx.data.armory.get(ctx.npc, i); WeaponAdapter adapter = AdapterRegistry.find(stack);
            if (((cnpcgun$value1 = adapter) instanceof GunAdapter && (cnpcgun$match1 = (GunAdapter)cnpcgun$value1) != null)) {
                Views.GunCaps caps = AdapterRegistry.query(cnpcgun$match1, () -> cnpcgun$match1.capabilities(stack), null);
                if (caps != null && caps.has(capability)) return true;
            } else if (((cnpcgun$value2 = adapter) instanceof BladeAdapter && (cnpcgun$match2 = (BladeAdapter)cnpcgun$value2) != null)) {
                Views.BladeCaps caps = AdapterRegistry.query(cnpcgun$match2, () -> cnpcgun$match2.capabilities(stack), null);
                if (caps != null && caps.has(capability)) return true;
            }
        }
        return false;
    }
    public static boolean firingMode(CombatContext ctx, String mode) {
        for (int i = 0; i < Armory.SIZE; i++) {
Object cnpcgun$value3; GunAdapter cnpcgun$match3;

            ItemStack stack = ctx.data.armory.get(ctx.npc, i); WeaponAdapter adapter = AdapterRegistry.find(stack);
            if (((cnpcgun$value3 = adapter) instanceof GunAdapter && (cnpcgun$match3 = (GunAdapter)cnpcgun$value3) != null)) {
                Views.GunCaps caps = AdapterRegistry.query(cnpcgun$match3, () -> cnpcgun$match3.capabilities(stack), null);
                if (caps != null && caps.firingModes != null && Arrays.asList(caps.firingModes).contains(mode)) return true;
            }
        }
        return false;
    }
    public static JsonArray snapshot(CombatContext ctx) {
        JsonArray slots = new JsonArray();
        for (int i = 0; i < Armory.SIZE; i++) {
Object cnpcgun$value5; BladeAdapter cnpcgun$match5;

Object cnpcgun$value4; GunAdapter cnpcgun$match4;

            ItemStack stack = ctx.data.armory.get(ctx.npc, i); WeaponAdapter adapter = AdapterRegistry.find(stack);
            JsonObject item = new JsonObject(); item.addProperty("slot", i);
            item.addProperty("adapter", adapter == null ? "" : adapter.id());
            if (((cnpcgun$value4 = adapter) instanceof GunAdapter && (cnpcgun$match4 = (GunAdapter)cnpcgun$value4) != null)) {
                item.add("gun", SafeJson.GSON.toJsonTree(AdapterRegistry.query(cnpcgun$match4, () -> cnpcgun$match4.capabilities(stack), null)));
                item.addProperty("interval", AdapterRegistry.query(cnpcgun$match4, () -> cnpcgun$match4.baseInterval(stack), 4));
            }
            if (((cnpcgun$value5 = adapter) instanceof BladeAdapter && (cnpcgun$match5 = (BladeAdapter)cnpcgun$value5) != null)) item.add("slashBlade", SafeJson.GSON.toJsonTree(AdapterRegistry.query(cnpcgun$match5, () -> cnpcgun$match5.capabilities(stack), null)));
            slots.add(item);
        }
        return slots;
    }
}
