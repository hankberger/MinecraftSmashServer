package dev.hanks.vanilla;

import java.util.*;
import dev.hanks.network.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.*;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.EntityHitResult;

/** Native resource-pack rendering, real NPC clicks and custom-action navigation. */
@SuppressWarnings("UnstableApiUsage")
public final class RankingsClientTest {
    private static VanillaSmash game(){return VanillaSmash.instance();}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static Wire.MatchResult result(UUID winner,String name,String fighter,UUID loser,int kos){
        var roster=List.of(new Wire.Ticket(winner,fighter,"DUEL",UUID.randomUUID()),new Wire.Ticket(loser,"ZOMBIE","DUEL",UUID.randomUUID()));
        return new Wire.MatchResult(UUID.randomUUID(),"DUEL",winner,roster,List.of(new Wire.ResultRow(winner,name,fighter,1,1,kos,2,100),new Wire.ResultRow(loser,"TrainingPartner","ZOMBIE",2,0,1,3,100)));
    }
    private static boolean text(ClientGameTestContext c,String value){return c.computeOnClient(mc->{var body=WidePickerClientTest.body(mc.gui.screen());return body!=null&&body.getMessage().getString().contains(value);});}
    private static ClickEvent.Custom event(Component text,int id){
        if(text.getStyle().getClickEvent() instanceof ClickEvent.Custom custom&&custom.payload().orElse(null) instanceof net.minecraft.nbt.CompoundTag tag&&tag.getIntOr("button",-1)==id)return custom;
        for(var child:text.getSiblings()){var result=event(child,id);if(result!=null)return result;}return null;
    }
    private static void ready(ClientGameTestContext c){c.waitFor(mc->mc.gui.screen()!=null&&mc.gui.screen().getTitle().getString().equals("Rankings")&&WidePickerClientTest.body(mc.gui.screen())!=null,120);}
    public static void run(ClientGameTestContext c){
        var props=new Properties();props.setProperty("online-mode","false");props.setProperty("server-ip","127.0.0.1");props.setProperty("view-distance","6");props.setProperty("allow-flight","true");
        try(var server=c.worldBuilder().createServer(props);var connection=server.connect()){
            c.getInput().resizeWindow(1280,720);c.runOnClient(mc->{mc.options.guiScale().set(2);mc.options.fov().set(70);mc.resizeGui();});
            MatchmakingClientTest.click(c,"Proceed");server.waitFor(s->game().uiPack.ready(connection.getServerPlayer())&&game().rankingsPoint.fighter()!=null,1200);
            connection.waitForChunksRender();c.waitTicks(40);
            var signIds=server.computeOnServer(s->{
                var level=s.getLevel(MvpWorlds.LOBBY);
                var displays=level.getEntities(EntityTypes.TEXT_DISPLAY,game().addressSign::owns);
                check(displays.stream().anyMatch(d->d.getText().getString().equals("mc.brawl.party")),"The courtyard displays the approved address");
                check(displays.stream().allMatch(d->!d.isPickable()&&!d.canBeCollidedWith(null)),"The sign cannot intercept clicks or block the walkway");
                return displays.stream().map(net.minecraft.world.entity.Entity::getId).toList();
            });
            c.waitFor(mc->signIds.stream().allMatch(id->mc.level.getEntity(id)!=null),100);
            c.getInput().lookAt(0,-6);c.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);c.waitTicks(5);
            c.takeScreenshot("courtyard-address-01-arrival");
            c.getInput().holdKeyFor(o->o.keyUp,16);c.getInput().lookAt(0,-12);c.waitTicks(5);
            c.takeScreenshot("courtyard-address-02-approach");
            // Check the sign's physical center from the far side of the pink gathering circle too.
            server.runOnServer(s->connection.getServerPlayer().teleportTo(.5,101,-52.5));c.waitTicks(10);
            c.getInput().lookAt(180,-18);c.waitTicks(5);c.takeScreenshot("courtyard-address-03-circle-center");
            c.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
            var actorId=server.computeOnServer(s->{
                var p=connection.getServerPlayer();var npc=game().rankingsPoint.fighter();
                check(npc.getType()==EntityTypes.IRON_GOLEM,"Rankings uses an Iron Golem");
                try{LobbyBuilder.ensureBuilt(p.level());LobbyBuilder.ensureBuilt(p.level());}catch(java.io.IOException e){throw new AssertionError(e);}
                check(p.level().getBlockState(LobbyRankingsPoint.PODIUM).is(net.minecraft.world.level.block.Blocks.GOLD_BLOCK),"Existing maps gain the gold rankings podium");
                check(game().rankingsPoint.fighter()==npc,"Repeated map prep does not duplicate the golem");
                p.teleportTo(LobbyRankingsPoint.POSITION.x,101,LobbyRankingsPoint.POSITION.z-5);return npc.getId();
            });
            var trophyIds=server.computeOnServer(s->s.getLevel(MvpWorlds.LOBBY).getEntities(EntityTypes.BLOCK_DISPLAY,game().rankingsPoint::owns).stream().map(net.minecraft.world.entity.Entity::getId).toList());
            check(!trophyIds.isEmpty(),"The trophy is spawned and tracked for cleanup");
            c.waitTicks(12);c.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_2);c.getInput().lookAt(0,-20);c.waitTicks(4);
            c.runOnClient(mc->{mc.gui.toastManager().clear();mc.gui.hud.getChat().clearMessages(true);});c.takeScreenshot("rankings-01-podium");
            server.runOnServer(s->connection.getServerPlayer().teleportTo(LobbyRankingsPoint.POSITION.x,101,LobbyRankingsPoint.POSITION.z-3));c.waitTicks(10);
            c.getInput().lookAt(0,-20);c.waitFor(mc->mc.hitResult instanceof EntityHitResult hit&&hit.getEntity().getId()==actorId,100);c.getInput().pressMouse(1);ready(c);
            c.waitFor(mc->WidePickerClientTest.body(mc.gui.screen()).getMessage().getString().contains("Unranked"),120);
            c.takeScreenshot("rankings-02-empty");
            c.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);c.waitFor(mc->mc.gui.screen()==null,100);
            server.waitFor(s->!game().rankings.active(connection.getServerPlayer()),100);
            server.runOnServer(s->{
                var p=connection.getServerPlayer();check(!game().rankings.active(p)&&!game().stage.active(p)&&game().network.selections.tickets().isEmpty(),"Escape returns directly to lobby, without queueing");
                String[] names={"Skybound","RedstoneRae","BlockBreaker","MossKnight","EnderEcho","CopperFox","LunaCraft","PixelPirate","QuartzQueen","CherryChamp","EmeraldAce",p.getPlainTextName()};
                var loser=UUID.randomUUID();var fighters=List.copyOf(Wire.CLASSES);
                for(int i=0;i<names.length;i++){
                    UUID winner=i==11?p.getUUID():UUID.randomUUID();String fighter=fighters.get(i%fighters.size());
                    for(int n=0;n<12-i;n++)game().points.settle(result(winner,names[i],fighter,loser,3)).join();
                }
                game().rankings.show(p);
            });ready(c);c.waitFor(mc->WidePickerClientTest.body(mc.gui.screen()).getMessage().getString().contains("#12"),120);
            c.runOnClient(mc->{
                var body=WidePickerClientTest.body(mc.gui.screen());
                check(mc.font.split(body.getMessage(),RankingsCanvas.WIDTH).size()==RankingsCanvas.ROWS,"All ten ranks and own rank fit without text wrapping");
                check(body.getHeight()==260,"Rankings uses the full-sized body");
            });
            check(text(c,"Skybound")&&text(c,"#12")&&text(c,"YOU"),"Top ten and own rank outside top ten are both visible");
            c.getInput().setCursorPos(20,400);c.waitTicks(5);c.takeScreenshot("rankings-03-weekly");
            var stale=c.computeOnClient(mc->event(WidePickerClientTest.body(mc.gui.screen()).getMessage(),2));
            PartyMenuClientTest.click(c,190,36);check(text(c,"All-time")&&!text(c,"Resets Mon"),"All-time tab works");
            c.runOnClient(mc->mc.getConnection().send(new net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket(stale.id(),stale.payload())));c.waitTicks(8);
            check(!text(c,"TrainingPartner"),"Stale tab token cannot navigate");
            PartyMenuClientTest.click(c,320,36);check(text(c,"TrainingPartner"),"KO tab orders by KOs, independently of wins");c.takeScreenshot("rankings-04-kos");
            c.getInput().resizeWindow(1920,1080);c.runOnClient(mc->{mc.options.guiScale().set(3);mc.resizeGui();});c.waitTicks(10);
            PartyMenuClientTest.click(c,64,36);check(text(c,"Resets Mon"),"Weekly tab works after resize at GUI scale 3");c.takeScreenshot("rankings-05-fullscreen");
            var point=c.computeOnClient(mc->{var b=PartyMenuClientTest.widget(mc.gui.screen(),Button.class,"Back to lobby");double scale=mc.getWindow().getGuiScale();return new double[]{(b.getX()+75)*scale,(b.getY()+10)*scale};});
            c.getInput().setCursorPos(point[0],point[1]);c.getInput().pressMouse(0);c.waitFor(mc->mc.gui.screen()==null,100);
            // A second entry by attacking the NPC is safe, and changing screens cancels async refreshes.
            c.getInput().lookAt(0,-20);c.waitTicks(10);c.getInput().pressMouse(0);ready(c);
            server.runOnServer(s->game().hub.open(connection.getServerPlayer()));
            server.waitFor(s->game().stage.active(connection.getServerPlayer()),120);
            server.runOnServer(s->check(!game().rankings.active(connection.getServerPlayer()),"Play releases rankings ownership"));
            server.waitFor(s->game().rankingsPoint.fighter()==null,100);
            server.runOnServer(s->{var level=s.getLevel(MvpWorlds.LOBBY);check(trophyIds.stream().allMatch(id->level.getEntity(id)==null),"Trophy parts unload with the golem");
                check(!game().addressSign.active()&&signIds.stream().allMatch(id->level.getEntity(id)==null),"Empty lobby releases all sign displays");
                game().returnFromPicker(connection.getServerPlayer());});
            c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.LOBBY)&&mc.gui.screen()==null,200);
            server.waitFor(s->game().rankingsPoint.fighter()!=null&&game().addressSign.active(),100);
            server.runOnServer(s->{check(!game().stage.active(connection.getServerPlayer()),"Return restores lobby camera");check(game().network.selections.tickets().isEmpty(),"Browsing never queues");});
        }
        VanillaSmash.LOG.info("RANKINGS_NATIVE_CLIENT_TEST_PASSED");
    }
}
