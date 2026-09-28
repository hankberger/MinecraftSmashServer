package dev.hanks.vanilla;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FighterMenuTest {
    @Test void queueTimeShowsElapsedTimeWithoutAnEstimate() {
        assertEquals("0:00",FighterMenu.queueTime(-1));
        assertEquals("0:59",FighterMenu.queueTime(1199));
        assertEquals("1:00",FighterMenu.queueTime(1200));
        assertEquals("12:34",FighterMenu.queueTime(15080));
    }
    @Test void allEightPortraitCornersHaveTheirOwnNativeHitRegion() {
        assertEquals(8,FighterMenu.PAGE_SIZE);
        for(int fighter=0;fighter<8;fighter++)for(int dx=0;dx<2;dx++)for(int dy=0;dy<2;dy++) {
            int slot=(2+2*(fighter/4)+dy)*9+2*(fighter%4)+dx;
            assertEquals(fighter,FighterMenu.slotAction(slot));
        }
        for(int row=0;row<6;row++)assertEquals(-1,FighterMenu.slotAction(row*9+8));
        assertEquals(38,FighterMenu.slotAction(87));
        assertEquals(31,FighterMenu.slotAction(74));
        assertEquals(-1,FighterMenu.slotAction(90));
    }
}
