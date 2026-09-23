package dev.hanks.vanilla;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LobbyRulesTest {
    @Test void gardenAndSatellitesAreExplorable() {
        for (double x : new double[]{-112, -77, .5, 77, 112})
            for (double z : new double[]{-112, -62, -2.5, 59, 104})
                for (double y : new double[]{99, 101, 113, 119, 233})
                    assertFalse(LobbyRules.needsReturn(x, y, z));
    }
    @Test void fallsAndEscapesReturnToCourtyardSpawn() {
        assertEquals(LobbyRules.SPAWN_X,LobbyRules.RESCUE_X);
        assertEquals(LobbyRules.SPAWN_Y,LobbyRules.RESCUE_Y);
        assertEquals(LobbyRules.SPAWN_Z,LobbyRules.RESCUE_Z);
        assertTrue(LobbyRules.needsReturn(77, 91.9, 59));
        assertTrue(LobbyRules.needsReturn(0, -100, 0));
        assertTrue(LobbyRules.needsReturn(129, 101, 0));
        assertTrue(LobbyRules.needsReturn(0, 101, -129));
        assertTrue(LobbyRules.needsReturn(0, 101, 121));
        assertTrue(LobbyRules.needsReturn(0, 251, 0));
        assertTrue(LobbyRules.needsReturn(Double.NaN, 101, 0));
        assertTrue(LobbyRules.needsReturn(0, Double.NEGATIVE_INFINITY, 0));
        assertTrue(LobbyRules.needsReturn(0, 101, Double.POSITIVE_INFINITY));
    }
}
