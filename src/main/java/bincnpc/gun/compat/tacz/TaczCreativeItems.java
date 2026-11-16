package bincnpc.gun.compat.tacz;

import com.tacz.legacy.api.item.*;
import com.tacz.legacy.api.item.gun.FireMode;
import com.tacz.legacy.common.item.LegacyItems;
import com.tacz.legacy.common.resource.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import java.util.*;

/** Common native templates, independent of the client's translated creative ordering. */
final class TaczCreativeItems {
    private TaczCreativeItems() {}
    static List<ItemStack> items() {
        TACZRuntimeSnapshot snapshot = TACZGunPackRuntimeRegistry.currentSnapshotForJava();
        List<ItemStack> items = new ArrayList<>();
        List<ResourceLocation> guns = new ArrayList<>(bincnpc.gun.common.legacy.Legacy.keys(snapshot.getGuns()));
        guns.sort(Comparator.comparing(ResourceLocation::toString));
        for (ResourceLocation id : guns) {
            GunCombatData data = GunDataAccessor.getGunData(id);
            if (data == null) continue;
            ItemStack item = new ItemStack(LegacyItems.INSTANCE.getMODERN_KINETIC_GUN$TACZ_Legacy());
            IGun gun = (IGun)item.getItem();
            gun.setGunId(item, id); gun.setCurrentAmmoCount(item, Math.max(0, data.getAmmoAmount()));
            String mode = data.getFireModesSet().isEmpty() ? "semi" : data.getFireModesSet().get(0);
            gun.setFireMode(item, mode.equals("auto") ? FireMode.AUTO : mode.equals("burst") ? FireMode.BURST : FireMode.SEMI);
            gun.setBulletInBarrel(item, data.getBoltType() != BoltType.OPEN_BOLT);
            items.add(item);
        }
        List<ResourceLocation> ammos = new ArrayList<>(bincnpc.gun.common.legacy.Legacy.keys(snapshot.getAmmos()));
        ammos.sort(Comparator.comparing(ResourceLocation::toString));
        for (ResourceLocation id : ammos) {
            ItemStack item = new ItemStack(LegacyItems.INSTANCE.getAMMO$TACZ_Legacy());
            ((IAmmo)item.getItem()).setAmmoId(item, id); items.add(item);
        }
        List<ResourceLocation> attachments = new ArrayList<>(bincnpc.gun.common.legacy.Legacy.keys(snapshot.getAttachments()));
        attachments.sort(Comparator.comparing(ResourceLocation::toString));
        for (ResourceLocation id : attachments) {
            ItemStack item = new ItemStack(LegacyItems.INSTANCE.getATTACHMENT$TACZ_Legacy());
            ((IAttachment)item.getItem()).setAttachmentId(item, id); items.add(item);
        }
        return items;
    }
}
