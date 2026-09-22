package dev.hanks.vanilla;

import com.mojang.math.Transformation;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.sounds.*;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Short, camera-readable contact accents. Packet-only shapes have no hitboxes. */
public final class ImpactFeedback {
    private static final int SPOKES=8, MAX_BURSTS=12;
    private record Burst(double x,double y,double radius,double angle,int born,int duration,
                         List<Display.BlockDisplay> pieces,Set<UUID> audience) {}
    private final Battle battle;
    private final Deque<Burst> bursts=new ArrayDeque<>();
    ImpactFeedback(Battle battle) { this.battle=battle; }
    static int pauseTicks(FighterMoves.Move move,boolean strong) { return strong?3:move.damage()>=12?2:1; }
    public void hit(Battle.Actor attacker,Battle.Actor target,FighterMoves.Move move,CombatGeometry.Point point) {
        boolean strong=target.state.strongLaunch, heavy=move.damage()>=12;
        burst(point.x(),point.y(),strong?1.05:heavy?.72:.46,Math.atan2(target.vy,target.vx),strong?6:heavy?5:4,attacker.kind);
        battle.level.sendParticles(ParticleTypes.CRIT,true,false,point.x(),point.y(),1.15,
                strong?12:heavy?7:4,.12,.16,.02,strong?.16:.07);
        sound(move.kind()==AttackKind.LIGHT?SoundEvents.PLAYER_ATTACK_STRONG:SoundEvents.PLAYER_ATTACK_CRIT,
                point.x(),heavy?.7f:.5f,strong?.68f:heavy?.85f:1.2f);
        var flavor=switch(attacker.kind) {
            case STEVE->SoundEvents.ANVIL_HIT;
            case ALEX->SoundEvents.PLAYER_ATTACK_SWEEP;
            case ZOMBIE->SoundEvents.ROOTED_DIRT_BREAK;
            case SKELETON->SoundEvents.ARROW_HIT;
            case VILLAGER->SoundEvents.BELL_BLOCK;
        };
        float pitch=switch(attacker.kind) { case STEVE->1.65f;case ALEX->1.8f;case ZOMBIE->.65f;case SKELETON->1.5f;case VILLAGER->1.9f; };
        sound(flavor,point.x(),heavy?.34f:.22f,pitch);
        if(strong) { sound(SoundEvents.PLAYER_ATTACK_KNOCKBACK,point.x(),.85f,.6f);sound(SoundEvents.FIREWORK_ROCKET_LAUNCH,point.x(),.4f,.8f); }
    }
    public void block(Battle.Actor target,CombatGeometry.Point point,boolean broken) {
        if(broken) { guardBreak(target);return; }
        burst(point.x(),point.y(),.4,Math.PI/8,4,FighterClass.SKELETON);
        battle.level.sendParticles(ParticleTypes.ELECTRIC_SPARK,true,false,point.x(),point.y(),1.1,7,.15,.2,.02,.07);
        sound(SoundEvents.SHIELD_BLOCK.value(),point.x(),.65f,1.2f);
    }
    public void object(Battle.Actor f,Vec3 point) {
        burst(point.x,point.y,.32,0,4,f.kind);
        sound(SoundEvents.BAMBOO_WOOD_HIT,point.x,.3f,1.5f);
    }
    public void guardBreak(Battle.Actor f) {
        burst(f.pose.x,f.pose.y+1,1.25,Math.PI/8,7,FighterClass.SKELETON);
        battle.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,Blocks.GLASS.defaultBlockState()),
                true,false,f.pose.x,f.pose.y+1,1.05,22,.3,.5,.04,.18);
        sound(SoundEvents.SHIELD_BREAK.value(),f.pose.x,.85f,.8f);
        sound(SoundEvents.GLASS_BREAK,f.pose.x,.5f,1.15f);
        battle.hud.announce("P"+f.slot+"  SHIELD BREAK",0x9cddff,22);
    }
    public void movement(Battle.Actor f,boolean airborne) {
        // The second jump has a small, flattened ring to show its air resource.
        if(airborne) for(int i=0;i<8;i++) {
            double a=Math.PI*2*i/8;
            battle.level.sendParticles(ParticleTypes.CLOUD,true,false,f.pose.x+Math.cos(a)*.55,f.pose.y+Math.sin(a)*.12,.9,1,0,0,0,0);
        }
        else battle.level.sendParticles(ParticleTypes.CLOUD,true,false,f.pose.x,f.pose.y+.08,.8,4,.25,.03,.06,.015);
        sound(SoundEvents.PLAYER_ATTACK_SWEEP,f.pose.x,airborne?.24f:.13f,airborne?1.9f:1.5f);
    }
    public void landing(Battle.Actor f,double speed) {
        if(speed<.45)return;
        battle.level.sendParticles(ParticleTypes.CLOUD,true,false,f.x,f.y+.1,.85,speed>1?8:4,.4,.04,.05,.025);
        sound(SoundEvents.ROOTED_DIRT_HIT,f.x,speed>1?.3f:.14f,speed>1?.7f:1.2f);
    }
    public void trail(Battle.Actor f,double fromX,double fromY) {
        if(!f.state.strongLaunch || battle.now()>=f.state.launchUntil || f.grounded || f.state.paused(battle.now()))return;
        for(int i=0;i<3;i++) {
            double a=i/3.0,x=fromX+(f.x-fromX)*a,y=fromY+(f.y-fromY)*a+1;
            battle.level.sendParticles(new DustParticleOptions(f.color(),1.15f),true,false,x,y,.85,1,.06,.06,.01,0);
            if(i==0)battle.level.sendParticles(ParticleTypes.CLOUD,true,false,x,y,.8,2,.13,.13,.02,.015);
        }
        if(battle.now()%2==0)battle.level.sendParticles(ParticleTypes.FIREWORK,true,false,f.x,f.y+1,1,2,.08,.12,.02,.03);
    }
    private void sound(SoundEvent sound,double x,float volume,float pitch) {
        for(var view:battle.game.viewers.values()) {
            var eye=view.camera();double pan=Math.clamp((x-eye.getX())*.18,-4,4);
            view.player().connection.send(new ClientboundSoundPacket(Holder.direct(sound),SoundSource.PLAYERS,
                    eye.getX()+pan,eye.getY(),eye.getZ(),volume,pitch,battle.level.getRandom().nextLong()));
        }
    }
    private void burst(double x,double y,double radius,double angle,int duration,FighterClass kind) {
        while(bursts.size()>=MAX_BURSTS)discard(bursts.removeFirst());
        var pieces=new ArrayList<Display.BlockDisplay>();
        var burst=new Burst(x,y,radius,angle,battle.now(),duration,pieces,Set.copyOf(battle.game.viewers.keySet()));
        var packets=new ArrayList<Packet<? super ClientGamePacketListener>>();
        for(int i=0;i<SPOKES;i++) {
            var d=new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY,battle.level);pieces.add(d);
            d.setBlockState((i%2==0?Blocks.CONCRETE.white():switch(kind) {
                case STEVE->Blocks.CONCRETE.lightBlue();case ALEX->Blocks.CONCRETE.orange();case ZOMBIE->Blocks.CONCRETE.lime();
                case SKELETON->Blocks.CONCRETE.cyan();case VILLAGER->Blocks.CONCRETE.yellow();
            }).defaultBlockState());
            d.setBrightnessOverride(new Brightness(15,15));d.setViewRange(3);d.setWidth(4);d.setHeight(4);d.setPosRotInterpolationDuration(1);
            position(burst,i,0);
            packets.add(new ClientboundAddEntityPacket(d.getId(),d.getUUID(),d.getX(),d.getY(),d.getZ(),0,0,EntityTypes.BLOCK_DISPLAY,0,Vec3.ZERO,0));
            packets.add(new ClientboundSetEntityDataPacket(d.getId(),d.getEntityData().getNonDefaultValues()));d.getEntityData().packDirty();
        }
        send(burst,new ClientboundBundlePacket(packets));bursts.addLast(burst);
    }
    private void position(Burst b,int index,int age) {
        double t=age/(double)b.duration(),angle=b.angle()+index*Math.PI/4;
        double inner=b.radius()*(.06+t*.75),length=b.radius()*(index%2==0?.82:.5)*(1-t*.7),width=b.radius()*.14*(1-t);
        var d=b.pieces().get(index);
        d.setPos(b.x()+Math.cos(angle)*inner+Math.sin(angle)*width/2,b.y()+Math.sin(angle)*inner-Math.cos(angle)*width/2,1.12);
        d.setTransformation(new Transformation(null,new Quaternionf().rotationZ((float)angle),new Vector3f((float)length,(float)width,.018f),null));
    }
    public void tick() {
        for(var it=bursts.iterator();it.hasNext();) {
            var b=it.next();int age=battle.now()-b.born();
            if(age>=b.duration()) {discard(b);it.remove();continue;}
            if(age==0)continue;
            var packets=new ArrayList<Packet<? super ClientGamePacketListener>>();
            for(int i=0;i<b.pieces().size();i++) {
                position(b,i,age);var d=b.pieces().get(i);packets.add(ClientboundEntityPositionSyncPacket.of(d));
                var data=d.getEntityData().packDirty();if(data!=null)packets.add(new ClientboundSetEntityDataPacket(d.getId(),data));
            }
            send(b,new ClientboundBundlePacket(packets));
        }
    }
    int activePieces() { return bursts.size()*SPOKES; }
    private void send(Burst b,Packet<? super ClientGamePacketListener> packet) {
        for(var id:b.audience()) {var view=battle.game.viewers.get(id);if(view!=null)view.player().connection.send(packet);}
    }
    private void discard(Burst b) {send(b,new ClientboundRemoveEntitiesPacket(b.pieces().stream().mapToInt(Entity::getId).toArray()));}
    public void close() {bursts.forEach(this::discard);bursts.clear();}
}
