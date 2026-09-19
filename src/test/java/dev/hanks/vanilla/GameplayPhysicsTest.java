package dev.hanks.vanilla;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameplayPhysicsTest {
    @Test void jumpClearsFourBlockPlatformsAndReturnsInAboutASecond() {
        double y = 0, peak = 0, vy = MovementRules.JUMP;
        int ticks = 0;
        do {
            vy = MovementRules.gravity(vy); y += vy; peak = Math.max(peak, y); ticks++;
        } while (y > 0 && ticks < 100);
        assertTrue(peak > 4.5 && peak < 5.5, "A normal jump must comfortably clear the next platform");
        assertTrue(ticks >= 18 && ticks <= 23, "Remove the old long hang time without shrinking jump height");
    }

    @Test void releasingMovementStopsPromptlyAndNormalMovementNeedsNoSprintKey() {
        double vx = MovementRules.RUN_SPEED;
        for (int i = 0; i < 3; i++) vx = MovementRules.steer(vx, 0, false, true, 1, 1, 1);
        assertTrue(vx < .02);
        double reverse = MovementRules.steer(MovementRules.RUN_SPEED, -1, false, true, 1, 1, 1);
        assertTrue(reverse < 0, "Ground direction changes take effect on the next tick");
        assertEquals(.50, MovementRules.RUN_SPEED);
        for (var kind : FighterClass.values())
            assertEquals(MovementRules.steer(.1, 1, true, true, FighterMoves.run(kind), FighterMoves.air(kind), 1),
                    MovementRules.steer(.1, 1, false, true, FighterMoves.run(kind), FighterMoves.air(kind), 1));
        assertTrue(MovementRules.steer(0, 1, false, false, 1, 1, 1) < MovementRules.steer(0, 1, false, true, 1, 1, 1));
    }

    @Test void fastFallIsFasterAndNeitherGravityNorFastFallSoftensASpike() {
        assertTrue(MovementRules.fastFallVelocity(MovementRules.gravity(-.3)) < MovementRules.gravity(-.3));
        assertEquals(-3.5, MovementRules.gravity(-3.5));
        assertEquals(-3.5, MovementRules.fastFallVelocity(-3.5));
    }

    @Test void ordinaryHitsAllowRecoveryButHighDamageSpecialsCrossEitherSideBeforeStunEnds() {
        for (var kind : new FighterClass[]{FighterClass.STEVE, FighterClass.ALEX, FighterClass.ZOMBIE, FighterClass.VILLAGER}) {
            var move = kind == FighterClass.VILLAGER ? FighterMoves.bell() : FighterMoves.special(kind, false, false);
            for (int direction : new int[]{-1, 1}) {
                assertFalse(exitsDuringStun(move.launch(25, direction, .9)), kind + " low damage");
                assertTrue(exitsDuringStun(move.launch(180, direction, .9)), kind + " high damage");
            }
        }
    }

    @Test void everyClassCanFinishUpwardAndUpStunGrowsWithDamage() {
        for (var kind : FighterClass.values()) {
            var up = FighterMoves.light(kind, AttackDirection.UP, false);
            var low = up.launch(25, 1, .9); var high = up.launch(240, 1, .9);
            assertTrue(high.stun() > low.stun());
            assertFalse(exitsDuringStun(low));
            double y = 81, vy = high.y();
            for (int t = 0; t < high.stun(); t++) { vy = MovementRules.gravity(vy); y += vy; if (y > 105) break; }
            assertTrue(y > 105, kind + " can launch through the upper blast zone from the main deck");
        }
    }

    @Test void hardLaunchBlocksRecoveryAndRespawnClearsItWithoutChangingStockCounters() {
        var state = new CombatState(); state.percent = 180;
        var attacker = UUID.randomUUID();
        var launch = state.receiveHit(10, attacker, 1, FighterMoves.special(FighterClass.STEVE, false, false)).launch();
        assertTrue(state.strongLaunch);
        for (int t = 10; t < 10 + launch.stun(); t++)
            assertFalse(state.beginMove(t, -1, FighterMoves.recovery(FighterClass.ALEX)));
        assertEquals(attacker, state.creditedAttacker(20));
        state.falls = 1; state.respawn(50);
        assertEquals(0, state.launchUntil); assertFalse(state.strongLaunch); assertEquals(1, state.falls);
        assertTrue(state.beginMove(50, -1, FighterMoves.recovery(FighterClass.ALEX)));
    }

    @Test void percentageAloneDoesNotKOAndShieldStillStopsAFinishingHit() {
        var state = new CombatState(); state.percent = 300;
        assertEquals(0, state.falls); assertEquals(0, state.launchUntil);
        assertTrue(state.requestGuard(10, true, true));
        assertTrue(state.receiveHit(11, UUID.randomUUID(), 1, FighterMoves.special(FighterClass.STEVE, false, false)).blocked());
        assertEquals(300, state.percent); assertEquals(0, state.launchUntil); assertFalse(state.strongLaunch);
    }

    private static boolean exitsDuringStun(CombatRules.Launch launch) {
        double x = .5, y = 81, vx = launch.x(), vy = launch.y();
        for (int t = 0; t < launch.stun(); t++) {
            vx *= MovementRules.LAUNCH_DRAG; vy = MovementRules.gravity(vy); x += vx; y += vy;
            if (ArenaRules.outside(x, y, .5)) return true;
            if (y <= 81 && x >= -16.3 && x <= 17.3) return false;
        }
        return false;
    }
}
