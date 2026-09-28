package dev.hanks.vanilla;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import dev.hanks.network.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.*;

/** Actual resource pack, mouse hitboxes and native text submission against a dedicated test server. */
@SuppressWarnings("UnstableApiUsage")
public final class PartyMenuClientTest {
    private static VanillaSmash game(){return VanillaSmash.instance();}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static net.minecraft.network.chat.ClickEvent.Custom action(net.minecraft.network.chat.Component text,int button){
        if(text.getStyle().getClickEvent() instanceof net.minecraft.network.chat.ClickEvent.Custom event
                && event.payload().orElse(null) instanceof net.minecraft.nbt.CompoundTag tag && tag.getIntOr("button",-1)==button)return event;
        for(var child:text.getSiblings()){var result=action(child,button);if(result!=null)return result;}return null;
    }
    static <T> T widget(GuiEventListener node,Class<T> type,String suffix){
        if(type.isInstance(node) && (!(node instanceof Button b)||b.getMessage().getString().endsWith(suffix)))return type.cast(node);
        if(node instanceof ContainerEventHandler c)for(var child:c.children()){var found=widget(child,type,suffix);if(found!=null)return found;}
        return null;
    }
    static void click(ClientGameTestContext c,int x,int y){
        var point=c.computeOnClient(mc->{var w=WidePickerClientTest.body(mc.gui.screen());double scale=mc.getWindow().getGuiScale();return new double[]{(w.getX()+4+x)*scale,(w.getY()+4+y)*scale};});
        c.getInput().setCursorPos(point[0],point[1]);c.getInput().pressMouse(0);c.waitTicks(8);
        c.runOnClient(mc->check(Math.abs(mc.mouseHandler.xpos()-point[0])<1&&Math.abs(mc.mouseHandler.ypos()-point[1])<1,"Party navigation preserves the mouse"));
    }
    static void submit(ClientGameTestContext c,String label){
        submit(c,label,true);
    }
    private static void submit(ClientGameTestContext c,String label,boolean staysInMenu){
        var point=c.computeOnClient(mc->{var w=widget(mc.gui.screen(),Button.class,label);check(w!=null,"Native button "+label);double scale=mc.getWindow().getGuiScale();return new double[]{(w.getX()+w.getWidth()/2.)*scale,(w.getY()+10)*scale};});
        c.getInput().setCursorPos(point[0],point[1]);c.getInput().pressMouse(0);c.waitTicks(8);
        if(staysInMenu)c.runOnClient(mc->check(Math.abs(mc.mouseHandler.xpos()-point[0])<1&&Math.abs(mc.mouseHandler.ypos()-point[1])<1,"Search submission preserves the mouse"));
    }
    private static void checkSearchText(ClientGameTestContext c){
        var pixels=new AtomicReference<int[]>();
        c.runOnClient(mc->{
            var button=widget(mc.gui.screen(),Button.class,"Search");double scale=mc.getWindow().getGuiScale();
            int center=button.getX()+button.getWidth()/2,half=UiPack.textWidth("Search")/2+2;
            int left=(int)((center-half)*scale),right=(int)((center+half)*scale),top=(int)((button.getY()+4)*scale),bottom=(int)((button.getY()+16)*scale);
            net.minecraft.client.Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(),image->{
                try(image){int ink=0,shadow=0;for(int y=top;y<bottom;y++)for(int x=left;x<right;x++){
                    int rgb=image.getPixel(x,y)&0xffffff;if(rgb==UiTheme.FOREST)ink++;if(rgb==0x060807)shadow++;
                }pixels.set(new int[]{ink,shadow});}
            });
        });
        c.waitFor(mc->pixels.get()!=null,100);
        check(pixels.get()[0]>40,"Search lettering is visibly rendered");
        check(pixels.get()[1]==0,"Search has no overlapping native text-shadow pixels: "+pixels.get()[1]);
    }
    private static void aimAt(ClientGameTestContext c,int id){
        c.waitFor(mc->mc.level.getEntity(id)!=null,100);
        var angles=c.computeOnClient(mc->{
            var entity=mc.level.getEntity(id);
            // The adult's head occludes the seated baby's lower body from below; aim at its face.
            var target=entity instanceof net.minecraft.world.entity.monster.zombie.Zombie zombie&&zombie.isBaby()?entity.getEyePosition():entity.getBoundingBox().getCenter();
            var delta=target.subtract(mc.player.getEyePosition());return new float[]{(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)))};
        });
        c.getInput().lookAt(angles[0],angles[1]);c.waitTicks(3);
        c.waitFor(mc->mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit&&hit.getEntity().getId()==id,100);
    }
    static void search(ClientGameTestContext c,String query){
        click(c,260,56);
        c.waitFor(mc->widget(mc.gui.screen(),EditBox.class,"")!=null,120);
        var point=c.computeOnClient(mc->{var w=widget(mc.gui.screen(),EditBox.class,"");double scale=mc.getWindow().getGuiScale();return new double[]{(w.getX()+20)*scale,(w.getY()+10)*scale};});
        c.getInput().setCursorPos(point[0],point[1]);c.getInput().pressMouse(0);
        c.getInput().holdControl();c.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_A);c.getInput().releaseControl();c.getInput().typeChars(query);
        c.runOnClient(mc->mc.gui.hud.getChat().clearMessages(true));c.waitTicks(2);checkSearchText(c);c.takeScreenshot("party-search-input");
        var hover=c.computeOnClient(mc->{var b=widget(mc.gui.screen(),Button.class,"Search");double scale=mc.getWindow().getGuiScale();return new double[]{(b.getX()+b.getWidth()/2.)*scale,(b.getY()+10)*scale};});
        c.getInput().setCursorPos(hover[0],hover[1]);c.waitTicks(2);checkSearchText(c);
        submit(c,"Search");
        c.waitFor(mc->mc.gui.screen()!=null&&mc.gui.screen().getTitle().getString().equals("Party"),120);
    }
    public static void run(ClientGameTestContext c){
        var props=new Properties();props.setProperty("online-mode","false");props.setProperty("server-ip","127.0.0.1");props.setProperty("view-distance","6");props.setProperty("allow-flight","true");
        try(var server=c.worldBuilder().createServer(props);var connection=server.connect()){
            c.getInput().resizeWindow(1280,720);c.runOnClient(mc->{mc.options.guiScale().set(2);mc.options.fov().set(70);mc.resizeGui();});
            MatchmakingClientTest.click(c,"Proceed");server.waitFor(s->game().uiPack.ready(connection.getServerPlayer()),1200);
            connection.waitForChunksRender();c.waitTicks(35);
            server.waitFor(s->game().partyPoint.fighter()!=null&&game().partyPoint.baby()!=null,100);
            server.runOnServer(s->{
                var level=s.getLevel(MvpWorlds.LOBBY);var adult=game().partyPoint.fighter();var baby=game().partyPoint.baby();
                check(baby.isBaby()&&baby.getVehicle()==adult,"Party has a baby zombie riding its adult");
                check(game().partyPoint.owns(baby)&&game().partyPoint.owns(adult),"Both zombies belong to the landmark lifecycle");
                level.setBlock(LobbyPlayPoint.PODIUM,net.minecraft.world.level.block.Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(),2);
                try{LobbyBuilder.ensureBuilt(level);LobbyBuilder.ensureBuilt(level);}catch(java.io.IOException e){throw new AssertionError(e);}
                check(level.getBlockState(LobbyPlayPoint.PODIUM).is(net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK),"Existing Play podium migrates to diamond");
                check(level.getBlockState(LobbyPartyPoint.PODIUM).is(net.minecraft.world.level.block.Blocks.AMETHYST_BLOCK),"Party podium is built on existing maps");
                check(game().partyPoint.fighter()==adult&&game().partyPoint.baby()==baby,"Repeated map preparation does not duplicate the zombie duo");
            });
            c.runOnClient(mc->{mc.gui.toastManager().clear();mc.gui.hud.getChat().clearMessages(true);});c.waitTicks(20);c.takeScreenshot("lobby-party-01-arrival");
            var adultId=server.computeOnServer(s->game().partyPoint.fighter().getId());
            server.runOnServer(s->connection.getServerPlayer().teleportTo(LobbyPartyPoint.POSITION.x,101,LobbyPartyPoint.POSITION.z-5));c.waitTicks(10);
            c.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_2);c.getInput().lookAt(0,-15);c.waitTicks(4);c.takeScreenshot("lobby-party-02-zombie-duo");
            server.runOnServer(s->connection.getServerPlayer().teleportTo(LobbyPartyPoint.POSITION.x,101,LobbyPartyPoint.POSITION.z-3));c.waitTicks(10);aimAt(c,adultId);
            var courtyard=server.computeOnServer(s->connection.getServerPlayer().position());
            c.getInput().pressMouse(1);c.waitFor(mc->widget(mc.gui.screen(),Button.class,"Back to lobby")!=null,120);
            c.waitTicks(25);
            server.runOnServer(s->{var p=connection.getServerPlayer();check(p.level().dimension().equals(MvpWorlds.LOBBY)&&!game().stage.active(p)&&!game().fighterMenu.active(p),"Podium Party stays in the lobby without creating a fighter stage");check(p.position().distanceToSqr(courtyard)<.001,"Opening Party retains the exact courtyard position");check(game().partyPoint.fighter().getId()==adultId&&game().partyPoint.baby()!=null,"Lobby landmarks remain present while Party is open");});
            c.runOnClient(mc->check(mc.getCameraEntity()==mc.player,"Party keeps the ordinary lobby camera"));
            c.takeScreenshot("lobby-party-03-menu-in-courtyard");
            submit(c,"Back to lobby",false);
            server.runOnServer(s->check(connection.getServerPlayer().position().distanceToSqr(courtyard)<.001,"Back closes the dialog without teleporting to spawn"));
            server.waitFor(s->game().partyPoint.baby()!=null,100);
            var babyId=server.computeOnServer(s->game().partyPoint.baby().getId());
            server.runOnServer(s->connection.getServerPlayer().teleportTo(LobbyPartyPoint.POSITION.x,101.5,LobbyPartyPoint.POSITION.z-1.3));c.waitTicks(10);
            c.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_2);aimAt(c,babyId);c.getInput().pressMouse(0);
            c.waitFor(mc->widget(mc.gui.screen(),Button.class,"Back to lobby")!=null,120);submit(c,"Back to lobby",false);
            server.runOnServer(s->connection.getServerPlayer().teleportTo(LobbyPartyPoint.POSITION.x,101,LobbyPartyPoint.POSITION.z-3));c.waitTicks(10);
            c.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_2);c.getInput().lookAt(0,40);c.waitTicks(3);
            c.waitFor(mc->mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit&&hit.getBlockPos().getY()==101,100);
            c.getInput().pressMouse(1);c.waitFor(mc->widget(mc.gui.screen(),Button.class,"Back to lobby")!=null,120);submit(c,"Back to lobby",false);
            server.runOnServer(s->check(game().network.selections.tickets().isEmpty()&&!game().stage.active(connection.getServerPlayer()),"Landmark browsing returns to the lobby without queueing"));
            var bloom=new AtomicReference<MatchmakingClientTest.Peer>();var moss=new AtomicReference<MatchmakingClientTest.Peer>();var block=new AtomicReference<MatchmakingClientTest.Peer>();
            server.runOnServer(s->{bloom.set(MatchmakingClientTest.Peer.join(s,"Bloom"));moss.set(MatchmakingClientTest.Peer.join(s,"Mossy"));block.set(MatchmakingClientTest.Peer.join(s,"MossBlock"));});c.waitTicks(40);
            // Creating, inviting and joining are available directly in the courtyard.
            var lobbyPosition=server.computeOnServer(s->connection.getServerPlayer().position());
            c.runOnClient(mc->mc.player.connection.sendCommand("smash party"));c.waitTicks(25);search(c,"Mossy");click(c,360,81);
            server.runOnServer(s->{
                var p=connection.getServerPlayer();var party=game().hub.parties.view(p.getUUID());
                check(party.party()&&game().hub.parties.sent(p.getUUID(),moss.get().player().getUUID(),game().ticks),"Lobby invitation creates a party");
                game().hub.social.accept(moss.get().player(),party.id());
                check(game().hub.parties.view(p.getUUID()).members().size()==2,"Friend joins the lobby party");
                check(!game().stage.active(p)&&!game().stage.active(moss.get().player())&&p.position().distanceToSqr(lobbyPosition)<.001,"Neither party member is moved into character select");
            });c.waitTicks(25);
            check(PackedMenuClientTest.contents(c).contains("Mossy")&&PackedMenuClientTest.contents(c).contains("In lobby"),"Lobby roster refreshes with the new member's name and status");
            c.takeScreenshot("lobby-party-04-joined-in-courtyard");
            click(c,65,221);server.runOnServer(s->game().hub.parties.leave(moss.get().player().getUUID()));submit(c,"Back to lobby",false);
            c.runOnClient(mc->mc.player.connection.sendCommand("smash join"));c.waitTicks(35);
            var stage=server.computeOnServer(s->game().stage.session(connection.getServerPlayer().getUUID()));
            var id=server.computeOnServer(s->connection.getServerPlayer().getUUID());
            server.runOnServer(s->{
                var book=game().hub.parties;book.ensure(id,"Player0");book.ensure(bloom.get().player().getUUID(),"Bloom");book.ensure(moss.get().player().getUUID(),"Mossy");book.ensure(block.get().player().getUUID(),"MossBlock");
                book.preview(bloom.get().player().getUUID(),"ALEX");book.preview(moss.get().player().getUUID(),"DROWNED");book.preview(block.get().player().getUUID(),"ZOMBIE");
                book.invite(id,bloom.get().player().getUUID(),game().ticks);book.accept(bloom.get().player().getUUID(),book.view(id).id(),game().ticks);
                book.invite(id,block.get().player().getUUID(),game().ticks);
                var round=book.start(id,"MATCH");book.ready(bloom.get().player().getUUID(),round,"ALEX");
                game().hub.social.presence(List.of(new Wire.OnlinePlayer(UUID.randomUUID(),"MossKnight","SKELETON",true)));
                game().fighterMenu.refresh(connection.getServerPlayer());
            });c.waitTicks(5);
            WidePickerClientTest.action(c,40);c.waitTicks(5);
            c.runOnClient(mc->{mc.gui.toastManager().clear();mc.gui.hud.getChat().clearMessages(true);var w=WidePickerClientTest.body(mc.gui.screen());check(w!=null&&w.getWidth()==400&&w.getHeight()==242,"Party canvas does not wrap: "+(w==null?"missing":w.getWidth()+"x"+w.getHeight()));});
            server.runOnServer(s->check(game().hub.parties.view(id).readyCount()==1,"Opening party preserves readiness"));
            var stale=c.computeOnClient(mc->action(WidePickerClientTest.body(mc.gui.screen()).getMessage(),2));
            search(c,"Moss");c.takeScreenshot("party-01-search");
            c.runOnClient(mc->mc.player.connection.send(new net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket(stale.id(),stale.payload())));c.waitTicks(8);
            check(PackedMenuClientTest.contents(c).contains("Search: Moss"),"An old tab token cannot act on a replacement screen");
            var owned=c.computeOnClient(mc->action(WidePickerClientTest.body(mc.gui.screen()).getMessage(),22));
            server.runOnServer(s->{bloom.get().player().connection.handleCustomClickAction(new net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket(owned.id(),owned.payload()));check(!game().hub.parties.sent(id,moss.get().player().getUUID(),game().ticks),"Another player cannot reuse the owner's invite action");});
            // Three search results sorted by name: sent, busy on another backend, available.
            click(c,360,162);
            server.runOnServer(s->check(game().hub.parties.sent(id,moss.get().player().getUUID(),game().ticks),"Mouse invite reaches the selected player"));
            c.takeScreenshot("party-02-sent");click(c,360,162);
            server.runOnServer(s->{check(game().hub.parties.invites(moss.get().player().getUUID(),game().ticks).size()==1,"Repeated click cannot duplicate invites");check(game().hub.parties.view(id).readyCount()==1,"Sending an invite preserves readiness");});
            server.runOnServer(s->game().hub.parties.invites(block.get().player().getUUID(),game().ticks+2401));c.waitTicks(25);
            c.runOnClient(mc->check(action(WidePickerClientTest.body(mc.gui.screen()).getMessage(),20)!=null,"Expired sent state becomes an Invite button without reopening"));
            server.runOnServer(s->block.get().leave());c.waitTicks(45);
            check(!PackedMenuClientTest.contents(c).contains("MossBlock"),"Disconnected player disappears from search results");
            submit(c,"Back to fighters");server.runOnServer(s->{check(game().stage.session(id)==stage,"Back preserves the same fighter stage");check(Math.abs(stage.preview.getX()-stage.origin()-4)<.01,"Preview returns to the original stage position");});
            WidePickerClientTest.action(c,40);
            click(c,65,81);c.takeScreenshot("party-03-member");click(c,260,162);
            server.runOnServer(s->check(game().hub.parties.view(id).members().size()==1,"Leader can remove a member without leaving the stage"));
            // Leave, receive an invitation, and accept it through the portrait row.
            click(c,65,221);
            server.runOnServer(s->{var book=game().hub.parties;book.invite(moss.get().player().getUUID(),id,game().ticks);});c.waitTicks(25);
            click(c,350,35);c.takeScreenshot("party-04-invitation");click(c,360,81);
            server.runOnServer(s->check(game().hub.parties.view(id).leader().equals(moss.get().player().getUUID()),"Recipient can join from invitations"));
            c.takeScreenshot("party-05-joined");
            click(c,65,221);submit(c,"Back to fighters");
            c.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);c.waitTicks(10);
            server.runOnServer(s->{check(!game().hub.social.active(connection.getServerPlayer()),"Leaving cleans up party UI");bloom.get().leave();moss.get().leave();});
            // Lobby entry keeps its position across search, tabs and repeated /smash party.
            var returnPosition=server.computeOnServer(s->connection.getServerPlayer().position());
            c.runOnClient(mc->mc.player.connection.sendCommand("smash party"));c.waitTicks(25);
            c.runOnClient(mc->check(widget(mc.gui.screen(),Button.class,"Back to lobby")!=null,"Lobby command has a lobby Back label"));
            server.runOnServer(s->check(!game().stage.active(connection.getServerPlayer())&&connection.getServerPlayer().position().distanceToSqr(returnPosition)<.001,"Command entry never leaves the lobby"));
            c.runOnClient(mc->{mc.options.guiScale().set(3);mc.resizeGui();});c.waitTicks(5);
            search(c,"Turblo");click(c,265,35);
            c.runOnClient(mc->mc.player.connection.sendCommand("smash party"));c.waitTicks(10);
            submit(c,"Back to lobby",false);
            server.runOnServer(s->{var p=connection.getServerPlayer();check(!game().stage.active(p)&&!game().fighterMenu.active(p)&&!game().hub.social.active(p)&&p.level().dimension().equals(MvpWorlds.LOBBY)&&p.position().distanceToSqr(returnPosition)<.001,"Lobby Back closes Party in the same spot");});
            c.runOnClient(mc->check(mc.gui.screen()==null&&mc.getCameraEntity()==mc.player,"Lobby Back restores gameplay and the player's camera"));
            c.runOnClient(mc->{mc.options.guiScale().set(2);mc.resizeGui();});
            // Exercise the actual head in the lobby hotbar, including nested Search Cancel and Escape.
            c.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_9);c.waitTicks(3);c.getInput().pressMouse(1);c.waitTicks(25);
            c.runOnClient(mc->check(widget(mc.gui.screen(),Button.class,"Back to lobby")!=null,"Hotbar entry has a lobby Back label"));
            server.runOnServer(s->check(!game().stage.active(connection.getServerPlayer())&&connection.getServerPlayer().position().distanceToSqr(returnPosition)<.001,"Hotbar Party stays in the same spot"));
            click(c,260,56);submit(c,"Cancel");
            c.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);c.waitTicks(12);
            server.runOnServer(s->check(!game().stage.active(connection.getServerPlayer())&&connection.getServerPlayer().level().dimension().equals(MvpWorlds.LOBBY),"Escape from a lobby-opened party also returns to the lobby"));
            c.runOnClient(mc->check(mc.gui.screen()==null,"Hotbar party exits to gameplay"));
            // A subsequent fighter entry must get its own fresh return destination.
            c.runOnClient(mc->mc.player.connection.sendCommand("smash join"));c.waitTicks(20);WidePickerClientTest.action(c,40);
            click(c,260,56);submit(c,"Cancel");submit(c,"Back to fighters");
            c.runOnClient(mc->check(mc.gui.screen().getTitle().getString().equals("Choose fighter"),"Fighter entry still returns to the picker after lobby visits"));
            c.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);c.waitTicks(10);
        }
        VanillaSmash.LOG.info("PARTY_MENU_NATIVE_CLIENT_TEST_PASSED");
    }
}
