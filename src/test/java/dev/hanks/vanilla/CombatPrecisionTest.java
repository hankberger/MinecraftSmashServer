package dev.hanks.vanilla;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatPrecisionTest {
    @Test void visibleArcIsTheBoundaryOfTheActualStrikeForEveryMeleeMove() {
        for (var kind : FighterClass.values()) for (var direction : AttackDirection.values()) for (boolean air : new boolean[]{false, true}) {
            var move = FighterMoves.light(kind, direction, air);
            for (int facing : new int[]{-1, 1}) {
                var shape = CombatGeometry.shape(move, facing, 10, 80);
                for (int i = 0; i <= 12; i++) {
                    var p = shape.arc(i / 12.0);
                    assertNotNull(shape.contact(new CombatGeometry.Box(p.x() - .001, p.y() - .001, p.x() + .001, p.y() + .001)));
                    double x = shape.x() + (p.x() - shape.x()) * 1.03, y = shape.y() + (p.y() - shape.y()) * 1.03;
                    assertNull(shape.contact(new CombatGeometry.Box(x, y, x, y)), "No invisible reach outside the visible arc");
                }
            }
        }
    }
    @Test void noMoreRectangularCornerHitsAndForwardDoesNotHitBehind() {
        var move = FighterMoves.light(FighterClass.STEVE, AttackDirection.FORWARD, false);
        var shape = CombatGeometry.shape(move, 1, 0, 0);
        assertNull(shape.contact(new CombatGeometry.Box(2.4, 1.6, 2.7, 1.9)));
        assertNotNull(shape.contact(new CombatGeometry.Box(2.4, .7, 2.7, 1.2)));
        assertNull(shape.contact(CombatGeometry.body(-1, 0, .6, 1.8)));
        assertNull(CombatGeometry.shape(move, -1, 0, 0).contact(CombatGeometry.body(1, 0, .6, 1.8)));
    }
    @Test void overheadDownwardAndLowStrikesOccupyDifferentSpaces() {
        var up = CombatGeometry.shape(FighterMoves.light(FighterClass.STEVE, AttackDirection.UP, false), 1, 0, 0);
        var down = CombatGeometry.shape(FighterMoves.light(FighterClass.STEVE, AttackDirection.DOWN, true), 1, 0, 0);
        var low = CombatGeometry.shape(FighterMoves.light(FighterClass.STEVE, AttackDirection.DOWN, false), 1, 0, 0);
        assertNotNull(up.contact(CombatGeometry.body(0, 3, .6, 1.8)));
        assertNull(up.contact(CombatGeometry.body(1.6, 3, .6, 1.8)));
        assertNotNull(down.contact(CombatGeometry.body(0, -2, .6, 1.8)));
        assertNull(down.contact(CombatGeometry.body(0, 1, .6, 1.8)));
        assertNotNull(low.contact(CombatGeometry.body(1.7, 0, .6, 1.8)));
        assertNull(low.contact(CombatGeometry.body(1.7, .8, .6, 1.8)));
    }
    @Test void overheadLightsCatchNearbyJumpersButNeedAJumpToReachTheNextPlatform() {
        for (var kind : FighterClass.values()) for (boolean air : new boolean[]{false, true}) {
            var move = FighterMoves.light(kind, AttackDirection.UP, air);
            var shape = CombatGeometry.shape(move, 1, 0, 0);
            assertTrue(shape.bounds().maxY() >= 3.1 && shape.bounds().maxY() <= 3.5);
            assertNotNull(shape.contact(CombatGeometry.body(0, 3, .6, 1.8)));
            assertNull(shape.contact(CombatGeometry.body(0, 4, .6, 1.8)), "No more full-platform-height hit from standing still");
            assertNotNull(CombatGeometry.shape(move, 1, 0, 1).contact(CombatGeometry.body(0, 4, .6, 1.8)), "Jump timing brings the platform target into range");
        }
    }
    @Test void fastCrossingHitsButParallelMotionDoesNotInventContact() {
        var move = FighterMoves.light(FighterClass.ALEX, AttackDirection.FORWARD, false);
        assertNotNull(CombatGeometry.sweep(move, 1, 0, 0, 0, 0, 4, 0, -3, 0, .6, 1.8));
        assertNull(CombatGeometry.sweep(move, 1, 0, 0, 8, 0, 4, 0, 12, 0, .6, 1.8));
        assertNull(CombatGeometry.sweep(move, 1, 0, 0, 0, 0, 4, 4, -3, 4, .6, 1.8));
    }
    @Test void facingLocksDuringStartupAndContactButNotAfterInterruption() {
        var state = new CombatState();
        state.beginMove(0, -1, FighterMoves.light(FighterClass.STEVE, AttackDirection.FORWARD, false));
        assertTrue(state.facingLocked(1));
        state.impactAt = -1; state.activeStartedAt = 3; state.activeUntil = 5;
        assertTrue(state.facingLocked(4)); assertFalse(state.facingLocked(5));
        state.interrupt(); assertFalse(state.facingLocked(4));
        state.respawn(10); state.fighterClass = FighterClass.SKELETON;
        state.beginMove(10, 1, FighterMoves.special(FighterClass.SKELETON, false, false));
        assertFalse(state.facingLocked(11), "Drawing the bow can still turn");
        state.chargeReleased = true; assertTrue(state.facingLocked(11), "Release captures its direction");
    }
    @Test void hitPausePreservesRecoveryAndLaunchDurationWithoutStacking() {
        var state = new CombatState();
        state.beginMove(0, 1, FighterMoves.special(FighterClass.STEVE, false, false));
        state.impactAt = -1; state.activeStartedAt = 4; state.activeUntil = 6;
        state.pause(4, 2); state.pause(4, 2);
        assertEquals(20, state.readyAt); assertEquals(8, state.activeUntil);
        assertTrue(state.paused(5)); assertTrue(state.paused(6)); assertFalse(state.paused(7));
        assertFalse(state.beginMove(5, -1, FighterMoves.light(FighterClass.ALEX, AttackDirection.UP, false)));
        var defender = new CombatState();
        var launch = defender.receiveHit(4, UUID.randomUUID(), 1, FighterMoves.special(FighterClass.STEVE, false, false)).launch();
        defender.pause(4, 2);
        assertEquals(4 + launch.stun() + 2, defender.stunUntil);
        assertEquals(defender.stunUntil, defender.launchUntil);
        defender.respawn(5); assertFalse(defender.paused(5));
    }
    @Test void presentationTracksNativeInterpolationAndSettlesInsteadOfDrifting() {
        var pose = new CombatPose(0, 81);
        pose.advance(.6, 81); assertEquals(.2, pose.x, .001);
        pose.advance(1.2, 81); assertEquals(1.0 / 3 + .2, pose.x, .001);
        pose.advance(1.2, 81); pose.advance(1.2, 81); assertEquals(1.2, pose.x, .001);
        for (int i = 0; i < 100; i++) pose.advance(1.2, 81);
        assertEquals(1.2, pose.x, .001);
        pose.reset(-10, 90); assertEquals(-10, pose.previousX); assertEquals(90, pose.y);
    }
}
