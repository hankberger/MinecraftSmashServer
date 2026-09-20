package dev.hanks.vanilla;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LedgeStateTest {
    @Test void descendingSweepsCatchBothEdgesWithoutPullingFromUnderTheIsland() {
        for (int side : new int[]{-1, 1}) {
            var ledge = new LedgeState(); double x = LedgeState.hangX(side);
            assertEquals(side, ledge.candidate(10, x, 81, x, 79, -2));
            assertEquals(side, ledge.candidate(10, x, 82, x, 77, -5), "Fast downward crossings do not tunnel through the grab area");
            assertEquals(side, ledge.candidate(10, x, 79.5, LedgeState.edge(side), 79, -.5), "Inward drift can catch the lip");
            assertEquals(0, ledge.candidate(10, x, 79, x, 80, 1), "Rising recovery is never interrupted by a grab");
            assertEquals(0, ledge.candidate(10, LedgeState.standX(side), 79, x, 78.5, -.5));
            assertEquals(0, ledge.candidate(10, x + side * 4, 80, x + side * 4, 79, -1));
            assertEquals(0, ledge.candidate(10, x, 76, x, 75, -1));
        }
        assertEquals(0, new LedgeState().candidate(10, Double.NaN, 80, 0, 79, -1));
    }
    @Test void controlsSettleThenAllowJumpClimbOrDropAndHangingTimesOut() {
        for (int side : new int[]{-1, 1}) {
            var ledge = new LedgeState(); ledge.grab(side, 10);
            assertEquals(LedgeState.Action.NONE, ledge.action(11, true, false, -side));
            assertEquals(LedgeState.Action.JUMP, ledge.action(14, true, false, -side));
            assertEquals(LedgeState.Action.DROP, ledge.action(14, false, true, 0));
            assertEquals(LedgeState.Action.CLIMB, ledge.action(14, false, false, -side));
            assertEquals(LedgeState.Action.NONE, ledge.action(14, false, false, side));
            assertEquals(LedgeState.Action.DROP, ledge.action(10 + LedgeState.HANG_TICKS, false, false, 0));
        }
    }
    @Test void climbingClearsTheLipBeforeMovingOntoTheDeck() {
        for (int side : new int[]{-1, 1}) {
            var ledge = new LedgeState(); ledge.grab(side, 0); ledge.climb(4);
            assertEquals(LedgeState.hangX(side), ledge.x(4));
            assertEquals(LedgeState.HANG_Y, ledge.y(4));
            assertEquals(LedgeState.hangX(side), ledge.x(8));
            assertEquals(ArenaRules.DECK_Y, ledge.y(8));
            assertFalse(ledge.climbFinished(11)); assertTrue(ledge.climbFinished(12));
            assertEquals(LedgeState.standX(side), ledge.x(12));
            assertEquals(ArenaRules.DECK_Y, ledge.y(12));
            assertEquals(LedgeState.Action.NONE, ledge.action(8, true, true, -side));
        }
    }
    @Test void regrabsHaveNoNewProtectionAndCannotContinueUntilLanding() {
        var ledge = new LedgeState(); double x = LedgeState.hangX(1);
        ledge.grab(1, 0); assertTrue(ledge.firstGrab()); ledge.release(4);
        assertEquals(0, ledge.candidate(15, x, 80, x, 79, -1));
        assertEquals(1, ledge.candidate(16, x, 80, x, 79, -1));
        ledge.grab(1, 16); assertFalse(ledge.firstGrab()); ledge.release(20);
        assertEquals(0, ledge.candidate(50, x, 80, x, 79, -1));
        double other = LedgeState.hangX(-1);
        assertEquals(0, ledge.candidate(50, other, 80, other, 79, -1), "Changing edges cannot reset the limit");
        ledge.land(); assertEquals(1, ledge.candidate(51, x, 80, x, 79, -1));
        ledge.grab(1, 51); assertTrue(ledge.firstGrab());
    }
    @Test void blastZonesLeaveRoomOffstageButStillHaveDefiniteLimits() {
        assertFalse(ArenaRules.outside(-32, 64, .5)); assertFalse(ArenaRules.outside(33, 64, .5));
        assertTrue(ArenaRules.DECK_LEFT - ArenaRules.BLAST_LEFT >= 20);
        assertTrue(ArenaRules.BLAST_RIGHT - ArenaRules.DECK_RIGHT >= 20);
        assertTrue(ArenaRules.DECK_Y - ArenaRules.BLAST_BOTTOM >= 26);
        assertTrue(ArenaRules.outside(ArenaRules.BLAST_LEFT - .01, 81, .5));
        assertTrue(ArenaRules.outside(ArenaRules.BLAST_RIGHT + .01, 81, .5));
        assertTrue(ArenaRules.outside(0, ArenaRules.BLAST_BOTTOM - .01, .5));
        assertTrue(ArenaRules.outside(0, ArenaRules.BLAST_TOP + .01, .5));
        assertTrue(ArenaRules.outside(Double.NaN, 81, .5));
        double y = 81, vy = 0; int ticks = 0;
        while (!ArenaRules.outside(22, y, .5)) { vy = MovementRules.gravity(vy); y += vy; ticks++; }
        assertTrue(ticks >= 20, "Walking off leaves over a second for a recovery decision");
    }
}
