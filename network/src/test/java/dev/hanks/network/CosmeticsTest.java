package dev.hanks.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

class CosmeticsTest {
    @TempDir Path dir;
    private void fund(PointsStore db,UUID player,int matches) throws Exception {
        for(int i=0;i<matches;i++) {
            var opponent=UUID.randomUUID();var players=List.of(player,opponent);
            db.settle(new Wire.MatchResult(UUID.randomUUID(),"DUEL",player,
                    players.stream().map(p->new Wire.Ticket(p,"STEVE","DUEL",UUID.randomUUID())).toList(),
                    players.stream().map(p->new Wire.ResultRow(p,"Test","STEVE",players.indexOf(p)+1,1,0,0,0)).toList()));
        }
    }
    @Test void everyClassHasOnePricedAlternativeAndValidationIsClassSpecific() {
        for(var fighter:Wire.CLASSES) {
            var skins=Cosmetics.forFighter(fighter);assertEquals(2,skins.size());assertEquals(0,skins.getFirst().price());assertEquals(250,skins.getLast().price());
            assertEquals("default",Cosmetics.skin(fighter,null).id());
            assertFalse(Cosmetics.Wardrobe.EMPTY.owns(skins.getLast()));assertTrue(Cosmetics.Wardrobe.EMPTY.owns(skins.getFirst()));
        }
        assertThrows(IllegalArgumentException.class,()->Cosmetics.skin("ALEX","diamond"));
        assertThrows(IllegalArgumentException.class,()->Cosmetics.skin("STEVE","made_up"));
    }
    @Test void purchaseEquipsPersistsAndDoesNotReduceLifetimeEarnings() throws Exception {
        var player=UUID.randomUUID();var path=dir.resolve("points.db");
        try(var db=new PointsStore(path)) {
            fund(db,player,4);
            assertEquals(PointsStore.OutfitResult.PURCHASED,db.outfit(player,"STEVE","diamond",true));
            assertEquals(new PointsStore.Account(50,300,4,4),db.account(player));
            assertEquals(PointsStore.OutfitResult.EQUIPPED,db.outfit(player,"STEVE","diamond",true));
            db.outfit(player,"STEVE","default",false);assertEquals("default",db.wardrobe(player).equipped("STEVE"));
            db.outfit(player,"STEVE","diamond",false);
        }
        try(var db=new PointsStore(path)) {
            assertEquals(50,db.account(player).balance());assertEquals(Set.of("diamond"),db.wardrobe(player).owned());
            assertEquals("diamond",db.wardrobes().get(player).equipped("STEVE"));
            assertEquals("default",db.wardrobe(player).equipped("ALEX"));
            fund(db,player,1);assertEquals(125,db.account(player).balance());assertEquals(375,db.account(player).earned());
        }
    }
    @Test void cannotEquipUnownedOrBuyWithoutFundsOrForgeClass() throws Exception {
        var player=UUID.randomUUID();try(var db=new PointsStore(dir.resolve("points.db"))) {
            assertEquals(PointsStore.OutfitResult.NOT_OWNED,db.outfit(player,"STEVE","diamond",false));
            assertEquals(PointsStore.OutfitResult.NEED_POINTS,db.outfit(player,"STEVE","diamond",true));
            assertThrows(IllegalArgumentException.class,()->db.outfit(player,"ZOMBIE","diamond",true));
            assertTrue(db.wardrobe(player).owned().isEmpty());assertEquals(PointsStore.Account.EMPTY,db.account(player));
        }
    }
    @Test void simultaneousPurchasesAndRetriesCannotOverspendOrChargeTwice() throws Exception {
        var player=UUID.randomUUID();try(var db=new PointsStore(dir.resolve("points.db"));var jobs=Executors.newVirtualThreadPerTaskExecutor()) {
            fund(db,player,4);var writes=new ArrayList<Future<PointsStore.OutfitResult>>();
            for(int i=0;i<24;i++){boolean steve=i%2==0;writes.add(jobs.submit(()->db.outfit(player,steve?"STEVE":"ALEX",steve?"diamond":"scout",true)));}
            int purchased=0;for(var f:writes)if(f.get()==PointsStore.OutfitResult.PURCHASED)purchased++;
            assertEquals(1,purchased);assertEquals(50,db.account(player).balance());assertEquals(1,db.wardrobe(player).owned().size());
        }
    }
    @Test void failureAfterDebitRollsBackBalanceOwnershipAndEquip() throws Exception {
        var player=UUID.randomUUID();var file=dir.resolve("points.db");
        try(var db=new PointsStore(file);var external=DriverManager.getConnection("jdbc:sqlite:"+file);var sql=external.createStatement()) {
            fund(db,player,4);
            sql.execute("CREATE TRIGGER fail_equip BEFORE INSERT ON equipped_skins BEGIN SELECT RAISE(ABORT,'fixture'); END");
            assertThrows(SQLException.class,()->db.outfit(player,"STEVE","diamond",true));
            assertEquals(300,db.account(player).balance());assertTrue(db.wardrobe(player).owned().isEmpty());
            sql.execute("DROP TRIGGER fail_equip");assertEquals(PointsStore.OutfitResult.PURCHASED,db.outfit(player,"STEVE","diamond",true));
        }
    }
    @Test void skinsSurviveTicketsRematchesAndResultSerializationAndOldRowsLoadDefault() {
        var p=UUID.randomUUID();var t=new Wire.Ticket(p,"SKELETON","DUEL",UUID.randomUUID()).withSkin("frost");
        assertEquals("frost",t.forRematch(UUID.randomUUID()).skin());
        assertEquals(t,Wire.JSON.fromJson(Wire.JSON.toJson(t),Wire.Ticket.class));
        assertThrows(IllegalArgumentException.class,()->t.withSkin("diamond"));
        var row=new Wire.ResultRow(p,"Test","SKELETON",1,1,2,3,4,false,"frost");
        assertEquals(row,Wire.JSON.fromJson(Wire.JSON.toJson(row),Wire.ResultRow.class));
        var old=Wire.JSON.toJsonTree(t).getAsJsonObject();old.remove("skin");
        assertEquals("default",Wire.JSON.fromJson(old,Wire.Ticket.class).skin());
        var oldRow=Wire.JSON.toJsonTree(row).getAsJsonObject();oldRow.remove("skin");
        assertEquals("default",Wire.JSON.fromJson(oldRow,Wire.ResultRow.class).skin());
    }
}
