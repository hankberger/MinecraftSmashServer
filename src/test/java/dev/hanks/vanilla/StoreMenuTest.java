package dev.hanks.vanilla;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StoreMenuTest {
    @Test void verifiedWebsiteIsTheDefaultAndOverridesMustBeSafe() {
        assertEquals(StoreMenu.DEFAULT_URL,StoreMenu.url(null).toString());
        assertEquals(StoreMenu.DEFAULT_URL,StoreMenu.url(" ").toString());
        assertEquals("https://store.example/shop",StoreMenu.url(" https://store.example/shop ").toString());
        for (var unsafe:List.of("http://store.example","javascript:alert(1)","https:/missing-host","https://user:password@store.example"))
            assertThrows(IllegalArgumentException.class,()->StoreMenu.url(unsafe));
    }
    @Test void balanceAndMembershipStatusComeFromThePlayersAccount() {
        var guest=StoreMenu.body(2345,false);
        assertTrue(guest.startsWith("2,345 credits"));
        assertTrue(guest.contains("$7.99 / month"));
        var member=StoreMenu.body(75,true);
        assertTrue(member.startsWith("75 credits"));
        assertTrue(member.contains("Ringshift Plus · Active"));
        assertFalse(member.contains("$7.99"));
        assertTrue(member.contains("while subscribed"));
    }
    @Test void cardsLinkToTheRelevantSectionAndMembersGetOrderManagement() {
        var base=StoreMenu.url(null);
        assertEquals("credits-title",StoreMenu.destination(base,true,false).getFragment());
        assertEquals("membership-title",StoreMenu.destination(base,false,false).getFragment());
        assertEquals("orders",StoreMenu.destination(base,false,true).getQuery());
        assertNull(StoreMenu.destination(base,false,true).getFragment());
        assertEquals("source=game&orders",StoreMenu.destination(StoreMenu.url("https://store.example/shop?source=game"),false,true).getQuery());
    }
}
