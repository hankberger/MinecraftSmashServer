package dev.hanks.vanilla;

import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.Blocks;

/** Packed tank portrait, native controls, brace counterplay and effect lifecycle. */
@SuppressWarnings("UnstableApiUsage")
public final class IronGolemClientTest {
    private static VanillaSmash game(){return VanillaSmash.instance();}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void place(UUID id,double x,double y,double targetX,double targetY) {
        var b=game().battle;b.reset(b.actors.get(id),x,y);b.reset(b.dummy(),targetX,targetY);b.actors.get(id).facing=targetX>x?1:-1;
    }
    public static void run(ClientGameTestContext c) {
        var props=new Properties();props.setProperty("online-mode","false");props.setProperty("server-ip","127.0.0.1");
        props.setProperty("server-port","25589");props.setProperty("view-distance","6");props.setProperty("simulation-distance","5");props.setProperty("allow-flight","true");
        try(var server=c.worldBuilder().createServer(props);var connection=server.connect()) {
            c.getInput().resizeWindow(1280,720);c.runOnClient(mc->{mc.options.fov().set(70);mc.options.guiScale().set(2);mc.resizeGui();});
            c.waitFor(mc->mc.gui.screen()!=null&&mc.gui.screen().getClass().getSimpleName().equals("PackConfirmScreen"),300);
            MatchmakingClientTest.click(c,"Proceed");server.waitFor(s->game().uiPack.ready(connection.getServerPlayer()),1200);
            connection.waitForChunksRender();server.waitFor(s->game().hub.available(connection.getServerPlayer()),240);
            var id=server.computeOnServer(s->connection.getServerPlayer().getUUID());
            c.runOnClient(mc->mc.player.connection.sendCommand("smash sandbox"));
            PackedMenuClientTest.click(c,FighterClass.IRON_GOLEM.ordinal());
            server.runOnServer(s->check(game().stage.session(id).selected==FighterClass.IRON_GOLEM,"Eighth portrait selects Iron Golem"));
            c.runOnClient(mc->mc.gui.toastManager().clear());c.takeScreenshot("iron-golem-picker");PackedMenuClientTest.click(c,31);
            server.waitFor(s->game().battle!=null&&game().match.phase()==MatchState.Phase.ACTIVE,300);
            c.waitFor(mc->MvpWorlds.battle(mc.level)&&mc.getCameraEntity()!=mc.player,300);c.waitTicks(15);
            c.runOnClient(mc->{mc.gui.toastManager().clear();mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);});
            server.runOnServer(s->{
                check(game().battle.actors.get(id).body instanceof net.minecraft.world.entity.animal.golem.IronGolem,"Native Iron Golem model");
                place(id,0,81,2,81);
            });c.waitTicks(6);
            int initialSpecials=server.computeOnServer(s->game().battle.actors.get(id).specials);
            c.getInput().holdMouse(1);c.waitTicks(3);
            var windup=server.computeOnServer(s->game().battle.effects.classes.entityIds(game().battle.actors.get(id)));
            check(windup.length==6,"Visible two-arm windup before impact");
            c.takeScreenshot("iron-golem-windup");
            server.waitFor(s->game().battle.dummy().state.percent>0,30);
            server.runOnServer(s->{var b=game().battle;var f=b.actors.get(id);
                check(b.dummy().state.percent==16,"Click uppercut deals one heavy hit");
                check(b.dummy().vy>Math.abs(b.dummy().vx)*2,"Uppercut throws upward");
                check(!f.state.chargingSpecial(),"Uppercut never waits for release");
            });
            c.waitTicks(2);c.takeScreenshot("iron-golem-uppercut");c.waitTicks(35);
            server.runOnServer(s->check(game().battle.actors.get(id).specials==initialSpecials+1,"Holding right-click never auto-repeats uppercut"));
            c.getInput().releaseMouse(1);
            server.runOnServer(s->place(id,11,81,16,81));c.waitTicks(6);
            c.getInput().pressKey(o->o.keySwapOffhand);
            server.waitFor(s->game().battle.actors.get(id).state.bracing(game().ticks,true),15);
            var braceIds=server.computeOnServer(s->{var b=game().battle;var f=b.actors.get(id);
                check(f.kit.utilityReadyAt>b.now(),"Brace has separate cooldown");
                return b.effects.classes.entityIds(f);
            });
            check(braceIds.length>=8,"Brace has visible iron feet and cracks");c.waitTicks(2);
            c.runOnClient(mc->{for(int entity:braceIds)check(mc.level.getEntity(entity)!=null,"Brace sculptures reached vanilla client");});
            c.takeScreenshot("iron-golem-brace");
            double braceX=server.computeOnServer(s->game().battle.actors.get(id).x);
            server.runOnServer(s->{var b=game().battle;var f=b.actors.get(id);
                var impact=b.hit(b.dummy(),f,-1,FighterMoves.light(FighterClass.STEVE,AttackDirection.FORWARD,false));
                check(impact.armored()&&f.state.percent==7,"Brace takes damage but absorbs light");
                check(f.grounded&&f.vy==0&&Math.abs(f.vx)<=.22,"Brace stays grounded");
                check(!f.state.bracing(b.now(),true),"First contact spends brace");
            });c.waitTicks(4);
            c.runOnClient(mc->{for(int entity:braceIds)check(mc.level.getEntity(entity)==null,"Spent brace disappears");});
            server.runOnServer(s->{var b=game().battle;var f=b.actors.get(id);
                check(f.x<braceX&&braceX-f.x<.7,"Absorbed contact gives a small real grounded recoil");
                check(!b.requestSecondary(f,Input.EMPTY),"Cooldown prevents immediate rebrace");
                check(b.request(f,true,Input.EMPTY),"Absorbed hit permits counter uppercut");
            });c.waitTicks(35);
            // Native movement inputs cannot slide or jump out of the committed stance.
            server.runOnServer(s->place(id,11,81,16,81));c.waitTicks(6);
            c.getInput().pressKey(o->o.keySwapOffhand);
            server.waitFor(s->game().battle.actors.get(id).state.bracing(game().ticks,true),15);
            double plantedX=server.computeOnServer(s->game().battle.actors.get(id).x);
            c.getInput().holdKey(o->o.keyRight);c.getInput().holdKey(o->o.keyJump);c.waitTicks(4);
            server.runOnServer(s->{var f=game().battle.actors.get(id);check(f.grounded&&Math.abs(f.x-plantedX)<.01,"Brace commits feet despite movement/jump");});
            c.getInput().releaseKey(o->o.keyRight);c.getInput().releaseKey(o->o.keyJump);
            server.runOnServer(s->{var b=game().battle;var f=b.actors.get(id);
                var hit=b.hit(b.dummy(),f,-1,FighterMoves.special(FighterClass.STEVE,false,false));
                check(!hit.armored()&&!f.grounded&&f.vy>0,"Heavy breaks through brace");
                check(!f.state.bracing(b.now(),true),"Heavy removes stance");
            });c.waitTicks(3);
            // Left-facing light, and an aerial stance rejection that spends no cooldown.
            server.runOnServer(s->{place(id,13,81,11,81);var b=game().battle;check(b.request(b.actors.get(id),false,Input.EMPTY),"Left sweep accepted");});
            server.waitFor(s->game().battle.dummy().state.percent>0,20);c.takeScreenshot("iron-golem-sweep");
            server.runOnServer(s->{place(id,11,91,16,81);var b=game().battle;var f=b.actors.get(id);
                check(!b.requestSecondary(f,Input.EMPTY),"Plant Feet is grounded only");
                check(f.kit.utilityReadyAt==0,"Aerial rejection spends no cooldown");
            });
            server.runOnServer(s->place(id,0,81,4,81));c.waitTicks(6);
            c.getInput().holdKey(o->o.keyUp);c.waitTicks(3);c.getInput().pressMouse(1);c.waitTicks(4);
            c.takeScreenshot("iron-golem-heave");c.getInput().releaseKey(o->o.keyUp);
            server.runOnServer(s->{var f=game().battle.actors.get(id);
                check(f.y>84,"W plus right-click uses strong vertical recovery");
                check(Math.abs(f.x)<.3,"Recovery has poor horizontal travel");
                check(!f.recovery.available()&&!f.recovery.recoveryAvailable(),"Recovery consumes ordinary air budget");
            });
            server.runOnServer(s->{place(id,11,81,16,81);var b=game().battle;b.requestSecondary(b.actors.get(id),Input.EMPTY);});
            server.waitFor(s->game().battle.actors.get(id).state.bracing(game().ticks,true),15);
            var finalIds=server.computeOnServer(s->game().battle.effects.classes.entityIds(game().battle.actors.get(id)));
            c.runOnClient(mc->mc.player.connection.sendCommand("smash leave"));server.waitFor(s->game().battle==null,80);c.waitTicks(3);
            c.runOnClient(mc->{for(int entity:finalIds)check(mc.level.getEntity(entity)==null,"Leaving removes owned visuals");});
            VanillaSmash.LOG.info("IRON_GOLEM_OK packed picker native uppercut brace light heavy movement cooldown aerial recovery mirrored cleanup");
        }
    }
}
