package bincnpc.gun.client.animation;

import bincnpc.gun.common.network.GunNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import noppes.npcs.entity.EntityNPCInterface;
import java.util.*;

/** Native 1.12 model presentation; there is no animation library dependency. */
public final class NpcAnimations implements BipedAnimationHooks.Handler {
    public static final NpcAnimations INSTANCE=new NpcAnimations();
    private static final Map<EntityNPCInterface,State> ANIMATIONS=new WeakHashMap<>();
    private static final Map<ModelBiped,List<Pose>> SAVED=new WeakHashMap<>();
    private NpcAnimations(){}
    @Override public void before(Object model){
        if(model instanceof ModelBiped&&!model.getClass().getName().startsWith("noppes.npcs."))restore((ModelBiped)model);
    }
    @Override public void after(Object model,Object entity,float partialTick){
        if(model instanceof ModelBiped&&entity instanceof EntityNPCInterface&&!model.getClass().getName().startsWith("noppes.npcs."))
            apply((EntityNPCInterface)entity,(ModelBiped)model,partialTick);
    }
    private static final class State{
        String domain="",pose="",action="";
        int age=1000,duration=1,summaryAge=1000,throwAge=1000,throwDuration=1,phantomAge=1000,guardAge=1000;
        boolean aiming,throwRelease;
        long bladeStarted;
        boolean nativeBlade;
        void start(String domain,String pose,String action,int duration,boolean aiming){
            if(!this.domain.equals(domain)){phantomAge=guardAge=1000;}
            this.domain=domain;this.aiming=aiming;summaryAge=0;
            if(pose.equals("STATE"))return;
            if(pose.equals("THROW_END")){throwAge=1000;return;}
            if(pose.equals("THROW")||pose.equals("THROW_RELEASE")){throwAge=0;throwDuration=MathHelper.clamp(duration,1,220);throwRelease=pose.equals("THROW_RELEASE");return;}
            if(pose.equals("PHANTOM_SWORD")){phantomAge=0;return;}
            if(pose.equals("DEFENSE")){guardAge=0;return;}
            this.pose=pose;this.action=action;age=0;this.duration=MathHelper.clamp(duration,1,2400);
        }
        void tick(){age++;summaryAge++;throwAge++;phantomAge++;guardAge++;}
    }
    private static final class Pose{
        final ModelRenderer part;final float x,y,z,rx,ry,rz;
        Pose(ModelRenderer part){this.part=part;x=part.rotationPointX;y=part.rotationPointY;z=part.rotationPointZ;rx=part.rotateAngleX;ry=part.rotateAngleY;rz=part.rotateAngleZ;}
        void restore(){part.rotationPointX=x;part.rotationPointY=y;part.rotationPointZ=z;part.rotateAngleX=rx;part.rotateAngleY=ry;part.rotateAngleZ=rz;}
    }
    public static void preview(EntityNPCInterface npc,String domain,String pose,String action,int duration,boolean aiming){
        State state=ANIMATIONS.computeIfAbsent(npc,key->new State());
        state.start(domain,pose,action,duration,aiming);
        if(domain.equals("SLASHBLADE")&&action.startsWith("combo:")&&!pose.equals("STATE")&&!pose.equals("PHANTOM_SWORD")&&!pose.equals("DEFENSE")) {
            state.bladeStarted=npc.world.getTotalWorldTime();state.nativeBlade=true;
            nativeBlade(npc,state);
        }
    }
    public static void clearPreview(EntityNPCInterface npc){ANIMATIONS.remove(npc);}
    public static void receive(GunNetwork.Animation packet){
        Minecraft mc=Minecraft.getMinecraft();if(mc.world==null)return;
        Entity entity=mc.world.getEntityByID(packet.entityId());
        if(!(entity instanceof EntityNPCInterface)||!entity.getUniqueID().equals(packet.npcId()))return;
        EntityNPCInterface npc=(EntityNPCInterface)entity;
        preview(npc,packet.domain(),packet.pose(),packet.actionId(),packet.duration(),packet.aiming());
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        if(Minecraft.getMinecraft().world==null){
            ANIMATIONS.clear();for(List<Pose> poses:SAVED.values())for(Pose pose:poses)pose.restore();SAVED.clear();return;
        }
        Iterator<Map.Entry<EntityNPCInterface,State>> iterator=ANIMATIONS.entrySet().iterator();
        while(iterator.hasNext()){
            Map.Entry<EntityNPCInterface,State> item=iterator.next();EntityNPCInterface npc=item.getKey();
            if(npc.isDead||npc.world!=Minecraft.getMinecraft().world)iterator.remove();else { item.getValue().tick();nativeBlade(npc,item.getValue()); }
        }
    }
    private static void nativeBlade(EntityNPCInterface npc,State state){
        if(!state.nativeBlade||!Loader.isModLoaded("flammpfeil.slashblade"))return;
        try {
            if(state.age<=state.duration+2&&state.domain.equals("SLASHBLADE"))
                bincnpc.gun.client.gui.editor.BladePreview.animate(npc,state.action,state.bladeStarted,state.age,state.duration,state.pose.equals("SPECIAL_ATTACK"));
            else {
                bincnpc.gun.client.gui.editor.BladePreview.clear(npc.getHeldItemMainhand(),npc.world.getTotalWorldTime());
                state.nativeBlade=false;
            }
        }catch(RuntimeException|LinkageError ignored){state.nativeBlade=false;}
    }
    private static float lerp(float weight,float from,float to){return from+(to-from)*MathHelper.clamp(weight,0,1);}
    private static void rotate(ModelRenderer part,float weight,float x,float y,float z){
        part.rotateAngleX=lerp(weight,part.rotateAngleX,x);part.rotateAngleY=lerp(weight,part.rotateAngleY,y);part.rotateAngleZ=lerp(weight,part.rotateAngleZ,z);
    }
    public static void apply(EntityNPCInterface npc,ModelBiped model,float partialTick){
        State s=ANIMATIONS.get(npc);if(s==null)return;
        restore(model);float elapsed=s.age+partialTick,phase=MathHelper.clamp(elapsed/s.duration,0,1),fade=MathHelper.clamp((s.duration+4-elapsed)/4,0,1);
        float pitch=npc.rotationPitch*(float)Math.PI/180,pulse=(float)Math.sin(phase*Math.PI);int direction=(s.action.hashCode()&1)==0?1:-1;
        List<Pose> poses=new ArrayList<>();
        for(ModelRenderer part:Arrays.asList(model.bipedHead,model.bipedHeadwear,model.bipedBody,model.bipedRightArm,model.bipedLeftArm,model.bipedRightLeg,model.bipedLeftLeg))poses.add(new Pose(part));
        SAVED.put(model,poses);
        if(s.domain.equals("GUN")&&s.summaryAge<45){
            float weight=MathHelper.clamp((45-s.summaryAge)/10F,0,1);
            rotate(model.bipedRightArm,weight,(s.aiming?-1.55F:-0.85F)+pitch-(s.pose.equals("FIRE")?pulse*fade*0.15F:0),-0.12F,0);
            rotate(model.bipedLeftArm,weight,(s.aiming?-1.45F:-0.55F)+pitch,0.55F,-0.12F);
            if(s.pose.equals("RELOAD")&&fade>0){rotate(model.bipedRightArm,fade,-0.7F-0.25F*pulse,-0.3F,0.2F);rotate(model.bipedLeftArm,fade,-0.8F-0.3F*pulse,0.6F,-0.25F);}
        }else if(s.domain.equals("SLASHBLADE")&&fade>0){
            rotate(model.bipedRightArm,fade,-1.5F+phase*2.5F,direction*(0.4F-pulse),direction*(0.2F+pulse*0.5F));
            rotate(model.bipedBody,fade,0.08F*pulse,direction*0.3F*pulse,0);
        }
        if(s.phantomAge<12)rotate(model.bipedLeftArm,MathHelper.clamp((12-s.phantomAge)/4F,0,1),-1.15F,0.3F,-0.3F);
        if(s.guardAge<16)rotate(model.bipedRightArm,MathHelper.clamp((16-s.guardAge)/4F,0,1),-1.2F,-0.6F,0.6F);
        if(s.throwAge<s.throwDuration+4){
            float time=MathHelper.clamp((s.throwAge+partialTick)/s.throwDuration,0,1),weight=MathHelper.clamp((s.throwDuration+4-s.throwAge-partialTick)/4,0,1);
            rotate(model.bipedLeftArm,weight,s.throwRelease?-1.65F+time*1.25F:-1.25F-time*1.15F,0.25F,-0.18F);
        }
        ModelBiped.copyModelAngles(model.bipedHead,model.bipedHeadwear);
    }
    public static void restore(ModelBase model){if(model instanceof ModelBiped){List<Pose> saved=SAVED.remove(model);if(saved!=null)for(Pose pose:saved)pose.restore();}}
    @SubscribeEvent public void after(RenderLivingEvent.Post<?> event){restore(event.getRenderer().getMainModel());}
}
