package bincnpc.gun.throwing;

import bincnpc.gun.common.combat.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import noppes.npcs.entity.EntityNPCInterface;

/** Budgeted main-thread terrain copies; numeric jobs never hold a world or entity. */
public final class CollisionSnapshot {
    public static final int MAX_CELLS=4096,PER_TICK=192,SERVER_BUDGET=2048;
    private static int remaining=SERVER_BUDGET;
    private final WorldServer level;
    private final EntityNPCInterface owner;
    private final Ballistics.Request request;
    private final List<Ballistics.Candidate> candidates;
    private final List<Ballistics.Cell> cells;
    private final Set<Ballistics.Cell> covered;
    private final List<Ballistics.Bounds> walls=new ArrayList<>();
    private int cursor;
    private boolean failed;
    public CollisionSnapshot(WorldServer level,EntityNPCInterface owner,Ballistics.Request request,List<Ballistics.Candidate> candidates){
        this.level=level;this.owner=owner;this.request=request;this.candidates=bincnpc.gun.common.legacy.Legacy.copyList(candidates);
        Set<Ballistics.Cell> sampled=new LinkedHashSet<>();
        for(Ballistics.Candidate candidate:candidates)for(int i=1;i<candidate.points().size();i++)
            for(Ballistics.Cell cell:Ballistics.cells(candidate.points().get(i-1),candidate.points().get(i),request.physics().radius(),MAX_CELLS)){
                sampled.add(cell);sampled.add(new Ballistics.Cell(cell.x(),cell.y()-1,cell.z()));
                if(sampled.size()>MAX_CELLS)throw new IllegalArgumentException("terrain budget");
            }
        covered=sampled;cells=bincnpc.gun.common.legacy.Legacy.copyList(sampled);
    }
    public boolean covers(Ballistics.Candidate candidate){
        for(int i=1;i<candidate.points().size();i++)
            for(Ballistics.Cell cell:Ballistics.cells(candidate.points().get(i-1),candidate.points().get(i),request.physics().radius(),MAX_CELLS))
                if(!covered.contains(cell))return false;
        return true;
    }
    public static void beginTick(){remaining=SERVER_BUDGET;}
    private static boolean budget(){if(remaining<=0)return false;remaining--;return true;}
    private static AxisAlignedBB unit(BlockPos pos){return new AxisAlignedBB(pos);}
    private static List<AxisAlignedBB> shape(WorldServer level,BlockPos pos,Entity entity,AxisAlignedBB area){
        List<AxisAlignedBB> result=new ArrayList<>();
        level.getBlockState(pos).addCollisionBoxToList(level,pos,area,result,entity,false);
        return result;
    }
    public boolean capture(){
        requireThread(level);
        for(int count=0;!failed&&cursor<cells.size()&&count<PER_TICK&&budget();count++,cursor++){
            Ballistics.Cell cell=cells.get(cursor);BlockPos pos=new BlockPos(cell.x(),cell.y(),cell.z());
            if(!loadedShape(level,pos)||level.getBlockState(pos).getMaterial().isLiquid())walls.add(bounds(unit(pos)));
            else for(AxisAlignedBB box:shape(level,pos,owner,unit(pos).grow(1))){
                walls.add(bounds(box));if(walls.size()>Ballistics.MAX_BOXES){failed=true;break;}
            }
            if(walls.size()>Ballistics.MAX_BOXES)failed=true;
        }
        return failed||cursor>=cells.size();
    }
    public Ballistics.World result(){
        if(failed||cursor<cells.size())return null;
        List<Ballistics.Bounds> danger=danger(level,owner,request.target(),request.physics().blast());
        return danger==null?null:new Ballistics.World(request,candidates,walls,danger);
    }
    public static Ballistics.Vec vector(Vec3d v){return new Ballistics.Vec(v.x,v.y,v.z);}
    public static Vec3d vector(Ballistics.Vec v){return new Vec3d(v.x(),v.y(),v.z());}
    public static Vec3d center(AxisAlignedBB box){return new Vec3d((box.minX+box.maxX)*0.5,(box.minY+box.maxY)*0.5,(box.minZ+box.maxZ)*0.5);}
    public static Ballistics.Bounds bounds(AxisAlignedBB b){return new Ballistics.Bounds(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
    public static boolean loaded(WorldServer level,BlockPos pos){return pos.getY()>=0&&pos.getY()<level.getHeight()&&level.isBlockLoaded(pos,false);}
    public static boolean loadedShape(WorldServer level,BlockPos pos){
        if(!loaded(level,pos))return false;
        for(int x:new int[]{-1,1})for(int z:new int[]{-1,1})if(!level.isBlockLoaded(pos.add(x,0,z),false))return false;
        return true;
    }
    public static boolean volumeLoaded(WorldServer level,AxisAlignedBB box){
        if(box.minY<0||box.maxY>level.getHeight())return false;
        int x0=(int)Math.floor((box.minX-1)/16),x1=(int)Math.floor((box.maxX+1)/16);
        int z0=(int)Math.floor((box.minZ-1)/16),z1=(int)Math.floor((box.maxZ+1)/16);
        if((long)(x1-x0+1)*(z1-z0+1)>64)return false;
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)
            if(!level.isBlockLoaded(new BlockPos(x*16,(int)Math.floor(box.minY),z*16),false))return false;
        return true;
    }
    private static void requireThread(WorldServer level){
        if(level.getMinecraftServer()==null||!level.getMinecraftServer().isCallingFromMinecraftThread())throw new IllegalStateException("main-thread terrain capture");
    }
    private static List<Ballistics.Bounds> danger(WorldServer level,EntityNPCInterface owner,Ballistics.Vec goal,double blast){
        requireThread(level);if(blast<=0)return Collections.emptyList();
        List<Ballistics.Bounds> result=new ArrayList<>();result.add(bounds(owner.getEntityBoundingBox().grow(blast+0.35)));
        AxisAlignedBB area=new AxisAlignedBB(goal.x()-blast-2,goal.y()-blast-2,goal.z()-blast-2,goal.x()+blast+2,goal.y()+blast+2,goal.z()+blast+2);
        for(EntityLivingBase nearby:level.getEntitiesWithinAABB(EntityLivingBase.class,area,entity->entity!=null&&entity!=owner&&entity.isEntityAlive()
            &&!bincnpc.gun.common.legacy.Platform.spectator(entity)&&!CombatService.hostile(owner,entity))){
            if(result.size()>=65)return null;result.add(bounds(nearby.getEntityBoundingBox().grow(blast+0.35)));
        }
        return bincnpc.gun.common.legacy.Legacy.copyList(result);
    }
    public static boolean safeArea(WorldServer level,EntityNPCInterface owner,Ballistics.Vec goal,double blast){
        List<Ballistics.Bounds> danger=danger(level,owner,goal,blast);
        return danger!=null&&danger.stream().noneMatch(box->box.contains(goal));
    }
    public static boolean clear(WorldServer level,Entity entity,Vec3d start,Vec3d end){
        requireThread(level);if(start.squareDistanceTo(end)>128*128)return false;
        try{for(Ballistics.Cell cell:Ballistics.cells(vector(start),vector(end),0,1024))
            if(!loadedShape(level,new BlockPos(cell.x(),cell.y(),cell.z())))return false;}
        catch(IllegalArgumentException tooLarge){return false;}
        RayTraceResult hit=level.rayTraceBlocks(start,end,false,true,false);
        return hit==null||hit.typeOfHit==RayTraceResult.Type.MISS;
    }
    public static boolean stepClear(WorldServer level,Entity projectile,Vec3d from,Vec3d to){
        if(!clear(level,projectile,from,to))return false;
        double radius=Math.min(2,projectile.width*0.5);
        try{for(Ballistics.Cell cell:Ballistics.cells(vector(from),vector(to),radius,512))
            if(!loaded(level,new BlockPos(cell.x(),cell.y(),cell.z())))return false;}
        catch(IllegalArgumentException tooLarge){return false;}
        AxisAlignedBB body=projectile.getEntityBoundingBox().offset(to.subtract(from));
        return volumeLoaded(level,body)&&level.getCollisionBoxes(projectile,body).isEmpty();
    }
    public static final class RouteCapture{
        private final WorldServer level;
        private final Entity projectile;
        private final Ballistics.Vec min,start,target;
        private final boolean[] blocked=new boolean[Ballistics.GRID_SIDE*Ballistics.GRID_SIDE*Ballistics.GRID_SIDE];
        private int cursor;
        public RouteCapture(WorldServer level,Entity projectile,Vec3d target){
            this.level=level;this.projectile=projectile;this.start=vector(projectile.getPositionVector());this.target=vector(target);
            min=new Ballistics.Vec(Math.floor(start.x())-4,Math.floor(start.y())-4,Math.floor(start.z())-4);
        }
        public Ballistics.Vec start(){return start;}public Ballistics.Vec target(){return target;}
        public boolean capture(){
            requireThread(level);int side=Ballistics.GRID_SIDE;
            for(int count=0;cursor<blocked.length&&count<PER_TICK/2&&remaining>=2;count++,cursor++){
                remaining-=2;
                int x=(int)min.x()+cursor%side,y=(int)min.y()+cursor/(side*side),z=(int)min.z()+cursor/side%side;
                BlockPos pos=new BlockPos(x,y,z);double r=Math.max(0.08,Math.min(0.49,projectile.width*0.5));
                AxisAlignedBB body=new AxisAlignedBB(x+0.5-r,y+0.5-r,z+0.5-r,x+0.5+r,y+0.5+r,z+0.5+r);
                if(!loadedShape(level,pos)||!loadedShape(level,pos.down())){blocked[cursor]=true;continue;}
                for(BlockPos sample:new BlockPos[]{pos,pos.down()}){
                    IBlockState state=level.getBlockState(sample);
                    if(state.getMaterial().isLiquid()){blocked[cursor]=true;break;}
                    for(AxisAlignedBB box:shape(level,sample,projectile,body))if(box.intersects(body)){blocked[cursor]=true;break;}
                }
            }
            return cursor>=blocked.length;
        }
        public Ballistics.Grid result(){return cursor>=blocked.length?new Ballistics.Grid(min,start,target,blocked):null;}
    }
}
