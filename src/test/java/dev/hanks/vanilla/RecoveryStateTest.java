package dev.hanks.vanilla;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryStateTest {
    @Test void fastFallRequiresDescentAndDoesNotStackOrConsumeAirJump() {
        var state = new RecoveryState();
        assertFalse(state.fastFall(true, -.2));
        assertFalse(state.fastFall(false, .5));
        assertFalse(state.fastFall(false, Double.NaN));
        assertTrue(state.fastFall(false, -.1));
        for (int i = 0; i < 40; i++) assertFalse(state.fastFall(false, -.2));
        assertTrue(state.available());
        assertTrue(state.jump(false)); assertFalse(state.fastFalling());
        assertTrue(state.fastFall(false, -.1));
        state.grounded(true, 10); assertFalse(state.fastFalling());
        assertTrue(state.available());
    }
    @Test void airJumpCannotBeSpammedAndLandingRestoresIt() {
        RecoveryState state = new RecoveryState();
        assertFalse(state.jump(true)); assertTrue(state.jump(false));
        for (int i = 0; i < 40; i++) { state.grounded(false, i); assertFalse(state.jump(false)); }
        state.grounded(true, 41); assertTrue(state.jump(false));
    }
    @Test void dropDoesNotRestoreSpentJumpFromAStaleGroundFlag() {
        RecoveryState state = new RecoveryState(); state.jump(false); state.drop(10);
        state.grounded(true, 11); assertFalse(state.available()); assertTrue(state.dropping(21));
        assertFalse(state.dropping(22)); state.grounded(true, 22); assertTrue(state.available());
        state.drop(23); state.reset(); assertFalse(state.dropping(24));
    }
    @Test void onlyRaisedDecksAllowDropping() {
        assertTrue(ArenaRules.standingOnPlatform(-8, 85, .5));
        assertTrue(ArenaRules.standingOnPlatform(0, 89, .5));
        assertFalse(ArenaRules.standingOnPlatform(0, 81, .5));
        assertFalse(ArenaRules.standingOnPlatform(0, 85, .5));
        assertFalse(ArenaRules.standingOnPlatform(-8, 84, .5));
        assertFalse(ArenaRules.platform(-8, 84, -10));
    }
}
