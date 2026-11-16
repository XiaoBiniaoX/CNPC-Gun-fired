package bincnpc.gun.client.animation;

import bincnpc.gun.common.api.SafeJson;
import com.google.gson.JsonObject;
import java.util.*;
import mods.flammpfeil.slashblade.entity.EntitySummonedSwordBase;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.MathHelper;
import noppes.npcs.entity.EntityNPCInterface;

/** Detached zero-damage native visuals, rendered directly without spawning or ticking. */
public final class BladePreviewEffects {
    private static final Map<EntityNPCInterface,Visual> VISUALS=new WeakHashMap<>();
    private static final class Visual{
        final String kind;final int count,duration,delay;
        final EntitySummonedSwordBase sword;final EntityDrive slash;int age;
        Visual(EntityNPCInterface npc,JsonObject info,JsonObject params,int duration){
            kind=SafeJson.string(info,"backend","");this.duration=MathHelper.clamp(duration,1,2400);delay=MathHelper.clamp(SafeJson.integer(params,"delay",0),0,100);
            count=MathHelper.clamp(SafeJson.integer(params,"count",kind.contains("rain")?18:kind.contains("basic")?1:8),1,32);
            sword=new EntitySummonedSwordBase(npc.world,npc,0);sword.setEntityId(-2);sword.noClip=true;
            sword.setColor(ItemSlashBlade.SummonedSwordColor.get(ItemSlashBlade.getItemTagCompound(npc.getHeldItemMainhand())));
            slash=new EntityDrive(npc.world,npc,0);slash.setEntityId(-3);slash.noClip=true;slash.setLifeTime(12);
        }
    }
    private BladePreviewEffects(){}
    public static void start(EntityNPCInterface npc,JsonObject info,JsonObject parameters,int duration){
        if(npc.getEntityId()>=0||!npc.world.isRemote)return;
        String type=SafeJson.string(info,"type","");
        if(type.equals("SPECIAL_ATTACK")||type.equals("PHANTOM_SWORD"))VISUALS.put(npc,new Visual(npc,info,parameters,duration));else clear(npc);
    }
    public static void clear(EntityNPCInterface npc){VISUALS.remove(npc);}
    public static void tick(EntityNPCInterface npc){Visual visual=VISUALS.get(npc);if(visual!=null&&++visual.age>visual.duration+visual.delay)clear(npc);}
    @SuppressWarnings({"rawtypes","unchecked"})
    public static void render(EntityNPCInterface npc,double x,double y,double z,float partial){
        Visual v=VISUALS.get(npc);if(v==null||v.age<v.delay)return;
        float elapsed=v.age-v.delay+partial,progress=MathHelper.clamp(elapsed/Math.max(1,v.duration-v.delay),0,1);
        GlStateManager.pushMatrix();
        try{
            if(v.kind.startsWith("phantom:")){
                Render renderer=Minecraft.getMinecraft().getRenderManager().getEntityRenderObject(v.sword);
                for(int i=0;i<v.count;i++){
                    double angle=i*Math.PI*2/v.count+elapsed*0.16;
                    double dx=Math.cos(angle)*0.85,dy=1.1+Math.sin(angle)*0.25,dz=Math.sin(angle)*0.7;
                    float yaw=(float)Math.toDegrees(angle),pitch=0;
                    if(v.kind.contains("rain")){dx=(i%6-2.5)*0.3;dy=2.6-progress*2+i/6*0.15;dz=-0.4;yaw=0;pitch=-90;}
                    else if(v.kind.contains("blistering")){dx=(i%2==0?1:-1)*(0.7-i/2*0.05);dy=1.3+i/2*0.12;dz=-progress*1.5;yaw=0;}
                    else if(v.kind.contains("basic")||v.kind.contains("spiral_fire")){dx=(i%2==0?1:-1)*0.5;dy=1.3;dz=-progress*2;yaw=0;}
                    v.sword.rotationYaw=v.sword.prevRotationYaw=yaw;v.sword.rotationPitch=v.sword.prevRotationPitch=pitch;v.sword.setRoll(i*360F/v.count);
                    renderer.doRender(v.sword,x+dx,y+dy,z+dz,yaw,partial);
                }
            }else{
                Render renderer=Minecraft.getMinecraft().getRenderManager().getEntityRenderObject(v.slash);
                int cuts=v.kind.contains("super")||v.kind.contains("judgement")?3:1;v.slash.ticksExisted=2+(int)elapsed%9;
                for(int i=0;i<cuts;i++){v.slash.setRoll(i*60F-30);renderer.doRender(v.slash,x,y+1.05+i*0.08,z-0.35,0,partial);}
            }
        }finally{GlStateManager.color(1,1,1,1);GlStateManager.popMatrix();}
    }
}
