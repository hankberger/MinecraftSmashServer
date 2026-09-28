package dev.hanks.vanilla;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryChallengeTest {
    @Test void requiresRecoveryAndSafeLandingThenAlternatesSidesUntilThreeSuccesses() {
        var c = new RecoveryChallenge(0);
        assertTrue(c.frozen()); assertFalse(c.tick(39, true, true, false));
        assertTrue(c.tick(40, false, false, false)); assertEquals(1, c.attempts());
        assertFalse(c.tick(41, true, false, false));
        assertFalse(c.tick(42, false, true, false));
        assertTrue(c.tick(43, true, true, false)); assertEquals(1, c.completed()); assertEquals(1, c.side());
        c.tick(63, false, false, false); c.tick(64, true, true, false);
        assertEquals(-1, c.side());
        c.tick(84, false, false, false); c.tick(85, true, true, false);
        assertEquals(3, c.completed()); assertEquals(3, c.attempts()); assertFalse(c.active()); assertFalse(c.frozen());
        assertFalse(c.tick(1000, false, false, true));
    }
    @Test void fallAndTimeoutRetryWithoutGivingCreditOrChangingTheExercise() {
        var c = new RecoveryChallenge(100); c.tick(140, false, false, false);
        c.tick(150, false, false, true);
        assertEquals(RecoveryChallenge.Phase.RETRY, c.phase()); assertEquals(0, c.completed());
        assertFalse(c.tick(169, false, false, false)); c.tick(170, false, false, false);
        assertEquals(2, c.attempts()); assertEquals(-1, c.side());
        c.tick(330, false, false, false); assertEquals(RecoveryChallenge.Phase.RETRY, c.phase());
        assertEquals(0, c.completed());
    }
}
