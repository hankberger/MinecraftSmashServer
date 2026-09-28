package dev.hanks.vanilla;

import java.util.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.Items;

/** Native E, movement, shielding and other screens, with the server pack and detached camera. */
@SuppressWarnings("UnstableApiUsage")
public final class InventoryGateClientTest {
    private static VanillaSmash game() { return VanillaSmash.instance(); }
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    private static void blocked(ClientGameTestContext c) {
        c.runOnClient(mc->check(mc.gameMode.isServerControlledInventory(),"Inventory key must take the native server-request branch, never create InventoryScreen"));
        c.getInput().pressKey(o->o.keyInventory);
        c.waitTicks(3);
        c.runOnClient(mc->check(mc.gui.screen()==null,"Inventory key leaves combat and cursor alone"));
    }
    public static void run(ClientGameTestContext c) {
        var props=new Properties();props.setProperty("online-mode","false");props.setProperty("server-ip","127.0.0.1");
        props.setProperty("view-distance","6");props.setProperty("allow-flight","true");
        try(var server=c.worldBuilder().createServer(props);var connection=server.connect()) {
            MatchmakingClientTest.click(c,"Proceed");server.waitFor(s->game().uiPack.ready(connection.getServerPlayer()),1200);
            connection.waitForChunksRender();c.waitTicks(35);
            c.getInput().pressKey(o->o.keyInventory);c.waitFor(mc->mc.gui.screen() instanceof InventoryScreen,60);
            c.getInput().pressKey(InputConstants.KEY_ESCAPE);c.waitTicks(3);
            server.runOnServer(s->game().choose(connection.getServerPlayer(),FighterClass.STEVE,VanillaSmash.Mode.PRACTICE));
            c.waitFor(mc->MvpWorlds.battle(mc.level)&&mc.getCameraEntity()!=mc.player&&mc.gameMode.isServerControlledInventory(),200);
            blocked(c); // Countdown is protected too.
            server.waitFor(s->game().match.phase()==MatchState.Phase.ACTIVE,200);
            var seat=server.computeOnServer(s->game().viewers.get(connection.getServerPlayer().getUUID()).rig().inventorySeat);
            c.runOnClient(mc->{
                check(mc.player.jumpableVehicle()==null,"No horse jump control or charge bar");
                check(mc.player.getVehicle().isInvisible()&&mc.player.isInvisible(),"Both controller and seat stay hidden");
            });
            server.runOnServer(s->{
                check(!connection.getServerPlayer().isPassenger(),"Server combat controller remains unmounted");
                game().battle.dummySpar=false;
                game().battle.reset(game().actor(connection.getServerPlayer()),-8,81);
                game().battle.reset(game().battle.dummy(),14,81);
            });
            for(int i=0;i<3;i++)blocked(c);
            c.getInput().holdKey(o->o.keyRight);c.waitTicks(8);
            server.runOnServer(s->check(game().actor(connection.getServerPlayer()).vx>MovementRules.RUN_SPEED*.9,"A/D remains full combat speed"));
            c.getInput().releaseKey(o->o.keyRight);c.waitTicks(5);
            c.getInput().holdKey(o->o.keyJump);c.waitTicks(5);
            server.runOnServer(s->check(game().actor(connection.getServerPlayer()).y>82,"Jump still reaches the combat actor"));
            c.getInput().releaseKey(o->o.keyJump);c.waitTicks(25);
            server.runOnServer(s->game().battle.reset(game().actor(connection.getServerPlayer()),0,81));c.waitTicks(4);
            c.getInput().holdKey(o->o.keyShift);c.waitTicks(8);
            server.runOnServer(s->check(game().actor(connection.getServerPlayer()).state.blocking(game().ticks),"Shift shields instead of dismounting"));
            blocked(c);c.getInput().releaseKey(o->o.keyShift);c.waitTicks(6);blocked(c);
            int lights=server.computeOnServer(s->game().actor(connection.getServerPlayer()).lights);
            c.getInput().pressMouse(0);c.waitTicks(4);
            server.runOnServer(s->check(game().actor(connection.getServerPlayer()).lights>lights,"Left click still attacks"));c.waitTicks(20);
            c.getInput().holdMouse(1);c.waitTicks(8);
            server.runOnServer(s->check(game().actor(connection.getServerPlayer()).state.chargingSpecial(),"Right-click charging still works"));
            c.getInput().pressKey(o->o.keyDrop);c.waitTicks(3);
            c.runOnClient(mc->check(mc.player.isInvisible()&&mc.player.getMainHandItem().is(Items.BOW),"Q keeps the hand hidden and input item intact"));
            c.getInput().releaseMouse(1);c.waitTicks(15);
            c.getInput().pressKey(o->o.keyChat);c.waitFor(mc->mc.gui.screen() instanceof ChatScreen,60);c.waitTicks(45);
            c.runOnClient(mc->check(mc.gui.screen() instanceof ChatScreen,"Chat stays open through camera refreshes"));
            c.getInput().pressKey(InputConstants.KEY_ESCAPE);c.waitTicks(3);
            c.getInput().pressKey(InputConstants.KEY_ESCAPE);c.waitFor(mc->mc.gui.screen() instanceof PauseScreen,60);c.waitTicks(45);
            c.runOnClient(mc->check(mc.gui.screen() instanceof PauseScreen,"Pause/settings remain usable"));
            c.getInput().pressKey(InputConstants.KEY_ESCAPE);c.waitTicks(3);blocked(c);
            c.takeScreenshot("inventory-blocked-combat");
            server.runOnServer(s->game().leave(connection.getServerPlayer()));
            c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.LOBBY)&&!mc.player.isPassenger(),200);c.waitTicks(5);
            server.runOnServer(s->check(seat.isRemoved(),"Private seat is cleaned up with the battle camera"));
            c.getInput().pressKey(o->o.keyInventory);c.waitFor(mc->mc.gui.screen() instanceof InventoryScreen,60);
            c.getInput().pressKey(InputConstants.KEY_ESCAPE);c.waitTicks(3);
            // Network arrivals park before begin(); reusing that rig must retain the gate.
            server.runOnServer(s->game().networkPark(connection.getServerPlayer()));
            c.waitFor(mc->MvpWorlds.battle(mc.level)&&mc.gameMode.isServerControlledInventory(),200);blocked(c);
            server.runOnServer(s->game().begin(List.of(connection.getServerPlayer()),VanillaSmash.Mode.SANDBOX));c.waitTicks(8);blocked(c);
            server.runOnServer(s->game().leave(connection.getServerPlayer()));
            c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.LOBBY)&&!mc.player.isPassenger(),200);
            server.runOnServer(s->game().hub.open(connection.getServerPlayer()));
            c.waitFor(mc->mc.gui.screen()!=null,100);
            c.runOnClient(mc->check(!mc.gameMode.isServerControlledInventory(),"Fighter selection is outside the combat gate"));
        }
        VanillaSmash.LOG.info("INVENTORY_GATE_NATIVE_CLIENT_TEST_PASSED");
    }
}
