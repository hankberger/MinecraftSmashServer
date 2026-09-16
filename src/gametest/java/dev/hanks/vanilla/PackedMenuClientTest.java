package dev.hanks.vanilla;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import com.mojang.blaze3d.platform.InputConstants;
import dev.hanks.network.PartyBook;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.dialog.DialogScreen;

/** Actual stock menu rendering and mouse input with a server-downloaded resource pack. */
@SuppressWarnings("UnstableApiUsage")
public final class PackedMenuClientTest {
    private static VanillaSmash game() { return VanillaSmash.instance(); }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static net.minecraft.client.gui.components.MultiLineTextWidget body(net.minecraft.client.gui.components.events.GuiEventListener node) {
        if(node instanceof net.minecraft.client.gui.components.MultiLineTextWidget text && text.getWidth()>=300)return text;
        if(node instanceof net.minecraft.client.gui.components.events.ContainerEventHandler group)for(var child:group.children()){var found=body(child);if(found!=null)return found;}
        return null;
    }
    private static void clickPoint(ClientGameTestContext c,int x,int y) {
        c.waitFor(mc->mc.gui.screen() instanceof DialogScreen<?>,300);
        var point=c.computeOnClient(mc->{
            var text=body(mc.gui.screen());check(text!=null,"Dialog body exists");
            double scale=mc.getWindow().getGuiScale();
            int padding=((net.minecraft.client.gui.components.FocusableTextWidget)text).getPadding();
            return new double[]{(text.getX()+(text.getWidth()-FighterMenu.CANVAS_WIDTH)/2.0+x)*scale,(text.getY()+padding+y)*scale};
        });
        c.getInput().setCursorPos(point[0],point[1]);c.getInput().pressMouse(0);c.waitTicks(8);
    }
    public static void click(ClientGameTestContext c,int action) {
        if(action<8)clickPoint(c,27+(action%4)*36,36+(action/4)*36);
        else if(action>=20 && action<=22)clickPoint(c,27+(action-20)*54,108);
        else if(action==31)clickPoint(c,135,153);
        else if(action==32)clickPoint(c,135,126);
        else throw new IllegalArgumentException("Unknown test action "+action);
    }
    private static String command(net.minecraft.network.chat.Component text,int action) {
        if(text.getStyle().getClickEvent() instanceof net.minecraft.network.chat.ClickEvent.RunCommand run && run.command().endsWith(" "+action))return run.command();
        for(var child:text.getSiblings()){var found=command(child,action);if(found!=null)return found;}
        return null;
    }
    public static void run(ClientGameTestContext c) {
        var props=new Properties(); props.setProperty("online-mode","false");props.setProperty("server-ip","127.0.0.1");props.setProperty("view-distance","6");props.setProperty("allow-flight","true");
        try(var server=c.worldBuilder().createServer(props); var connection=server.connect()) {
            c.getInput().resizeWindow(1280,720);
            c.runOnClient(mc->{mc.options.fov().set(70);mc.options.guiScale().set(2);mc.resizeGui();});
            // Upgrade a persisted garden, not just an empty new room.
            server.runOnServer(s->{
                var level=s.getLevel(MvpWorlds.SHOWCASE);ShowcaseBuilder.ensureBuilt(level,0);
                level.setBlock(new net.minecraft.core.BlockPos(0,93,0),net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK.defaultBlockState(),2);
                level.setBlock(new net.minecraft.core.BlockPos(-25,117,-9),net.minecraft.world.level.block.Blocks.POLISHED_ANDESITE.defaultBlockState(),2);
            });
            c.waitFor(mc->mc.gui.screen()!=null && mc.gui.screen().getClass().getSimpleName().equals("PackConfirmScreen"),300);
            c.waitTicks(5); c.takeScreenshot("packed-00-pack-prompt");
            MatchmakingClientTest.click(c,"Proceed");
            server.waitFor(s->game().uiPack.ready(connection.getServerPlayer()),1200);
            connection.waitForChunksRender(); c.waitTicks(40);
            c.runOnClient(mc->mc.player.connection.sendCommand("smash join"));
            c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.SHOWCASE) && mc.gui.screen() instanceof DialogScreen<?>,400);
            server.runOnServer(s->{
                var level=s.getLevel(MvpWorlds.SHOWCASE);
                check(level.getBlockState(new net.minecraft.core.BlockPos(0,93,0)).is(net.minecraft.world.level.block.Blocks.EMERALD_BLOCK),"Existing garden upgraded to the studio");
                for(var pos:List.of(new net.minecraft.core.BlockPos(-9,104,-2),new net.minecraft.core.BlockPos(-12,108,-5),new net.minecraft.core.BlockPos(-7,95,3),new net.minecraft.core.BlockPos(-25,117,-9)))
                    check(level.getBlockState(pos).isAir(),"Old shelves, trees and roots removed: "+pos);
                ShowcaseBuilder.ensureFighterBuilt(level,0);
                check(level.getBlockState(new net.minecraft.core.BlockPos(4,101,0)).is(net.minecraft.world.level.block.Blocks.BAMBOO_MOSAIC),"Repeated preparation retains the studio dais");
            });
            c.waitTicks(30); c.runOnClient(mc->{mc.gui.toastManager().clear();check(mc.getCameraEntity()!=mc.player,"Detached stage camera is active");}); c.takeScreenshot("packed-01-steve");
            for(int i=0;i<FighterClass.values().length;i++) {
                var kind=FighterClass.values()[i];click(c,i);
                server.runOnServer(s->check(game().stage.session(connection.getServerPlayer().getUUID()).selected==kind,"Mouse selected "+kind));
                c.takeScreenshot("packed-02-"+kind.name().toLowerCase(Locale.ROOT));
            }
            // Repeated native clicks across all four image corners must select the same fighter.
            for(int[] offset:new int[][]{{3,3},{32,3},{3,32},{32,32}}) {
                click(c,0);
                clickPoint(c,45+offset[0],18+offset[1]); c.takeScreenshot("edge-"+offset[0]+"-"+offset[1]);
                server.runOnServer(s->check(game().stage.session(connection.getServerPlayer().getUUID()).selected==FighterClass.ALEX,"Full portrait is clickable at "+Arrays.toString(offset)));
            }
            var stale=c.computeOnClient(mc->command(body(mc.gui.screen()).getMessage(),0));
            check(stale!=null,"Native body exposes a bound selection action");
            click(c,4);
            c.runOnClient(mc->mc.player.connection.sendCommand(stale));c.waitTicks(6);
            c.runOnClient(mc->mc.player.connection.sendCommand("smash fighter "+UUID.randomUUID()+" 0"));c.waitTicks(6);
            server.runOnServer(s->check(game().stage.session(connection.getServerPlayer().getUUID()).selected==FighterClass.VILLAGER,"Stale and forged tokens cannot change selection"));
            // Protected empty slots must not turn inventory shortcuts into fighter actions.
            for(var input:List.of(net.minecraft.world.inventory.ContainerInput.QUICK_MOVE,net.minecraft.world.inventory.ContainerInput.SWAP,net.minecraft.world.inventory.ContainerInput.THROW)) {
                c.runOnClient(mc->mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId,0,0,input,mc.player));c.waitTicks(6);
                server.runOnServer(s->check(game().stage.session(connection.getServerPlayer().getUUID()).selected==FighterClass.VILLAGER,"Inventory shortcut cannot select a fighter: "+input));
            }
            c.runOnClient(mc->{var menu=mc.player.containerMenu;mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundContainerClickPacket(menu.containerId,menu.getStateId()-1,(short)0,(byte)0,net.minecraft.world.inventory.ContainerInput.PICKUP,new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>(),net.minecraft.network.HashedStack.EMPTY));});
            c.waitTicks(8);
            server.runOnServer(s->check(game().stage.session(connection.getServerPlayer().getUUID()).selected==FighterClass.VILLAGER,"Stale content revision cannot act on a changed page"));
            click(c,31); c.waitTicks(30);
            server.runOnServer(s->{var p=connection.getServerPlayer();check(game().network.selected(p.getUUID()) && game().stage.active(p),"Queued fighter stays on the stage");});
            c.takeScreenshot("packed-03-queued");
            click(c,31);
            server.runOnServer(s->check(!game().network.selected(connection.getServerPlayer().getUUID()) && game().stage.active(connection.getServerPlayer()),"Cancel search keeps the stage"));
            c.getInput().resizeWindow(1024,768);c.waitTicks(10);c.takeScreenshot("packed-04-four-three");
            c.runOnClient(mc->{mc.options.guiScale().set(3);mc.resizeGui();});c.waitTicks(10);c.takeScreenshot("packed-05-scale-three");
            // The old Party region is intentionally inert; party setup now lives in the lobby.
            clickPoint(c,161,95);
            server.runOnServer(s->check(game().fighterMenu.active(connection.getServerPlayer()),"Removed Party button cannot intercept clicks"));
            c.runOnClient(mc->mc.player.connection.sendCommand(command(body(mc.gui.screen()).getMessage(),0)));
            c.getInput().pressKey(InputConstants.KEY_ESCAPE);c.waitTicks(15);
            server.runOnServer(s->check(!game().stage.active(connection.getServerPlayer()),"Escape immediately after selecting cannot strand a player"));
            var friend=new AtomicReference<MatchmakingClientTest.Peer>();
            server.runOnServer(s->friend.set(MatchmakingClientTest.Peer.join(s,"PackedFriend")));
            c.waitTicks(80);
            server.runOnServer(s->{
                var peer=friend.get().player(); game().hub.selectMode(peer,VanillaSmash.Mode.PRACTICE);
                check(!game().stage.active(peer),"Direct commands cannot bypass pack readiness");
                game().uiPack.response(peer,new ServerboundResourcePackPacket(UiPack.ID,ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
            });
            c.getInput().pressKey(InputConstants.KEY_9);c.waitTicks(5);c.getInput().pressMouse(1);
            c.waitTicks(20);c.takeScreenshot("packed-05-party-dialog");
            MatchmakingClientTest.click(c,"Create party"); MatchmakingClientTest.click(c,"Invite player"); MatchmakingClientTest.click(c,"PackedFriend");
            server.runOnServer(s->{
                game().hub.partyCommand(friend.get().player(),"accept",connection.getServerPlayer().getPlainTextName());
                check(game().hub.parties.view(connection.getServerPlayer().getUUID()).members().size()==2,"Invitation accepted into party");
            });
            click(c,21);
            var otherToken=c.computeOnClient(mc->UUID.fromString(command(body(mc.gui.screen()).getMessage(),4).split(" ")[2]));
            server.runOnServer(s->check(game().fighterMenu.action(friend.get().player(),otherToken,4)==0,"A party member cannot reuse another player's token"));
            click(c,31);
            server.runOnServer(s->{
                var p=connection.getServerPlayer(); var view=game().hub.parties.view(p.getUUID());
                check(view.readyCount()==1 && !game().network.selected(p.getUUID()),"One ready member cannot queue the party");
                game().hub.selectMode(friend.get().player(),VanillaSmash.Mode.DUEL);
                check(game().hub.parties.view(p.getUUID()).mode().equals("MATCH"),"Nonleader cannot change mode");
                game().hub.pickerAction(friend.get().player());
                check(game().network.selected(p.getUUID()) && game().stage.active(p),"All-ready party queues in place");
            });
            c.waitTicks(10);c.takeScreenshot("packed-06-party-queued");
            click(c,1);
            server.runOnServer(s->{
                var p=connection.getServerPlayer();check(!game().network.selected(p.getUUID()) && !game().network.selected(friend.get().player().getUUID()),"Changing a queued fighter withdraws whole party");
                check(game().hub.parties.view(p.getUUID()).readyCount()==1,"Other member retains ready choice");
            });
            c.getInput().pressKey(InputConstants.KEY_ESCAPE);c.waitTicks(10);
            server.runOnServer(s->{var p=connection.getServerPlayer();check(!game().stage.active(p) && !game().fighterMenu.active(p) && p.level().dimension().equals(MvpWorlds.LOBBY),"Escape restores lobby and cleans up menu");friend.get().leave();});
            c.runOnClient(mc->mc.player.connection.sendCommand("smash join"));c.waitTicks(20);
            server.runOnServer(s->check(game().stage.session(connection.getServerPlayer().getUUID()).selected==FighterClass.ALEX,"Reopening preserves last fighter"));
            click(c,FighterClass.VILLAGER.ordinal());
            click(c,22);click(c,31);
            c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.ARENA),400);
            server.runOnServer(s->check(game().actor(connection.getServerPlayer()).kind==FighterClass.VILLAGER,"Practice transfers selected fighter"));
            c.waitTicks(30);c.takeScreenshot("packed-07-practice");
            server.runOnServer(s->{var p=connection.getServerPlayer();game().endRound(false);game().returnFromPicker(p);game().hub.results.receive(WinnerStageClientTest.result(p.getUUID(),FighterClass.VILLAGER,false));});
            MatchmakingClientTest.winnerReady(c);
            c.runOnClient(mc->mc.player.connection.sendCommand("smash join"));c.waitTicks(25);
            c.takeScreenshot("packed-08-results-button");click(c,32);
            c.waitTicks(60);c.takeScreenshot("packed-09-reopened-winner");
            c.runOnClient(mc->check(mc.getCameraEntity()!=mc.player,"Reused winner room attaches its new camera"));
            server.runOnServer(s->{var scene=game().hub.results.scene.session(connection.getServerPlayer().getUUID());check(scene.entities.stream().noneMatch(net.minecraft.world.entity.Entity::isRemoved),"Reopened scene entities survive pending chunk unloads");});
            MatchmakingClientTest.winnerReady(c);
            server.runOnServer(s->check(!game().stage.active(connection.getServerPlayer()) && !game().fighterMenu.active(connection.getServerPlayer()),"Results button replaces and cleans up the fighter stage"));
            MatchmakingClientTest.winnerAction(c,2);
            c.waitFor(mc->mc.gui.screen() instanceof DialogScreen<?>,200);
            server.runOnServer(s->check(game().stage.active(connection.getServerPlayer()) && !game().hub.results.scene.active(connection.getServerPlayer()),"Winner Change fighter opens the packed picker"));
        }
        VanillaSmash.LOG.info("PACKED_MENU_NATIVE_CLIENT_TEST_PASSED");
    }
}
