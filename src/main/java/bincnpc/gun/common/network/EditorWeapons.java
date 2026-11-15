package bincnpc.gun.common.network;

import bincnpc.gun.common.adapter.*;
import bincnpc.gun.common.api.*;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.gun.adapter.GunAdapter;
import bincnpc.gun.gun.switching.Armory;
import bincnpc.gun.slashblade.adapter.BladeAdapter;
import com.google.gson.*;
import java.util.*;
import net.minecraft.world.item.ItemStack;

/** Read-only editing capabilities from server-owned hand and reserve weapons. */
public final class EditorWeapons {
    private EditorWeapons() {}
    public static JsonObject presentation(CombatContext ctx, bincnpc.gun.slashblade.action.ActionRegistry.Definition action) {
        for (int i = 0; i < Armory.SIZE; i++) {
            ItemStack stack = ctx.data.armory.get(ctx.npc, i); WeaponAdapter adapter = AdapterRegistry.find(stack);
            if (adapter instanceof BladeAdapter blade && adapter.id().equals(action.adapterId))
                return AdapterRegistry.query(adapter, () -> blade.presentation(ctx.withWeapon(stack), action), new JsonObject());
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
            ItemStack stack = ctx.data.armory.get(ctx.npc, i); WeaponAdapter adapter = AdapterRegistry.find(stack);
            if (adapter instanceof GunAdapter gun) {
                Views.GunCaps caps = AdapterRegistry.query(gun, () -> gun.capabilities(stack), null);
                if (caps != null && caps.has(capability)) return true;
            } else if (adapter instanceof BladeAdapter blade) {
                Views.BladeCaps caps = AdapterRegistry.query(blade, () -> blade.capabilities(stack), null);
                if (caps != null && caps.has(capability)) return true;
            }
        }
        return false;
    }
    public static boolean firingMode(CombatContext ctx, String mode) {
        for (int i = 0; i < Armory.SIZE; i++) {
            ItemStack stack = ctx.data.armory.get(ctx.npc, i); WeaponAdapter adapter = AdapterRegistry.find(stack);
            if (adapter instanceof GunAdapter gun) {
                Views.GunCaps caps = AdapterRegistry.query(gun, () -> gun.capabilities(stack), null);
                if (caps != null && caps.firingModes != null && Arrays.asList(caps.firingModes).contains(mode)) return true;
            }
        }
        return false;
    }
    public static JsonArray snapshot(CombatContext ctx) {
        JsonArray slots = new JsonArray();
        for (int i = 0; i < Armory.SIZE; i++) {
            ItemStack stack = ctx.data.armory.get(ctx.npc, i); WeaponAdapter adapter = AdapterRegistry.find(stack);
            JsonObject item = new JsonObject(); item.addProperty("slot", i);
            item.addProperty("adapter", adapter == null ? "" : adapter.id());
            if (adapter instanceof GunAdapter gun) {
                item.add("gun", SafeJson.GSON.toJsonTree(AdapterRegistry.query(gun, () -> gun.capabilities(stack), null)));
                item.addProperty("interval", AdapterRegistry.query(gun, () -> gun.baseInterval(stack), 4));
            }
            if (adapter instanceof BladeAdapter blade) item.add("slashBlade", SafeJson.GSON.toJsonTree(AdapterRegistry.query(blade, () -> blade.capabilities(stack), null)));
            slots.add(item);
        }
        return slots;
    }
}
