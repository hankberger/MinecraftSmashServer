package dev.hanks.vanilla;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MovementControlTest {
    private double peak(int heldTicks) {
        var jump = new JumpHeight(); jump.start();
        double y = 0, peak = 0, vy = MovementRules.JUMP;
        for (int tick = 0; tick < 40 && (tick == 0 || y > 0); tick++) {
            vy = MovementRules.gravity(jump.apply(vy, tick < heldTicks)); y += vy; peak = Math.max(y, peak);
        }
        return peak;
    }
    @Test void tapHopIsLowButHoldingStillClearsThePlatforms() {
        assertTrue(peak(1) > 1 && peak(1) < 2);
        assertTrue(peak(2) < 3);
        assertTrue(peak(40) > 4.5);
        assertTrue(peak(1) < peak(3) && peak(3) < peak(40));
    }
    @Test void releasingJumpCannotTrimHitLaunchRecoveryOrAlreadyFalling() {
        var jump = new JumpHeight();
        assertEquals(1.4, jump.apply(1.4, false));
        jump.start(); jump.clear(); assertEquals(2.1, jump.apply(2.1, false));
        jump.start(); assertEquals(-.6, jump.apply(-.6, false));
        assertEquals(1.4, jump.apply(1.4, false));
        jump.start(); jump.apply(.9, false); assertEquals(.8, jump.apply(.8, false), "Only trim once");
    }
    @Test void holdingSpaceThroughTakeoffOrLandingDoesNotGenerateAnotherAction() {
        var jump = new JumpIntent();
        jump.observe(true, false, true, 1); assertTrue(jump.pending(1)); jump.consume();
        jump.observe(true, true, false, 2); assertFalse(jump.pending(2));
        jump.observe(false, true, false, 3);
        jump.observe(true, false, false, 4); assertTrue(jump.pending(4)); jump.consume();
        jump.observe(true, true, false, 5); assertFalse(jump.pending(5));
        jump.observe(true, true, true, 20); assertFalse(jump.pending(20), "Landing while holding jump does not bounce automatically");
        jump.observe(false, true, true, 21);
        jump.observe(true, false, true, 22); assertTrue(jump.pending(22)); assertTrue(jump.groundJump(true,22));
    }
    @Test void onlyDirectionlessAerialsUseNairAndGroundStillUsesForward() {
        assertEquals(AttackDirection.NEUTRAL, AttackDirection.input(false, false, false, false));
        assertEquals(AttackDirection.FORWARD, AttackDirection.input(false, false, true, false));
        assertEquals(AttackDirection.UP, AttackDirection.input(true, false, true, false));
        assertEquals(AttackDirection.DOWN, AttackDirection.input(false, true, false, true));
        for (var kind : FighterClass.values()) {
            assertEquals(FighterMoves.light(kind, AttackDirection.FORWARD, false), FighterMoves.light(kind, AttackDirection.NEUTRAL, false));
            var nair = FighterMoves.light(kind, AttackDirection.NEUTRAL, true);
            var shape = CombatGeometry.shape(nair, 1, 0, 0);
            assertNotNull(shape.contact(CombatGeometry.body(-1, 0, .6, 1.8)));
            assertNotNull(shape.contact(CombatGeometry.body(1, 0, .6, 1.8)));
            assertNull(shape.contact(CombatGeometry.body(2, 0, .6, 1.8)));
            assertTrue(nair.startup() + FighterMoves.activeTicks(nair) < nair.lockout());
        }
    }
    @Test void airShieldIsOneBriefWindowWithSharedEnergyAndNoHitstunCancel() {
        var state = new CombatState();
        assertTrue(state.requestGuard(0, true, false));
        for (int t = 0; t < CombatState.AIR_GUARD_TICKS; t++) {
            assertTrue(state.requestGuard(t, true, false)); assertTrue(state.blocking(t)); state.tickGuard(t, true);
        }
        assertEquals(86, state.guard);
        assertFalse(state.requestGuard(7, true, false)); assertFalse(state.blocking(7));
        state.requestGuard(8, false, false); assertFalse(state.requestGuard(9, true, false));
        state.requestGuard(10, false, true); assertTrue(state.requestGuard(11, true, false));
        assertTrue(state.receiveHit(12, UUID.randomUUID(), 1, AttackKind.LIGHT).blocked());
        state.requestGuard(13, false, false); assertFalse(state.requestGuard(14, true, false), "Release ends the window");
        state.respawn(20); state.hit(20, UUID.randomUUID(), 1); assertFalse(state.requestGuard(21, true, false));
    }
    @Test void bufferKeepsAimAndFacingUntilReadyThenExpiresOrClearsOnHit() {
        var state = new CombatState(); state.readyAt = 10;
        var input = new AttackIntent(AttackKind.LIGHT, AttackDirection.DOWN, 0, false, 0, -1);
        assertFalse(state.buffer(6, input)); assertTrue(state.buffer(7, input));
        assertEquals(input, state.pending(10)); assertNull(state.pending(11));
        state.readyAt = 20; assertTrue(state.buffer(18, input));
        assertTrue(state.beginMove(20, state.pending(20).facing(), FighterMoves.light(FighterClass.STEVE, state.pending(20).direction(), false)));
        assertNull(state.pending(20)); assertEquals(-1, state.attackDirection);
        state.respawn(30); state.requestGuard(30, true, true); assertTrue(state.buffer(30, input));
        assertEquals(input, state.pending(32)); assertNull(state.pending(34));
        state.requestGuard(35, false, true); state.readyAt = 38; assertTrue(state.buffer(35, input));
        state.hit(36, UUID.randomUUID(), 1); assertNull(state.pending(36));
    }
    @Test void bufferedBowReleaseSurvivesButDrawingDoesNotQueueAnotherShot() {
        var state = new CombatState(); state.fighterClass = FighterClass.SKELETON;
        state.readyAt = 3;
        var released = new AttackIntent(AttackKind.HEAVY, AttackDirection.FORWARD, 0, true, 0, 1);
        assertTrue(state.buffer(1, released)); assertTrue(state.pending(3).release());
        state.beginMove(3, 1, FighterMoves.special(FighterClass.SKELETON, false, false));
        assertFalse(state.buffer(3, released));
    }
}
