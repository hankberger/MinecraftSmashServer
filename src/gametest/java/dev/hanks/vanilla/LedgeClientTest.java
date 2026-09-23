package dev.hanks.vanilla;

import java.util.Properties;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/** Native Space/A/D/S input across both ledges, plus combat and stock-boundary integration. */
@SuppressWarnings("UnstableApiUsage")
public final class LedgeClientTest {
    private static VanillaSmash game() { return VanillaSmash.instance(); }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void falling(Battle.Actor f, int side, double y) {
        var b = game().battle; b.reset(f, LedgeState.hangX(side), y); f.grounded = false;
        b.reset(b.dummy(), 0, 81); f.state.percent = 73; f.recovery.recover(false);
    }
    public static void run(ClientGameTestContext c) {
        var props = new Properties(); props.setProperty("online-mode", "false"); props.setProperty("server-ip", "127.0.0.1");
        props.setProperty("view-distance", "6"); props.setProperty("simulation-distance", "5"); props.setProperty("allow-flight", "true");
        try (var server = c.worldBuilder().createServer(props); var connection = server.connect()) {
            connection.waitForChunksRender(); c.getInput().resizeWindow(1280, 720);
            c.runOnClient(mc -> { mc.options.fov().set(70); mc.options.guiScale().set(2); mc.resizeGui(); });
            server.waitFor(s -> game().hub.available(connection.getServerPlayer()), 240);
            server.runOnServer(MapWorldTest::verifyArena);
            var id = server.computeOnServer(s -> connection.getServerPlayer().getUUID());
            for (var kind : FighterClass.values()) {
                server.runOnServer(s -> {
                    if (game().battle != null) game().leave(connection.getServerPlayer());
                    game().choose(connection.getServerPlayer(), kind, VanillaSmash.Mode.SANDBOX);
                });
                server.waitFor(s -> game().battle != null && game().match.phase() == MatchState.Phase.ACTIVE, 300);
                c.waitFor(mc -> MvpWorlds.battle(mc.level) && mc.getCameraEntity() != mc.player, 300); c.waitTicks(15);
                for (int side : new int[]{-1, 1}) {
                    int recoveries = server.computeOnServer(s -> {
                        var b = game().battle; var f = b.actors.get(id);
                        b.reset(f, LedgeState.hangX(side), 74); b.reset(b.dummy(), 0, 81);
                        f.recovery.jump(false); f.state.percent = 73; return f.recoveries;
                    });
                    c.getInput().holdKeyFor(o -> o.keyJump, 4);
                    server.waitFor(s -> game().battle.actors.get(id).ledge.attached(), 55);
                    server.runOnServer(s -> {
                        var b = game().battle; var f = b.actors.get(id);
                        check(f.recoveries == recoveries + 1, kind + " uses native Space recovery from below the stage");
                        check(f.ledge.side() == side && f.facing == -side && !f.grounded, kind + " faces inward while hanging");
                        check(f.state.percent == 73 && f.recovery.helpless() && !f.recovery.available(), "Catching preserves damage and spent air resources");
                        check(!f.state.hittable(game().ticks), "First grab grants brief protection");
                        b.hit(b.dummy(), f, side, FighterMoves.light(FighterClass.ZOMBIE, AttackDirection.FORWARD, false));
                        check(f.state.percent == 73 && f.ledge.attached(), "First-grab protection actually rejects a hit");
                    });
                    c.waitTicks(12);
                    server.runOnServer(s -> check(game().battle.actors.get(id).state.hittable(game().ticks), "Ledge protection expires while still hanging"));
                    if (kind == FighterClass.STEVE && side == -1 || kind == FighterClass.ZOMBIE && side == 1)
                        c.takeScreenshot("ledge-" + kind.name().toLowerCase() + "-" + side);
                    c.getInput().holdKey(o -> side < 0 ? o.keyRight : o.keyLeft);
                    server.waitFor(s -> { var f = game().battle.actors.get(id); return f.grounded && !f.ledge.attached(); }, 25);
                    c.getInput().releaseKey(o -> side < 0 ? o.keyRight : o.keyLeft);
                    server.waitFor(s -> !connection.getServerPlayer().getLastClientInput().left() && !connection.getServerPlayer().getLastClientInput().right(), 10);
                    server.runOnServer(s -> {
                        var f = game().battle.actors.get(id);
                        check(f.y == ArenaRules.DECK_Y && f.recovery.available() && f.recovery.recoveryAvailable(), "Climbing onto the deck restores the air budget");
                        check(f.ledge.grabs() == 0 && f.state.percent == 73, "Landing resets ledge visits without resetting damage");
                    });
                }
                VanillaSmash.LOG.info("LEDGE_NATIVE_RECOVERY_OK {}", kind);
            }

            // Native Space leaves a ledge even after recovery was spent, without refilling it.
            server.runOnServer(s -> falling(game().battle.actors.get(id), 1, 80));
            server.waitFor(s -> game().battle.actors.get(id).ledge.attached(), 10); c.waitTicks(4);
            c.getInput().holdKey(o -> o.keyJump); c.waitTicks(4);
            server.runOnServer(s -> {
                var f = game().battle.actors.get(id);
                check(!f.ledge.attached() && f.vy > 0 && f.y > LedgeState.HANG_Y, "Space jumps off the ledge");
                check(f.recovery.helpless() && !f.recovery.available(), "Ledge jump does not refill spent air options");
                check(f.state.protectedUntil == 0, "Leaving the ledge ends protection");
            });
            c.getInput().releaseKey(o -> o.keyJump);
            server.waitFor(s -> !connection.getServerPlayer().getLastClientInput().jump(), 10);

            // S releases; immediate recatches fail, the second grab is vulnerable, and a third requires landing.
            server.runOnServer(s -> falling(game().battle.actors.get(id), 1, 80));
            server.waitFor(s -> game().battle.actors.get(id).ledge.attached(), 10); c.waitTicks(4);
            c.getInput().holdKey(o -> o.keyDown);
            server.waitFor(s -> !game().battle.actors.get(id).ledge.attached(), 10); c.waitTicks(3);
            server.runOnServer(s -> check(game().battle.actors.get(id).y < LedgeState.HANG_Y, "S drops below the ledge"));
            c.getInput().releaseKey(o -> o.keyDown);
            server.waitFor(s -> !connection.getServerPlayer().getLastClientInput().backward(), 10);
            server.runOnServer(s -> {
                var f = game().battle.actors.get(id); f.x = LedgeState.hangX(1); f.y = 80; f.vx = f.vy = 0;
                f.recovery.cancelFastFall(); f.state.hitPauseUntil = game().ticks + LedgeState.REGRAB_TICKS;
            });
            server.waitFor(s -> game().battle.actors.get(id).ledge.attached(), 25);
            server.runOnServer(s -> {
                var f = game().battle.actors.get(id);
                check(f.ledge.grabs() == 2 && f.state.hittable(game().ticks), "Second grab has no invulnerability");
                check(!game().battle.requestSecondary(f, Input.EMPTY), "Cannot attack while hanging");
            });
            c.waitTicks(4); c.getInput().holdKey(o -> o.keyDown);
            server.waitFor(s -> !game().battle.actors.get(id).ledge.attached(), 10);
            c.getInput().releaseKey(o -> o.keyDown);
            server.waitFor(s -> !connection.getServerPlayer().getLastClientInput().backward(), 10);
            server.runOnServer(s -> {
                var f = game().battle.actors.get(id); f.x = LedgeState.hangX(1); f.y = 80; f.vx = f.vy = 0;
                f.recovery.cancelFastFall(); f.state.hitPauseUntil = game().ticks + LedgeState.REGRAB_TICKS;
            });
            c.waitTicks(LedgeState.REGRAB_TICKS + 5);
            server.runOnServer(s -> check(!game().battle.actors.get(id).ledge.attached(), "Cannot loop a third grab before landing"));

            // An expired hanger can be hit; hit stun and a held drop cannot snap a fighter to safety.
            server.runOnServer(s -> falling(game().battle.actors.get(id), -1, 80));
            server.waitFor(s -> game().battle.actors.get(id).ledge.attached(), 10); c.waitTicks(12);
            server.runOnServer(s -> {
                var b = game().battle; var f = b.actors.get(id);
                b.hit(b.dummy(), f, -1, FighterMoves.light(FighterClass.ZOMBIE, AttackDirection.FORWARD, false));
                check(!f.ledge.attached() && f.state.percent > 73 && f.state.stunUntil > game().ticks, "A hit interrupts hanging and applies launch/stun");
                falling(f, -1, 80); f.state.stunUntil = game().ticks + 12;
            });
            c.waitTicks(5);
            server.runOnServer(s -> check(!game().battle.actors.get(id).ledge.attached(), "Ledge cannot cancel hit stun"));
            c.getInput().holdKey(o -> o.keyDown);
            server.waitFor(s -> connection.getServerPlayer().getLastClientInput().backward(), 10);
            server.runOnServer(s -> falling(game().battle.actors.get(id), -1, 80)); c.waitTicks(5);
            server.runOnServer(s -> check(!game().battle.actors.get(id).ledge.attached(), "Holding S deliberately bypasses the ledge"));
            c.getInput().releaseKey(o -> o.keyDown);
            server.waitFor(s -> !connection.getServerPlayer().getLastClientInput().backward(), 10);

            // A new arrival displaces an existing hanger rather than losing a stock to an occupied edge.
            server.runOnServer(s -> falling(game().battle.actors.get(id), 1, 80));
            server.waitFor(s -> game().battle.actors.get(id).ledge.attached(), 10);
            server.runOnServer(s -> { var d = game().battle.dummy(); game().battle.reset(d, LedgeState.hangX(1), 80); });
            server.waitFor(s -> game().battle.dummy().ledge.attached(), 10);
            server.runOnServer(s -> check(!game().battle.actors.get(id).ledge.attached(), "New arrival displaces the old hanger"));

            server.runOnServer(s -> falling(game().battle.actors.get(id), -1, 80));
            server.waitFor(s -> game().battle.actors.get(id).ledge.attached(), 10); c.waitTicks(LedgeState.HANG_TICKS + 2);
            server.runOnServer(s -> check(!game().battle.actors.get(id).ledge.attached(), "Idle hanging automatically releases"));

            int falls = server.computeOnServer(s -> {
                var b = game().battle; var f = b.actors.get(id); b.reset(f, 32, 65);
                f.state.hitPauseUntil = game().ticks + 25;
                check(!game().battle.objects.outside(new Vec3(32, 64, .5)), "Offstage projectiles use the expanded bounds too");
                return f.state.falls;
            });
            c.waitTicks(8); c.takeScreenshot("ledge-expanded-offstage-frame");
            server.runOnServer(s -> {
                var f = game().battle.actors.get(id);
                check(f.state.falls == falls && !f.state.floating(game().ticks), "Beyond the old side and bottom limits remains playable");
                f.state.hitPauseUntil = 0; f.x = ArenaRules.BLAST_RIGHT + .1;
            });
            server.waitFor(s -> game().battle.actors.get(id).state.falls == falls + 1, 10);
            server.runOnServer(s -> {
                var b = game().battle; var f = b.actors.get(id);
                check(f.ledge.grabs() == 0 && !f.ledge.attached() && f.state.floating(game().ticks), "KO clears ledge state and starts the normal respawn");
                b.reset(f, 25, ArenaRules.BLAST_BOTTOM - .1);
            });
            server.waitFor(s -> game().battle.actors.get(id).state.falls == falls + 2, 10);
            VanillaSmash.LOG.info("LEDGE_CLIENT_TEST_PASSED");
        }
    }
}
