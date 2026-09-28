package dev.hanks.vanilla;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import com.mojang.blaze3d.platform.InputConstants;
import dev.hanks.network.PartyBook;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.network.chat.ClickEvent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/** Actual stock menu rendering and mouse input with a server-downloaded resource pack. */
@SuppressWarnings("UnstableApiUsage")
public final class PackedMenuClientTest {
    private static VanillaSmash game() { return VanillaSmash.instance(); }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static void clickPoint(ClientGameTestContext c,int x,int y) {
        clickPoint(c,x,y,8);
    }
    private static void clickPoint(ClientGameTestContext c,int x,int y,int wait) {
        c.waitFor(mc->mc.gui.screen() instanceof AbstractContainerScreen<?>,300);
        var point=c.computeOnClient(mc->{
            double scale=mc.getWindow().getGuiScale();
            return new double[]{((mc.gui.screen().width-FighterMenu.WIDTH)/2+x)*scale,((mc.gui.screen().height-FighterMenu.HEIGHT)/2+y)*scale};
        });
        c.getInput().setCursorPos(point[0],point[1]);c.getInput().pressMouse(0);c.waitTicks(wait);
    }
    public static void click(ClientGameTestContext c,int action) {
        c.waitFor(mc->mc.gui.screen() instanceof AbstractContainerScreen<?> ||
                (mc.gui.screen()!=null && mc.gui.screen().getTitle().getString().equals("Choose fighter") && WidePickerClientTest.body(mc.gui.screen())!=null),300);
        if(c.computeOnClient(mc->!(mc.gui.screen() instanceof AbstractContainerScreen<?>))){WidePickerClientTest.action(c,action);return;}
        if(action<8)clickPoint(c,25+(action%4)*36,71+(action/4)*36);
        else if(action>=20 && action<=22)clickPoint(c,action==22?133:34+(action-20)*54,165);
        else if(action==31)clickPoint(c,97,183);
        else if(action==32)clickPoint(c,97,205);
        else if(action==30)clickPoint(c,25,183);
        else if(action>=40 && action<=43)clickPoint(c,25+(action-40)*36,26);
        else if(action==35)clickPoint(c,16,147);
        else if(action==36)clickPoint(c,88,147);
        else if(action==37)clickPoint(c,124,147);
        else if(action==38)clickPoint(c,133,205);
        else throw new IllegalArgumentException("Unknown test action "+action);
    }
    static String contents(ClientGameTestContext c) {
        return c.computeOnClient(mc->{
            var body=WidePickerClientTest.body(mc.gui.screen());
            var text=body==null?mc.gui.screen().getTitle():body.getMessage();
            return text.getString()+hoverText(text);
        });
    }
    private static String hoverText(net.minecraft.network.chat.Component text){
        var result=new StringBuilder();
        if(text.getStyle().getHoverEvent() instanceof net.minecraft.network.chat.HoverEvent.ShowText hover)result.append(hover.value().getString());
        for(var child:text.getSiblings())result.append(hoverText(child));
        return result.toString();
    }
    private static ClickEvent.Custom action(net.minecraft.network.chat.Component text,int button) {
        check(!(text.getStyle().getClickEvent() instanceof ClickEvent.RunCommand),"Menu clicks must not send chat commands");
        if(text.getStyle().getClickEvent() instanceof ClickEvent.Custom event
                && event.payload().orElse(null) instanceof net.minecraft.nbt.CompoundTag payload
                && payload.getIntOr("button",-1)==button)return event;
        for(var child:text.getSiblings()){var found=action(child,button);if(found!=null)return found;}
        return null;
    }
    private static ServerboundCustomClickActionPacket packet(ClickEvent.Custom event) {
        check(event!=null,"Native menu exposes a bound custom action");
        return new ServerboundCustomClickActionPacket(event.id(),event.payload());
    }
    private static int commandSpam(net.minecraft.server.level.ServerPlayer player) {
        try {
            var field=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("commandSpamThrottler");field.setAccessible(true);
            var count=net.minecraft.util.TickThrottler.class.getDeclaredField("count");count.setAccessible(true);
            return count.getInt(field.get(player.connection));
        } catch(ReflectiveOperationException e) {throw new AssertionError(e);}
    }
    private static void checkFocusBorder(ClientGameTestContext c,String name) {
        c.waitTicks(3);
        c.takeScreenshot(name);
        var white=new java.util.concurrent.atomic.AtomicInteger(-1);
        c.runOnClient(mc->{
            double scale=mc.getWindow().getGuiScale();
            int x=(int)(((mc.gui.screen().width-FighterMenu.WIDTH)/2)*scale),y=(int)(((mc.gui.screen().height-FighterMenu.HEIGHT)/2)*scale),w=(int)(FighterMenu.WIDTH*scale),h=(int)(FighterMenu.HEIGHT*scale);
            net.minecraft.client.Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(),im->{
                try(im){int count=0;for(int i=Math.max(0,x);i<Math.min(im.getWidth(),x+w);i++) {
                    if(y>=0 && y<im.getHeight() && (im.getPixel(i,y)&0xffffff)==0xffffff)count++;
                    int bottom=y+h-1;if(bottom>=0 && bottom<im.getHeight() && (im.getPixel(i,bottom)&0xffffff)==0xffffff)count++;
                }
                for(int j=Math.max(0,y);j<Math.min(im.getHeight(),y+h);j++) {
                    if(x>=0 && x<im.getWidth() && (im.getPixel(x,j)&0xffffff)==0xffffff)count++;
                    int right=x+w-1;if(right>=0 && right<im.getWidth() && (im.getPixel(right,j)&0xffffff)==0xffffff)count++;
                }white.set(count);}
            });
        });
        c.waitFor(mc->white.get()>=0,100);
        check(white.get()<12,"Picker focus border must not flash white: "+white.get()+" pixels");
    }
    private static void checkOtherOutlines(ClientGameTestContext c) {
        var previous=c.computeOnClient(mc->mc.gui.screen());
        c.runOnClient(mc->mc.gui.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.empty()) {
            @Override public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g,int x,int y,float partial) {
                g.fill(5,5,355,60,0xff000000);
                g.outline(10,10,80,20,0xffffffff);
                g.outline(10,40,344,10,0xffff0000);
            }
        }));c.waitTicks(3);
        var pixels=new java.util.concurrent.atomic.AtomicInteger(-1);
        c.runOnClient(mc->{
            double scale=mc.getWindow().getGuiScale();
            net.minecraft.client.Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(),im->{
                try(im){int a=im.getPixel((int)(15*scale),(int)(10*scale))&0xffffff;
                    int b=im.getPixel((int)(15*scale),(int)(40*scale))&0xffffff;
                    pixels.set(a==0xffffff && b==0xff0000?1:0);}
            });
        });c.waitFor(mc->pixels.get()>=0,100);
        check(pixels.get()==1,"Ordinary white outlines and colored edges remain visible");
        c.runOnClient(mc->mc.gui.setScreen(previous));c.waitTicks(3);
    }
    public static void run(ClientGameTestContext c) {
        var opening=new java.util.concurrent.atomic.AtomicBoolean();
        var openingEyes=new ArrayList<net.minecraft.world.phys.Vec3>();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(opening.get() && mc.level!=null && mc.level.dimension().equals(MvpWorlds.SHOWCASE)
                    && mc.gui.screen() instanceof AbstractContainerScreen<?> && mc.getCameraEntity()!=null)
                openingEyes.add(mc.getCameraEntity().getEyePosition(1));
        });
        var props=new Properties(); props.setProperty("online-mode","false");props.setProperty("server-ip","127.0.0.1");props.setProperty("view-distance","6");props.setProperty("allow-flight","true");
        try(var server=c.worldBuilder().createServer(props)) {
            // This test deliberately disconnects below; the connection wrapper forbids closing after a kick.
            var connection=server.connect();
            c.getInput().resizeWindow(1280,720);
            c.runOnClient(mc->{mc.options.fov().set(70);mc.options.guiScale().set(2);mc.resizeGui();});
            // Upgrade a persisted garden, not just an empty new room.
            server.runOnServer(s->{
                var player=connection.getServerPlayer();s.getPlayerList().deop(player.nameAndId());
                check(!s.getPlayerList().isOp(player.nameAndId()) && !s.isSingleplayerOwner(player.nameAndId()),"Menu spam test uses an ordinary, non-exempt player");
                var level=s.getLevel(MvpWorlds.SHOWCASE);ShowcaseBuilder.ensureBuilt(level,0);
                level.setBlock(new net.minecraft.core.BlockPos(0,93,0),net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK.defaultBlockState(),2);
                level.setBlock(new net.minecraft.core.BlockPos(-25,117,-9),net.minecraft.world.level.block.Blocks.POLISHED_ANDESITE.defaultBlockState(),2);
            });
            c.waitFor(mc->mc.gui.screen()!=null && mc.gui.screen().getClass().getSimpleName().equals("PackConfirmScreen"),300);
            c.waitTicks(5); c.takeScreenshot("packed-00-pack-prompt");
            MatchmakingClientTest.click(c,"Proceed");
            server.waitFor(s->game().uiPack.ready(connection.getServerPlayer()),1200);
            connection.waitForChunksRender(); c.waitTicks(40);
            opening.set(true);c.runOnClient(mc->mc.player.connection.sendCommand("smash join"));
            c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.SHOWCASE) && mc.gui.screen() instanceof AbstractContainerScreen<?>,400);
            server.runOnServer(s->{
                var level=s.getLevel(MvpWorlds.SHOWCASE);
                check(level.getBlockState(new net.minecraft.core.BlockPos(0,93,0)).is(net.minecraft.world.level.block.Blocks.EMERALD_BLOCK),"Existing garden upgraded to the studio");
                for(var pos:List.of(new net.minecraft.core.BlockPos(-9,104,-2),new net.minecraft.core.BlockPos(-12,108,-5),new net.minecraft.core.BlockPos(-7,95,3),new net.minecraft.core.BlockPos(-25,117,-9)))
                    check(level.getBlockState(pos).isAir(),"Old shelves, trees and roots removed: "+pos);
                ShowcaseBuilder.ensureFighterBuilt(level,0);
                check(level.getBlockState(new net.minecraft.core.BlockPos(4,101,0)).is(net.minecraft.world.level.block.Blocks.BAMBOO_MOSAIC),"Repeated preparation retains the studio dais");
            });
            c.waitTicks(45);opening.set(false);
            var previewId=new java.util.concurrent.atomic.AtomicInteger();
            server.runOnServer(s->{
                var model=game().stage.session(connection.getServerPlayer().getUUID()).preview;
                check(!model.isRemoved(),"Initial preview survives stage preparation");previewId.set(model.getId());
            });
            // Entity tracking follows the asynchronous dimension/chunk delivery.
            c.waitFor(mc->mc.level.getEntity(previewId.get())!=null,200);
            c.runOnClient(mc->check(mc.level.getEntity(previewId.get())!=null,"Initial fighter preview reaches the client without selecting a different class"));
            c.runOnClient(mc->{
                mc.gui.toastManager().clear();check(mc.getCameraEntity()!=mc.player,"Private stage camera is attached before the menu opens");
                check(openingEyes.size()>=35,"Captured the stage opening and former delayed handoff");
                for(var eye:openingEyes)check(eye.distanceTo(openingEyes.getLast())<.01,"Opening view never jumps: "+eye+" -> "+openingEyes.getLast());
            }); c.takeScreenshot("packed-01-steve");
            c.runOnClient(mc->check(mc.level.getScoreboard().getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR)==null,"Party is beside the picker rather than at the screen edge"));
            server.runOnServer(s->check(game().stage.session(connection.getServerPlayer().getUUID()).mode==VanillaSmash.Mode.DUEL,"Fresh picker defaults to 1v1"));
            c.runOnClient(mc->{
                for(var value:List.of("2/2 ready","In Queue  0:01","Finding players..."))
                    check(mc.font.width(UiPack.pickerText(value,161,false))==UiPack.textWidth(value),"Native text advances preserve canvas alignment, including spaces: "+value);
                check(mc.font.width(UiPack.partyName("MMMMMMMM",26))<=32,"Two party-name lines fit a full sixteen-character username");
                for(var label:List.of("Party 4/4","2/4 ready"))
                    check(mc.font.width(UiPack.pickerText(label,5,true))==UiPack.pickerNameWidth(label),"Party header renders without shifting the click canvas: "+label);
            });
            click(c,0);checkFocusBorder(c,"packed-focus-two");
            checkOtherOutlines(c);
            for(int i=0;i<FighterClass.values().length;i++) {
                var kind=FighterClass.values()[i];click(c,i%FighterMenu.PAGE_SIZE);
                server.runOnServer(s->check(game().stage.session(connection.getServerPlayer().getUUID()).selected==kind,"Mouse selected "+kind));
                int tile=i%FighterMenu.PAGE_SIZE;
                c.runOnClient(mc->{
                    var name=kind.name().toLowerCase(Locale.ROOT);
                    check(mc.font.width(UiPack.strip("menu_picker_card_"+name+"_on_"+tile))==36,"High-density portrait advance matches native click width: "+kind);
                    check(mc.font.width(UiPack.strip("menu_results_head_"+name+"_0"))==16,"Transparent results portrait keeps its advance: "+kind);
                    check(mc.font.width(UiPack.strip("menu_hud_head_"+name))==18,"Transparent HUD portrait keeps its advance: "+kind);
                });
                c.takeScreenshot("packed-02-"+kind.name().toLowerCase(Locale.ROOT));
            }
            // Repeated native clicks across all four image corners must select the same fighter.
            for(int[] offset:new int[][]{{3,3},{32,3},{3,32},{32,32}}) {
                click(c,0);
                clickPoint(c,43+offset[0],53+offset[1]); c.takeScreenshot("edge-"+offset[0]+"-"+offset[1]);
                server.runOnServer(s->check(game().stage.session(connection.getServerPlayer().getUUID()).selected==FighterClass.ALEX,"Full portrait is clickable at "+Arrays.toString(offset)));
            }
            var spamBefore=new java.util.concurrent.atomic.AtomicInteger();
            server.runOnServer(s->spamBefore.set(commandSpam(connection.getServerPlayer())));
            for(int i=0;i<60;i++) {
                int index=i%FighterMenu.PAGE_SIZE;
                clickPoint(c,25+(index%4)*36,71+(index/4)*36,1);
            }
            c.waitTicks(8);click(c,1);
            server.runOnServer(s->{
                var p=connection.getServerPlayer();
                check(game().stage.session(p.getUUID()).selected==FighterClass.ALEX,"Rapid mouse clicks leave selection responsive");
                check(commandSpam(p)<=spamBefore.get(),"Sixty native mouse clicks never increase command spam");
            });
            VanillaSmash.LOG.info("MENU_RAPID_CLICK_TEST_PASSED clicks=60 non_op=true");
            var stale=c.computeOnClient(mc->action(mc.gui.screen().getTitle(),0));
            check(stale!=null,"Native body exposes a bound selection action");
            click(c,4);
            c.runOnClient(mc->mc.player.connection.send(packet(stale)));c.waitTicks(6);
            c.runOnClient(mc->mc.player.connection.send(packet(MenuActions.event(MenuActions.FIGHTER,UUID.randomUUID(),0))));c.waitTicks(6);
            c.runOnClient(mc->{
                var malformed=new net.minecraft.nbt.CompoundTag();malformed.putString("token","not-a-uuid");malformed.putInt("button",0);
                var wrongType=new net.minecraft.nbt.CompoundTag();wrongType.putString("token",UUID.randomUUID().toString());wrongType.putString("button","0");
                for(var payload:List.<net.minecraft.nbt.Tag>of(malformed,wrongType,net.minecraft.nbt.StringTag.valueOf("bad payload")))
                    mc.player.connection.send(new ServerboundCustomClickActionPacket(MenuActions.FIGHTER,Optional.of(payload)));
                mc.player.connection.send(new ServerboundCustomClickActionPacket(MenuActions.FIGHTER,Optional.empty()));
                mc.player.connection.send(packet(MenuActions.event(MenuActions.FIGHTER,UUID.randomUUID(),101)));
            });c.waitTicks(6);
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
            click(c,38);
            server.runOnServer(s->check(game().fighterMenu.active(connection.getServerPlayer()) && game().network.selected(connection.getServerPlayer().getUUID()),"Hidden store action cannot interrupt queue progress"));
            c.takeScreenshot("packed-03-queued");
            var queueAction=c.computeOnClient(mc->{check(mc.gui.screen().getTitle().getString().contains("In Queue"),"Queue state is explicit");return action(mc.gui.screen().getTitle(),31);});
            var before=c.computeOnClient(mc->mc.gui.screen().getTitle().getString());c.waitTicks(22);
            c.runOnClient(mc->{check(!before.equals(mc.gui.screen().getTitle().getString()),"Queue elapsed time advances");check(queueAction.equals(action(mc.gui.screen().getTitle(),31)),"Queue animation keeps in-flight action valid");mc.player.connection.send(packet(queueAction));});c.waitTicks(8);
            server.runOnServer(s->check(!game().network.selected(connection.getServerPlayer().getUUID()) && game().stage.active(connection.getServerPlayer()),"Cancel search keeps the stage"));
            c.getInput().resizeWindow(1024,768);c.waitTicks(10);c.takeScreenshot("packed-04-four-three");
            c.runOnClient(mc->{mc.options.guiScale().set(3);mc.resizeGui();});c.waitTicks(10);c.takeScreenshot("packed-05-scale-three");
            checkFocusBorder(c,"packed-focus-three");
            c.getInput().resizeWindow(1920,1080);
            c.runOnClient(mc->{mc.options.guiScale().set(2);mc.resizeGui();});c.waitTicks(10);c.takeScreenshot("packed-05-hd-scale-two");
            click(c,0);
            server.runOnServer(s->check(game().stage.session(connection.getServerPlayer().getUUID()).selected==FighterClass.STEVE,"Centered click at HD scale two"));
            c.runOnClient(mc->{mc.options.guiScale().set(0);mc.resizeGui();});c.waitTicks(10);c.takeScreenshot("packed-05-hd-auto");
            click(c,4);
            server.runOnServer(s->check(game().stage.session(connection.getServerPlayer().getUUID()).selected==FighterClass.VILLAGER,"Centered click at HD Auto scale"));
            c.getInput().resizeWindow(1280,720);
            c.runOnClient(mc->{mc.options.guiScale().set(2);mc.resizeGui();});c.waitTicks(10);
            // Blank space is inert; party setup still lives in the lobby.
            clickPoint(c,60,144);
            server.runOnServer(s->check(game().fighterMenu.active(connection.getServerPlayer()),"Removed Party button cannot intercept clicks"));
            c.runOnClient(mc->{check(mc.gui.screen().getTitle().getStyle().getClickEvent()==null,"Empty menu space has no network action");mc.player.connection.send(packet(action(mc.gui.screen().getTitle(),0)));});
            c.getInput().pressKey(InputConstants.KEY_ESCAPE);c.waitTicks(15);
            server.runOnServer(s->check(!game().stage.active(connection.getServerPlayer()),"Escape immediately after selecting cannot strand a player"));
            c.runOnClient(mc->check(mc.level.getScoreboard().getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR)==null,"Picker sidebar is removed on exit"));
            var friend=new AtomicReference<MatchmakingClientTest.Peer>();
            server.runOnServer(s->friend.set(MatchmakingClientTest.Peer.join(s,"PackedFriend")));
            c.waitTicks(80);
            server.runOnServer(s->{
                var peer=friend.get().player(); game().hub.selectMode(peer,VanillaSmash.Mode.PRACTICE);
                check(!game().stage.active(peer),"Direct commands cannot bypass pack readiness");
                game().uiPack.response(peer,new ServerboundResourcePackPacket(UiPack.ID,ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
            });
            c.runOnClient(mc->mc.player.connection.sendCommand("smash join"));c.waitTicks(20);
            click(c,41);c.takeScreenshot("packed-05-party-invite");
            server.runOnServer(s->spamBefore.set(commandSpam(connection.getServerPlayer())));
            PartyMenuClientTest.search(c,"PackedFriend");PartyMenuClientTest.click(c,360,90);
            server.runOnServer(s->{
                check(commandSpam(connection.getServerPlayer())<=spamBefore.get(),"Party dialog buttons do not consume the command spam budget");
                game().hub.partyCommand(friend.get().player(),"accept",connection.getServerPlayer().getPlainTextName());
                check(game().hub.parties.view(connection.getServerPlayer().getUUID()).members().size()==2,"Invitation accepted into party");
            });
            c.waitTicks(10);
            PartyMenuClientTest.submit(c,"Back to fighters");
            c.runOnClient(mc->mc.player.connection.sendCommand("smash join"));
            click(c,21);
            server.runOnServer(s->check(game().hub.parties.view(connection.getServerPlayer().getUUID()).mode().equals("MATCH"),"Leader selects four-player through mouse click"));
            var otherAction=c.computeOnClient(mc->action(mc.gui.screen().getTitle(),4));
            server.runOnServer(s->{
                var peer=friend.get().player();var selected=game().stage.session(peer.getUUID()).selected;
                peer.connection.handleCustomClickAction(packet(otherAction));
                check(game().stage.session(peer.getUUID()).selected==selected,"A party member cannot reuse another player's token");
            });
            click(c,31);
            click(c,38);
            server.runOnServer(s->{
                var p=connection.getServerPlayer();
                check(game().hub.parties.view(p.getUUID()).readyCount()==0 && !game().network.selected(p.getUUID()),"Store browsing withdraws ready vote without queuing the party");
                check(game().stage.active(p) && !game().fighterMenu.active(p),"Store keeps the character stage without reopening the fighter menu");
            });
            MatchmakingClientTest.click(c,"Close");click(c,31);
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
            var fullParty=new ArrayList<MatchmakingClientTest.Peer>();
            server.runOnServer(s->{
                var p=connection.getServerPlayer();check(!game().stage.active(p) && !game().fighterMenu.active(p) && p.level().dimension().equals(MvpWorlds.LOBBY),"Escape restores lobby and cleans up menu");
                fullParty.add(MatchmakingClientTest.Peer.join(s,"MMMMMMMMMMMMMMMM"));
                fullParty.add(MatchmakingClientTest.Peer.join(s,"Birch"));
            });c.waitTicks(80);
            server.runOnServer(s->{
                var p=connection.getServerPlayer();
                for(var peer:fullParty){
                    var other=peer.player();game().uiPack.response(other,new ServerboundResourcePackPacket(UiPack.ID,ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
                    game().hub.parties.ensure(other.getUUID(),other.getPlainTextName());
                    game().hub.parties.invite(p.getUUID(),other.getUUID(),game().ticks);
                    game().hub.parties.accept(other.getUUID(),game().hub.parties.view(p.getUUID()).id(),game().ticks);
                }
                game().hub.selectMode(p,VanillaSmash.Mode.MATCH);
                game().hub.preview(fullParty.get(0).player(),FighterClass.IRON_GOLEM);
                game().hub.preview(fullParty.get(1).player(),FighterClass.ENDERMAN);
                game().hub.pickerAction(p);
                game().hub.preview(friend.get().player(),FighterClass.DROWNED);
                check(game().hub.parties.view(p.getUUID()).readyCount()==1,"A teammate browsing preserves the leader's ready vote");
                game().hub.pickerAction(friend.get().player());
                check(game().hub.parties.view(p.getUUID()).readyCount()==2,"Party strip includes each ready vote");
                game().hub.preview(friend.get().player(),FighterClass.SKELETON);
                check(game().hub.parties.view(p.getUUID()).readyCount()==1,"Changing fighter clears only that teammate's ready vote");
            });c.waitTicks(12);
            c.runOnClient(mc->{
                var text=mc.gui.screen().getTitle().getString();
                check(text.contains(UiPack.strip("menu_picker_party_head_skeleton").getString()),"Teammate's new portrait reaches the open picker before ready-up");
                check(text.contains("PackedFr") && text.contains("iend") && text.contains("MMMMMMMM"),"Party names render completely across two short lines");
            });
            c.takeScreenshot("packed-06-full-party-live");
            c.getInput().pressKey(InputConstants.KEY_ESCAPE);c.waitTicks(10);
            server.runOnServer(s->{friend.get().leave();fullParty.forEach(MatchmakingClientTest.Peer::leave);});
            c.runOnClient(mc->mc.player.connection.sendCommand("smash join"));c.waitTicks(20);
            server.runOnServer(s->check(game().stage.session(connection.getServerPlayer().getUUID()).selected==FighterClass.ALEX,"Reopening preserves last fighter"));
            click(c,FighterClass.VILLAGER.ordinal());
            click(c,22);click(c,31);
            c.waitFor(mc->MvpWorlds.battle(mc.level),400);
            server.runOnServer(s->check(game().actor(connection.getServerPlayer()).kind==FighterClass.VILLAGER,"Practice transfers selected fighter"));
            c.waitTicks(30);c.takeScreenshot("packed-07-practice");
            server.runOnServer(s->game().battle.hud.announce("P2  KO!",0xff624b,40));
            c.waitTicks(6);c.takeScreenshot("packed-07-ko");
            server.runOnServer(s->{var p=connection.getServerPlayer();game().endRound(false);game().returnFromPicker(p);game().hub.results.receive(WinnerStageClientTest.result(p.getUUID(),FighterClass.VILLAGER,false));});
            MatchmakingClientTest.winnerReady(c);
            c.runOnClient(mc->mc.player.connection.sendCommand("smash join"));c.waitTicks(25);
            c.takeScreenshot("packed-08-results-button");click(c,32);
            c.waitTicks(60);c.takeScreenshot("packed-09-reopened-winner");
            c.runOnClient(mc->check(mc.getCameraEntity()!=mc.player,"Reused winner room attaches its new camera"));
            server.runOnServer(s->{var scene=game().hub.results.scene.session(connection.getServerPlayer().getUUID());check(scene.entities.stream().noneMatch(net.minecraft.world.entity.Entity::isRemoved),"Reopened scene entities survive pending chunk unloads");});
            MatchmakingClientTest.winnerReady(c);
            server.runOnServer(s->check(!game().stage.active(connection.getServerPlayer()) && !game().fighterMenu.active(connection.getServerPlayer()),"Results button replaces and cleans up the fighter stage"));
            c.runOnClient(mc->check(mc.gui.screen() instanceof AbstractContainerScreen<?>,"Winner actions use the packed mouse menu"));
            server.runOnServer(s->spamBefore.set(commandSpam(connection.getServerPlayer())));
            clickPoint(c,88,90); // The scoreboard is not an action.
            clickPoint(c,150,147); // Transparent space beside the narrow panel is not a button.
            server.runOnServer(s->check(game().hub.results.menu.active(connection.getServerPlayer()),"Scoreboard clicks are inert"));
            MatchmakingClientTest.winnerAction(c,0);
            server.runOnServer(s->{
                check(game().hub.results.menu.active(connection.getServerPlayer()),"Unavailable rematch leaves usable buttons open");
                check(commandSpam(connection.getServerPlayer())<=spamBefore.get(),"Result buttons do not consume command spam budget");
            });
            c.getInput().resizeWindow(960,720);c.runOnClient(mc->{mc.options.guiScale().set(3);mc.resizeGui();});c.waitTicks(8);
            c.takeScreenshot("packed-10-results-scale3");
            MatchmakingClientTest.winnerAction(c,2);
            c.waitFor(mc->mc.gui.screen() instanceof AbstractContainerScreen<?>,200);
            server.runOnServer(s->check(game().stage.active(connection.getServerPlayer()) && !game().hub.results.scene.active(connection.getServerPlayer()),"Winner Change fighter opens the packed picker"));
            server.runOnServer(s->check(!game().hub.results.menu.active(connection.getServerPlayer()),"Change fighter cleans up result buttons"));
            // A real command burst still gets the stock spam kick; only UI transport changed.
            c.runOnClient(mc->{for(int i=0;i<40;i++)mc.player.connection.sendCommand("smash");});
            c.waitFor(mc->mc.gui.screen() instanceof net.minecraft.client.gui.screens.DisconnectedScreen,200);
            VanillaSmash.LOG.info("MENU_COMMAND_SPAM_PROTECTION_TEST_PASSED");
        }
        c.runOnClient(mc->mc.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen()));
        VanillaSmash.LOG.info("PACKED_MENU_NATIVE_CLIENT_TEST_PASSED");
    }
}
