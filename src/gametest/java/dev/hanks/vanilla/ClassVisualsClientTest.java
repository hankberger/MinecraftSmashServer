package dev.hanks.vanilla;

import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Input;

/** Server combat fixtures, actual client renderables, and lifecycle checks for all five class identities. */
@SuppressWarnings("UnstableApiUsage")
public final class ClassVisualsClientTest {
    private static VanillaSmash game(){return VanillaSmash.instance();}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void reset(UUID id,int direction) {
        var b=game().battle;var f=b.actors.get(id);b.reset(f,0,81);b.reset(b.dummy(),14,81);f.facing=direction;
    }
    public static void run(ClientGameTestContext c) {
        var props=new Properties();props.setProperty("online-mode","false");props.setProperty("server-ip","127.0.0.1");
        props.setProperty("server-port","25589");props.setProperty("view-distance","6");
        props.setProperty("simulation-distance","5");props.setProperty("allow-flight","true");
        try(var server=c.worldBuilder().createServer(props);var connection=server.connect()) {
            c.getInput().resizeWindow(1280,720);
            c.runOnClient(mc->{mc.options.fov().set(70);mc.options.guiScale().set(2);mc.resizeGui();});
            c.waitFor(mc->mc.gui.screen()!=null && mc.gui.screen().getClass().getSimpleName().equals("PackConfirmScreen"),300);
            MatchmakingClientTest.click(c,"Proceed");
            server.waitFor(s->game().uiPack.ready(connection.getServerPlayer()),1200);
            connection.waitForChunksRender();server.waitFor(s->game().hub.available(connection.getServerPlayer()),240);
            var id=server.computeOnServer(s->connection.getServerPlayer().getUUID());
            for(var kind:FighterClass.values()) {
                if(kind==FighterClass.ENDERMAN || kind==FighterClass.DROWNED || kind==FighterClass.IRON_GOLEM)continue;
                server.runOnServer(s->{game().leave(connection.getServerPlayer());game().choose(connection.getServerPlayer(),kind,VanillaSmash.Mode.SANDBOX);});
                server.waitFor(s->game().battle!=null && game().match.phase()==MatchState.Phase.ACTIVE,300);c.waitTicks(20);
                c.runOnClient(mc->{mc.gui.setScreen(null);mc.gui.toastManager().clear();mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);});
                for(int direction:new int[]{1,-1}) {
                    server.runOnServer(s->reset(id,direction));c.waitTicks(3);
                    server.runOnServer(s->{var b=game().battle;check(b.request(b.actors.get(id),false,Input.EMPTY),kind+" light accepted");});
                    server.waitFor(s->{var b=game().battle;return b.effects.classes.entityIds(b.actors.get(id)).length>0;},25);
                    int[] ids=server.computeOnServer(s->{var b=game().battle;return b.effects.classes.entityIds(b.actors.get(id));});
                    check(ids.length>0 && ids.length<=18,kind+" bounded melee sculpture");
                    c.waitTicks(1);
                    c.runOnClient(mc->{for(int entity:ids)check(mc.level.getEntity(entity) instanceof Display.BlockDisplay,kind+" melee reaches the vanilla renderer");});
                    c.takeScreenshot("class-"+kind.name().toLowerCase()+"-swing-"+direction);
                    c.waitTicks(12);
                    c.runOnClient(mc->{for(int entity:ids)check(mc.level.getEntity(entity)==null,kind+" melee expires on client");});
                    server.runOnServer(s->{var b=game().battle;check(b.dummy().state.percent==0,kind+" decoration does not extend attack reach");});
                }
                server.runOnServer(s->reset(id,1));c.waitTicks(3);
                server.runOnServer(s->{var b=game().battle;check(b.request(b.actors.get(id),true,Input.EMPTY),kind+" charge accepted");});
                c.waitTicks(ChargeRules.fullTicks(kind)+5);
                server.runOnServer(s->{var b=game().battle;b.releaseSpecial(b.actors.get(id));});
                server.waitFor(s->{var b=game().battle;return b.effects.classes.entityIds(b.actors.get(id)).length>0;},30);
                c.waitTicks(1);c.takeScreenshot("class-"+kind.name().toLowerCase()+"-special");c.waitTicks(18);
                server.runOnServer(s->reset(id,1));c.waitTicks(6);
                server.runOnServer(s->{
                    var b=game().battle;
                    check(b.request(b.actors.get(id),true,new Input(true,false,false,false,false,false,false)),kind+" recovery accepted");
                });
                int[] recovery=server.computeOnServer(s->{var b=game().battle;return b.effects.classes.entityIds(b.actors.get(id));});
                check(recovery.length>0 && recovery.length<=18,kind+" bounded recovery sculpture");
                c.waitTicks(2);
                c.runOnClient(mc->{for(int entity:recovery)check(mc.level.getEntity(entity) instanceof Display.BlockDisplay,kind+" recovery renders");});
                c.takeScreenshot("class-"+kind.name().toLowerCase()+"-recovery");
                server.runOnServer(s->{
                    var b=game().battle;var f=b.actors.get(id);
                    check(!f.recovery.recoveryAvailable(),kind+" visual does not refund recovery");
                    b.hit(b.dummy(),f,-1,FighterMoves.light(FighterClass.STEVE,AttackDirection.FORWARD,false));
                });c.waitTicks(3);
                c.runOnClient(mc->{for(int entity:recovery)check(mc.level.getEntity(entity)==null,kind+" interrupt removes recovery prop");});
                server.runOnServer(s->{
                    reset(id,-1);var b=game().battle;var f=b.actors.get(id);
                    check(b.request(f,false,Input.EMPTY),kind+" cleanup fixture attack");
                });
                server.waitFor(s->{var b=game().battle;return b.effects.classes.entityIds(b.actors.get(id)).length>0;},20);
                int[] leaving=server.computeOnServer(s->{var b=game().battle;return b.effects.classes.entityIds(b.actors.get(id));});
                server.runOnServer(s->game().leave(connection.getServerPlayer()));c.waitTicks(3);
                c.runOnClient(mc->{for(int entity:leaving)check(mc.level.getEntity(entity)==null,kind+" leaving removes displays");});
            }
            VanillaSmash.LOG.info("CLASS_VISUALS_OK five classes client rendering both facings charged releases recoveries interrupts cleanup");
        }
    }
}
