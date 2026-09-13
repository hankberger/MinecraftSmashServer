package dev.hanks.vanilla;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatStateTest {
    @Test void repeatedRequestsCannotRestartWindupOrBypassCooldown() {
        var state = new CombatState();
        state.respawn(100);
        assertTrue(state.beginAttack(100, -1));
        assertEquals(103, state.impactAt);
        assertEquals(0, state.protectedUntil);
        assertEquals(112, state.readyAt);
        state.impactAt = -1; // Even after impact, recovery rejects early re-presses.
        for (int tick = 103; tick < 112; tick++) assertFalse(state.beginAttack(tick, 1));
        assertEquals(-1, state.attackDirection);
        state.impactAt = -1;
        assertTrue(state.beginAttack(112, 1));
    }
    @Test void hitsAccumulatePercentAndScaleLaunchInBothDirections() {
        var state = new CombatState();
        var id = UUID.randomUUID();
        var first = state.hit(0, id, 1);
        assertEquals(8, state.percent);
        var second = state.hit(4, id, 1);
        assertEquals(16, state.percent);
        assertTrue(second.x() > first.x());
        assertTrue(second.y() > first.y());
        var mirrored = CombatRules.launch(16, -1);
        assertEquals(-second.x(), mirrored.x());
        assertEquals(second.y(), mirrored.y());
        assertEquals(1, CombatRules.direction(-90));
        assertEquals(-1, CombatRules.direction(90));
    }
    @Test void hitstunInterruptsStartupAndRejectsAttacksUntilItExpires() {
        var state = new CombatState();
        assertTrue(state.beginAttack(0, 1));
        var hit = state.hit(1, UUID.randomUUID(), -1);
        assertEquals(-1, state.impactAt);
        assertFalse(state.beginAttack(2, 1));
        assertTrue(state.beginAttack(Math.max(state.readyAt, 1 + hit.stun()), 1));
    }
    @Test void hitImmunityRejectsSimultaneousDuplicateDamage() {
        var state = new CombatState();
        assertNotNull(state.hit(10, UUID.randomUUID(), 1));
        for (int tick = 10; tick < 14; tick++) assertNull(state.hit(tick, UUID.randomUUID(), 1));
        assertEquals(8, state.percent);
        assertNotNull(state.hit(14, UUID.randomUUID(), 1));
    }
    @Test void ringoutCreditExpiresAndRespawnClearsAllTransientCombatState() {
        var state = new CombatState();
        var attacker = UUID.randomUUID();
        state.hit(5, attacker, 1);
        assertEquals(attacker, state.creditedAttacker(205));
        assertNull(state.creditedAttacker(206));
        state.knockouts = 2;
        state.falls = 3;
        state.respawn(300);
        assertEquals(0, state.percent);
        assertEquals(0, state.stunUntil);
        assertEquals(-1, state.impactAt);
        assertNull(state.lastAttacker);
        assertEquals(2, state.knockouts);
        assertEquals(3, state.falls);
        assertTrue(state.hittable(300), "round initialization must never grant spawn immunity");
        assertEquals(0, state.protectedUntil);
        state.beginFloat(300);
        assertFalse(state.hittable(339), "actual knockout returns remain protected");
        assertTrue(state.hittable(357));
    }
    @Test void extremeDamageHasBoundedLaunchAndSeparateFightersRemainIndependent() {
        var state = new CombatState();
        var other = new CombatState();
        state.percent = 998;
        var launch = state.hit(0, UUID.randomUUID(), 1);
        assertEquals(999, state.percent);
        assertTrue(launch.x() <= 3.2);
        assertTrue(launch.y() <= 1.0);
        assertTrue(launch.stun() <= 26);
        assertEquals(0, other.percent);
        assertNull(other.lastAttacker);
    }
}
