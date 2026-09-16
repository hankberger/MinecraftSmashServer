package dev.hanks.vanilla;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FighterMenuTest {
    @Test void paginatesGrowingRosterAndWrapsInBothDirections() {
        assertEquals(1,FighterMenu.pageCount(5));
        assertEquals(1,FighterMenu.pageCount(12));
        assertEquals(2,FighterMenu.pageCount(13));
        assertEquals(5,FighterMenu.pageCount(50));
        assertEquals(4,FighterMenu.pageStep(0,-1,50));
        assertEquals(0,FighterMenu.pageStep(4,1,50));
        assertEquals(0,FighterMenu.pageStep(0,-1,5));
    }
    @Test void fourPortraitsPerRowStayInsideTheChestAndNeverOverlap() {
        var used=new java.util.HashSet<Integer>();
        for(int i=0;i<FighterMenu.PAGE_SIZE;i++) {
            int first=FighterMenu.portraitSlot(i);
            for(int offset:new int[]{0,1,9,10}) {
                assertTrue(first+offset<54,"Portraits cannot enter mode or action rows");
                assertTrue(used.add(first+offset),"Portrait hit regions must be distinct");
            }
        }
        assertEquals(6,FighterMenu.portraitSlot(3));
        assertEquals(18,FighterMenu.portraitSlot(4));
    }
}
