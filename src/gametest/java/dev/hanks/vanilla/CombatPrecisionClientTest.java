package dev.hanks.vanilla;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentLinkedDeque;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Input;

/** Uses stock client movement/interpolation and mouse packets against a dedicated server. */
@SuppressWarnings("UnstableApiUsage")
public final class CombatPrecisionClientTest {
    private record Frame(double x, double y, double shownX, double shownY) {}
    private static VanillaSmash game() { return VanillaSmash.instance(); }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void run(ClientGameTestContext c) {
        var capture = new AtomicInteger(-1); var gaps = new ArrayList<Double>(); var errors = new ArrayList<Double>();
        var frames = new ConcurrentLinkedDeque<Frame>();
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (game().battle == null || capture.get() < 0) return;
            for (var f : game().battle.actors.values()) if (f.body.getId() == capture.get()) {
                frames.addLast(new Frame(f.x, f.y, f.pose.x, f.pose.y));
                while (frames.size() > 120) frames.removeFirst();
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.level == null) return;
            var e = mc.level.getEntity(capture.get());
            if (e != null && e.getInterpolation() != null) {
                var destination = e.getInterpolation().position();
                double gap = e.position().distanceTo(destination); gaps.add(gap);
                if (gap > .2) {
                    double error = frames.stream().filter(f -> Math.hypot(f.x - destination.x, f.y - destination.y) < .003)
                            .mapToDouble(f -> Math.hypot(f.shownX - e.getX(), f.shownY - e.getY())).min().orElse(Double.NaN);
                    if (Double.isFinite(error)) errors.add(error);
                }
            }
        });
        var props = new Properties(); props.setProperty("online-mode", "false"); props.setProperty("server-ip", "127.0.0.1");
        props.setProperty("view-distance", "6"); props.setProperty("simulation-distance", "5"); props.setProperty("allow-flight", "true");
        try (var server = c.worldBuilder().createServer(props); var connection = server.connect()) {
            connection.waitForChunksRender(); c.getInput().resizeWindow(1280, 720);
            c.runOnClient(mc -> { mc.options.fov().set(70); mc.options.guiScale().set(2); mc.resizeGui(); });
            server.waitFor(s -> game().hub.available(connection.getServerPlayer()), 240);
            server.runOnServer(s -> game().choose(connection.getServerPlayer(), FighterClass.ALEX, VanillaSmash.Mode.SANDBOX));
            server.waitFor(s -> game().battle != null && game().match.phase() == MatchState.Phase.ACTIVE, 300);
            c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.ARENA) && mc.getCameraEntity() != mc.player, 300);
            var id = server.computeOnServer(s -> connection.getServerPlayer().getUUID());
            server.runOnServer(s -> {
                var f = game().battle.actors.get(id); game().battle.reset(f, -10, 81); capture.set(f.body.getId());
            });
            c.waitTicks(20); c.runOnClient(mc -> { gaps.clear(); errors.clear(); });
            c.getInput().holdKeyFor(o -> o.keyRight, 26); c.waitTicks(10);
            c.runOnClient(mc -> {
                VanillaSmash.LOG.info("COMBAT_NATIVE_INTERPOLATION samples={} maxTargetGap={}", gaps.size(), gaps.stream().mapToDouble(Double::doubleValue).max().orElse(0));
                double mean = errors.stream().mapToDouble(Double::doubleValue).average().orElse(99);
                double max = errors.stream().mapToDouble(Double::doubleValue).max().orElse(99);
                VanillaSmash.LOG.info("COMBAT_NATIVE_POSE_ALIGNMENT samples={} meanError={} maxError={}", errors.size(), mean, max);
                check(errors.size() >= 15 && mean < .12 && max < .5, "Hit pose follows native interpolation instead of the leading destination");
                capture.set(-1);
            });
            for (var kind : FighterClass.values()) {
                server.runOnServer(s -> { game().leave(connection.getServerPlayer()); game().choose(connection.getServerPlayer(), kind, VanillaSmash.Mode.SANDBOX); });
                server.waitFor(s -> game().battle != null && game().match.phase() == MatchState.Phase.ACTIVE, 300);
                c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.ARENA) && mc.getCameraEntity() != mc.player, 300);
                c.waitTicks(20);
                server.runOnServer(s -> {
                    var b = game().battle; var f = b.actors.get(id); b.reset(f, 0, 81); b.reset(b.dummy(), .9, 81); f.facing = 1;
                });
                c.waitTicks(10);
                int lights = server.computeOnServer(s -> game().battle.actors.get(id).lights);
                c.getInput().pressMouse(0);
                server.waitFor(s -> game().battle.actors.get(id).state.impactAt >= 0, 10);
                c.getInput().holdKey(o -> o.keyLeft);
                server.runOnServer(s -> {
                    var f = game().battle.actors.get(id);
                    check(f.state.attackDirection == 1 && f.facing == 1, kind + " keeps its original facing while winding up");
                });
                server.waitFor(s -> game().battle.dummy().state.percent > 0, 20);
                c.getInput().releaseKey(o -> o.keyLeft);
                c.waitTicks(1);
                c.runOnClient(mc -> check(java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false)
                        .anyMatch(e -> e.getType() == EntityTypes.BLOCK_DISPLAY), "Native client received the strike trail"));
                c.takeScreenshot("combat-02-" + kind.name().toLowerCase() + "-forward");
                c.waitTicks(18);
                server.runOnServer(s -> {
                    var f = game().battle.actors.get(id);
                    check(f.lights == lights + 1, "One click starts one attack");
                    check(game().battle.dummy().state.percent == FighterMoves.light(kind, AttackDirection.FORWARD, false).damage(), "One hit per strike");
                    game().battle.reset(f, 6, 81); game().battle.reset(game().battle.dummy(), 6, 85);
                });
                c.waitTicks(12); c.getInput().holdKey(o -> o.keyUp); c.waitTicks(2); c.getInput().pressMouse(0);
                c.waitTicks(20);
                server.runOnServer(s -> check(game().battle.dummy().state.percent == 0, "Grounded up light cannot reach the platform four blocks above"));
                c.getInput().holdKey(o -> o.keyJump); c.waitTicks(2); c.getInput().pressMouse(0);
                server.waitFor(s -> game().battle.dummy().state.percent > 0, 20);
                c.getInput().releaseKey(o -> o.keyUp); c.getInput().releaseKey(o -> o.keyJump); c.waitTicks(1); c.takeScreenshot("combat-03-" + kind.name().toLowerCase() + "-overhead");
                c.waitTicks(20);
                server.runOnServer(s -> {
                    var f = game().battle.actors.get(id);
                    check(f.state.move.aim() == AttackDirection.UP, "Native W-click is overhead");
                    game().battle.reset(f, -4, 81); game().battle.reset(game().battle.dummy(), 1, 81); f.facing = 1;
                });
                c.waitTicks(12); c.getInput().pressMouse(0); c.waitTicks(18);
                server.runOnServer(s -> check(game().battle.dummy().state.percent == 0, "Out-of-range swing really misses"));
                if (kind == FighterClass.ZOMBIE) {
                    server.runOnServer(s -> {
                        var b = game().battle; var f = b.actors.get(id); b.reset(f, 8, 96); b.reset(b.dummy(), 9.5, 85);
                    });
                    c.getInput().pressMouse(1);
                    server.waitFor(s -> game().battle.actors.get(id).state.motionType == 4, 15);
                    server.waitFor(s -> game().battle.dummy().state.percent > 0, 30);
                    server.runOnServer(s -> {
                        var f = game().battle.actors.get(id);
                        check(Math.abs(f.pose.y - f.y) < .06 && f.grounded, "Aerial slam connects as the visible model reaches the platform");
                        check(game().battle.dummy().state.percent == 22, "Air slam keeps its damage");
                    });
                    c.waitTicks(1); c.takeScreenshot("combat-06-slam-landing"); c.waitTicks(20);
                }
            }
            // A substantial hit briefly holds participants, preserving the full launch afterwards.
            server.runOnServer(s -> { game().leave(connection.getServerPlayer()); game().choose(connection.getServerPlayer(), FighterClass.STEVE, VanillaSmash.Mode.SANDBOX); });
            server.waitFor(s -> game().battle != null && game().match.phase() == MatchState.Phase.ACTIVE, 300);
            c.waitTicks(25);
            server.runOnServer(s -> {
                var b = game().battle; var f = b.actors.get(id); b.reset(f, 0, 81); b.reset(b.dummy(), 2.3, 81); f.facing = 1;
            });
            c.waitTicks(12); c.getInput().pressMouse(1);
            server.waitFor(s -> game().battle.dummy().state.paused(game().ticks), 20);
            server.runOnServer(s -> {
                var b = game().battle; var f = b.actors.get(id);
                check(f.state.paused(game().ticks), "Both melee participants receive the impact hold");
                check(b.dummy().state.percent == 18, "Pickaxe sweet spot still rewards spacing");
            });
            c.waitTicks(1); c.takeScreenshot("combat-04-heavy-contact"); c.waitTicks(20);
            // Shield input and simultaneous strikes retain their existing counterplay.
            server.runOnServer(s -> {
                var b = game().battle; var f = b.actors.get(id); b.reset(f, 0, 81); b.reset(b.dummy(), -1, 81); b.dummy().facing = 1; f.facing = -1;
            });
            c.waitTicks(12); c.getInput().holdKey(o -> o.keyShift); c.waitTicks(4);
            server.runOnServer(s -> check(game().battle.request(game().battle.dummy(), true, Input.EMPTY), "Dummy winds up a heavy against guard"));
            c.waitTicks(8);
            server.runOnServer(s -> {
                var f = game().battle.actors.get(id); check(f.state.percent == 0 && f.state.guard < 70, "Heavy contacts guard, not health");
                check(f.body.isUsingItem(), "Native shield pose remains visible");
            });
            c.takeScreenshot("combat-05-shield"); c.getInput().releaseKey(o -> o.keyShift); c.waitTicks(20);
            server.runOnServer(s -> {
                var b = game().battle; var f = b.actors.get(id); b.reset(f, 0, 81); b.reset(b.dummy(), 1.4, 81); f.facing = 1; b.dummy().facing = -1;
                check(b.request(f, false, Input.EMPTY) && b.request(b.dummy(), false, Input.EMPTY), "Both sides can start a trade");
                f.state.impactAt = b.dummy().state.impactAt = game().ticks + 4;
            });
            server.waitFor(s -> game().battle.actors.get(id).state.percent > 0 && game().battle.dummy().state.percent > 0, 20);
            c.waitTicks(20);
            c.runOnClient(mc -> check(java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false)
                    .noneMatch(e -> e.getType() == EntityTypes.BLOCK_DISPLAY), "Strike ribbons are cleaned up after recovery"));
            server.runOnServer(s -> game().leave(connection.getServerPlayer()));
            c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.LOBBY), 240);
        }
        VanillaSmash.LOG.info("COMBAT_PRECISION_CLIENT_TEST_PASSED");
    }
}
