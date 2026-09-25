package dev.hanks.vanilla;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Blocks;

/** Real blocks, native input, camera entry and stock lifecycle on every stage. */
@SuppressWarnings("UnstableApiUsage")
final class StagesClientTest {
    private static VanillaSmash game(){return VanillaSmash.instance();}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void run(ClientGameTestContext c) {
        var entering=new AtomicBoolean(); var badFrame=new AtomicReference<String>();
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(!entering.get()||mc.level==null||!MvpWorlds.battle(mc.level))return;
            var eye=mc.getCameraEntity();
            if(eye==null||eye==mc.player||!(eye.getVehicle() instanceof Display.BlockDisplay)) badFrame.compareAndSet(null,"First-person camera exposed during stage entry");
        });
        var props=new Properties(); props.setProperty("online-mode","false"); props.setProperty("server-ip","127.0.0.1");
        props.setProperty("view-distance","8"); props.setProperty("simulation-distance","5"); props.setProperty("allow-flight","true");
        try(var server=c.worldBuilder().createServer(props);var connection=server.connect()) {
            connection.waitForChunksRender(); c.getInput().resizeWindow(1280,720);
            c.runOnClient(mc->{mc.options.fov().set(70);mc.options.guiScale().set(2);mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.FANCY);mc.resizeGui();});
            server.waitFor(s->game().hub.available(connection.getServerPlayer()),240);
            server.runOnServer(MapWorldTest::verifyArena);
            var id=server.computeOnServer(s->connection.getServerPlayer().getUUID());
            for(var stage:BattleStage.values()) {
                if (Boolean.getBoolean("smash_vanilla.groveTest") && stage != BattleStage.SKYBOUND_GROVE) continue;
                server.runOnServer(s->{
                    var level=s.getLevel(MvpWorlds.arena(stage));
                    ArenaBuilder.ensureBuilt(level); ArenaBuilder.ensureBuilt(level);
                    check(MvpWorlds.managed(level),"New stage has gameplay protection");
                    for(int x=(int)stage.blastLeft();x<=stage.blastRight();x++) for(int y=80;y<=stage.blastTop();y++) for(int z=0;z<=0;z++) {
                        var pos=new BlockPos(x,y,z); var shape=level.getBlockState(pos).getCollisionShape(level,pos);
                        boolean intended=y==80&&x>=stage.left&&x<=stage.right||stage.platform(x,y,z);
                        check(intended?!shape.isEmpty()&&shape.max(Direction.Axis.Y)==1:shape.isEmpty(),stage+" unintended collision at "+pos);
                    }
                    for(int side:new int[]{-1,1}) {
                        int edge=side<0?stage.left:stage.right;
                        for(int y=77;y<=80;y++) for(int z=1;z<=3;z++) check(level.getBlockState(new BlockPos(edge,y,z)).isAir(),"Visible ledge corners");
                    }
                    check(level.getBlockState(new BlockPos(1,40,0)).is(stage==BattleStage.SKYBOUND_GROVE?Blocks.LAPIS_BLOCK:Blocks.REINFORCED_DEEPSLATE),"Build marker is below blast zone");
                });
                entering.set(true);
                server.runOnServer(s->game().begin(List.of(connection.getServerPlayer()),VanillaSmash.Mode.SANDBOX,stage));
                c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.arena(stage))&&mc.getCameraEntity()!=mc.player,300);
                c.waitTicks(35); entering.set(false);
                var bodies=server.computeOnServer(s->game().battle.actors.values().stream().map(f->f.body.getId()).toList());
                server.runOnServer(s->VanillaSmash.LOG.info("STAGE_SERVER_BODIES {}",game().battle.actors.values().stream().map(f->f.body.getId()+" removed="+f.body.isRemoved()+" registered="+(game().battle.level.getEntity(f.body.getId())!=null)+" health="+f.body.getHealth()).toList()));
                c.runOnClient(mc->VanillaSmash.LOG.info("STAGE_VISIBLE stage={} dimension={} bodies={}", stage,mc.level.dimension(),bodies.stream().map(entity->{var e=mc.level.getEntity(entity);return entity+":"+(e==null?"missing":e.getType()+" invisible="+e.isInvisible()+" pos="+e.position());}).toList()));
                c.waitFor(mc->bodies.stream().allMatch(entity->mc.level.getEntity(entity)!=null&&!mc.level.getEntity(entity).isInvisible()),100);
                connection.waitForChunksRender(); c.waitTicks(10);
                check(badFrame.get()==null,badFrame.get());
                server.runOnServer(s->{
                    check(game().battle.stage==stage,"Chosen stage is retained by the battle");
                    check(game().battle.dummy().x==stage.dummyX(),"Dummy fits the stage");
                    check(game().battle.actors.values().stream().allMatch(f->stage.supported(f.x,f.y)),"All opening positions supported");
                });
                c.runOnClient(mc->mc.gui.toastManager().clear());
                if(stage==BattleStage.SKYBOUND_GROVE) c.runOnClient(mc->{
                    check(mc.level.getBlockState(new BlockPos(-43,83,-23)).is(Blocks.WATER),"Waterfall reaches the stock client at its normal view distance");
                    check(mc.level.getBlockState(new BlockPos(42,82,-24)).is(Blocks.NETHER_PORTAL),"Portal backdrop reaches the stock client");
                    check(mc.level.getBlockState(new BlockPos(-47,82,-23)).is(Blocks.CHERRY_WOOD),"Bent cherry trunk remains visible within the ordinary client chunk range");
                });
                c.takeScreenshot("stage-"+stage.id+"-opening");
                for(var p:stage.platforms) {
                    double x=(p.left()+p.right()+1)/2.0;
                    server.runOnServer(s->{var b=game().battle;var f=b.actors.get(id);b.reset(f,x,p.top()+1.5);f.vy=-.4;b.reset(b.dummy(),stage.dummyX(),81);});
                    server.waitFor(s->{var f=game().battle.actors.get(id);return f.grounded&&f.y==p.top();},35);
                    c.getInput().holdKeyFor(o->o.keyDown,7);
                    server.waitFor(s->game().battle.actors.get(id).y<p.top()-.5,25);
                    server.waitFor(s->!connection.getServerPlayer().getLastClientInput().backward(),10);
                    server.runOnServer(s->{var b=game().battle;var f=b.actors.get(id);b.reset(f,x,p.top()-2);f.vy=MovementRules.JUMP;f.grounded=false;});
                    server.waitFor(s->game().battle.actors.get(id).y>p.top()+.3,20);
                }
                for(int side:new int[]{-1,1}) {
                    server.runOnServer(s->{var b=game().battle;var f=b.actors.get(id);b.reset(f,stage.hangX(side),80);f.vy=-.3;});
                    server.waitFor(s->game().battle.actors.get(id).ledge.attached(),20); c.waitTicks(4);
                    if(side==1)c.takeScreenshot("stage-"+stage.id+"-ledge");
                    c.getInput().holdKeyFor(o->side<0?o.keyRight:o.keyLeft,9);
                    server.waitFor(s->{var f=game().battle.actors.get(id);return !f.ledge.attached()&&f.grounded;},25);
                    server.runOnServer(s->check(stage.supported(game().battle.actors.get(id).x,81),"Ledge climb lands on this deck"));
                    server.waitFor(s->!connection.getServerPlayer().getLastClientInput().left()&&!connection.getServerPlayer().getLastClientInput().right(),10);
                }
                // Each kit's recovery must collide with the main island, regardless of decorative depth.
                for(var kind:FighterClass.values()) server.runOnServer(s->{
                    var b=game().battle;var f=b.add(null,kind,0);b.reset(f,0,60);f.vy=15;f.grounded=false;
                    var size=f.body.getDimensions(net.minecraft.world.entity.Pose.STANDING);
                    var box=new net.minecraft.world.phys.AABB(-size.width()/2,60,.25,size.width()/2,60+size.height(),.75);
                    var step=StageCollision.move(box,0,15,b.level.getBlockCollisions(f.body,box.expandTowards(0,15,0)));
                    check(step.ceiling()&&step.y()<15,kind+" cannot tunnel through "+stage+" underside");
                    b.eliminate(f);b.actors.remove(f.id);f.marker.discard();b.displays.remove(f.marker);
                });
                server.runOnServer(s->{var b=game().battle;var f=b.actors.get(id);b.reset(f,stage.blastRight()+.1,81);});
                server.waitFor(s->game().battle.actors.get(id).state.floating(game().ticks),15);
                server.waitFor(s->{var f=game().battle.actors.get(id);return !f.state.floating(game().ticks)&&f.grounded;},60);
                server.runOnServer(s->{var f=game().battle.actors.get(id);check(f.y==stage.respawnLanding()&&stage.supported(f.x,f.y),"Respawn lands on actual center platform");});
                c.waitTicks(20); c.takeScreenshot("stage-"+stage.id+"-respawn");
                server.runOnServer(s->game().leave(connection.getServerPlayer()));
                c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.LOBBY)&&mc.getCameraEntity()==mc.player,200);
                VanillaSmash.LOG.info("STAGE_NATIVE_CHECKS_PASSED {}",stage);
            }
            VanillaSmash.LOG.info("STAGES_CLIENT_TEST_PASSED");
        } finally {entering.set(false);}
    }
}
