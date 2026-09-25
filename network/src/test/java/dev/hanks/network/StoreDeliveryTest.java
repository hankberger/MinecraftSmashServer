package dev.hanks.network;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoreDeliveryTest {
    @TempDir Path directory;
    @Test void committedDeliverySurvivesRestartAndLostAcknowledgmentWithoutDoubleCredit() throws Exception {
        var file=directory.resolve("points.db");var player=UUID.randomUUID();
        var delivery=new PointsStore.StoreDelivery("order:checkout_1",player,2500,-1,"test");
        try(var store=new PointsStore(file)){assertEquals(2500,store.deliver(delivery).balance());}
        try(var store=new PointsStore(file)){
            assertEquals(2500,store.deliver(delivery).balance());
            assertEquals(0,store.account(player).matches());
            assertThrows(SQLException.class,()->store.deliver(new PointsStore.StoreDelivery(delivery.id(),player,1000,-1,"test")));
            assertEquals(2500,store.account(player).balance());
        }
    }
    @Test void membershipLeasesSkinsAndRevocationPreservesPermanentPurchases() throws Exception {
        var file=directory.resolve("points.db");var player=UUID.randomUUID();var skin=Cosmetics.forFighter("STEVE").get(1);
        try(var store=new PointsStore(file)){
            store.deliver(new PointsStore.StoreDelivery("order:credits",player,5500,-1,"test"));
            assertEquals(PointsStore.OutfitResult.PURCHASED,store.outfit(player,"STEVE",skin.id(),true));
            store.deliver(new PointsStore.StoreDelivery("invoice:month1",player,1000,System.currentTimeMillis()+86400000,"test"));
            assertEquals(5,store.wardrobe(player).owned().size());
        }
        try(var store=new PointsStore(file)){
            assertEquals(5,store.wardrobes().get(player).owned().size());
            store.deliver(new PointsStore.StoreDelivery("membership:revoke",player,0,0,"test"));
            assertEquals(1,store.wardrobe(player).owned().size());assertTrue(store.wardrobe(player).owns(skin));
        }
    }
    @Test void memberWithoutEquippedSkinLoadsAtStartupAndExpiryRemovesOnlyLeasedAccess() throws Exception {
        var file=directory.resolve("points.db");var player=UUID.randomUUID();
        try(var store=new PointsStore(file)){
            store.deliver(new PointsStore.StoreDelivery("invoice:member",player,1000,System.currentTimeMillis()+86400000,"test"));
            assertEquals(5,store.wardrobes().get(player).owned().size());
            store.deliver(new PointsStore.StoreDelivery("membership:expired",player,0,System.currentTimeMillis()-1,"test"));
            assertTrue(store.wardrobe(player).owned().isEmpty());assertEquals(1000,store.account(player).balance());
        }
    }
    @Test void malformedAndLiveDeliveriesAreRejected(){
        var p=UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,()->new PointsStore.StoreDelivery("order:x",p,1000,-1,"live"));
        assertThrows(IllegalArgumentException.class,()->new PointsStore.StoreDelivery("order:x",p,-1,-1,"test"));
        assertThrows(IllegalArgumentException.class,()->new PointsStore.StoreDelivery("order:x",p,6000,-1,"test"));
    }
}
