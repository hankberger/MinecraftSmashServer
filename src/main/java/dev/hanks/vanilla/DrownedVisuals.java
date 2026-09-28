package dev.hanks.vanilla;

import java.util.*;
import com.mojang.math.Transformation;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A visible sea-green rope and three-pronged spear. Packet-only: never a second projectile/hitbox. */
final class DrownedVisuals {
    private static final int ROPE=12, PARTS=18;
    private final Battle battle;
    private final Map<UUID,List<Display.BlockDisplay>> ropes=new HashMap<>();
    private final Map<Integer,Display> entities=new LinkedHashMap<>();
    private final Map<UUID,Set<Integer>> delivered=new HashMap<>();
    DrownedVisuals(Battle battle){this.battle=battle;}
    private Display.BlockDisplay block(BlockState material) {
        var d=new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY,battle.level);d.setBlockState(material);
        d.setNoGravity(true);d.setBrightnessOverride(new Brightness(15,15));d.setViewRange(3);d.setWidth(20);d.setHeight(20);
        d.setPosRotInterpolationDuration(1);d.setTransformationInterpolationDuration(1);entities.put(d.getId(),d);return d;
    }
    private void line(Display.BlockDisplay d,double x,double y,double tx,double ty,float width) {
        double angle=Math.atan2(ty-y,tx-x);
        d.setPos(x+Math.sin(angle)*width/2,y-Math.cos(angle)*width/2,1.05);
        d.setTransformationInterpolationDelay(0);
        d.setTransformation(new Transformation(null,new Quaternionf().rotationZ((float)angle),
                new Vector3f((float)Math.max(.001,Math.hypot(tx-x,ty-y)),width,.035f),null));
    }
    void tether(Battle.Actor f,Vec3 tip,Vec3 velocity,boolean taut) {
        var parts=ropes.computeIfAbsent(f.id,id->{
            var list=new ArrayList<Display.BlockDisplay>();
            for(int i=0;i<PARTS;i++)list.add(block((i<ROPE?Blocks.CONCRETE.cyan():i==ROPE?Blocks.PRISMARINE_BRICKS:Blocks.CONCRETE.lightBlue()).defaultBlockState()));
            return list;
        });
        double x=f.pose.x,y=f.pose.y+1.2,sag=taut?.04:Math.min(.65,Math.abs(tip.x-x)*.08);
        for(int i=0;i<ROPE;i++) {
            double a=i/(double)ROPE,b=(i+1)/(double)ROPE;
            line(parts.get(i),x+(tip.x-x)*a,y+(tip.y-y)*a-Math.sin(a*Math.PI)*sag,
                    x+(tip.x-x)*b,y+(tip.y-y)*b-Math.sin(b*Math.PI)*sag,taut?.045f:.028f);
        }
        Vec3 direction=taut?new Vec3(Math.signum(tip.x-x),0,0):velocity.normalize();
        double dx=direction.x,dy=direction.y,nx=-dy,ny=dx;
        line(parts.get(ROPE),tip.x-dx*1.15,tip.y-dy*1.15,tip.x,tip.y,.10f);
        line(parts.get(ROPE+1),tip.x-dx*.25-nx*.24,tip.y-dy*.25-ny*.24,tip.x-dx*.25+nx*.24,tip.y-dy*.25+ny*.24,.09f);
        for(int i=-1;i<=1;i++)line(parts.get(ROPE+3+i),tip.x-dx*.25+nx*i*.24,tip.y-dy*.25+ny*i*.24,
                tip.x+dx*(i==0?.12:0)+nx*i*.24,tip.y+dy*(i==0?.12:0)+ny*i*.24,.065f);
        line(parts.get(PARTS-1),tip.x-dx*.8,tip.y-dy*.8,tip.x-dx*.6,tip.y-dy*.6,.15f);
    }
    void remove(UUID owner){var parts=ropes.remove(owner);if(parts!=null)for(var d:parts)entities.remove(d.getId());}
    void clear(){ropes.clear();entities.clear();flush();delivered.clear();}
    int[] entityIds(){return entities.keySet().stream().mapToInt(Integer::intValue).toArray();}
    void flush() {
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
}
