package dev.hanks.vanilla;

import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Native clicks for each kit, plus precise server fixtures for interruption and object ownership. */
@SuppressWarnings("UnstableApiUsage")
public final class KitDepthClientTest {
    private static VanillaSmash game() { return VanillaSmash.instance(); }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void run(ClientGameTestContext c) {
        var props = new Properties(); props.setProperty("online-mode","false"); props.setProperty("server-ip","127.0.0.1");
        props.setProperty("view-distance","6"); props.setProperty("simulation-distance","5"); props.setProperty("allow-flight","true");
        try (var server = c.worldBuilder().createServer(props); var connection = server.connect()) {
            connection.waitForChunksRender(); c.getInput().resizeWindow(1280,720);
            c.runOnClient(mc -> { mc.options.fov().set(70); mc.options.guiScale().set(2); mc.resizeGui(); });
            server.waitFor(s -> game().hub.available(connection.getServerPlayer()),240);
            var id = server.computeOnServer(s -> connection.getServerPlayer().getUUID());
            for (var kind : FighterClass.values()) {
                server.runOnServer(s -> { game().leave(connection.getServerPlayer()); game().choose(connection.getServerPlayer(),kind,VanillaSmash.Mode.SANDBOX); });
                server.waitFor(s -> game().battle != null && game().match.phase() == MatchState.Phase.ACTIVE,300);
                c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.ARENA) && mc.getCameraEntity() != mc.player,300);
                c.waitTicks(20);
                server.runOnServer(s -> { var b = game().battle; b.reset(b.actors.get(id),0,81); b.reset(b.dummy(),1,81); b.actors.get(id).facing = 1; });
                c.waitTicks(12);
                switch (kind) {
                    case STEVE -> {
                        c.getInput().holdKey(o -> o.keyDown); c.waitTicks(1); c.getInput().pressMouse(0);
                        server.waitFor(s -> game().battle.actors.get(id).state.specialConfirm(game().ticks),15);
                        c.getInput().releaseKey(o -> o.keyDown); c.getInput().pressMouse(1);
                        server.waitFor(s -> game().battle.actors.get(id).specials == 1,12);
                        server.runOnServer(s -> {
                            var f = game().battle.actors.get(id);
                            check(f.state.move.startup() == 2,"Shovel contact accelerates the pickaxe follow-up");
                        });
                        server.waitFor(s -> game().battle.dummy().state.percent >= 20,20);
                        server.runOnServer(s -> { var b = game().battle; b.reset(b.actors.get(id),0,81); b.reset(b.dummy(),2.25,81); });
                        c.waitTicks(12); c.getInput().pressMouse(0);
                        server.waitFor(s -> game().battle.dummy().state.percent == 9,20);
                    }
                    case ALEX -> {
                        c.getInput().pressMouse(0);
                        server.waitFor(s -> game().battle.actors.get(id).state.specialConfirm(game().ticks),15);
                        c.getInput().pressMouse(1);
                        server.waitFor(s -> game().battle.dummy().state.percent >= 17,20);
                        server.runOnServer(s -> {
                            var b = game().battle; var f = b.actors.get(id);
                            check(f.state.readyAt - f.state.startedAt < 15,"Dash contact reduces recovery");
                            b.reset(f,0,81); b.reset(b.dummy(),2,81);
                            f.state.beginMove(b.now(),1,FighterMoves.special(FighterClass.ALEX,false,false));
                            f.state.motionType = 1; f.state.motionUntil = b.now()+5; f.vx = .92;
                            b.dummy().state.requestGuard(b.now(),true,true);
                            b.hit(f,b.dummy(),1,f.state.move);
                            check(f.state.motionUntil == 0 && f.vx == 0 && f.state.readyAt >= b.now()+10,"A shield stops and punishes the dash");
                            b.reset(f,0,95); f.recovery.burst(false);
                            f.state.beginMove(b.now(),1,FighterMoves.light(FighterClass.ALEX,AttackDirection.NEUTRAL,true));
                            f.state.impactAt = -1; f.state.activeUntil = b.now()+4; f.state.confirm(b.now(),f.state.move);
                            b.request(f,true,Input.EMPTY);
                            check(f.state.move.id() == 10 && !f.recovery.burstAvailable(),"A hit confirm cannot refund an aerial dash");
                        });
                    }
                    case ZOMBIE -> {
                        // Arm the native request, then place the light during its short armor window.
                        c.getInput().pressMouse(1);
                        server.waitFor(s -> game().battle.actors.get(id).state.armored(game().ticks,true),10);
                        server.runOnServer(s -> {
                            var b = game().battle; var f = b.actors.get(id); long impact = f.state.impactAt;
                            b.hit(b.dummy(),f,-1,FighterMoves.light(FighterClass.ALEX,AttackDirection.FORWARD,false));
                            check(f.state.percent == 5 && f.state.impactAt == impact && f.state.stunUntil == 0,"Grounded armor keeps slam winding while taking damage");
                            check(b.dummy().damageDealt == 5,"Armored hits still count toward damage dealt");
                        });
                        server.waitFor(s -> game().battle.dummy().state.percent == 18,20);
                        server.runOnServer(s -> {
                            var b = game().battle; var f = b.actors.get(id); b.reset(f,0,81);
                            f.state.beginMove(b.now()-2,1,FighterMoves.special(FighterClass.ZOMBIE,false,false));
                            b.hit(b.dummy(),f,-1,FighterMoves.arrow(20));
                            check(f.state.impactAt == -1 && f.state.stunUntil > b.now(),"An arrow interrupts the armored windup");
                        });
                    }
                    case SKELETON -> {
                        c.getInput().holdKey(o -> o.keyDown); c.waitTicks(1); c.getInput().pressMouse(0);
                        c.waitTicks(8); c.getInput().releaseKey(o -> o.keyDown);
                        server.runOnServer(s -> {
                            var b = game().battle; var f = b.actors.get(id);
                            check(f.x < -.7 && b.dummy().state.percent == 4,"Low sweep hits then steps away");
                            b.reset(f,0,81); b.reset(b.dummy(),-12,81);
                        });
                        c.waitTicks(12); c.getInput().holdMouse(1); c.waitTicks(23); c.getInput().releaseMouse(1);
                        server.waitFor(s -> game().battle.objects.hasArrow(game().battle.actors.get(id)),12);
                        server.runOnServer(s -> {
                            var arrow = game().battle.objects.arrows.get(id);
                            check(arrow.charge() == 20 && arrow.entity().isCritArrow(),"Full draw fires a native critical arrow");
                            check(arrow.entity().getDeltaMovement().y < 0,"Full draw still uses normal gravity");
                        });
                    }
                    case VILLAGER -> {
                        server.runOnServer(s -> game().battle.reset(game().battle.dummy(),-12,81));
                        c.getInput().pressMouse(1);
                        server.waitFor(s -> game().battle.objects.bellArmed(game().battle.actors.get(id)),60);
                        double bellX = server.computeOnServer(s -> {
                            var b = game().battle; var f = b.actors.get(id); var bell = b.objects.bells.get(id);
                            // Move only the fighter; resetting would intentionally remove its owned trap.
                            f.x = bell.pos.x-1; f.y = 81; f.vx = f.vy = 0; f.pose.reset(f.x,f.y); return bell.pos.x;
                        });
                        c.waitTicks(12); c.getInput().pressMouse(0);
                        server.waitFor(s -> game().battle.objects.bells.get(id).lastBattedAt != Long.MIN_VALUE,15);
                        c.waitTicks(4);
                        server.runOnServer(s -> {
                            var b = game().battle; var f = b.actors.get(id); var bell = b.objects.bells.get(id);
                            check(bell.pos.x > bellX+1 && bell.armedAt < 0 && b.objects.bells.size() == 1,"Own light bats the existing bell instead of deleting or duplicating it");
                            // Every active frame of this same swing must not launch the bell again.
                            bell.pos = new Vec3(f.pose.x+1, f.pose.y+.3,.5); bell.velocity = new Vec3(.13,.12,0);
                            b.objects.strikeBells(f,CombatGeometry.shape(f.state.move,1,f.pose.x,f.pose.y));
                            check(bell.velocity.x == .13,"One bat per attack");
                            var foe = b.dummy(); foe.state.move = FighterMoves.light(FighterClass.ZOMBIE,AttackDirection.FORWARD,false);
                            b.objects.strikeBells(foe,CombatGeometry.shape(foe.state.move,1,f.pose.x,f.pose.y));
                            check(!b.objects.hasBell(f) && f.state.bellReadyAt > b.now(),"Enemy strikes destroy the bell and enforce replacement cooldown");
                        });
                    }
                }
                // Hold an active visual for a clear native-client screenshot; functional timings were tested above.
                server.runOnServer(s -> {
                    var b = game().battle; var f = b.actors.get(id); b.reset(f,0,89); b.reset(b.dummy(),14,81);
                    check(b.request(f,false,Input.EMPTY),"Visual fixture starts a real forward attack");
                    f.state.impactAt = -1; f.state.activeStartedAt = b.now(); f.state.activeUntil = b.now()+24; f.state.readyAt = b.now()+24;
                    b.effects.strike(f);
                });
                c.waitTicks(6);
                c.runOnClient(mc -> {
                    var blocks = java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false)
                            .filter(e -> e instanceof Display.BlockDisplay).map(e -> ((Display.BlockDisplay)e).getBlockState()).toList();
                    var material = switch(kind) {
                        case STEVE -> Blocks.CONCRETE.lightBlue(); case ALEX -> Blocks.CONCRETE.orange();
                        case ZOMBIE -> Blocks.CONCRETE.lime(); case SKELETON -> Blocks.BONE_BLOCK; case VILLAGER -> Blocks.GOLD_BLOCK;
                    };
                    check(blocks.stream().filter(state -> state.is(material)).count() >= 12,"Distinct native attack material: "+kind);
                });
                c.takeScreenshot("kits-"+kind.name().toLowerCase()); c.waitTicks(25);
                c.runOnClient(mc -> check(java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false)
                        .noneMatch(e -> e instanceof Display.BlockDisplay),"Strike display entities clean up after "+kind));
                VanillaSmash.LOG.info("KIT_DEPTH_NATIVE_OK {}",kind);
            }
        }
    }
}
