package dev.hanks.vanilla;

import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.Blocks;

/** Packed seventh portrait, real throw/reel inputs, server fixtures and vanilla effect lifecycle. */
@SuppressWarnings("UnstableApiUsage")
public final class DrownedClientTest {
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
            PackedMenuClientTest.click(c,FighterClass.DROWNED.ordinal());
            server.runOnServer(s->check(game().stage.session(id).selected==FighterClass.DROWNED,"Seventh portrait selects Drowned"));
            c.takeScreenshot("drowned-picker");PackedMenuClientTest.click(c,31);
            server.waitFor(s->game().battle!=null&&game().match.phase()==MatchState.Phase.ACTIVE,300);
            c.waitFor(mc->MvpWorlds.battle(mc.level)&&mc.getCameraEntity()!=mc.player,300);c.waitTicks(15);
            c.runOnClient(mc->{mc.gui.toastManager().clear();mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);});
            server.runOnServer(s->{
                check(game().battle.actors.get(id).body instanceof net.minecraft.world.entity.monster.zombie.Drowned,"Native drowned model");
                place(id,11,81,16,81);
            });
            c.getInput().pressMouse(1);
            server.waitFor(s->game().battle.drowned.target(game().battle.actors.get(id))!=null,30);
            server.runOnServer(s->{var b=game().battle;check(b.dummy().state.percent==6,"Native throw deals one harpoon hit");
                check(!b.actors.get(id).drowned.armed(),"Thrown weapon is unavailable");});
            c.waitTicks(5);
            var tether=server.computeOnServer(s->game().battle.drowned.visuals.entityIds());
            c.runOnClient(mc->{for(int entity:tether)check(mc.level.getEntity(entity)!=null,"Tether and trident rendered by client");});
            c.takeScreenshot("drowned-hook");
            double before=server.computeOnServer(s->{var b=game().battle;return Math.abs(b.dummy().x-b.actors.get(id).x);});
            c.getInput().pressKey(o->o.keySwapOffhand);c.waitTicks(5);c.takeScreenshot("drowned-reel");c.waitTicks(12);
            server.runOnServer(s->{var b=game().battle;var f=b.actors.get(id);
                check(Math.abs(b.dummy().x-f.x)<before-1,"F reels victim toward Drowned");
                check(b.dummy().state.percent==6,"Reel adds no second hit");
                check(f.drowned.armed()&&b.drowned.target(f)==null,"One reel consumes tether and returns weapon");
            });
            // Mirrored aerial self-reel: no teleport or refreshed air resources.
            server.runOnServer(s->{place(id,12,100,6,100);var b=game().battle;check(b.request(b.actors.get(id),true,Input.EMPTY),"Left throw accepted");});
            server.waitFor(s->game().battle.drowned.target(game().battle.actors.get(id))!=null,25);c.waitTicks(9);
            double selfX=server.computeOnServer(s->game().battle.actors.get(id).x);
            server.runOnServer(s->{var b=game().battle;var f=b.actors.get(id);
                // Keep this movement fixture in open air after the falling-shot assertion above.
                // Otherwise the center platform can legitimately break the tether during the wait.
                f.y=b.dummy().y=96;f.vy=b.dummy().vy=0;f.grounded=b.dummy().grounded=false;
                f.pose.reset(f.x,f.y);b.dummy().pose.reset(b.dummy().x,b.dummy().y);
                check(b.requestSecondary(f,new Input(false,false,false,true,false,false,false)),"Away plus F selects self reel: phase="+f.drowned.phase()+" target="+f.drowned.target(b.now())+" ready="+(f.state.readyAt-b.now())+" stunned="+(f.state.stunUntil-b.now())+" ledge="+f.ledge.attached()+" helpless="+f.recovery.helpless());});
            c.waitTicks(6);c.takeScreenshot("drowned-self-reel");
            server.runOnServer(s->{var b=game().battle;var f=b.actors.get(id);
                check(f.x<selfX-1,"Self reel moves toward left-side target");check(!f.drowned.canSelfReel(),"Aerial self reel is spent");
                check(f.recovery.recoveryAvailable(),"Self reel preserves rather than refunds recovery");});
            // A miss returns visibly; unarmed lights use their reduced hitbox and damage.
            server.runOnServer(s->{place(id,0,81,15,81);var b=game().battle;b.request(b.actors.get(id),true,Input.EMPTY);});
            c.waitTicks(15);
            server.runOnServer(s->{var b=game().battle;var f=b.actors.get(id);
                check(!f.drowned.armed(),"Miss spends time returning");check(b.request(f,false,Input.EMPTY),"Unarmed light accepted");
                check(f.state.move.damage()==3&&f.state.move.reach()<=1.25,"Weapon-away melee is weaker and shorter");});
            c.takeScreenshot("drowned-return");c.waitTicks(16);
            server.runOnServer(s->{var b=game().battle;check(b.actors.get(id).drowned.armed()&&b.dummy().state.percent==0,"Return is harmless and restores weapon");});
            // Spawn protection, shields, and terrain all reject the hook.
            for(boolean shield:new boolean[]{false,true}) {
                server.runOnServer(s->{place(id,0,81,4,81);var b=game().battle;
                    if(shield){b.dummy().state.pause(b.now(),40);b.dummy().state.guardUntil=b.now()+40;}
                    else b.dummy().state.protectedUntil=b.now()+40;
                    b.request(b.actors.get(id),true,Input.EMPTY);});c.waitTicks(13);
                server.runOnServer(s->{var b=game().battle;check(b.dummy().state.percent==0&&b.drowned.target(b.actors.get(id))==null,"Blocked/protected target never hooks");});
            }
            var wall=new BlockPos(3,82,0);
            var old=server.computeOnServer(s->game().battle.level.getBlockState(wall));
            try {
                server.runOnServer(s->{place(id,0,81,5,81);var b=game().battle;b.level.setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());b.request(b.actors.get(id),true,Input.EMPTY);});
                c.waitTicks(18);server.runOnServer(s->{var b=game().battle;check(b.dummy().state.percent==0&&b.drowned.target(b.actors.get(id))==null,"Terrain stops harpoon");});
            } finally {server.runOnServer(s->game().battle.level.setBlockAndUpdate(wall,old));}
            server.runOnServer(s->{place(id,0,81,5,81);var b=game().battle;b.request(b.actors.get(id),true,Input.EMPTY);});
            server.waitFor(s->game().battle.drowned.target(game().battle.actors.get(id))!=null,25);
            server.runOnServer(s->{var b=game().battle;b.hit(b.dummy(),b.actors.get(id),-1,FighterMoves.light(FighterClass.STEVE,AttackDirection.FORWARD,false));});c.waitTicks(2);
            server.runOnServer(s->{var b=game().battle;check(b.drowned.target(b.actors.get(id))==null,"Hitting owner breaks tether");});
            server.runOnServer(s->place(id,0,81,14,81));c.waitTicks(6);
            c.getInput().holdKey(o->o.keyUp);c.waitTicks(3);c.getInput().pressMouse(1);c.waitTicks(4);c.takeScreenshot("drowned-riptide");
            c.getInput().releaseKey(o->o.keyUp);
            server.runOnServer(s->{var f=game().battle.actors.get(id);check(f.y>83,"Native W+RMB rises with Riptide");
                check(!f.recovery.available()&&!f.recovery.recoveryAvailable(),"Riptide spends normal air budget");});
            server.runOnServer(s->{place(id,0,81,5,81);var b=game().battle;b.request(b.actors.get(id),true,Input.EMPTY);});
            server.waitFor(s->game().battle.drowned.target(game().battle.actors.get(id))!=null,25);
            var finalIds=server.computeOnServer(s->game().battle.drowned.visuals.entityIds());
            var battle=server.computeOnServer(s->game().battle);
            c.runOnClient(mc->mc.player.connection.sendCommand("smash leave"));server.waitFor(s->game().battle==null,80);c.waitTicks(3);
            server.runOnServer(s->check(battle.drowned.count()==0&&battle.drowned.visuals.entityIds().length==0,"Departure clears all owned objects"));
            c.runOnClient(mc->{for(int entity:finalIds)check(mc.level.getEntity(entity)==null,"Departure removes tether from client");});
            VanillaSmash.LOG.info("DROWNED_OK packed picker native throw reel recovery mirrored aerial self reel shield protection walls interrupts cleanup");
        }
    }
}
