package dev.hanks.vanilla;

import dev.hanks.network.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.Items;

/** Real downloaded pack, native mouse clicks, all five looks, purchase and match lifecycle. */
@SuppressWarnings("UnstableApiUsage")
public final class CosmeticsClientTest {
    private static VanillaSmash game(){return VanillaSmash.instance();}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void command(ClientGameTestContext c,String value){c.runOnClient(mc->mc.player.connection.sendCommand(value));}
    private static void appearance(LivingEntity body,FighterClass kind) {
        switch(kind) {
            case STEVE -> check(body.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE),"Diamond outfit equipped");
            case ALEX -> check(body.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE),"Scout outfit equipped");
            case ZOMBIE -> check(body.getType()==EntityTypes.HUSK,"Dune Zombie renders a husk");
            case SKELETON -> check(body.getType()==EntityTypes.STRAY,"Frost Skeleton renders a stray");
            case VILLAGER -> check(((net.minecraft.world.entity.npc.villager.Villager)body).getVillagerData().type().is(net.minecraft.world.entity.npc.villager.VillagerType.DESERT),"Desert villager outfit");
        }
    }
    public static void run(ClientGameTestContext c) {
        var props=new Properties();props.setProperty("online-mode","false");props.setProperty("server-ip","127.0.0.1");
        props.setProperty("view-distance","6");props.setProperty("simulation-distance","5");props.setProperty("allow-flight","true");
        try(var server=c.worldBuilder().createServer(props);var connection=server.connect()) {
            c.getInput().resizeWindow(1280,720);c.runOnClient(mc->{mc.options.fov().set(70);mc.options.guiScale().set(2);mc.resizeGui();});
            c.waitFor(mc->mc.gui.screen()!=null && mc.gui.screen().getClass().getSimpleName().equals("PackConfirmScreen"),400);
            MatchmakingClientTest.click(c,"Proceed");server.waitFor(s->game().uiPack.ready(connection.getServerPlayer()),1200);
            connection.waitForChunksRender();c.waitTicks(30);c.runOnClient(mc->mc.gui.toastManager().clear());
            var id=server.computeOnServer(s->connection.getServerPlayer().getUUID());
            command(c,"smash join");c.waitTicks(30);PackedMenuClientTest.click(c,36);c.waitTicks(25);
            c.runOnClient(mc->check(mc.gui.screen().getTitle().getString().contains("Need 250 Points"),"Locked skin shows exact shortfall"));
            c.takeScreenshot("cosmetics-00-locked");
            PackedMenuClientTest.click(c,37);PackedMenuClientTest.click(c,31);
            server.runOnServer(s->{
                check(!game().network.selected(id),"Unowned preview cannot queue");
                check(game().points.account(id).balance()==0 && game().points.wardrobe(id).owned().isEmpty(),"Insufficient balance cannot unlock");
                for(int i=0;i<20;i++){
                    var pair=List.of(id,UUID.randomUUID());
                    game().points.settle(new Wire.MatchResult(UUID.randomUUID(),"DUEL",id,
                            pair.stream().map(p->new Wire.Ticket(p,"STEVE","DUEL",UUID.randomUUID())).toList(),
                            pair.stream().map(p->new Wire.ResultRow(p,"Fixture","STEVE",pair.indexOf(p)+1,1,0,0,0)).toList())).join();
                }
            });c.waitTicks(12);
            c.runOnClient(mc->check(mc.gui.screen().getTitle().getString().contains("Buy - 250 Points"),"Purchase button shows price"));
            for(var kind:FighterClass.values()) {
                if(kind!=FighterClass.STEVE){PackedMenuClientTest.click(c,kind.ordinal());PackedMenuClientTest.click(c,36);}
                PackedMenuClientTest.click(c,37);
                server.waitFor(s->game().points.wardrobe(id).equipped(kind.name()).equals(Cosmetics.forFighter(kind.name()).getLast().id()),100);
                c.waitTicks(20);c.takeScreenshot("cosmetics-01-"+kind.name().toLowerCase(Locale.ROOT));
                server.runOnServer(s->{
                    var session=game().stage.session(id);appearance(session.preview,kind);
                    var baseline=FighterModels.create(s.getLevel(MvpWorlds.SHOWCASE),kind);
                    var alt=FighterModels.create(s.getLevel(MvpWorlds.SHOWCASE),kind,session.skin);
                    check(Math.abs(baseline.getBbHeight()-alt.getBbHeight())<.001 && Math.abs(baseline.getBbWidth()-alt.getBbWidth())<.001,"Cosmetics preserve "+kind+" dimensions");
                });
                PackedMenuClientTest.click(c,37); // Equipped button cannot charge twice.
            }
            server.runOnServer(s->check(game().points.account(id).equals(new PointsStore.Account(250,1500,20,20)),"All five purchases debit only currency"));
            PackedMenuClientTest.click(c,FighterClass.STEVE.ordinal());
            PackedMenuClientTest.click(c,35);PackedMenuClientTest.click(c,37);
            server.waitFor(s->game().points.wardrobe(id).equipped("STEVE").equals("default"),100);
            PackedMenuClientTest.click(c,36);PackedMenuClientTest.click(c,37);
            server.waitFor(s->game().points.wardrobe(id).equipped("STEVE").equals("diamond"),100);
            c.getInput().resizeWindow(960,720);c.waitTicks(10);c.takeScreenshot("cosmetics-02-four-three");
            c.getInput().resizeWindow(1920,1080);c.runOnClient(mc->{mc.options.guiScale().set(3);mc.resizeGui();});
            c.waitTicks(10);c.takeScreenshot("cosmetics-03-full-hd");
            server.runOnServer(s->{
                game().points.close();game().points.start(s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("smash/points.db"));
                check(game().points.wardrobe(id).owned().size()==5 && game().points.wardrobe(id).equipped("STEVE").equals("diamond"),"Ownership and equipped skin survive restart");
            });
            PackedMenuClientTest.click(c,31);
            server.runOnServer(s->check(game().network.selections.tickets().stream().anyMatch(t->t.player().equals(id)&&t.skin().equals("diamond")),"Queue ticket freezes equipped look"));
            final MatchmakingClientTest.Peer[] friend=new MatchmakingClientTest.Peer[1];
            server.runOnServer(s->friend[0]=MatchmakingClientTest.Peer.join(s,"CosmeticFriend"));c.waitTicks(60);
            server.runOnServer(s->{
                game().uiPack.response(friend[0].player(),new ServerboundResourcePackPacket(UiPack.ID,ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
                game().choose(friend[0].player(),FighterClass.SKELETON,VanillaSmash.Mode.DUEL);
            });
            server.waitFor(s->game().match.phase()==MatchState.Phase.ACTIVE,300);c.waitTicks(20);c.takeScreenshot("cosmetics-04-duel");
            server.runOnServer(s->{appearance(game().actor(connection.getServerPlayer()).body,FighterClass.STEVE);game().match.finish(id,"Cosmetics fixture");});
            server.waitFor(s->game().hub.results.book.result(id)!=null && game().battle==null,200);
            MatchmakingClientTest.winnerReady(c);c.waitTicks(30);c.takeScreenshot("cosmetics-05-winner");
            server.runOnServer(s->{
                var result=game().hub.results.book.result(id);
                check(result.rows().stream().anyMatch(r->r.player().equals(id)&&r.skin().equals("diamond")),"Result carries equipped look");
                appearance(game().hub.results.scene.session(id).models.getFirst(),FighterClass.STEVE);
                check(game().points.account(id).balance()==325,"Match earnings add to spent balance");
            });
            server.runOnServer(s->{
                var match=game().hub.results.book.result(id).id();
                game().hub.results.rematch(connection.getServerPlayer(),match);game().hub.results.rematch(friend[0].player(),match);
            });
            server.waitFor(s->game().match.phase()==MatchState.Phase.ACTIVE,300);
            server.runOnServer(s->{
                check(game().actor(connection.getServerPlayer()).skin.equals("diamond"),"Rematch retains owned skin");
                appearance(game().actor(connection.getServerPlayer()).body,FighterClass.STEVE);game().match.finish(id,"Cosmetics rematch");
            });
            server.waitFor(s->game().hub.results.book.result(id)!=null && game().battle==null,200);MatchmakingClientTest.winnerReady(c);
            MatchmakingClientTest.winnerAction(c,3);
            server.runOnServer(s->friend[0].leave());
            for(var kind:FighterClass.values()) {
                server.runOnServer(s->game().choose(connection.getServerPlayer(),kind,VanillaSmash.Mode.PRACTICE));
                server.waitFor(s->game().match.phase()==MatchState.Phase.ACTIVE,300);
                server.runOnServer(s->{
                    appearance(game().actor(connection.getServerPlayer()).body,kind);
                    if(kind==FighterClass.ZOMBIE)check(game().battle.companions.buddies.get(id).body.getType()==EntityTypes.HUSK,"Zombie buddy matches Dune cosmetic");
                    game().match.finish(id,"Cosmetics practice");
                });
                server.waitFor(s->game().battle==null,200);
            }
        }
        VanillaSmash.LOG.info("COSMETICS_NATIVE_CLIENT_TEST_PASSED: all five previews, purchases, equip/default, persistence, queued skin, battle and winner, native mouse and resize");
    }
}
