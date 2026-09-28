package dev.hanks.vanilla;

import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.player.Input;

/** Packed picker, vanilla input, and deterministic collision/resource/lifecycle fixtures. */
@SuppressWarnings("UnstableApiUsage")
public final class EndermanClientTest {
    private static VanillaSmash game() { return VanillaSmash.instance(); }
    private static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
    private static void place(UUID id,double x,double y,double dummyX,double dummyY) {
        var b=game().battle;b.reset(b.actors.get(id),x,y);b.reset(b.dummy(),dummyX,dummyY);b.actors.get(id).facing=1;
    }
    public static void run(ClientGameTestContext c) {
        var props=new Properties();props.setProperty("online-mode","false");props.setProperty("server-ip","127.0.0.1");
        props.setProperty("server-port","25589");
        props.setProperty("view-distance","6");props.setProperty("simulation-distance","5");props.setProperty("allow-flight","true");
        try(var server=c.worldBuilder().createServer(props);var connection=server.connect()) {
            c.getInput().resizeWindow(1280,720);
            c.runOnClient(mc->{mc.options.fov().set(70);mc.options.guiScale().set(2);mc.resizeGui();});
            c.waitFor(mc->mc.gui.screen()!=null && mc.gui.screen().getClass().getSimpleName().equals("PackConfirmScreen"),300);
            MatchmakingClientTest.click(c,"Proceed");
            server.waitFor(s->game().uiPack.ready(connection.getServerPlayer()),1200);
            connection.waitForChunksRender();
            server.waitFor(s->game().hub.available(connection.getServerPlayer()),240);
            var id=server.computeOnServer(s->connection.getServerPlayer().getUUID());
            c.runOnClient(mc->mc.player.connection.sendCommand("smash sandbox"));
            PackedMenuClientTest.click(c,FighterClass.ENDERMAN.ordinal());
            c.takeScreenshot("enderman-picker-click");
            server.runOnServer(s->check(game().stage.session(id).selected==FighterClass.ENDERMAN,"Sixth portrait selects Enderman"));
            c.waitTicks(20);c.runOnClient(mc->mc.gui.toastManager().clear());c.takeScreenshot("enderman-picker");
            PackedMenuClientTest.click(c,31);
            server.waitFor(s->game().battle!=null && game().match.phase()==MatchState.Phase.ACTIVE,300);
            c.waitFor(mc->MvpWorlds.battle(mc.level)&&mc.getCameraEntity()!=mc.player,300);c.waitTicks(15);
            c.runOnClient(mc->{mc.gui.toastManager().clear();mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);});
            server.runOnServer(s->{
                check(game().battle.actors.get(id).body instanceof net.minecraft.world.entity.monster.EnderMan,"Native Enderman model");
                place(id,13.5,81,15,81);
            });
            c.getInput().pressMouse(0);
            server.waitFor(s->game().battle.actors.get(id).ender.prey(game().ticks)!=null,25);
            server.runOnServer(s->{
                var b=game().battle;var f=b.actors.get(id);
                check(f.ender.prey(b.now()).equals(b.dummy().id)&&b.enders.markerCount()==1,"Real light automatically marks one prey");
            });
            c.waitTicks(3);
            int[] markIds=server.computeOnServer(s->game().battle.enders.visuals.entityIds());
            c.runOnClient(mc->{for(int entityId:markIds)check(mc.level.getEntity(entityId)!=null,"Prey visuals delivered to the real client");});
            c.takeScreenshot("enderman-prey");
            server.runOnServer(s->place(id,13.5,81,16,81));
            c.getInput().pressKey(o->o.keySwapOffhand);
            server.waitFor(s->game().battle.dummy().state.percent==8,25);
            server.runOnServer(s->{
                var b=game().battle;var f=b.actors.get(id);var target=b.dummy();
                check(target.vx>.5&&target.vy>.5,"F immediately launches away and up");
                check(target.x>15.5,"Swing never snaps the opponent into a hold");
                check(f.state.specialConfirm(b.now()),"A connected swing permits Pursuit");
            });
            c.takeScreenshot("enderman-arm-swing");
            c.getInput().pressMouse(1);
            server.waitFor(s->!game().battle.actors.get(id).ender.airAvailable(),20);
            server.runOnServer(s->{
                var b=game().battle;check(b.dummy().state.percent==8,"Pursuit still requires a manual follow-up");
                check(b.actors.get(id).recovery.recoveryAvailable(),"Swing and pursuit preserve recovery");
                place(id,-13.5,81,-16,81);b.actors.get(id).facing=-1;
            });
            c.getInput().pressKey(o->o.keySwapOffhand);
            server.waitFor(s->game().battle.dummy().state.percent==8,25);
            server.runOnServer(s->check(game().battle.dummy().vx<-.5&&game().battle.dummy().vy>.5,"Left-facing swing also throws away and up"));
            server.runOnServer(s->place(id,13.5,96,16,96));
            c.getInput().pressKey(o->o.keySwapOffhand);
            server.waitFor(s->game().battle.dummy().state.percent==8,25);
            server.runOnServer(s->{
                var b=game().battle;check(b.dummy().vx>.5&&b.dummy().vy>.5,"Aerial swing uses the same upward launch");
                place(id,0,81,14,81);
            });
            // Missed swing has a punishable recovery; teleport is not an attack along its path.
            c.getInput().pressKey(o->o.keySwapOffhand);c.waitTicks(11);
            server.runOnServer(s->{
                var b=game().battle;var f=b.actors.get(id);
                check(b.dummy().state.percent==0&&f.state.readyAt>b.now(),"Whiffed swing leaves recovery");
                check(f.ender.prey(b.now())==null,"Whiff never marks");place(id,0,81,2,81);
            });
            int specials=server.computeOnServer(s->game().battle.actors.get(id).specials);
            c.getInput().holdMouse(1);c.waitTicks(35);c.getInput().releaseMouse(1);
            server.runOnServer(s->{
                var b=game().battle;var f=b.actors.get(id);
                check(Math.abs(f.x-4)<.2&&f.specials==specials+1,"Unmarked phase commits once per press");
                check(b.dummy().state.percent==0,"No swept teleport damage");
                place(id,0,81,14,81);f.ender.mark(b.now(),b.dummy().id);b.dummy().x=25;
                check(!b.enders.available(f,new AttackIntent(AttackKind.HEAVY,AttackDirection.FORWARD,0,false)),"Distant prey cannot be chased");
                place(id,13.5,81,15,81);check(b.requestSecondary(f,Input.EMPTY),"Start interruptible swing");
                f.state.receiveHit(b.now(),UUID.randomUUID(),-1,AttackKind.LIGHT);
            });c.waitTicks(7);
            server.runOnServer(s->{
                var b=game().battle;var f=b.actors.get(id);
                check(b.dummy().state.percent==0,"Hitting Enderman interrupts the windup");
                place(id,0,81,14,81);check(b.request(f,true,Input.EMPTY),"Start interrupted pursuit");
                f.state.receiveHit(b.now(),UUID.randomUUID(),-1,AttackKind.LIGHT);
            });c.waitTicks(6);
            server.runOnServer(s->{
                var b=game().battle;var f=b.actors.get(id);check(f.x<1,"Hit cancels departure");
                place(id,-18.5,76,14,81);check(!b.enders.clear(f,new EnderState.Point(-14.5,76)),"Terrain rejects embedded endpoint");
                check(b.request(f,true,Input.EMPTY),"Commit blocked phase");
            });c.waitTicks(7);
            server.runOnServer(s->{
                var b=game().battle;var f=b.actors.get(id);check(f.x< -18,"Blocked phase cannot enter solid island");
                place(id,0,81,14,81);f.ender.mark(b.now(),b.dummy().id);
            });c.waitTicks(3);
            server.runOnServer(s->check(game().battle.enders.markerCount()==1,"Mark display appears"));
            c.waitTicks(EnderState.MARK_TICKS);
            server.runOnServer(s->{
                var b=game().battle;var f=b.actors.get(id);check(f.ender.prey(b.now())==null&&b.enders.markerCount()==0,"Mark expires cleanly");
                f.ender.mark(b.now(),b.dummy().id);
            });c.waitTicks(2);
            server.runOnServer(s->{
                var b=game().battle;var f=b.actors.get(id);b.ringOut(b.dummy());
                check(f.ender.prey(b.now())==null&&b.enders.markerCount()==0,"Prey KO removes every mark");
                place(id,0,81,14,81);
            });
            // Native W + primary previews and performs a substantial upward escape.
            c.getInput().holdKey(o->o.keyUp);c.waitTicks(3);c.getInput().pressMouse(1);c.waitTicks(2);
            c.takeScreenshot("enderman-escape-tell");
            server.waitFor(s->game().battle.actors.get(id).y>90,20);
            c.getInput().releaseKey(o->o.keyUp);
            server.runOnServer(s->{
                var b=game().battle;var f=b.actors.get(id);
                check(!f.recovery.available()&&!f.recovery.recoveryAvailable(),"Grounded escape consumes air budget after arrival");
                check(!b.request(f,true,Input.EMPTY),"No pursuit after exhausting escape");
                place(id,0,81,14,81);
            });
            c.runOnClient(mc->mc.player.connection.sendCommand("smash challenge recovery"));
            server.waitFor(s->game().battle.challenge!=null,20);
            for(int round=0;round<3;round++) {
                final int complete=round+1;
                server.waitFor(s->game().battle.challenge.phase()==RecoveryChallenge.Phase.ATTEMPT,60);
                final int side=server.computeOnServer(s->game().battle.challenge.side());
                c.getInput().holdKey(o->side<0?o.keyRight:o.keyLeft);
                c.getInput().holdKeyFor(o->o.keyJump,6);c.waitTicks(3);c.getInput().holdKeyFor(o->o.keyJump,4);
                server.waitFor(s->game().battle.challenge.completed()>=complete,150);
                c.getInput().releaseKey(o->side<0?o.keyRight:o.keyLeft);
            }
            c.takeScreenshot("enderman-recovery");
            server.runOnServer(s->{
                var b=game().battle;check(b.challenge.attempts()==3,"Recovery succeeds on both sides");
                b.resetTraining();b.actors.get(id).ender.mark(b.now(),b.dummy().id);
            });c.waitTicks(2);
            var battle=server.computeOnServer(s->game().battle);
            c.runOnClient(mc->mc.player.connection.sendCommand("smash leave"));
            server.waitFor(s->game().battle==null,80);
            server.runOnServer(s->check(battle.enders.visuals.pieces()==0,"Leaving clears marks, portals and afterimages"));
            VanillaSmash.LOG.info("ENDERMAN_SWING_OK native immediate launch both facings aerial pursuit interrupts visuals recovery cleanup");
        }
    }
}
