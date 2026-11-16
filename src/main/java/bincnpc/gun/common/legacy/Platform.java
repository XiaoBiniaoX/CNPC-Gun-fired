package bincnpc.gun.common.legacy;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import java.util.UUID;

/** Small native API differences shared by combat, persistence and optional adapters. */
public final class Platform {
    private Platform() {}
    public static ResourceLocation id(String value) {
        if (value == null || value.length() > 256 || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) return null;
        try { return new ResourceLocation(value); } catch (IllegalArgumentException invalid) { return null; }
    }
    public static NBTTagCompound tag(ItemStack stack) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        return stack.getTagCompound();
    }
    public static ItemStack copy(ItemStack stack, int count) { ItemStack result = stack.copy(); result.setCount(count); return result; }
    public static java.util.List<net.minecraft.nbt.NBTBase> tags(net.minecraft.nbt.NBTTagList list) {
        java.util.List<net.minecraft.nbt.NBTBase> result = new java.util.ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) result.add(list.get(i));
        return result;
    }
    public static boolean is(ItemStack stack, net.minecraft.item.Item item) { return !stack.isEmpty() && stack.getItem() == item; }
    public static boolean sameItem(ItemStack a, ItemStack b) {
        return ItemStack.areItemsEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b) && a.areCapsCompatible(b);
    }
    public static Vec3d motion(Entity entity) { return new Vec3d(entity.motionX, entity.motionY, entity.motionZ); }
    public static void motion(Entity entity, Vec3d velocity) { motion(entity, velocity.x, velocity.y, velocity.z); }
    public static void motion(Entity entity, double x, double y, double z) {
        entity.motionX = x; entity.motionY = y; entity.motionZ = z; entity.velocityChanged = true;
    }
    public static double eyeY(EntityLivingBase entity) { return entity.posY + entity.getEyeHeight(); }
    public static double horizontalSquare(Vec3d vector) { return vector.x * vector.x + vector.z * vector.z; }
    public static void yaw(Entity entity, float value) { entity.rotationYaw = value; }
    public static void pitch(Entity entity, float value) { entity.rotationPitch = value; }
    public static boolean spectator(Entity entity) { return entity instanceof net.minecraft.entity.player.EntityPlayer && ((net.minecraft.entity.player.EntityPlayer)entity).isSpectator(); }
    public static boolean allied(noppes.npcs.entity.EntityNPCInterface npc, EntityLivingBase target) {
        if (npc.isOnSameTeam(target) || target == npc.getOwner()) return true;
        if (target instanceof noppes.npcs.entity.EntityNPCInterface) {
            noppes.npcs.entity.EntityNPCInterface other = (noppes.npcs.entity.EntityNPCInterface)target;
            if (npc.getOwner() != null && npc.getOwner() == other.getOwner()) return true;
            return npc.getFaction() != null && other.getFaction() != null
                && npc.getFaction().id == other.getFaction().id;
        }
        return false;
    }
    public static Entity entity(World world, UUID id) {
        if (world instanceof WorldServer) return ((WorldServer)world).getEntityFromUuid(id);
        for (Entity entity : world.loadedEntityList) if (id.equals(entity.getUniqueID())) return entity;
        return null;
    }
}
