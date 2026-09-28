package dev.hanks.vanilla;

import dev.hanks.network.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PointsShopTest {
    @Test void storeMustBeExplicitAndHttps() {
        assertTrue(PointsShop.url(null).isEmpty());
        assertTrue(PointsShop.url("  ").isEmpty());
        assertEquals("https://store.example/points",PointsShop.url(" https://store.example/points ").orElseThrow().toString());
        for(var url:List.of("http://store.example","javascript:alert(1)","https:/missing-host","https://user:password@store.example","not a URL"))
            assertThrows(IllegalArgumentException.class,()->PointsShop.url(url));
    }
    @Test void lockedSkinUsesActualDiscountAndShortfall() {
        var body=PointsShop.body(100,Cosmetics.Wardrobe.EMPTY,Cosmetics.skin("STEVE","diamond"),false);
        assertTrue(body.contains("Balance: 100 Points"));
        assertTrue(body.contains("Lumberjack · 750 Points · 50% off"));
        assertTrue(body.contains("650 more to unlock"));
        assertTrue(body.contains("coming soon"));
        assertFalse(body.contains("online store"));
    }
    @Test void existingBuyerSeesStandardPriceAndOwnedSkinHasNoUpsellShortfall() {
        var wardrobe=new Cosmetics.Wardrobe(Set.of("diamond"),Map.of("STEVE","diamond"));
        var locked=PointsShop.body(250,wardrobe,Cosmetics.skin("ALEX","scout"),true);
        assertTrue(locked.contains("Gardener · 1,500 Points"));
        assertTrue(locked.contains("1,250 more to unlock"));
        assertFalse(locked.contains("50% off"));
        assertTrue(locked.contains("online store"));
        var owned=PointsShop.body(0,wardrobe,Cosmetics.skin("STEVE","diamond"),true);
        assertFalse(owned.contains("more to unlock"));
        assertTrue(owned.contains("50 per qualifying match · +25 for a win"));
    }
}
