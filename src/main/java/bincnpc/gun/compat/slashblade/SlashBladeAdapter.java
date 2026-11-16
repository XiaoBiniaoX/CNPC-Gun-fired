package bincnpc.gun.compat.slashblade;

import bincnpc.gun.common.api.*;
import bincnpc.gun.common.adapter.AdapterRegistry;
import bincnpc.gun.common.combat.*;
import bincnpc.gun.common.config.Parameter;
import bincnpc.gun.slashblade.action.*;
import bincnpc.gun.slashblade.adapter.BladeAdapter;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.item.ItemSlashBlade.ComboSequence;
import mods.flammpfeil.slashblade.specialattack.*;
import mods.flammpfeil.slashblade.ability.UntouchableTime;
import net.minecraft.entity.*;
import net.minecraft.entity.projectile.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Enchantments;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.registry.IThrowableEntity;
import noppes.npcs.entity.EntityNPCInterface;
import com.google.gson.*;
import java.util.*;
import static bincnpc.gun.common.config.Parameter.*;

public final class SlashBladeAdapter implements BladeAdapter {
    private final Map<Integer,String> arts=new LinkedHashMap<>();
    public SlashBladeAdapter(){MinecraftForge.EVENT_BUS.register(this);}
    @Override public void close(){MinecraftForge.EVENT_BUS.unregister(this);}
    @Override public String id(){return "slashblade";}
    @Override public Object catalogueRevision(){return Arrays.asList(mods.flammpfeil.slashblade.SlashBlade.BladeRegistry.size(),
        new ArrayList<>(mods.flammpfeil.slashblade.ItemSlashBladeNamed.NamedBlades));}
    @Override public java.util.List<ItemStack> catalogueItems(){return SlashBladeCreativeItems.items();}
    @Override public boolean matches(ItemStack stack){return stack!=null && !stack.isEmpty() && stack.getItem() instanceof ItemSlashBlade;}
    private NBTTagCompound tag(ItemStack stack){return ItemSlashBlade.getItemTagCompound(stack);}
    @Override public Views.BladeCaps capabilities(ItemStack stack) {
        Views.BladeCaps c=new Views.BladeCaps();c.canAttack=c.specialAttack=c.phantomSword=c.lockOn=c.defense=c.counter=c.movement=true;
        c.actionTypes=new String[]{"NORMAL_ATTACK","COMBO_ATTACK","SPECIAL_ATTACK","PHANTOM_SWORD","LOCK_ON","DEFENSE","COUNTER","MOVEMENT"};return c;
    }
    private void add(ActionRegistry registry,String action,String type,String label,String backend,int duration,int cooldown,Integer cost,
            boolean parallel,boolean target,Parameter[] schema,String...tags) {
        registry.register(new ActionRegistry.Definition(new Views.ActionInfo(action,type,"slashblade",label,
            "cnpcgun.action.description."+type.toLowerCase(Locale.ROOT),cooldown,duration,cost,schema,true,"",
            !type.equals("SPECIAL_ATTACK"),parallel,target),id(),backend,tags));
    }
    @Override public void registerActions(ActionRegistry registry) {
        arts.clear();
        for(Map.Entry<String,ComboSequence> entry:LegacyBladeActions.COMBOS.entrySet()) {
            String path=entry.getKey();ComboSequence combo=entry.getValue();List<String> tags=new ArrayList<>();
            tags.add(path.contains("aerial") || path.startsWith("a_") || combo.name().startsWith("A")?"air":"ground");
            if(path.contains("rapid") || path.contains("rising") || path.contains("stinger"))tags.add("dash");
            if(path.contains("cleave"))tags.add("fall");
            if(combo==ComboSequence.SlashDim)tags.add("dimension");
            if(combo==ComboSequence.Noutou || combo==ComboSequence.RapidSlashEnd)tags.add("recovery");
            String type=tags.contains("dash") || tags.contains("fall")?"MOVEMENT":path.startsWith("combo_")?"NORMAL_ATTACK":"COMBO_ATTACK";
            add(registry,"slashblade:slashblade/combo/"+path,type,"combo.slashblade."+path,"combo:slashblade:"+path,
                LegacyBladeActions.duration(combo),LegacyBladeActions.duration(combo),0,false,true,new Parameter[0],tags.toArray(new String[0]));
        }
        List<Integer> keys=new ArrayList<>(bincnpc.gun.common.legacy.Legacy.keys(ItemSlashBlade.specialAttacks));Collections.sort(keys);
        for(int key:keys) {
            SpecialAttackBase art=ItemSlashBlade.specialAttacks.get(key);if(art==null)continue;
            String path=art.toString().replaceAll("[^A-Za-z0-9_./-]","_").toLowerCase(Locale.ROOT);
            String action="slashblade:slashblade/sa/"+path+"_"+key;
            boolean supported=LegacySwordArts.supported(art);
            registry.register(new ActionRegistry.Definition(new Views.ActionInfo(action,"SPECIAL_ATTACK","slashblade",
                "slashblade.specialattack."+art.toString(),"cnpcgun.action.description.special_attack",40,16,LegacySwordArts.cost(art),
                new Parameter[]{choice("artsType","Success","Success","Jackpot","Super")},supported,supported?"":"UNSUPPORTED_NPC",
                false,false,true),id(),"sa:"+key,"dimension"));
            arts.put(key,action);
        }
        add(registry,"slashblade:cnpcgun/sa/native_super","SPECIAL_ATTACK","cnpcgun.action.native_super","sa:super",40,400,20,false,true,
            new Parameter[]{choice("artsType","Super","Success","Jackpot","Super")},"dimension","super");
        for(String kind:new String[]{"basic","spiral","storm","blistering","rain"}) {
            List<Parameter> params=new ArrayList<>();int count=kind.equals("basic")?1:kind.equals("rain")?18:6;
            params.add(integer("count",count,1,kind.equals("rain")?32:16));
            if(!kind.equals("basic"))params.add(integer("delay",0,0,100));
            if(kind.equals("storm") || kind.equals("rain"))params.add(integer("targetCount",1,1,16));
            add(registry,"slashblade:slashblade/phantom/"+kind,"PHANTOM_SWORD","cnpcgun.action."+kind,"phantom:"+kind,4,
                kind.equals("basic")?8:40,kind.equals("basic")?1:10,true,!kind.equals("spiral"),params.toArray(new Parameter[0]),"phantom",kind);
        }
        add(registry,"slashblade:slashblade/phantom/spiral_fire","PHANTOM_SWORD","cnpcgun.action.spiral_fire","phantom:spiral_fire",2,10,0,true,true,new Parameter[0],"phantom");
        add(registry,"slashblade:cnpcgun/lock_on","LOCK_ON","cnpcgun.action.lock_on","lock",1,1,0,true,true,new Parameter[0],"lock");
        add(registry,"slashblade:cnpcgun/spin_guard","DEFENSE","cnpcgun.action.spin_guard","guard",12,30,0,true,false,new Parameter[]{integer("duration",12,1,100)},"counter","guard");
        add(registry,"slashblade:cnpcgun/air_trick","MOVEMENT","cnpcgun.action.air_trick","teleport",8,40,0,false,true,new Parameter[]{number("distance",2,1,4)},"teleport");
        add(registry,"slashblade:cnpcgun/counter","COUNTER","cnpcgun.action.counter","counter",8,20,0,false,true,new Parameter[0],"counter");
    }
    private ComboSequence current(CombatContext ctx) {
        // The native onUpdate owns timeout and Noutou transitions; state queries are read-only.
        return ItemSlashBlade.getComboSequence(tag(ctx.weapon));
    }
    @Override public JsonObject state(CombatContext ctx) {
        NBTTagCompound tag=tag(ctx.weapon);ComboSequence combo=current(ctx);JsonObject out=new JsonObject();
        out.addProperty("combo","slashblade:"+LegacyBladeActions.path(combo));out.addProperty("resource",ItemSlashBlade.ProudSoul.get(tag));
        EnumSet<ItemSlashBlade.SwordType> types=((ItemSlashBlade)ctx.weapon.getItem()).getSwordType(ctx.weapon);
        out.addProperty("broken",types.contains(ItemSlashBlade.SwordType.Broken));out.addProperty("sealed",types.contains(ItemSlashBlade.SwordType.Sealed));
        out.addProperty("elapsed",Math.max(0,ctx.now-ItemSlashBlade.LastActionTime.get(tag)));
        out.addProperty("specialAttack",arts.getOrDefault(ItemSlashBlade.SpecialAttackType.get(tag),""));
        MaidPhaseRules.describe(combo,ctx.now-ItemSlashBlade.LastActionTime.get(tag),ctx.config.text("slashblade.preset").equals("MAID"),out);return out;
    }
    @Override public JsonObject presentation(CombatContext ctx,ActionRegistry.Definition action) {
        JsonObject out=BladeAdapter.super.presentation(ctx,action);ComboSequence combo=ComboSequence.SlashDim;
        if(action.backend.startsWith("combo:"))combo=LegacyBladeActions.resolve(action.backend.substring(6));
        else if(action.backend.equals("counter") || action.backend.equals("guard"))combo=ComboSequence.Saya1;
        out.addProperty("stageId","slashblade:"+LegacyBladeActions.path(combo));out.addProperty("legacyCombo",combo.name());
        if(action.backend.startsWith("sa:")) {
            JsonObject stages=new JsonObject(),durations=new JsonObject();
            for(String mode:new String[]{"Success","Jackpot","Super"}){stages.addProperty(mode,"slashblade:"+LegacyBladeActions.path(combo));durations.addProperty(mode,mode.equals("Success")?16:12);}
            out.add("previewStages",stages);out.add("previewDurations",durations);out.addProperty("defaultArtsType","Success");
        }
        return out;
    }
    @Override public boolean canChain(CombatContext ctx,ActionRegistry.Definition action,JsonObject params) {
        ActionRegistry.Definition current=ActionRegistry.INSTANCE.get(ctx.runtime.scheduler.action);
        if(ctx.runtime.scheduler.busy(ctx.now) && current!=null && (current.tags.contains("super")
                || current.info.type.equals("SPECIAL_ATTACK") && ctx.runtime.actionEnd-ctx.runtime.maid.lastSpecial>=40))return false;
        JsonObject phase=state(ctx);return phase.get("elapsed").getAsLong()>=4
            && (action.backend.startsWith("combo:") || phase.get("perfectCharge").getAsBoolean() && action.backend.startsWith("sa:"));
    }
    @Override public String nextNormal(CombatContext ctx) {
        ComboSequence combo = current(ctx);
        boolean bare = ((ItemSlashBlade)ctx.weapon.getItem()).getSwordType(ctx.weapon).contains(ItemSlashBlade.SwordType.NoScabbard);
        if (ctx.npc.onGround && (bare || (combo == ComboSequence.None || combo == ComboSequence.Noutou)
                && ctx.config.integer("slashblade.attackInterval") >= ComboSequence.Saya1.comboResetTicks))
            combo = combo == ComboSequence.Kiriage ? ComboSequence.Kiriorosi : ComboSequence.Kiriage;
        else combo = LegacyBladeActions.next(combo, !ctx.npc.onGround);
        return "slashblade:slashblade/combo/" + LegacyBladeActions.path(combo);
    }
    @Override public Result execute(CombatContext ctx,ActionRegistry.Definition action,JsonObject params) {
        if(!CombatService.serverThread(ctx.npc) || ctx.runtime.editorPaused)return Result.fail("PERMISSION_DENIED");
        EnumSet<ItemSlashBlade.SwordType> types=((ItemSlashBlade)ctx.weapon.getItem()).getSwordType(ctx.weapon);
        if(!ctx.config.bool("slashblade.ignoreRequirements") && (types.contains(ItemSlashBlade.SwordType.Broken) || types.contains(ItemSlashBlade.SwordType.Sealed)))return Result.fail("CONDITION_NOT_MET");
        NBTTagCompound tag=tag(ctx.weapon);ItemSlashBlade.TargetEntityId.set(tag,ctx.validTarget()?ctx.target.getEntityId():0);
        ctx.npc.rotationYaw=ctx.yaw();ctx.npc.rotationYawHead=ctx.yaw();ctx.npc.rotationPitch=ctx.pitch();
        JsonObject options=ctx.config.action(action.info.id);
        int cost=(int)Math.ceil((action.info.resourceCost==null?0:action.info.resourceCost)*ActionConfig.value(options,"resourceMultiplier").getAsDouble());
        int reserve=Math.max(ctx.config.integer("slashblade.resourceReserve"),ActionConfig.value(options,"resourceReserve").getAsInt());
        if(ctx.config.bool("slashblade.ignoreRequirements")){cost=0;reserve=0;}
        if(action.backend.startsWith("phantom:"))return LegacyPhantoms.execute(ctx,action.backend.substring(8),params,cost,reserve);
        if(ItemSlashBlade.ProudSoul.get(tag)-cost<reserve)return Result.fail("INSUFFICIENT_RESOURCE");
        Result result;
        if(action.backend.startsWith("combo:"))result=LegacyBladeActions.attack(ctx,LegacyBladeActions.resolve(action.backend.substring(6)));
        else if(action.backend.startsWith("sa:")) {
            SpecialAttackBase art=action.backend.equals("sa:super")?new SlashDimension():ItemSlashBlade.specialAttacks.get(Integer.parseInt(action.backend.substring(3)));
            result=LegacySwordArts.execute(ctx,art,params.get("artsType").getAsString());
        } else switch(action.backend) {
            case "lock":return Result.success();
            case "guard":
                if(!ctx.npc.onGround || !ctx.config.bool("slashblade.ignoreRequirements") && EnchantmentHelper.getEnchantmentLevel(Enchantments.THORNS,ctx.weapon)<=0)return Result.fail("CONDITION_NOT_MET");
                ctx.runtime.maid.guardStart=ctx.now;ctx.runtime.maid.guardEnd=ctx.now+params.get("duration").getAsInt();
                ctx.runtime.maid.guardBudget=ctx.npc.getMaxHealth()*(ctx.config.text("slashblade.preset").equals("MAID")?1:0.2);return Result.success();
            case "counter":
                if(ctx.now-ctx.runtime.lastHurt>12)return Result.fail("CONDITION_NOT_MET");
                result=LegacyBladeActions.attack(ctx,ComboSequence.Battou);break;
            case "teleport":
                if(!ctx.validTarget() || ctx.distance>32)return Result.fail("OUT_OF_RANGE");
                Vec3d offset=ctx.npc.getPositionVector().subtract(ctx.target.getPositionVector()).normalize().scale(params.get("distance").getAsDouble());
                Vec3d next=ctx.target.getPositionVector().add(offset);
                WorldServer world=(WorldServer)ctx.npc.world;
                AxisAlignedBB box=ctx.npc.getEntityBoundingBox().offset(next.subtract(ctx.npc.getPositionVector()));
                if(!bincnpc.gun.throwing.CollisionSnapshot.volumeLoaded(world,box) || !world.getCollisionBoxes(ctx.npc,box).isEmpty())return Result.fail("CONDITION_NOT_MET");
                boolean moved=ctx.npc.attemptTeleport(next.x,next.y,next.z);
                if(moved){UntouchableTime.setUntouchableTime(ctx.npc,10,false);ctx.npc.getNavigator().clearPath();}
                return moved?Result.success():Result.fail("CONDITION_NOT_MET");
            default:return Result.fail("UNSUPPORTED");
        }
        if(result.ok && cost>0)ItemSlashBlade.ProudSoul.set(tag,ItemSlashBlade.ProudSoul.get(tag)-cost);
        return result;
    }
    @Override public void tick(CombatContext ctx) {
        if(!CombatService.serverThread(ctx.npc))return;
        NBTTagCompound tag=tag(ctx.weapon);ItemSlashBlade.TargetEntityId.set(tag,ctx.validTarget()?ctx.target.getEntityId():0);
        ((ItemSlashBlade)ctx.weapon.getItem()).onUpdate(ctx.weapon,ctx.npc.world,ctx.npc,0,true);
        bincnpc.gun.slashblade.strategy.MaidCombatState maid=ctx.runtime.maid;
        if(maid.lastTick!=ctx.now){long elapsed=maid.lastTick==Long.MIN_VALUE?1:Math.min(100,Math.max(1,ctx.now-maid.lastTick));maid.lastTick=ctx.now;maid.pressure=Math.max(0,maid.pressure-2*elapsed);}
        if(maid.guardEnd<=ctx.now)maid.guardBudget=0;
        if(maid.escapeAt>0 && ctx.now>=maid.escapeAt){maid.escapeAt=0;maid.escapeUntil=ctx.now+20;}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void burst(ExplosionEvent.Detonate event) {
        LegacyBladeCombat.filterBurst(event);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void guard(LivingAttackEvent event) {
        if(!(event.getEntityLiving() instanceof EntityNPCInterface))return;
        EntityNPCInterface npc=(EntityNPCInterface)event.getEntityLiving();
        if(!CombatService.serverThread(npc) || !CombatService.validNpc(npc))return;
        CombatContext ctx=new CombatContext(npc);
        if(ctx.runtime.adapter!=this || !AdapterRegistry.loaded(id()) || ctx.runtime.editorPaused || !ctx.runtime.command.equals("AUTO")
                || !ctx.config.bool("combat.enabled") || !ctx.config.bool("slashblade.enabled") || !ctx.config.bool("slashblade.counter")
                || event.getSource()==DamageSource.OUT_OF_WORLD)return;
        Entity attacker=event.getSource().getTrueSource();
        if(!(attacker instanceof EntityLivingBase) || !CombatService.validTarget(npc,(EntityLivingBase)attacker) || !npc.onGround)return;
        bincnpc.gun.slashblade.strategy.MaidCombatState maid=ctx.runtime.maid;boolean highest=ctx.config.text("slashblade.preset").equals("MAID");
        String guard="slashblade:cnpcgun/spin_guard";
        if(highest && !ctx.data.blade.autoEnabled(ctx,guard))return;
        if(highest && ctx.now<maid.guardJustEnd){event.setCanceled(true);return;}
        if(highest && maid.guardBudget<=0) {
            maid.pressure+=150;maid.accumulatedDamage+=Math.max(0,event.getAmount());
            if((maid.pressure>500 || maid.accumulatedDamage>npc.getMaxHealth()*0.2) && ctx.now>=maid.guardCooldown
                    && ctx.data.blade.eligible(ctx,guard) && ctx.data.blade.execute(ctx,guard,new JsonObject()).ok){maid.pressure=0;maid.accumulatedDamage=0;}
            return;
        }
        if(ctx.now>=maid.guardEnd || maid.guardBudget<=0)return;
        boolean just=ctx.now-(highest?maid.lastSlash:maid.guardStart)<3;
        Entity direct=event.getSource().getImmediateSource();
        if(!highest && !just && (!(direct instanceof IThrowableEntity) && !(direct instanceof EntityArrow) && !(direct instanceof EntityThrowable) && !(direct instanceof EntityFireball)))return;
        if(!highest && event.getSource().isUnblockable() && !just)return;
        event.setCanceled(true);ctx.runtime.lastHurt=ctx.now;
        if(!just){maid.guardBudget=Math.max(0,maid.guardBudget-event.getAmount());if(maid.guardBudget<=0){maid.guardCooldown=ctx.now+300;maid.guardEnd=ctx.now;maid.escapeAt=ctx.now+5;}}
        if(direct instanceof EntityArrow || direct instanceof EntityThrowable || direct instanceof EntityFireball || direct instanceof IThrowableEntity) {
            if(direct instanceof IThrowableEntity)((IThrowableEntity)direct).setThrower(npc);
            if(direct instanceof EntityArrow)((EntityArrow)direct).shootingEntity=npc;
            if(direct instanceof EntityFireball)((EntityFireball)direct).shootingEntity=npc;
            net.minecraft.util.math.Vec3d velocity=npc.getLookVec().scale(Math.max(1,bincnpc.gun.common.legacy.Platform.motion(direct).length()));
            bincnpc.gun.common.legacy.Platform.motion(direct,velocity);
        } else if(direct instanceof EntityLivingBase)((EntityLivingBase)direct).knockBack(npc,0.5F,npc.posX-direct.posX,npc.posZ-direct.posZ);
        if(just){maid.guardJustEnd=ctx.now+10;maid.specialBurst=0;maid.specialCooldown=0;maid.nextPhantom=0;
            for(ActionRegistry.Definition def:ActionRegistry.INSTANCE.all())if(def.adapterId.equals(id()) && def.info.type.equals("PHANTOM_SWORD"))ctx.runtime.cooldowns.remove(def.info.id);
            ctx.runtime.cooldowns.remove(maid.specialAction);
        }
        bincnpc.gun.common.network.GunNetwork.animation(ctx,"DEFENSE",8,"combo:slashblade:combo_a1");
    }
    @Override public Result interruptForThrow(CombatContext ctx) {
        JsonObject phase=state(ctx);if(!phase.get("idle").getAsBoolean() && !phase.get("canInterrupt").getAsBoolean())return Result.fail("BUSY");
        ItemSlashBlade.setComboSequence(tag(ctx.weapon),ComboSequence.None);ItemSlashBlade.LastActionTime.set(tag(ctx.weapon),ctx.now);
        ctx.npc.resetActiveHand();ctx.npc.updateClient=true;return Result.success();
    }
    @Override public void cancel(CombatContext ctx) {
        ctx.runtime.maid.reset();ctx.npc.resetActiveHand();
        if(matches(ctx.weapon)){ItemSlashBlade.setComboSequence(tag(ctx.weapon),ComboSequence.None);ItemSlashBlade.LastActionTime.set(tag(ctx.weapon),ctx.now);ItemSlashBlade.TargetEntityId.set(tag(ctx.weapon),0);}
    }
}
