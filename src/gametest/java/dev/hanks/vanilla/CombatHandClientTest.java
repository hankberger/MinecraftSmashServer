package dev.hanks.vanilla;

import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

/** Check the native empty-hand render condition before a cancelled drop can be resynchronized. */
@SuppressWarnings("UnstableApiUsage")
public final class CombatHandClientTest {
    private static VanillaSmash game() { return VanillaSmash.instance(); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void run(ClientGameTestContext c) {
        var props = new Properties();
        props.setProperty("online-mode", "false"); props.setProperty("server-ip", "127.0.0.1");
        props.setProperty("view-distance", "6"); props.setProperty("allow-flight", "true");
        try (var server = c.worldBuilder().createServer(props); var connection = server.connect()) {
            connection.waitForChunksRender(); c.getInput().resizeWindow(1280, 720);
            server.waitFor(s -> game().hub.available(connection.getServerPlayer()), 240);
            server.runOnServer(s -> game().begin(List.of(connection.getServerPlayer()), VanillaSmash.Mode.SANDBOX));
            c.waitFor(mc -> MvpWorlds.battle(mc.level) && mc.getCameraEntity() != mc.player, 200);
            c.waitTicks(25);
            server.runOnServer(s -> {
                var p = connection.getServerPlayer();
                // Both of these vanilla paths recompute the shared invisibility flag.
                p.onUpdateAbilities(); p.addEffect(new MobEffectInstance(MobEffects.SPEED, 2));
            });
            c.waitTicks(5);
            c.runOnClient(mc -> {
                var effect = mc.player.getEffect(MobEffects.INVISIBILITY);
                check(effect != null && !effect.isVisible() && !effect.showIcon(), "Controller invisibility has no particles or HUD icon");
            });
            for (boolean wholeStack : new boolean[] {false, true}) {
                c.runOnClient(mc -> {
                    check(mc.player.getMainHandItem().is(Items.BOW), "Hidden bow equipped before drop");
                    // This is the exact LocalPlayer method Q / Ctrl+Q calls. Inspect in the same
                    // frame, before any server response, so localhost latency cannot hide a flash.
                    check(mc.player.drop(wholeStack), "Native drop predicts removing the input item");
                    check(mc.player.getMainHandItem().isEmpty(), "Predicted drop leaves an empty hand");
                    check(mc.player.isInvisible(), "Empty hand must be invisible before the server cancels Q / Ctrl+Q");
                });
                c.waitFor(mc -> mc.player.getMainHandItem().is(Items.BOW), 40);
            }
            c.getInput().pressKey(o -> o.keyDrop); c.waitTicks(5);
            c.runOnClient(mc -> check(mc.player.isInvisible() && mc.player.getMainHandItem().is(Items.BOW), "Q keeps the controller hidden and restores combat input"));
            c.takeScreenshot("combat-q-hand-hidden");
            int bodyId = server.computeOnServer(s -> game().actor(connection.getServerPlayer()).body.getId());
            c.runOnClient(mc -> check(!mc.level.getEntity(bodyId).isInvisible(), "The fighter model remains visible"));
            server.runOnServer(s -> {
                game().battle.reset(game().actor(connection.getServerPlayer()), 0, 81);
                game().battle.reset(game().battle.dummy(), -14, 81);
            });
            c.waitTicks(8);
            c.getInput().holdMouse(1); c.waitTicks(8);
            server.runOnServer(s -> check(game().actor(connection.getServerPlayer()).state.chargingSpecial(), "Charging still works after Q"));
            c.getInput().pressKey(o -> o.keyDrop); c.waitTicks(5);
            c.runOnClient(mc -> check(mc.player.isInvisible(), "Q during a charge cannot expose the hand"));
            c.getInput().releaseMouse(1); c.waitTicks(5);
            c.getInput().pressMouse(0); c.waitTicks(4);
            c.runOnClient(mc -> check(mc.getCameraEntity() != mc.player && mc.player.isInvisible(), "Combat input keeps the detached view hidden"));
            server.runOnServer(s -> game().leave(connection.getServerPlayer()));
            c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.LOBBY) && mc.getCameraEntity() == mc.player, 200);
            c.waitTicks(5);
            c.runOnClient(mc -> check(!mc.player.isInvisible() && !mc.player.hasEffect(MobEffects.INVISIBILITY), "Returning to spawn restores normal player visibility"));
            server.runOnServer(s -> game().networkPark(connection.getServerPlayer()));
            c.waitFor(mc -> MvpWorlds.battle(mc.level) && mc.getCameraEntity() != mc.player, 200);
            c.waitTicks(10);
            c.runOnClient(mc -> check(mc.player.isInvisible() && mc.player.getMainHandItem().isEmpty(), "The empty-handed arena arrival view stays hidden too"));
            server.runOnServer(s -> game().begin(List.of(connection.getServerPlayer()), VanillaSmash.Mode.SANDBOX));
            c.waitTicks(10);
            c.runOnClient(mc -> check(mc.player.isInvisible() && mc.player.getMainHandItem().is(Items.BOW), "Starting from the waiting camera preserves invisibility"));
            server.runOnServer(s -> game().leave(connection.getServerPlayer()));
            c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.LOBBY), 200); c.waitTicks(5);
            c.runOnClient(mc -> check(!mc.player.isInvisible(), "Repeated entry/exit restores lobby visibility"));
            VanillaSmash.LOG.info("COMBAT_HAND_CLIENT_TEST_PASSED");
        }
    }
}
