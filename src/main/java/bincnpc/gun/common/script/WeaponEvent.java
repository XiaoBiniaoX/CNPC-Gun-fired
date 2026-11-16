package bincnpc.gun.common.script;

import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.*;
import noppes.npcs.api.event.NpcEvent;
import noppes.npcs.entity.EntityNPCInterface;
import net.minecraft.entity.EntityLivingBase;

public final class WeaponEvent extends NpcEvent {
    public final String name, actionId, weaponId, state;
    public final IEntity target;
    public final bincnpc.gun.common.api.Result result;
    public WeaponEvent(EntityNPCInterface npc, String name, String action, String weapon, EntityLivingBase target, bincnpc.gun.common.api.Result result, String state) {
        super(npc.wrappedNPC);
        this.name = name; this.actionId = action; this.weaponId = weapon;
        this.target = target == null ? null : NpcAPI.Instance().getIEntity(target);
        this.result = result; this.state = state;
    }
}
