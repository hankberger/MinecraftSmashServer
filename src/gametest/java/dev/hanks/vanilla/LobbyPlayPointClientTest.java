package dev.hanks.vanilla;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.*;

/** Native clicks must work with an empty hand, independent of the existing Play compass. */
@SuppressWarnings("UnstableApiUsage")
public final class LobbyPlayPointClientTest {
    private static VanillaSmash game() { return VanillaSmash.instance(); }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void run(ClientGameTestContext c) {
        var props = new Properties(); props.setProperty("online-mode","false"); props.setProperty("server-ip","127.0.0.1");
        props.setProperty("view-distance","6"); props.setProperty("simulation-distance","5"); props.setProperty("allow-flight","true");
        try (var server = c.worldBuilder().createServer(props); var connection = server.connect()) {
            connection.waitForChunksRender(); c.getInput().resizeWindow(1280,720);
            c.runOnClient(mc -> { mc.options.fov().set(70); mc.options.guiScale().set(2); mc.resizeGui(); });
            server.waitFor(s -> game().hub.available(connection.getServerPlayer()) && game().playPoint.fighter() != null,200);
            var id = new AtomicInteger(); server.runOnServer(s -> id.set(game().playPoint.fighter().getId()));
            c.waitFor(mc -> mc.level.getEntity(id.get()) != null); c.waitTicks(100);
            c.takeScreenshot("spawn-play-01-arrival");
            server.runOnServer(s -> {
                var p=connection.getServerPlayer();var level=s.getLevel(MvpWorlds.LOBBY);
                check(p.position().distanceToSqr(new Vec3(.5,101,-78.5))<.001 && p.getYRot()==0,"Arrival is between the statues facing the courtyard");
                check(game().playPoint.fighter().position().distanceToSqr(new Vec3(4.5,102,-71.5))<.001,"Play fighter stands at the front of the courtyard");
                // Simulate the deployed podium before preparing this existing garden.
                for(int x=3;x<=5;x++)for(int z=-100;z<=-98;z++) {
                    level.setBlock(new BlockPos(x,100,z),net.minecraft.world.level.block.Blocks.COPPER_BLOCK.waxed().oxidized().defaultBlockState(),2);
                    level.setBlock(new BlockPos(x,101,z),net.minecraft.world.level.block.Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(),2);
                }
                try { LobbyBuilder.ensureBuilt(s.getLevel(MvpWorlds.LOBBY)); } catch (java.io.IOException e) { throw new AssertionError(e); }
                check(game().playPoint.fighter().getId() == id.get(),"Rebuilding the podium does not duplicate its fighter");
                for(int x=3;x<=5;x++)for(int z=-100;z<=-98;z++) {
                    check(level.getBlockState(new BlockPos(x,101,z)).isAir(),"Old podium top is removed");
                    check(level.getBlockState(new BlockPos(x,100,z)).is(x==5?net.minecraft.world.level.block.Blocks.SMOOTH_QUARTZ:net.minecraft.world.level.block.Blocks.CHERRY_PLANKS),"Old podium ground is restored to its original pattern");
                }
                for (int x=-2; x<=2; x++) for (int z=-79; z<=-62; z++) {
                    check(!level.getBlockState(new BlockPos(x,100,z)).getCollisionShape(level,new BlockPos(x,100,z)).isEmpty(),"Court approach has solid ground");
                    check(level.getBlockState(new BlockPos(x,101,z)).isAir() && level.getBlockState(new BlockPos(x,102,z)).isAir(),"Arrival path stays clear");
                }
                check(!game().playPoint.click(p,new BlockPos(4,101,-99),net.minecraft.world.InteractionHand.MAIN_HAND),"Old podium no longer opens Play");
                try {LobbyBuilder.ensureBuilt(level);}catch(java.io.IOException e){throw new AssertionError(e);}
                check(level.getBlockState(LobbyPlayPoint.PODIUM).is(net.minecraft.world.level.block.Blocks.CHISELED_QUARTZ_BLOCK),"Repeated preparation retains the new podium");
            });
            c.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_2);
            c.getInput().holdKeyFor(o -> o.keyUp,18); c.getInput().holdKeyFor(o -> o.keyLeft,18);
            c.getInput().lookAt(0,-15); c.waitTicks(5);
            c.waitFor(mc -> mc.hitResult instanceof EntityHitResult hit && hit.getEntity().getId()==id.get(),100);
            c.takeScreenshot("spawn-play-02-podium");
            c.getInput().pressMouse(1); MatchmakingClientTest.menuReady(c);
            server.runOnServer(s -> {
                var p = connection.getServerPlayer();
                check(p.getMainHandItem().isEmpty(),"NPC works without the compass");
                check(game().playPoint.fighter().getMainHandItem().is(Items.IRON_SWORD),"Interaction cannot take the NPC equipment");
                check(game().network.selections.tickets().isEmpty() && !game().stage.active(p),"Click opens mode choice without queueing");
            });
            c.takeScreenshot("spawn-play-03-mode-menu");
            c.runOnClient(mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId,20,0,net.minecraft.world.inventory.ContainerInput.QUICK_MOVE,mc.player));
            c.waitTicks(5);
            server.runOnServer(s -> check(connection.getServerPlayer().getInventory().countItem(Items.IRON_SWORD)==0
                    && game().hub.menu.mainOpen(connection.getServerPlayer().getUUID()),"Shift-click cannot take menu icons or queue a mode"));
            MatchmakingClientTest.click(c,"1v1");
            c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.SHOWCASE) && mc.getCameraEntity()==mc.player && mc.gui.screen()==null,300);
            c.waitTicks(30);
            server.runOnServer(s -> check(game().playPoint.fighter()==null,"Idle landmark releases its entities when the lobby is empty"));
            c.getInput().pressKey(o -> o.keyDrop);
            c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.LOBBY) && mc.gui.screen()==null,300);
            server.waitFor(s -> game().playPoint.fighter()!=null,100);
            server.runOnServer(s -> {
                check(connection.getServerPlayer().position().distanceToSqr(new Vec3(.5,101,-78.5))<.001,"Returning from selection uses the new courtyard spawn");
                id.set(game().playPoint.fighter().getId()); connection.getServerPlayer().teleportTo(LobbyPlayPoint.POSITION.x,101,LobbyPlayPoint.POSITION.z-3);
            });
            c.waitFor(mc -> mc.level.getEntity(id.get()) != null && Math.abs(mc.player.getX()-4.5)<.1); c.waitTicks(10);
            c.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_2); c.getInput().lookAt(0,-15);
            c.waitFor(mc -> mc.hitResult instanceof EntityHitResult hit && hit.getEntity().getId()==id.get(),100);
            c.getInput().pressMouse(0); MatchmakingClientTest.menuReady(c); MatchmakingClientTest.click(c,"Back");
            c.waitTicks(10); c.getInput().lookAt(0,40);
            c.waitFor(mc -> mc.hitResult instanceof BlockHitResult hit && hit.getBlockPos().getY()==101,100);
            c.getInput().pressMouse(1); MatchmakingClientTest.menuReady(c); MatchmakingClientTest.click(c,"Back");
            server.runOnServer(s -> check(game().network.selections.tickets().isEmpty() && !game().stage.active(connection.getServerPlayer()),"NPC and podium clicks never bypass selection or ready-up"));
        }
        VanillaSmash.LOG.info("LOBBY_PLAY_POINT_NATIVE_CLIENT_TEST_PASSED");
    }
}
