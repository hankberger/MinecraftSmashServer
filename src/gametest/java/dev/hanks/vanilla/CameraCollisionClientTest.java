package dev.hanks.vanilla;

import java.util.*;
import java.util.concurrent.atomic.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;

/** Measures the actual vanilla camera/rider motion and exercises every recovery against solid terrain. */
@SuppressWarnings("UnstableApiUsage")
public final class CameraCollisionClientTest {
    private record Sample(double x,double y,double z,float yaw,float pitch,float fov,double hudOffset) {}
    private static VanillaSmash game() { return VanillaSmash.instance(); }
    private static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
    public static void run(ClientGameTestContext c) {
        var cameraId=new AtomicInteger(-1); var capture=new AtomicBoolean(); var trace=new ArrayList<Sample>();
        var collisionId=new AtomicReference<UUID>(); var penetration=new AtomicReference<String>();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            var camera=mc.getCameraEntity();
            if(!capture.get() || camera==null || camera.getId()!=cameraId.get())return;
            var vehicle=camera.getVehicle();
            var hud=vehicle==null ? null : vehicle.getPassengers().stream().filter(e->e instanceof Display.TextDisplay).findFirst().orElse(null);
            trace.add(new Sample(camera.getX(),camera.getY(),camera.getZ(),camera.getViewYRot(1),camera.getViewXRot(1),
                    mc.gameRenderer.mainCamera().getFov(),hud==null ? Double.NaN : hud.getX()-camera.getX()));
        });
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if(collisionId.get()==null || game().battle==null)return;
            var f=game().battle.actors.get(collisionId.get()); if(f==null || f.ledge.attached() || f.state.floating(game().ticks))return;
            var size=f.body.getDimensions(Pose.STANDING);
            var box=new AABB(f.x-size.width()/2,f.y,.25,f.x+size.width()/2,f.y+size.height(),.75).deflate(.0001);
            for(var shape:game().battle.level.getBlockCollisions(f.body,box))
                if(!shape.isEmpty() && shape.max(Direction.Axis.Y)<=ArenaRules.DECK_Y && shape.toAabbs().stream().anyMatch(box::intersects))
                    penetration.compareAndSet(null,f.kind+" intersects solid island at "+f.x+", "+f.y);
        });
        var props=new Properties(); props.setProperty("online-mode","false"); props.setProperty("server-ip","127.0.0.1");
        props.setProperty("view-distance","6"); props.setProperty("simulation-distance","5"); props.setProperty("allow-flight","true");
        try(var server=c.worldBuilder().createServer(props); var connection=server.connect()) {
            connection.waitForChunksRender(); c.getInput().resizeWindow(1280,720);
            c.runOnClient(mc->{mc.options.fov().set(70); mc.options.guiScale().set(2); mc.resizeGui();});
            server.waitFor(s->game().hub.available(connection.getServerPlayer()),240);
            var id=server.computeOnServer(s->connection.getServerPlayer().getUUID());
            server.runOnServer(s->game().choose(connection.getServerPlayer(),FighterClass.STEVE,VanillaSmash.Mode.SANDBOX));
            server.waitFor(s->game().battle!=null && game().match.phase()==MatchState.Phase.ACTIVE,300);
            cameraId.set(server.computeOnServer(s->game().viewers.get(id).camera().getId()));
            c.waitFor(mc->mc.getCameraEntity()!=null && mc.getCameraEntity().getId()==cameraId.get(),300); c.waitTicks(25);
            c.runOnClient(mc->check(mc.getCameraEntity().getVehicle() instanceof Display.BlockDisplay,"Camera uses a native interpolated carrier"));
            server.runOnServer(s->game().battle.reset(game().battle.actors.get(id),-12,81)); c.waitTicks(25);
            c.takeScreenshot("follow-camera-close");
            capture.set(true); c.getInput().holdKeyFor(o->o.keyRight,40); c.waitTicks(10); capture.set(false);
            check(trace.size()>=45,"Captured native camera motion");
            int moving=0; double largest=0;
            for(int i=1;i<trace.size();i++) {
                var a=trace.get(i-1); var b=trace.get(i); double step=b.x-a.x; largest=Math.max(largest,Math.abs(step));
                check(step>=-.005,"Running right never shakes the camera backward");
                check(Math.abs(step)<.8,"Camera has no two-tick position snaps: "+step);
                check(Math.abs(Math.abs(b.yaw)-180)<.01 && Math.abs(b.pitch)<.01,"Side view never rotates: yaw="+b.yaw+" pitch="+b.pitch);
                check(Math.abs(b.fov-70)<.1,"Camera does not pump the FOV");
                check(Math.abs(b.hudOffset)<.005,"HUD and eye share the same client movement");
                if(step>.005)moving++;
            }
            check(moving>30,"Client interpolates continuously between server updates: "+moving);
            check(trace.getLast().x-trace.getFirst().x>8,"Camera follows the running fighter");
            VanillaSmash.LOG.info("FOLLOW_CAMERA_NATIVE_MOTION_PASSED samples={} movingTicks={} largestStep={}",trace.size(),moving,largest);
            c.takeScreenshot("follow-camera-panned");
            server.waitFor(s->!connection.getServerPlayer().getLastClientInput().right(),10);
            server.runOnServer(s->{var f=game().battle.actors.get(id);game().battle.reset(f,28,66);f.state.hitPauseUntil=game().ticks+55;});
            c.waitTicks(45); c.takeScreenshot("follow-camera-offstage");
            server.runOnServer(s->{
                var eye=game().viewers.get(id).camera(); check(eye.getX()>20 && eye.getEyeY()<71,"Camera follows deep offstage action");
                check(Math.abs(eye.getZ()-18)<.001,"Offstage follow keeps the closer zoom");
            });

            for(var kind:FighterClass.values()) {
                server.runOnServer(s->{game().leave(connection.getServerPlayer());game().choose(connection.getServerPlayer(),kind,VanillaSmash.Mode.SANDBOX);});
                server.waitFor(s->game().battle!=null && game().match.phase()==MatchState.Phase.ACTIVE,300);
                c.waitTicks(20); collisionId.set(id);
                server.runOnServer(s->{var f=game().battle.actors.get(id);game().battle.reset(f,0,68);f.recovery.jump(false);});
                c.getInput().holdKeyFor(o->o.keyJump,4); c.waitTicks(7);
                server.runOnServer(s->{
                    var f=game().battle.actors.get(id);
                    check(f.recovery.helpless() && !f.recovery.available(),"Blocked recovery stays spent");
                    check(f.y+f.body.getBbHeight()<=71.001,kind+" cannot recover through the island's underside");
                });
                server.waitFor(s->!connection.getServerPlayer().getLastClientInput().jump(),10);
                // S may crouch the model, but releasing it under a ceiling must not embed its standing body.
                c.getInput().holdKey(o->o.keyDown); c.waitTicks(3);
                server.runOnServer(s->{var f=game().battle.actors.get(id);game().battle.reset(f,0,68);f.recovery.jump(false);});
                c.getInput().holdKeyFor(o->o.keyJump,4); c.getInput().releaseKey(o->o.keyDown); c.waitTicks(8);
                server.waitFor(s->!connection.getServerPlayer().getLastClientInput().jump()&&!connection.getServerPlayer().getLastClientInput().backward(),10);
                for(int side:new int[]{-1,1}) {
                    server.runOnServer(s->{var f=game().battle.actors.get(id);game().battle.reset(f,LedgeState.hangX(side),76);f.recovery.jump(false);});
                    c.getInput().holdKey(o->side<0 ? o.keyRight : o.keyLeft);
                    c.getInput().holdKeyFor(o->o.keyJump,4); c.waitTicks(24);
                    c.getInput().releaseKey(o->side<0 ? o.keyRight : o.keyLeft);
                    server.waitFor(s->!connection.getServerPlayer().getLastClientInput().right()&&!connection.getServerPlayer().getLastClientInput().left(),10);
                }
                check(penetration.get()==null,"Recovery path stays outside solid terrain: "+penetration.get());
                collisionId.set(null); VanillaSmash.LOG.info("STAGE_COLLISION_NATIVE_OK {}",kind);
            }
            int oldCamera=server.computeOnServer(s->game().viewers.get(id).camera().getId());
            int oldCarrier=server.computeOnServer(s->game().viewers.get(id).rig().carrier.getId());
            server.runOnServer(s->game().leave(connection.getServerPlayer()));
            c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.LOBBY)&&mc.getCameraEntity()==mc.player,200);
            c.runOnClient(mc->check(mc.level.getEntity(oldCamera)==null&&mc.level.getEntity(oldCarrier)==null,"Leaving removes the private camera rig"));
            VanillaSmash.LOG.info("CAMERA_COLLISION_CLIENT_TEST_PASSED");
        } finally { capture.set(false); collisionId.set(null); }
    }
}
