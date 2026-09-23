package dev.hanks.vanilla;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;

/** Real vanilla keys, wheel, clicks, camera and model rendering; no picker client code. */
@SuppressWarnings("UnstableApiUsage")
public final class ShowcaseClientTest {
    private static VanillaSmash game() { return VanillaSmash.instance(); }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void run(ClientGameTestContext c) {
        var props = new Properties(); props.setProperty("online-mode", "false"); props.setProperty("server-ip", "127.0.0.1");
        props.setProperty("view-distance", "6"); props.setProperty("simulation-distance", "5"); props.setProperty("allow-flight", "true");
        try (var server = c.worldBuilder().createServer(props)) {
            try (var connection = server.connect()) {
                connection.waitForChunksRender(); c.getInput().resizeWindow(1280, 720);
                c.runOnClient(mc -> { mc.options.fov().set(70); mc.options.guiScale().set(2); mc.resizeGui(); });
                c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.LOBBY), 300);
                c.waitTicks(160);
                c.getInput().holdMouse(1);
                MatchmakingClientTest.menuReady(c); MatchmakingClientTest.click(c,"Free-for-all");
                stageReady(c); c.waitTicks(40);
                server.runOnServer(s -> check(game().stage.active(connection.getServerPlayer()) && game().match.queue().isEmpty(), "Holding the opening click cannot confirm a class"));
                c.getInput().releaseMouse(1); c.waitTicks(12);
                c.takeScreenshot("stage-01-steve");
                // The old detached view could send precisely this self-target attack and get kicked.
                c.runOnClient(mc -> mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundAttackPacket(mc.player.getId())));
                c.waitTicks(5);
                server.runOnServer(s -> check(game().stage.active(connection.getServerPlayer()),"Self-target camera clicks cannot disconnect the player"));
                var previous = new AtomicInteger();
                server.runOnServer(s -> {
                    var session = game().stage.session(connection.getServerPlayer().getUUID());
                    previous.set(session.preview.getId());
                    check(session.selected == FighterClass.STEVE && session.entities.size() == 22, "Exactly five roster models and one selected preview");
                });
                for (var kind : FighterClass.values()) {
                    var targetId=new AtomicInteger();
                    server.runOnServer(s -> targetId.set(game().stage.session(connection.getServerPlayer().getUUID()).targets.entrySet().stream()
                            .filter(e -> e.getValue()==kind && s.getLevel(MvpWorlds.SHOWCASE).getEntity(e.getKey()).getType()==EntityTypes.INTERACTION)
                            .findFirst().orElseThrow().getKey()));
                    aim(c,targetId.get()); c.getInput().pressMouse(0);
                    server.waitFor(s -> game().stage.session(connection.getServerPlayer().getUUID()).selected == kind);
                    var model = new AtomicInteger();
                    server.runOnServer(s -> {
                        var session = game().stage.session(connection.getServerPlayer().getUUID());
                        check(game().match.queue().isEmpty() && !game().network.selected(connection.getServerPlayer().getUUID()), "Preview never publishes a matchmaking ticket");
                        check(session.entities.size() == 22, "Changing fighters does not leak models");
                        model.set(session.preview.getId());
                    });
                    c.waitFor(mc -> mc.level.getEntity(model.get()) != null);
                    c.runOnClient(mc -> {
                        var entity = mc.level.getEntity(model.get());
                        check(entity.getType() == switch (kind) {
                            case STEVE, ALEX -> EntityTypes.MANNEQUIN;
                            case ZOMBIE -> EntityTypes.ZOMBIE;
                            case SKELETON -> EntityTypes.SKELETON;
                            case VILLAGER -> EntityTypes.VILLAGER;
                        }, "Selected model is a vanilla " + kind);
                        check(((LivingEntity)entity).getScale() > 2.5, "Native scale makes the selected character prominent");
                    });
                    c.getInput().lookAt(180,8); c.waitTicks(12); c.takeScreenshot("stage-02-" + kind.label.toLowerCase());
                }
                c.waitFor(mc -> mc.level.getEntity(previous.get()) == null);
                // Both ends of the five-character ring, using native wheel input.
                c.getInput().scroll(-1); server.waitFor(s -> game().stage.session(connection.getServerPlayer().getUUID()).selected == FighterClass.STEVE);
                c.waitTicks(3); c.getInput().scroll(1); server.waitFor(s -> game().stage.session(connection.getServerPlayer().getUUID()).selected == FighterClass.VILLAGER);
                c.getInput().holdKeyFor(o -> o.keyRight, 15);
                c.getInput().holdKeyFor(o -> o.keyJump, 4);
                server.runOnServer(s -> {
                    var p = connection.getServerPlayer(); var session = game().stage.session(p.getUUID());
                    check(Math.abs(p.getX() - session.origin()) < .1 && Math.abs(p.getY() - 104.5) < .1, "Showcase camera body stays anchored");
                });
                c.getInput().lookAt(120, -20); c.waitTicks(5);
                c.runOnClient(mc -> check(Math.abs(net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot() - 120)) < .1, "Mouse aims the native first-person selection camera"));
                c.getInput().lookAt(180,8);
                // Minecraft's native inventory is still protected while previewing.
                c.getInput().pressKey(o -> o.keyInventory); c.waitTicks(3);
                c.runOnClient(mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 36, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player));
                c.waitTicks(3);
                server.runOnServer(s -> check(connection.getServerPlayer().getInventory().getItem(0).is(Items.STICK)
                        && connection.getServerPlayer().containerMenu.getCarried().isEmpty(), "Preview input props cannot be removed"));
                c.getInput().pressKey(InputConstants.KEY_ESCAPE);
                c.waitFor(mc -> mc.gui.screen() == null);
                // Framing at a second common aspect ratio and a wider FOV.
                c.getInput().resizeWindow(1024, 768); c.waitTicks(8); c.takeScreenshot("stage-03-four-three");
                c.getInput().resizeWindow(1280, 720);
                c.runOnClient(mc -> mc.options.fov().set(90)); c.waitTicks(8); c.takeScreenshot("stage-04-wide-fov");
                c.runOnClient(mc -> mc.options.fov().set(70));
                c.getInput().pressKey(o -> o.keyDrop);
                lobbyReady(c);
                server.runOnServer(s -> {
                    var p = connection.getServerPlayer();
                    check(!game().stage.active(p) && game().match.queue().isEmpty(), "Q cancels without queuing");
                    check(!p.isInvisible() && !p.isNoGravity() && p.getAttributeValue(Attributes.MOVEMENT_SPEED) == .1, "Cancel restores ordinary lobby movement and visibility");
                    check(s.getLevel(MvpWorlds.SHOWCASE).getAllEntities().iterator().hasNext() == false, "Cancel removes every camera, preview and label");
                });
                c.runOnClient(mc -> mc.player.connection.sendCommand("smash ffa")); stageReady(c);
                c.getInput().pressKey(InputConstants.KEY_2); c.waitTicks(22);
                var readyId=new AtomicInteger(); server.runOnServer(s -> readyId.set(game().stage.session(connection.getServerPlayer().getUUID()).readyTarget));
                aim(c,readyId.get()); c.getInput().pressMouse(0); lobbyReady(c);
                server.waitFor(s -> game().match.queue().size() == 1);
                server.runOnServer(s -> check(game().choices.get(connection.getServerPlayer().getUUID()) == FighterClass.ALEX, "Confirm queues the previewed fighter"));
                c.runOnClient(mc -> mc.player.connection.sendCommand("smash unqueue"));
                server.waitFor(s -> game().match.queue().isEmpty());
                c.runOnClient(mc -> mc.player.connection.sendCommand("smash practice")); stageReady(c);
                c.getInput().pressKey(InputConstants.KEY_3); c.waitTicks(22); c.getInput().pressMouse(1);
                c.waitFor(mc -> MvpWorlds.battle(mc.level) && mc.getCameraEntity() != mc.player, 300);
                server.runOnServer(s -> check(game().actor(connection.getServerPlayer()).kind == FighterClass.ZOMBIE && !game().stage.active(connection.getServerPlayer()), "Practice transfers chosen class and replaces the camera"));
                c.runOnClient(mc -> mc.player.connection.sendCommand("smash leave")); lobbyReady(c);
                c.runOnClient(mc -> mc.player.connection.sendCommand("smash ffa")); stageReady(c);
            }
            server.waitFor(s -> !s.getLevel(MvpWorlds.SHOWCASE).getAllEntities().iterator().hasNext());
            try (var connection = server.connect()) {
                connection.waitForChunksRender(); lobbyReady(c);
                server.runOnServer(s -> check(!game().stage.active(connection.getServerPlayer()) && game().match.queue().isEmpty(), "Disconnect while browsing leaves no stale session"));
                c.runOnClient(mc -> mc.player.connection.sendCommand("smash ffa")); stageReady(c);
                server.runOnServer(s -> check(game().stage.session(connection.getServerPlayer().getUUID()).room == 0, "Released stage is reused"));
                c.runOnClient(mc -> mc.player.connection.sendCommand("smash leave")); lobbyReady(c);
            }
        }
        VanillaSmash.LOG.info("SHOWCASE_NATIVE_CLIENT_TEST_PASSED");
    }
    private static void aim(ClientGameTestContext c,int entityId) {
        c.waitFor(mc -> mc.level.getEntity(entityId)!=null);
        var angles=c.computeOnClient(mc -> {
            var delta=mc.level.getEntity(entityId).getBoundingBox().getCenter().subtract(mc.player.getEyePosition());
            return new float[]{(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)))};
        });
        c.getInput().lookAt(angles[0],angles[1]); c.waitTicks(3);
        c.waitFor(mc -> mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit && hit.getEntity().getId()==entityId,100);
    }
    private static void stageReady(ClientGameTestContext c) {
        c.waitFor(mc -> mc.level != null && mc.level.dimension().equals(MvpWorlds.SHOWCASE) && mc.getCameraEntity() == mc.player && mc.gui.screen() == null, 400);
    }
    private static void lobbyReady(ClientGameTestContext c) {
        c.waitFor(mc -> mc.level != null && mc.level.dimension().equals(MvpWorlds.LOBBY) && mc.getCameraEntity() == mc.player, 300);
    }
}
