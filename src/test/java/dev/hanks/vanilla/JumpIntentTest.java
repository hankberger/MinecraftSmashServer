package dev.hanks.vanilla;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JumpIntentTest {
    @Test void earlyPressSurvivesLandingButExpiresQuickly() {
        var jump = new JumpIntent();
        jump.observe(true, false, false, 100);
        jump.observe(false, true, true, 102);
        assertTrue(jump.pending(102)); assertTrue(jump.groundJump(true, 102));
        assertFalse(jump.pending(104));
    }
    @Test void edgeGraceDoesNotSpendAirJumpAndCannotBeRepeated() {
        var jump = new JumpIntent(); var recovery = new RecoveryState();
        jump.observe(false, false, true, 100);
        jump.observe(true, false, false, 102);
        assertTrue(jump.pending(102)); assertTrue(jump.groundJump(false, 102)); assertTrue(recovery.available());
        jump.consume(); jump.observe(true, true, false, 103);
        assertFalse(jump.pending(103)); assertFalse(jump.groundJump(false, 103));
        assertTrue(recovery.jump(false)); assertFalse(recovery.jump(false));
    }
    @Test void intentionalDropHitOrRecoveryClearsGraceAndBufferedPress() {
        var jump = new JumpIntent(); jump.observe(true, false, true, 50); jump.clear();
        assertFalse(jump.pending(51)); assertFalse(jump.groundJump(false, 51));
        jump.observe(false, false, true, 100); assertFalse(jump.groundJump(false, 103));
    }
}
