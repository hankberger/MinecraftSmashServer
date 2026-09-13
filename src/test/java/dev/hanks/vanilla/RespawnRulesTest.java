package dev.hanks.vanilla;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RespawnRulesTest {
    @Test void returnDescendsSmoothlyAndStopsOnTheUpperDeck() {
        assertEquals(RespawnRules.TOP_Y, RespawnRules.y(-10));
        double previous = RespawnRules.TOP_Y;
        for (int t = 1; t <= RespawnRules.FLOAT_TICKS; t++) {
            double y = RespawnRules.y(t);
            assertTrue(y <= previous && y >= RespawnRules.LANDING_Y);
            assertTrue(previous - y < .4); previous = y;
        }
        assertEquals(89, RespawnRules.y(100));
        assertTrue(ArenaRules.standingOnPlatform(RespawnRules.X, RespawnRules.LANDING_Y, ArenaRules.PLANE_Z));
    }
    @Test void floatingBlocksHitsAndAttacksThenGivesBriefLandingProtection() {
        var state = new CombatState(); state.respawn(100); state.beginFloat(100);
        for (int t = 100; t < 132; t++) {
            assertFalse(state.beginAttack(t, 1));
            assertNull(state.hit(t, UUID.randomUUID(), 1));
        }
        assertFalse(state.hittable(156)); assertTrue(state.hittable(157));
        assertTrue(state.beginAttack(132, 1)); assertTrue(state.hittable(132));
        state.respawn(200); assertFalse(state.floating(200));
    }
    @Test void protectionFlashesAndFastFallNeverSoftensAHarderDownwardLaunch() {
        assertFalse(RespawnRules.dim(0)); assertTrue(RespawnRules.dim(9)); assertFalse(RespawnRules.dim(13));
        assertTrue(MovementRules.fastFallVelocity(-.4) < -.4);
        assertEquals(-2, MovementRules.fastFallVelocity(-2));
    }
}
