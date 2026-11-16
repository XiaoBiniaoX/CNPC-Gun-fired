package bincnpc.gun.throwing;

import bincnpc.gun.CNPCGun;
import bincnpc.gun.common.combat.*;
import bincnpc.gun.common.legacy.Platform;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.*;
import net.minecraft.util.math.Vec3d;
import noppes.npcs.entity.EntityNPCInterface;

/** Only server-created projectiles enter this bounded table. */
public final class ThrowFlights {
    public static final int MAX_FLIGHTS=192,FAILURE_TICKS=40;
    private static final Map<UUID,Flight> flights=new LinkedHashMap<>();
    private static final String OWNER_TAG="CNPCGunThrowOwner";
    private static final class Flight{
        final UUID id,owner,target;
        final int dimension;
        final ThrowAdapter adapter;
        final ThrowAdapter.Profile profile;
        final Ballistics.Trajectory mode;
        final Vec3d goal;
        final double speed,travel;
        final long started;
        int failures,waypoint;
        long nextRay,nextRoute,nextSync,routeStarted;
        boolean direct;
        CollisionSnapshot.RouteCapture capture;
        ThrowPlanner.Ticket<List<Ballistics.Vec>> ticket;
        Ballistics.Vec routeOrigin,routeTarget;
        List<Ballistics.Vec> path=Collections.emptyList();
        Flight(CombatContext ctx,Entity projectile,ThrowAdapter adapter,ThrowAdapter.Profile profile,Ballistics.Candidate candidate,UUID target){
            id=projectile.getUniqueID();owner=ctx.npc.getUniqueID();this.target=target;dimension=ctx.npc.world.provider.getDimension();
            this.adapter=adapter;this.profile=profile;mode=candidate.mode();goal=CollisionSnapshot.vector(candidate.goal());
            speed=candidate.velocity().length();travel=candidate.ticks();started=ctx.now;
        }
        Flight(Flight prior,Entity bounce){
            id=bounce.getUniqueID();owner=prior.owner;target=prior.target;dimension=prior.dimension;adapter=prior.adapter;profile=prior.profile;
            mode=prior.mode;goal=prior.goal;speed=prior.speed;travel=prior.travel;started=prior.started;
        }
        void cancelRoute(){if(ticket!=null)ticket.cancel();ticket=null;capture=null;path=Collections.emptyList();waypoint=0;}
    }
    private ThrowFlights(){}
    public static boolean hasRoom(){return flights.size()<MAX_FLIGHTS;}
    public static boolean managed(Entity projectile){return projectile!=null&&projectile.getEntityData().hasUniqueId(OWNER_TAG);}
    public static void mark(CombatContext ctx,Entity projectile){
        if(CombatService.serverThread(ctx.npc)&&projectile.world==ctx.npc.world)projectile.getEntityData().setUniqueId(OWNER_TAG,ctx.npc.getUniqueID());
    }
    public static boolean register(CombatContext ctx,Entity projectile,ThrowAdapter adapter,ThrowAdapter.Profile profile,Ballistics.Candidate candidate,UUID target){
        if(!CombatService.serverThread(ctx.npc)||projectile.isDead||projectile.world!=ctx.npc.world||!hasRoom())return false;
        mark(ctx,projectile);flights.put(projectile.getUniqueID(),new Flight(ctx,projectile,adapter,profile,candidate,target));return true;
    }
    public static boolean owned(Entity projectile,Entity owner){
        return owner!=null&&managed(projectile)&&owner.getUniqueID().equals(projectile.getEntityData().getUniqueId(OWNER_TAG));
    }
    public static void followBounce(Entity prior,Entity bounce){
        if(prior==null||bounce==null||prior.world.isRemote||bounce.world!=prior.world||prior.world.getMinecraftServer()==null
            ||!prior.world.getMinecraftServer().isCallingFromMinecraftThread())return;
        Flight flight=flights.remove(prior.getUniqueID());if(flight==null)return;
        flight.cancelRoute();bounce.getEntityData().setUniqueId(OWNER_TAG,flight.owner);
        if(prior.hasNoGravity())flight.adapter.straight(bounce);
        flights.put(bounce.getUniqueID(),new Flight(flight,bounce));
    }
    public static void tick(MinecraftServer server){
        if(!server.isCallingFromMinecraftThread())return;
        for(Flight flight:new ArrayList<>(flights.values())){
            WorldServer level=server.getWorld(flight.dimension);
            Entity projectile=level==null?null:level.getEntityFromUuid(flight.id);
            if(projectile==null||projectile.isDead){remove(flight);continue;}
            long now=level.getTotalWorldTime();
            try{
                if(now<flight.started||now-flight.started>=Math.min(1200,flight.profile.life()+FAILURE_TICKS)){detonate(flight,projectile);continue;}
                if(flight.mode==Ballistics.Trajectory.TRACKING)tracking(level,flight,projectile,now);
                else{
                    if(flight.mode==Ballistics.Trajectory.CURVE&&flight.profile.customGravity())
                        Platform.motion(projectile,Platform.motion(projectile).add(0,-flight.profile.gravity(),0));
                    if((flight.profile.blastRadius()>0||flight.adapter.settleAtLanding(projectile))&&now-flight.started>=Math.ceil(flight.travel)
                        &&projectile.getPositionVector().squareDistanceTo(flight.goal)<Math.max(4,flight.profile.blastRadius()*flight.profile.blastRadius()))
                        flight.adapter.guide(projectile,Vec3d.ZERO);
                    try{
                        for(Ballistics.Cell cell:Ballistics.cells(CollisionSnapshot.vector(projectile.getPositionVector()),
                            CollisionSnapshot.vector(projectile.getPositionVector().add(Platform.motion(projectile))),flight.profile.radius(),1024))
                            if(!CollisionSnapshot.loadedShape(level,new net.minecraft.util.math.BlockPos(cell.x(),cell.y(),cell.z()))){flight.adapter.guide(projectile,Vec3d.ZERO);break;}
                    }catch(IllegalArgumentException tooLarge){flight.adapter.guide(projectile,Vec3d.ZERO);}
                }
                if(!projectile.isDead&&now>=flight.nextSync){flight.nextSync=now+5;flight.adapter.sync(projectile);}
            }catch(RuntimeException|LinkageError error){CNPCGun.LOGGER.warn("NPC throwable flight ended after adapter failure: {}",error.toString());projectile.setDead();remove(flight);}
        }
    }
    private static void tracking(WorldServer level,Flight f,Entity projectile,long now){
        Entity owner=level.getEntityFromUuid(f.owner),target=f.target==null?null:level.getEntityFromUuid(f.target);
        if(!(owner instanceof EntityNPCInterface)||!(target instanceof EntityLivingBase)
            ||!CombatService.validTarget((EntityNPCInterface)owner,(EntityLivingBase)target)||projectile.getDistanceSq(target)>128*128){
            f.cancelRoute();f.adapter.guide(projectile,Vec3d.ZERO);
            if(++f.failures>=FAILURE_TICKS)detonate(f,projectile);return;
        }
        Vec3d goal=CollisionSnapshot.center(target.getEntityBoundingBox());
        if(now>=f.nextRay){f.nextRay=now+4;f.direct=CollisionSnapshot.clear(level,projectile,projectile.getPositionVector(),goal);if(f.direct)f.cancelRoute();}
        if(f.direct){
            Vec3d movement=goal.subtract(projectile.getPositionVector());
            double speed=Math.min(f.speed,Math.max(0.01,movement.length()));Vec3d next=projectile.getPositionVector().add(movement.normalize().scale(speed));
            if(CollisionSnapshot.stepClear(level,projectile,projectile.getPositionVector(),next)){f.failures=0;f.adapter.guide(projectile,movement.normalize().scale(speed));return;}
            f.direct=false;
        }
        if(f.ticket!=null&&f.ticket.finished()){
            List<Ballistics.Vec> path=f.ticket.result();f.ticket=null;
            if(path!=null&&now-f.routeStarted<=28&&f.routeOrigin!=null&&f.routeTarget!=null
                &&f.routeOrigin.distance(CollisionSnapshot.vector(projectile.getPositionVector()))<=0.8&&f.routeTarget.distance(CollisionSnapshot.vector(goal))<=3){f.path=path;f.waypoint=0;}
            else f.path=Collections.emptyList();
        }
        while(f.waypoint<f.path.size()&&projectile.getPositionVector().squareDistanceTo(CollisionSnapshot.vector(f.path.get(f.waypoint)))<0.2*0.2)f.waypoint++;
        if(f.waypoint<f.path.size()){
            Vec3d movement=CollisionSnapshot.vector(f.path.get(f.waypoint)).subtract(projectile.getPositionVector());
            Vec3d step=movement.normalize().scale(Math.min(f.speed,movement.length()));
            if(CollisionSnapshot.stepClear(level,projectile,projectile.getPositionVector(),projectile.getPositionVector().add(step))){f.failures=0;f.adapter.guide(projectile,step);return;}
            f.path=Collections.emptyList();f.waypoint=0;
        }
        f.adapter.guide(projectile,Vec3d.ZERO);
        if(++f.failures>=FAILURE_TICKS){detonate(f,projectile);return;}
        if(f.capture!=null&&f.capture.capture()){
            Ballistics.Grid grid=f.capture.result();boolean fresh=f.capture.start().distance(CollisionSnapshot.vector(projectile.getPositionVector()))<=0.8&&now-f.routeStarted<=24;
            f.routeOrigin=f.capture.start();f.routeTarget=f.capture.target();f.capture=null;
            if(fresh&&grid!=null)f.ticket=ThrowPlanner.route(grid);if(f.ticket==null)f.nextRoute=now+4;
        }
        if(f.capture==null&&f.ticket==null&&now>=f.nextRoute){f.routeStarted=now;f.nextRoute=now+12;f.capture=new CollisionSnapshot.RouteCapture(level,projectile,goal);}
    }
    private static void remove(Flight flight){flights.remove(flight.id);flight.cancelRoute();}
    private static void detonate(Flight flight,Entity projectile){remove(flight);if(!projectile.isDead){flight.adapter.detonate(projectile);if(!projectile.isDead)projectile.setDead();}}
    public static void stop(MinecraftServer server){
        for(Flight flight:new ArrayList<>(flights.values())){
            flight.cancelRoute();WorldServer level=server.getWorld(flight.dimension);Entity entity=level==null?null:level.getEntityFromUuid(flight.id);
            if(entity!=null&&!entity.isDead)try{flight.adapter.restore(entity,flight.profile);}catch(RuntimeException|LinkageError ignored){entity.setNoGravity(false);}
        }
        flights.clear();
    }
}
