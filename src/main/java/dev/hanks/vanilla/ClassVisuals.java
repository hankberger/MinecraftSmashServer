package dev.hanks.vanilla;

import java.util.*;
import com.mojang.math.Transformation;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Short, camera-facing native sculptures. These never enter the world or resolve combat. */
final class ClassVisuals {
    private enum Mode { STRIKE, RELEASE, RECOVERY, WIND, BRACE, WINDUP }
    private static final BlockState STEEL=Blocks.IRON_BLOCK.defaultBlockState(), WOOD=Blocks.OAK_PLANKS.defaultBlockState(),
            BONE=Blocks.BONE_BLOCK.defaultBlockState(), GREEN=Blocks.CONCRETE.green().defaultBlockState(),
            CLAW=Blocks.CONCRETE.lime().defaultBlockState(), WHITE=Blocks.CONCRETE.white().defaultBlockState(),
            GOLD=Blocks.GOLD_BLOCK.defaultBlockState(), ORANGE=Blocks.CONCRETE.orange().defaultBlockState();
    private static final class Effect {
        final Battle.Actor actor;
        final FighterMoves.Move move;
        final Mode mode;
        final int born, life, direction;
        final long started;
        final double x,y;
        final List<Display.BlockDisplay> pieces=new ArrayList<>();
        Effect(Battle.Actor f,Mode mode,int now,int life) {
            actor=f;move=f.state.move;this.mode=mode;born=now;this.life=life;
            started=f.state.startedAt;direction=f.state.attackDirection;x=f.x;y=f.y;
        }
    }
    private final Battle battle;
    private final Map<UUID,Effect> effects=new LinkedHashMap<>();
    private final Map<UUID,Set<Integer>> delivered=new HashMap<>();
    ClassVisuals(Battle battle){this.battle=battle;}

    void strike(Battle.Actor f) {
        if(f.kind==FighterClass.ENDERMAN || f.state.move.kind()==AttackKind.RECOVERY)return;
        start(f,Mode.STRIKE,6);
    }
    void release(Battle.Actor f){if(f.kind==FighterClass.SKELETON || f.kind==FighterClass.VILLAGER)start(f,Mode.RELEASE,7);}
    void brace(Battle.Actor f){start(f,Mode.BRACE,CombatState.BRACE_TICKS);}
    void windup(Battle.Actor f){start(f,Mode.WINDUP,f.state.move.startup());}
    void departure(Battle.Actor f) {
        if(f.kind==FighterClass.ENDERMAN)return;
        start(f,f.state.move.technique()==FighterMoves.Technique.WIND_STEP?Mode.WIND:Mode.RECOVERY,10);
    }
    private void start(Battle.Actor f,Mode mode,int life) {
        var e=new Effect(f,mode,battle.now(),life);effects.put(f.id,e);draw(e,0);flush();
    }
    private void draw(Effect e,int age) {
        var pen=new Pen(e);double t=Math.min(1,age/(double)(e.life-1));
        double fade=1-Math.max(0,t-.55)*1.7;
        var f=e.actor;double x=f.kind==FighterClass.DROWNED?f.pose.x:f.x,y=f.kind==FighterClass.DROWNED?f.pose.y:f.y;int dir=e.direction;
        if(e.mode==Mode.STRIKE) {
            var shape=CombatGeometry.shape(e.move,dir,x,y);
            // The animated tip travels INSIDE the existing collision arc. The full arc remains the reach cue.
            boolean chop=e.move.aim()==AttackDirection.UP || e.move.aim()==AttackDirection.FORWARD
                    && (f.kind==FighterClass.VILLAGER || f.kind==FighterClass.STEVE && e.move.kind()==AttackKind.HEAVY);
            double sweep=chop?.85-t*.7:.15+t*.7;
            if(dir<0 && e.move.aim()==AttackDirection.FORWARD)sweep=1-sweep;
            if(f.kind==FighterClass.DROWNED && e.move.aim()==AttackDirection.FORWARD)sweep=.5;
            var tip=shape.arc(sweep);double tx=shape.x()+(tip.x()-shape.x())*.88,ty=shape.y()+(tip.y()-shape.y())*.88;
            double ox=shape.x(),oy=shape.y();
            double dx=tx-ox,dy=ty-oy,len=Math.max(.1,Math.hypot(dx,dy)),nx=-dy/len,ny=dx/len;
            switch(f.kind) {
                case IRON_GOLEM -> {
                    // Two thick forearms rise together for the uppercut; ordinary swings sweep a single fist.
                    int arms=e.move.id()==6 || e.move.aim()==AttackDirection.UP ? 2 : 1;
                    for(int arm=0;arm<arms;arm++) {
                        double offset=(arm-(arms-1)*.5)*.5;
                        double rootX=ox+offset,rootY=oy+.15;
                        double fistX=tx+offset,fistY=ty;
                        if(arms==2) {
                            rootX=x+offset;rootY=y+1.4;
                            fistX=x+dir*(.85+.30*Math.sin(t*Math.PI))+offset;
                            fistY=y+.65+Math.sin(Math.min(1,t*1.6)*Math.PI/2)*2.6;
                        }
                        pen.line(STEEL,rootX,rootY,fistX,fistY,.30*fade);
                        pen.line(WHITE,fistX-.16,fistY,fistX+.16,fistY,.37*fade);
                        pen.line(GREEN,rootX,rootY,rootX+(fistX-rootX)*.4,rootY+(fistY-rootY)*.4,.07*fade);
                    }
                }
                case DROWNED -> {
                    var prism=Blocks.PRISMARINE_BRICKS.defaultBlockState();
                    if(e.move.reach()<=1.25)pen.line(prism,ox,oy,tx,ty,.1*fade);
                    else {
                        double thrust=.72+.28*Math.sin(t*Math.PI);tx=ox+dx*thrust;ty=oy+dy*thrust;
                        pen.line(prism,ox,oy,tx,ty,.10*fade);
                        pen.line(prism,tx-dx*.14-nx*.24,ty-dy*.14-ny*.24,tx-dx*.14+nx*.24,ty-dy*.14+ny*.24,.08*fade);
                        for(int tine=-1;tine<=1;tine++)pen.line(WHITE,tx-dx*.14+nx*tine*.24,ty-dy*.14+ny*tine*.24,
                                tx+nx*tine*.24,ty+ny*tine*.24,.055*fade);
                    }
                }
                case STEVE -> {
                    boolean pick=e.move.kind()==AttackKind.HEAVY;
                    pen.line(WOOD,ox,oy,tx-dx*.28,ty-dy*.28,.12*fade);
                    if(pick) {
                        pen.line(STEEL,tx-nx*.38,ty-ny*.38,tx+nx*.38,ty+ny*.38,.20*fade);
                        pen.line(STEEL,tx+nx*.38,ty+ny*.38,tx+nx*.38-dx*.12,ty+ny*.38-dy*.12,.12*fade);
                    } else pen.line(STEEL,ox+dx*.3,oy+dy*.3,tx,ty,.13*fade);
                    pen.line(WHITE,tx-dx*.16,ty-dy*.16,tx,ty,.055*fade);
                }
                case ALEX -> {
                    // Three tapering ribbons follow the cut, with the white leading edge kept crisp.
                    for(int ribbon=0;ribbon<3;ribbon++) for(int j=0;j<5;j++) {
                        double p=Math.clamp(sweep-(4-j)*.075,0,1),q=Math.clamp(p+.065,0,1);
                        var a=shape.arc(p);var b=shape.arc(q);double inset=.90-ribbon*.12;
                        pen.line(ribbon==0?WHITE:ORANGE,ox+(a.x()-ox)*inset,oy+(a.y()-oy)*inset,
                                ox+(b.x()-ox)*inset,oy+(b.y()-oy)*inset,(.035+j*.012)*fade);
                    }
                }
                case ZOMBIE -> {
                    // A broad green forearm and three pale claws, instead of another weapon silhouette.
                    pen.line(GREEN,ox,oy,tx,ty,.23*fade);
                    for(int claw=-1;claw<=1;claw++)pen.line(CLAW,tx-dx*.28+nx*claw*.16,ty-dy*.28+ny*claw*.16,
                            tx+nx*claw*.16,ty+ny*claw*.16,.085*fade);
                }
                case SKELETON -> {
                    pen.line(BONE,ox,oy,tx,ty,.11*fade);
                    for(double end:new double[]{.22,.88}) {
                        double bx=ox+dx*end,by=oy+dy*end;
                        pen.line(BONE,bx-nx*.15,by-ny*.15,bx+nx*.15,by+ny*.15,.20*fade);
                    }
                }
                case VILLAGER -> {
                    pen.line(WOOD,ox,oy,tx,ty,.14*fade);
                    pen.line(STEEL,tx-dx*.2-nx*.16,ty-dy*.2-ny*.16,tx-dx*.2+nx*.34,ty-dy*.2+ny*.34,.34*fade);
                    pen.line(GOLD,tx-dx*.12-nx*.14,ty-dy*.12-ny*.14,tx-dx*.12+nx*.32,ty-dy*.12+ny*.32,.065*fade);
                }
                default -> {}
            }
        } else if(e.mode==Mode.WINDUP) {
            for(int arm=0;arm<2;arm++) {
                double offset=(arm-.5)*.55,handX=x+dir*(.75-t*.2)+offset,handY=y+.7-t*.25;
                pen.line(STEEL,x+offset,y+1.7,handX,handY,.28);
                pen.line(WHITE,handX-.16,handY,handX+.16,handY,.34);
                pen.line(GREEN,x+offset,y+1.7,x+offset+dir*.2,y+1.35,.065);
            }
        } else if(e.mode==Mode.BRACE) {
            // Iron foot brackets and branching cracks read clearly from the fixed side camera.
            for(int side:new int[]{-1,1}) {
                double foot=x+side*.48;
                pen.line(STEEL,foot-side*.20,y+.10,foot+side*.24,y+.10,.14);
                pen.line(STEEL,foot+side*.24,y+.10,foot+side*.24,y+.48,.12);
                pen.line(WHITE,x+side*.25,y+.03,x+side*.85,y+.10,.045);
                pen.line(WHITE,x+side*.85,y+.10,x+side*1.3,y+.035,.035);
                pen.line(WHITE,x+side*.85,y+.10,x+side*1.05,y+.24,.03);
            }
        } else if(e.mode==Mode.RELEASE) {
            if(f.kind==FighterClass.SKELETON) {
                // Bone bow limbs snap forward; the string straightens as the real arrow leaves.
                double root=x+dir*.55,front=root+dir*(.25+.28*Math.sin(t*Math.PI));
                pen.line(BONE,root,y+.85,front,y+1.45,.10*fade);
                pen.line(BONE,front,y+1.45,root,y+2.05,.10*fade);
                double string=root-dir*.25*(1-t);
                pen.line(WHITE,root,y+.85,string,y+1.45,.035*fade);
                pen.line(WHITE,string,y+1.45,root,y+2.05,.035*fade);
            } else {
                // Small golden chime diamonds open from the hands; they are not another damage ring.
                for(int i=0;i<3;i++) {
                    double cx=x+dir*(.5+t*.9),cy=y+.65+i*.42,s=.12*fade;
                    pen.line(GOLD,cx-s,cy,cx,cy+s,.05*fade);pen.line(GOLD,cx,cy+s,cx+s,cy,.05*fade);
                    pen.line(GOLD,cx+s,cy,cx,cy-s,.05*fade);pen.line(GOLD,cx,cy-s,cx-s,cy,.05*fade);
                }
            }
        } else {
            // Recovery props stay at takeoff: a visible cause for the upward motion, not a moving platform.
            x=e.x;y=e.y;
            switch(f.kind) {
                case IRON_GOLEM -> {
                    double lift=Math.sin(t*Math.PI)*.95;
                    for(int side:new int[]{-1,1}) {
                        double piston=x+side*.42;
                        pen.line(Blocks.POLISHED_ANDESITE.defaultBlockState(),piston-.25,y-.2,piston+.25,y-.2,.30*fade);
                        pen.line(STEEL,piston,y-.2,piston,y+lift,.16*fade);
                        pen.line(WHITE,piston-.27,y+lift,piston+.27,y+lift,.18*fade);
                        pen.line(WHITE,x+side*.6,y+.08,x+side*(.8+t*.9),y+.10,.05*fade);
                    }
                }
                case DROWNED -> {
                    // Counter-rotating water coils surround the moving fighter during Riptide.
                    x=f.pose.x;y=f.pose.y;
                    for(int coil=0;coil<2;coil++)for(int i=0;i<9;i++) {
                        double a=i*.7+age*.9+coil*Math.PI,b=a+.6;
                        pen.line(coil==0?Blocks.CONCRETE.cyan().defaultBlockState():WHITE,
                                x+Math.cos(a)*.65,y-.25+i*.25,x+Math.cos(b)*.65,y+(i+1)*.25-.25,.065*fade);
                    }
                }
                case STEVE -> {
                    double lift=Math.sin(Math.min(1,t*1.5)*Math.PI)*.65;
                    pen.line(WOOD,x-.4,y-.35,x+.4,y-.35,.28*fade);
                    pen.line(STEEL,x,y-.35,x,y+lift,.18*fade);
                    pen.line(WOOD,x-.5,y+lift,x+.5,y+lift,.17*fade);
                }
                case ALEX -> {
                    for(int ring=0;ring<3;ring++)for(int i=0;i<6;i++) {
                        double a=i*Math.PI/5+t*3+ring, b=a+.4,r=(.3+ring*.17)*(1+t*.4),cy=y+ring*.36-t*.35;
                        pen.line(ring==1?ORANGE:WHITE,x+Math.cos(a)*r,cy+Math.sin(a)*.13,
                                x+Math.cos(b)*r,cy+Math.sin(b)*.13,.055*fade);
                    }
                }
                case ZOMBIE -> {
                    for(int side:new int[]{-1,1}) {
                        double hx=x+side*(.32+t*.35),hy=y+.35*Math.sin(t*Math.PI);
                        pen.line(GREEN,hx,y-.3,hx,hy,.22*fade);
                        for(int finger=0;finger<3;finger++)pen.line(CLAW,hx-.12+finger*.1,hy,hx-.12+finger*.1,hy+.25,.065*fade);
                    }
                }
                case SKELETON -> {
                    for(int i=0;i<5;i++) {
                        double sy=y-.45+i*(.12+t*.11),sx=x+(i%2==0?-.28:.28);
                        pen.line(BONE,sx,sy,2*x-sx,sy+.12+t*.11,.085*fade);
                    }
                    pen.line(BONE,x-.4,y-.5,x+.4,y-.5,.15*fade);
                }
                case VILLAGER -> {
                    // A red rocket with a white band and a stepped exhaust, following the float.
                    x=f.x-dir*.45;y=f.y;
                    pen.line(Blocks.CONCRETE.red().defaultBlockState(),x,y-.15,x,y+.65,.22*fade);
                    pen.line(WHITE,x-.12,y+.25,x+.12,y+.25,.12*fade);
                    pen.line(GOLD,x,y-.15,x,y-.7-(age%2)*.18,.12*fade);
                    pen.line(WHITE,x,y-.18,x,y-.45,.055*fade);
                }
                default -> {}
            }
        }
    }
    private final class Pen {
        final Effect effect;int index;
        Pen(Effect e){effect=e;}
        void line(BlockState material,double x,double y,double tx,double ty,double width) {
            if(effect.mode==Mode.STRIKE) {
                var f=effect.actor;
                var shape=CombatGeometry.shape(effect.move,effect.direction,
                        f.kind==FighterClass.DROWNED?f.pose.x:f.x,f.kind==FighterClass.DROWNED?f.pose.y:f.y);
                var a=inset(shape,x,y,width/2+.015);var b=inset(shape,tx,ty,width/2+.015);
                x=a.x();y=a.y();tx=b.x();ty=b.y();
            }
            Display.BlockDisplay d;
            if(index==effect.pieces.size()) {
                d=new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY,battle.level);
                d.setBlockState(material);d.setNoGravity(true);d.setBrightnessOverride(new Brightness(15,15));
                d.setViewRange(3);d.setWidth(8);d.setHeight(8);d.setPosRotInterpolationDuration(1);
                d.setTransformationInterpolationDuration(1);effect.pieces.add(d);
            } else d=effect.pieces.get(index);
            index++;double angle=Math.atan2(ty-y,tx-x);
            d.setPos(x+Math.sin(angle)*width/2,y-Math.cos(angle)*width/2,1.10);
            d.setTransformationInterpolationDelay(0);
            d.setTransformation(new Transformation(null,new Quaternionf().rotationZ((float)angle),
                    new Vector3f((float)Math.max(.001,Math.hypot(tx-x,ty-y)),(float)Math.max(.001,width),.04f),null));
        }
    }
    private static CombatGeometry.Point inset(CombatGeometry.Shape shape,double x,double y,double margin) {
        var bounds=shape.bounds();
        x=Math.clamp(x,bounds.minX()+margin,bounds.maxX()-margin);
        y=Math.clamp(y,bounds.minY()+margin,bounds.maxY()-margin);
        double dx=x-shape.x(),dy=y-shape.y();
        double radius=Math.hypot(dx/Math.max(.01,shape.rx()-margin),dy/Math.max(.01,shape.ry()-margin));
        if(radius>1){x=shape.x()+dx/radius;y=shape.y()+dy/radius;}
        return new CombatGeometry.Point(x,y);
    }
    void tick() {
        effects.values().removeIf(e->e.actor.eliminated || !battle.game.fighting(e.actor)
                || e.mode==Mode.BRACE && !e.actor.state.bracing(battle.now(),e.actor.grounded)
                || battle.now()-e.born>=e.life || e.actor.state.startedAt!=e.started || battle.now()<e.actor.state.stunUntil);
        for(var e:effects.values())draw(e,battle.now()-e.born);
        flush();
    }
    private void flush() {
        var entities=new LinkedHashMap<Integer,Display.BlockDisplay>();
        for(var e:effects.values())for(var d:e.pieces)entities.put(d.getId(),d);
        var dirty=new HashMap<Integer,List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>>>();
        for(var d:entities.values()){var data=d.getEntityData().packDirty();if(data!=null)dirty.put(d.getId(),data);}
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
    int[] entityIds(Battle.Actor f){var e=effects.get(f.id);return e==null?new int[0]:e.pieces.stream().mapToInt(Entity::getId).toArray();}
    void remove(Battle.Actor f){effects.remove(f.id);flush();}
    void close(){effects.clear();flush();delivered.clear();}
}
