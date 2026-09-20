package dev.hanks.vanilla;

import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.player.Input;

/** Real vanilla mouse holds/releases, including cancel, air physics and pending inputs. */
@SuppressWarnings("UnstableApiUsage")
public final class ChargeClientTest {
    private static VanillaSmash game() { return VanillaSmash.instance(); }
    private static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
    private static void reset(UUID id,double x,double y) {
        var b=game().battle;var f=b.actors.get(id);b.reset(f,x,y);b.reset(b.dummy(),-14,81);f.facing=1;
    }
    public static void run(ClientGameTestContext c) {
        var props=new Properties();props.setProperty("online-mode","false");props.setProperty("server-ip","127.0.0.1");
        props.setProperty("view-distance","6");props.setProperty("simulation-distance","5");props.setProperty("allow-flight","true");
        try(var server=c.worldBuilder().createServer(props);var connection=server.connect()) {
            connection.waitForChunksRender();c.getInput().resizeWindow(1280,720);
            c.runOnClient(mc->{mc.options.fov().set(70);mc.options.guiScale().set(2);mc.resizeGui();});
            server.waitFor(s->game().hub.available(connection.getServerPlayer()),240);
            var id=server.computeOnServer(s->connection.getServerPlayer().getUUID());
            for(var kind:FighterClass.values()) {
                server.runOnServer(s->{game().leave(connection.getServerPlayer());game().choose(connection.getServerPlayer(),kind,VanillaSmash.Mode.SANDBOX);});
                server.waitFor(s->game().battle!=null && game().match.phase()==MatchState.Phase.ACTIVE,300);c.waitTicks(20);
                server.runOnServer(s->reset(id,0,81));c.waitTicks(8);
                c.getInput().holdMouse(1);c.waitTicks(ChargeRules.fullTicks(kind)+8);
                server.runOnServer(s->{
                    var b=game().battle;var f=b.actors.get(id);
                    check(f.state.chargingSpecial() && f.owner.isUsingItem(),kind+" keeps holding through full charge using native use state");
                    check(f.state.activeUntil==0 && f.state.motionUntil==0 && b.dummy().state.percent==0 && b.objects.arrows.isEmpty() && b.objects.bells.isEmpty(),kind+" does not fire on press or full charge");
                    check(f.state.chargeFullShown,kind+" shows the full-charge cue");
                });
                c.takeScreenshot("charge-"+kind.name().toLowerCase());
                c.getInput().releaseMouse(1);
                if(kind==FighterClass.ALEX) {
                    server.waitFor(s->game().battle.actors.get(id).state.activeUntil>game().ticks,12);
                    server.runOnServer(s->{var f=game().battle.actors.get(id);check(f.state.activeUntil>=f.state.motionUntil,"Charged dash contact covers its longer motion");});
                }
                c.waitTicks(8);
                server.runOnServer(s->{
                    var f=game().battle.actors.get(id);
                    check(f.state.chargeReleased && !f.state.chargingSpecial(),kind+" native release commits the attack");
                    check(f.state.releasedCharge==ChargeRules.fullTicks(kind),kind+" release preserves bounded full charge");
                    if(kind==FighterClass.STEVE)check(f.state.move.damage()==23,"Charged pickaxe power");
                    if(kind==FighterClass.ALEX)check(f.state.move.damage()==17 && f.x>4,"Charged dash advances farther");
                    if(kind==FighterClass.ZOMBIE)check(f.state.move.damage()==26,"Charged shockwave power");
                    if(kind==FighterClass.VILLAGER)check(game().battle.objects.bells.get(id).charge==16,"Thrown bell retains charge");
                    if(kind==FighterClass.SKELETON)check(game().battle.objects.hasArrow(f),"Bow release fires a native arrow");
                });

                server.runOnServer(s->reset(id,0,81));c.waitTicks(6);c.getInput().pressMouse(1);c.waitTicks(12);
                server.runOnServer(s->{
                    var f=game().battle.actors.get(id);
                    check(f.state.chargeReleased && !f.state.chargingSpecial(),kind+" quick tap cannot get stuck holding");
                    if(kind!=FighterClass.SKELETON)check(ChargeRules.power(kind,f.state.releasedCharge)==0,kind+" tap keeps base power");
                });

                server.runOnServer(s->reset(id,0,81));c.waitTicks(6);c.getInput().holdMouse(1);c.waitTicks(7);
                server.runOnServer(s->{var b=game().battle;b.hit(b.dummy(),b.actors.get(id),1,FighterMoves.light(FighterClass.ALEX,AttackDirection.FORWARD,false));});
                c.getInput().releaseMouse(1);c.waitTicks(4);
                server.runOnServer(s->check(!game().battle.actors.get(id).state.chargingSpecial() && game().battle.actors.get(id).state.impactAt<0,kind+" hit interrupts charge and release cannot revive it"));

                server.runOnServer(s->reset(id,0,81));c.waitTicks(6);c.getInput().holdMouse(1);c.waitTicks(7);
                c.getInput().holdKey(o->o.keyShift);c.waitTicks(8);c.getInput().releaseMouse(1);
                server.runOnServer(s->{var f=game().battle.actors.get(id);check(!f.state.chargingSpecial() && f.state.blocking(game().ticks),kind+" shield cancels charging");});
                c.getInput().releaseKey(o->o.keyShift);c.waitTicks(6);

                server.runOnServer(s->reset(id,25,96));c.getInput().holdMouse(1);c.waitTicks(6);
                server.runOnServer(s->{var f=game().battle.actors.get(id);check(f.state.chargingSpecial() && f.y<95 && !f.grounded,kind+" air charge preserves gravity");});
                c.getInput().releaseMouse(1);c.waitTicks(5);
                server.runOnServer(s->{var f=game().battle.actors.get(id);check(f.state.chargeReleased,kind+" can release in midair");});

                server.runOnServer(s->{reset(id,25,96);game().battle.actors.get(id).recovery.jump(false);});
                c.getInput().holdMouse(1);c.waitTicks(4);c.getInput().holdKeyFor(o->o.keyJump,3);c.waitTicks(3);
                server.runOnServer(s->{var f=game().battle.actors.get(id);check(!f.state.chargingSpecial()&&f.state.move.kind()==AttackKind.RECOVERY&&f.recovery.helpless(),kind+" recovery replaces a held charge using the existing air budget: charging="+f.state.chargingSpecial()+" move="+f.state.move+" air="+f.recovery.available()+" recovery="+f.recovery.recoveryAvailable()+" jumpPending="+f.jump.pending(game().ticks)+" input="+f.owner.getLastClientInput()+" y="+f.y);});
                c.getInput().releaseMouse(1);c.waitTicks(3);

                server.runOnServer(s->reset(id,0,81));c.waitTicks(6);c.getInput().holdMouse(1);c.waitTicks(5);
                c.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_2);c.waitTicks(3);c.getInput().releaseMouse(1);
                server.runOnServer(s->check(!game().battle.actors.get(id).state.chargingSpecial() && game().battle.actors.get(id).state.impactAt<0,kind+" changing hotbar slot cancels without firing"));
                c.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_1);c.waitTicks(3);

                server.runOnServer(s->{
                    reset(id,0,81);var f=game().battle.actors.get(id);f.state.readyAt=game().ticks+3;
                    check(game().battle.request(f,true,Input.EMPTY),kind+" primary buffers near recovery end");
                    game().battle.releaseSpecial(f);
                });c.waitTicks(12);
                server.runOnServer(s->{var f=game().battle.actors.get(id);check(f.state.chargeReleased && !f.state.chargingSpecial(),kind+" release survives the input buffer");});
                VanillaSmash.LOG.info("PRIMARY_CHARGE_NATIVE_OK {}",kind);
            }
            // Charge a planted bell, then check its actual ring damage after release.
            server.runOnServer(s->{
                reset(id,0,81);var b=game().battle;var f=b.actors.get(id);b.objects.bell(f);
                var bell=b.objects.bells.get(id);bell.pos=new net.minecraft.world.phys.Vec3(3,81.3,.5);bell.entity.setPos(bell.pos);
                bell.velocity=net.minecraft.world.phys.Vec3.ZERO;bell.armedAt=b.now();bell.ringAt=b.now()+200;b.reset(b.dummy(),3,81);
            });c.waitTicks(6);c.getInput().holdMouse(1);c.waitTicks(20);
            server.runOnServer(s->check(game().battle.dummy().state.percent==0,"Holding a remote ring never detonates early"));
            c.getInput().releaseMouse(1);
            server.waitFor(s->game().battle.dummy().state.percent==18,20);
            server.runOnServer(s->game().leave(connection.getServerPlayer()));
            c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.LOBBY)&&mc.getCameraEntity()==mc.player,200);
        }
        VanillaSmash.LOG.info("CHARGE_CLIENT_TEST_PASSED");
    }
}
