package dev.hanks.vanilla;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShieldHeavyTest {
    @Test void heavyCommitsToLongerStartupAndSharesCooldownWithJab() {
        var state = new CombatState();
        assertTrue(state.beginAttack(10, -1, AttackKind.HEAVY));
        assertEquals(14, state.impactAt); assertEquals(30, state.readyAt);
        for (int tick = 10; tick < 30; tick++) {
            assertFalse(state.beginAttack(tick, 1));
            assertFalse(state.requestGuard(tick, true, true));
        }
        state.impactAt = -1;
        assertTrue(state.beginAttack(30, 1));
    }
    @Test void heavyDoesMoreDamageAndLaunchAndCanBeInterrupted() {
        var light = new CombatState(); var heavy = new CombatState(); var attacker = UUID.randomUUID();
        var jab = light.hit(10, attacker, 1);
        var smash = heavy.receiveHit(10, attacker, -1, AttackKind.HEAVY).launch();
        assertEquals(18, heavy.percent); assertEquals(8, light.percent);
        assertTrue(-smash.x() > jab.x() * 1.5); assertTrue(smash.y() > jab.y()); assertTrue(smash.stun() > jab.stun());
        var startup = new CombatState(); startup.beginAttack(0, 1, AttackKind.HEAVY);
        startup.hit(3, attacker, -1); assertEquals(-1, startup.impactAt);
    }
    @Test void shieldAbsorbsEitherDirectionAndHeavyConsumesMoreGuard() {
        var state = new CombatState(); var attacker = UUID.randomUUID();
        assertTrue(state.requestGuard(0, true, true));
        assertFalse(state.beginAttack(1, 1));
        var first = state.receiveHit(1, attacker, -1, AttackKind.LIGHT);
        assertTrue(first.blocked()); assertNull(first.launch()); assertEquals(82, state.guard);
        assertFalse(state.receiveHit(2, attacker, 1, AttackKind.HEAVY).blocked()); // duplicate immunity
        assertEquals(82, state.guard);
        var second = state.receiveHit(5, attacker, 1, AttackKind.HEAVY);
        assertTrue(second.blocked()); assertEquals(40, state.guard); assertEquals(0, state.percent);
        assertNull(state.creditedAttacker(5));
        state.requestGuard(6, false, true);
        assertNotNull(state.receiveHit(9, attacker, 1, AttackKind.HEAVY).launch());
    }
    @Test void guardBreakCreatesVulnerabilityAndCannotBeRefilledByPacketSpam() {
        var state = new CombatState(); state.guard = 25; state.requestGuard(0, true, true);
        for (int i = 0; i < 100; i++) state.requestGuard(0, true, true);
        assertEquals(25, state.guard);
        var hit = state.receiveHit(1, UUID.randomUUID(), 1, AttackKind.HEAVY);
        assertTrue(hit.guardBroken()); assertEquals(0, state.guard); assertFalse(state.blocking(1));
        assertEquals(31, state.stunUntil); assertFalse(state.requestGuard(2, true, true));
        assertNotNull(state.hit(5, UUID.randomUUID(), 1));
    }
    @Test void guardDrainsRecoversAndReleasesOnLeaseExpiryAirOrRespawn() {
        var state = new CombatState();
        assertFalse(state.requestGuard(0, true, false));
        state.requestGuard(0, true, true); state.tickGuard(1, true); assertEquals(99, state.guard);
        assertFalse(state.blocking(12)); state.tickGuard(21, true); assertEquals(100, state.guard);
        state.requestGuard(22, true, true); state.tickGuard(23, false); assertFalse(state.blocking(23));
        state.requestGuard(24, true, true); state.respawn(25); assertFalse(state.blocking(25));
        state.beginFloat(25); assertFalse(state.requestGuard(26, true, true));
    }
    @Test void holdingUntilEmptyBreaksShieldAndRegenWaitsForStun() {
        var state = new CombatState();
        for (int tick = 0; tick < 100; tick++) {
            state.requestGuard(tick, true, true);
            assertEquals(tick == 99, state.tickGuard(tick, true));
        }
        assertEquals(0, state.guard); assertEquals(129, state.stunUntil);
        state.tickGuard(128, true); assertEquals(0, state.guard);
        state.tickGuard(129, true); assertEquals(2, state.guard);
    }
}
