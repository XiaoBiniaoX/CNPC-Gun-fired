package bincnpc.gun.common.npc;

import bincnpc.gun.common.adapter.WeaponAdapter;
import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.api.Views;
import bincnpc.gun.common.combat.Scheduler;
import bincnpc.gun.common.script.WeaponEvent;
import java.util.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class RuntimeState {
    public final bincnpc.gun.common.combat.MovementState movement = new bincnpc.gun.common.combat.MovementState();
    public long lastTick = Long.MIN_VALUE, nextDecision, nextShot, noAmmoRetry, nextSwitch, lastSupply,
        firstTargetTick, lastHurt = -1000, nextTargetScan, actionEnd;
    public long inventoryFingerprint, nextCapabilityCheck, nextAnimationSummary, lastFired = -1000;
    public long weaponEpoch, decisionCycle = Long.MIN_VALUE;
    public bincnpc.gun.common.combat.CombatDecisions.Ticket decisionTicket;
    public bincnpc.gun.common.combat.CombatDecisions.Advice decisionAdvice;
    public long editorUntil, nextDropScan;
    public boolean restoreSupplies = true, editorPaused;
    public float shotYawOffset, shotPitchOffset;
    public String animationSummary = "";
    public int burstRemaining, shotStreak, reloadTargetTicks;
    public long reloadStarted;
    public boolean reloadObserved;
    public boolean fireStopped, reloading, ammoEmpty, noAmmoEvent, aiming;
    public String combatState = "IDLE", weaponId = "", domain = "UNKNOWN", actionId = "",
        interrupted = "", comboId = "", comboNode = "", command = "AUTO", noAmmoCommand = "", lastBladeAction = "";
    public UUID targetId, comboTarget;
    public ItemStack lastStack = ItemStack.EMPTY;
    public WeaponAdapter adapter;
    public Views.GunCaps gunCaps;
    public Views.BladeCaps bladeCaps;
    public Result lastResult = Result.success();
    public final Scheduler scheduler = new Scheduler();
    public final bincnpc.gun.slashblade.strategy.MaidCombatState maid = new bincnpc.gun.slashblade.strategy.MaidCombatState();
    public final Map<String, Long> cooldowns = new HashMap<>();
    public final Map<UUID, Long> killAwards = new LinkedHashMap<>();
    public final Map<String, Object> adapterState = new HashMap<>();
    public final List<LivingEntity> nearbyTargets = new ArrayList<>();
    public final ArrayDeque<WeaponEvent> events = new ArrayDeque<>();
    public boolean flushingEvents;
    public long eventTick;
    public int eventsThisTick;
}
