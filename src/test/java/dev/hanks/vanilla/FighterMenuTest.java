package dev.hanks.vanilla;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FighterMenuTest {
    @Test void paginatesGrowingRosterAndWrapsInBothDirections() {
        assertEquals(1,FighterMenu.pageCount(5));
        assertEquals(1,FighterMenu.pageCount(6));
        assertEquals(2,FighterMenu.pageCount(7));
        assertEquals(9,FighterMenu.pageCount(50));
        assertEquals(8,FighterMenu.pageStep(0,-1,50));
        assertEquals(0,FighterMenu.pageStep(8,1,50));
        assertEquals(0,FighterMenu.pageStep(0,-1,5));
    }
}
