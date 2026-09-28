package dev.hanks.vanilla;

import java.util.*;
import com.mojang.math.Transformation;
import net.minecraft.core.particles.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.joml.Quaternionf;

/** Small native display sculptures: paired prey eyes, opening rifts, and collapsing silhouettes. */
final class EnderVisuals {
    private record Piece(Display.BlockDisplay entity,float x,float y,float w,float h,float angle) {}
    private record Effect(UUID owner,EnderState.Point origin,int born,int life,boolean ghost,List<Piece> pieces) {}
    private record Swing(Battle.Actor actor,long started,int born,Display.BlockDisplay arm) {}
    private final List<Swing> swings=new ArrayList<>();
    private record Mark(List<Display.BlockDisplay> eyes,Display.TextDisplay label) {}
    private final Battle battle;
    private final List<Effect> effects=new ArrayList<>();
    private final Map<UUID,Mark> marks=new HashMap<>();
    private final Map<Integer,Display> entities=new LinkedHashMap<>();
    private final Map<UUID,Set<Integer>> delivered=new HashMap<>();
    private static final BlockState VIOLET=Blocks.CONCRETE.purple().defaultBlockState();
    private static final BlockState LILAC=Blocks.CONCRETE.magenta().defaultBlockState();
    private static final BlockState WHITE=Blocks.CONCRETE.white().defaultBlockState();
    EnderVisuals(Battle battle){this.battle=battle;}
    private Display.BlockDisplay block(BlockState state) {
        var d=new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY,battle.level);
        d.setBlockState(state);d.setNoGravity(true);d.setInvulnerable(true);
        d.setBrightnessOverride(new Brightness(15,15));d.setViewRange(3);d.setWidth(5);d.setHeight(5);
        d.setTransformationInterpolationDuration(1);d.setPosRotInterpolationDuration(1);
        d.addTag(VanillaSmash.TEMP);return d;
    }
    private void transform(Display.BlockDisplay d,double x,double y,double z,float w,float h,float angle) {
        d.setPos(x,y,z);d.setTransformationInterpolationDelay(0);
        d.setTransformation(new Transformation(new Vector3f(),new Quaternionf().rotationZ(angle),new Vector3f(w,h,.055f),new Quaternionf()));
    }
    private Piece piece(BlockState state,float x,float y,float w,float h,float angle) {
        return new Piece(block(state),x,y,w,h,angle);
    }
    private void add(Effect effect) {
        position(effect,0);effect.pieces.forEach(p->entities.put(p.entity.getId(),p.entity));effects.add(effect);flush();
    }
    void rift(UUID owner,EnderState.Point p,int life) {
        var parts=new ArrayList<Piece>();
        for(int i=0;i<16;i++) {
            double a=i*Math.PI/8,b=(i+1)*Math.PI/8;
            float x=(float)(Math.cos(a)*.64),y=(float)(1.4+Math.sin(a)*1.5);
            double dx=(Math.cos(b)-Math.cos(a))*.64,dy=(Math.sin(b)-Math.sin(a))*1.5;
            float angle=(float)Math.atan2(dy,dx),length=(float)Math.hypot(dx,dy)+.02f;
            parts.add(piece(VIOLET,x,y,length,.16f,angle));
            parts.add(piece(i%4==0?WHITE:LILAC,x,y,length,.065f,angle));
        }
        add(new Effect(owner,p,battle.now(),life,false,parts));
    }
    void afterimage(Battle.Actor f) {
        var glass=Blocks.STAINED_GLASS.purple().defaultBlockState();
        var parts=new ArrayList<Piece>();
        parts.add(piece(glass,-.25f,2.4f,.5f,.5f,0));
        parts.add(piece(glass,-.20f,1.55f,.4f,.8f,0));
        parts.add(piece(glass,-.19f,.05f,.11f,1.5f,0));parts.add(piece(glass,.09f,.05f,.11f,1.5f,0));
        parts.add(piece(glass,-.38f,.7f,.10f,1.6f,-.06f));parts.add(piece(glass,.29f,.7f,.10f,1.6f,.06f));
        parts.add(piece(LILAC,-.22f,2.62f,.18f,.07f,0));parts.add(piece(LILAC,.04f,2.62f,.18f,.07f,0));
        add(new Effect(f.id,new EnderState.Point(f.x,f.y),battle.now(),9,true,parts));
    }
    void arrive(Battle.Actor f) {
        battle.level.sendParticles(ParticleTypes.REVERSE_PORTAL,true,false,f.x,f.y+1.4,1,14,.25,.8,.12,.12);
        rift(f.id,new EnderState.Point(f.x,f.y),6);
    }
    void armSwing(Battle.Actor f) {
        var arm=block(Blocks.CONCRETE.black().defaultBlockState());
        var swing=new Swing(f,f.state.startedAt,battle.now(),arm);
        positionArm(swing,0);entities.put(arm.getId(),arm);swings.add(swing);flush();
    }
    private void positionArm(Swing swing,int age) {
        var f=swing.actor;
        double angle=-1.15+Math.min(1,age/4.0)*1.65;
        float rotation=(float)(f.state.attackDirection>0?angle:Math.PI-angle);
        transform(swing.arm,f.x,f.y+2.0,1.12,2.5f,.14f,rotation);
    }
    void mark(Battle.Actor f,Battle.Actor victim) {
        if(!marks.containsKey(f.id)) {
            var eyes=new ArrayList<Display.BlockDisplay>();
            for(int i=0;i<4;i++)eyes.add(block(i<2?LILAC:WHITE));
            var label=new Display.TextDisplay(EntityTypes.TEXT_DISPLAY,battle.level);
            label.setNoGravity(true);label.setBackgroundColor(0);label.setBillboardConstraints(Display.BillboardConstraints.CENTER);
            label.setBrightnessOverride(new Brightness(15,15));label.setViewRange(3);label.setPosRotInterpolationDuration(2);
            label.setFlags((byte)(Display.TextDisplay.FLAG_SHADOW|Display.TextDisplay.FLAG_SEE_THROUGH));label.addTag(VanillaSmash.TEMP);
            label.setTextOpacity((byte)255);
            label.setText(Component.literal("▰  ▰").withColor(0xf2aeff).append(Component.literal("\nP"+f.slot+" PREY").withColor(f.color())));
            label.setTransformation(new Transformation(null,null,new Vector3f(1.1f),null));
            var mark=new Mark(eyes,label);marks.put(f.id,mark);track(f,victim);
            eyes.forEach(d->entities.put(d.getId(),d));entities.put(label.getId(),label);flush();
        }
        battle.level.sendParticles(ParticleTypes.REVERSE_PORTAL,true,false,victim.x,victim.y+victim.body.getBbHeight()+.8,1,6,.35,.12,0,.04);
    }
    void track(Battle.Actor f,Battle.Actor victim) {
        if(victim==null){unmark(f.id);return;}
        var m=marks.get(f.id);if(m==null){mark(f,victim);return;}
        double x=victim.x,y=victim.y+victim.body.getBbHeight()+1.05+(f.slot-1)*.25;
        for(int i=0;i<4;i++) {
            boolean core=i>=2;int side=i%2==0?-1:1;
            transform(m.eyes.get(i),x+side*.22-(core?.045:.14),y,1.2+(core?.02:0),core?.09f:.28f,core?.08f:.11f,0);
        }
        m.label.setPos(x,y+.23,1.2);
    }
    private void position(Effect e,int age) {
        float progress=age/(float)e.life;
        float scale=e.ghost?1-progress*.85f:Math.min(1,.5f+age*.25f)*(1-Math.max(0,progress-.6f)*1.9f);
        for(var p:e.pieces) {
            float x=e.ghost?p.x*scale:p.x,y=e.ghost?p.y:1.4f+(p.y-1.4f)*scale;
            double depth=p.entity.getBlockState().equals(VIOLET)?1.02:1.08;
            transform(p.entity,e.origin.x()+x,e.origin.y()+y,depth,p.w*(e.ghost?scale:1),Math.max(.01f,p.h*scale),p.angle);
        }
    }
    void tick() {
        for(var it=swings.iterator();it.hasNext();) {
            var swing=it.next();var f=swing.actor;int age=battle.now()-swing.born;
            if(age>=6||f.eliminated||f.state.startedAt!=swing.started||battle.now()<f.state.stunUntil||!battle.game.fighting(f)) {
                discard(swing.arm);it.remove();
            } else positionArm(swing,age);
        }
        for(var it=effects.iterator();it.hasNext();) {
            var e=it.next();int age=battle.now()-e.born;
            if(age>=e.life){e.pieces.forEach(p->discard(p.entity));it.remove();}
            else position(e,age);
        }
        flush();
    }
    private void discard(Display d){entities.remove(d.getId());}
    /** Send spawn + full metadata atomically. World tracking is too slow for a three-tick tell. */
    private void flush() {
        var dirty=new HashMap<Integer,List<SynchedEntityData.DataValue<?>>>();
        for(var d:entities.values()) { var data=d.getEntityData().packDirty();if(data!=null)dirty.put(d.getId(),data); }
        delivered.keySet().retainAll(battle.game.viewers.keySet());
        for(var view:battle.game.viewers.entrySet()) {
            var known=delivered.computeIfAbsent(view.getKey(),id->new HashSet<>());
            var packets=new ArrayList<Packet<? super ClientGamePacketListener>>();
            int[] removed=known.stream().filter(id->!entities.containsKey(id)).mapToInt(Integer::intValue).toArray();
            if(removed.length>0){packets.add(new ClientboundRemoveEntitiesPacket(removed));for(int id:removed)known.remove(id);}
            for(var d:entities.values()) {
                if(known.add(d.getId())) {
                    packets.add(new ClientboundAddEntityPacket(d.getId(),d.getUUID(),d.getX(),d.getY(),d.getZ(),0,0,d.getType(),0,Vec3.ZERO,0));
                    packets.add(new ClientboundSetEntityDataPacket(d.getId(),d.getEntityData().getNonDefaultValues()));
                } else {
                    packets.add(ClientboundEntityPositionSyncPacket.of(d));
                    if(dirty.containsKey(d.getId()))packets.add(new ClientboundSetEntityDataPacket(d.getId(),dirty.get(d.getId())));
                }
            }
            if(!packets.isEmpty())view.getValue().player().connection.send(new ClientboundBundlePacket(packets));
        }
    }
    void unmark(UUID owner){var m=marks.remove(owner);if(m!=null){m.eyes.forEach(this::discard);discard(m.label);}}
    void remove(UUID owner){unmark(owner);swings.removeIf(s->{if(!s.actor.id.equals(owner))return false;discard(s.arm);return true;});effects.removeIf(e->{if(!e.owner.equals(owner))return false;e.pieces.forEach(p->discard(p.entity));return true;});}
    void clear(){swings.forEach(s->discard(s.arm));swings.clear();new ArrayList<>(marks.keySet()).forEach(this::unmark);effects.forEach(e->e.pieces.forEach(p->discard(p.entity)));effects.clear();flush();delivered.clear();}
    int markerCount(){return marks.size();}
    int[] entityIds(){return entities.keySet().stream().mapToInt(Integer::intValue).toArray();}
    int pieces(){return swings.size()+effects.stream().mapToInt(e->e.pieces.size()).sum()+marks.size()*5;}
}
