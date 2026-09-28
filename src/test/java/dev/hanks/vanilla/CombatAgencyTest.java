package dev.hanks.vanilla;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatAgencyTest {
    @Test void influencePreservesSpeedAndStunAndHasBoundedAngleInEveryDirection() {
        for (double x : new double[]{-3, -.1, 0, .1, 3}) for (double y : new double[]{-2, 0, 2}) {
            var launch = new CombatRules.Launch(x, y, 24);
            for (int horizontal = -1; horizontal <= 1; horizontal++) for (int vertical = -1; vertical <= 1; vertical++) {
                var influenced = LaunchInfluence.apply(launch, horizontal, vertical);
                assertEquals(Math.hypot(x,y), Math.hypot(influenced.x(),influenced.y()), 1e-10);
                assertEquals(24, influenced.stun());
                double angle = Math.atan2(x*influenced.y()-y*influenced.x(), x*influenced.x()+y*influenced.y());
                assertTrue(Math.abs(angle) <= LaunchInfluence.MAX_ANGLE + 1e-10);
            }
        }
    }
    @Test void inwardInfluenceBendsOutwardLaunchUpwardAndNeutralDoesNothing() {
        var right = new CombatRules.Launch(2, 1, 20);
        var left = new CombatRules.Launch(-2, 1, 20);
        assertEquals(right, LaunchInfluence.apply(right, 0, 0));
        var a = LaunchInfluence.apply(right, -1, 0);
        var b = LaunchInfluence.apply(left, 1, 0);
        assertTrue(a.x() < right.x()); assertTrue(a.y() > right.y());
        assertEquals(-a.x(), b.x(), 1e-10); assertEquals(a.y(), b.y(), 1e-10);
    }
    @Test void freshGroundShieldParriesOnceAndCanAttackImmediatelyOnRelease() {
        var s = new CombatState(); s.requestGuard(10, true, true);
        var impact = s.receiveHit(12, UUID.randomUUID(), 1, AttackKind.HEAVY);
        assertTrue(impact.parried()); assertTrue(impact.blocked()); assertEquals(90, s.guard);
        assertEquals(0, s.percent); assertFalse(s.launchInfluencePending);
        s.requestGuard(12, false, true);
        assertTrue(s.beginAttack(12, 1));
    }
    @Test void heldShieldAndAirShieldRemainOrdinaryAndRetappingCostsGuard() {
        var s = new CombatState(); s.requestGuard(0, true, true);
        for (int n = 0; n < 100; n++) s.requestGuard(0, true, true);
        assertEquals(100, s.guard);
        assertFalse(s.receiveHit(3, UUID.randomUUID(), 1, AttackKind.LIGHT).parried());
        s.requestGuard(4, false, true); s.requestGuard(5, true, true);
        assertEquals(74, s.guard);
        assertFalse(s.receiveHit(7, UUID.randomUUID(), 1, AttackKind.LIGHT).parried());
        s.requestGuard(8, false, true); s.requestGuard(21, true, true);
        assertTrue(s.receiveHit(21, UUID.randomUUID(), 1, AttackKind.LIGHT).parried());
        s.respawn(30); s.requestGuard(30, true, false);
        assertFalse(s.receiveHit(31, UUID.randomUUID(), 1, AttackKind.LIGHT).parried());
    }
    @Test void holdingThroughAttackLockoutDoesNotCreateAutomaticParry() {
        var s = new CombatState(); s.readyAt = 10;
        assertFalse(s.requestGuard(9, true, true)); assertTrue(s.requestGuard(10, true, true));
        assertFalse(s.receiveHit(10, UUID.randomUUID(), 1, AttackKind.LIGHT).parried());
    }
    @Test void pausePreservesWindowButDoesNotMakeItReusableAndRespawnClearsInfluence() {
        var s = new CombatState(); s.requestGuard(0, true, true); s.pause(0, 3);
        assertTrue(s.receiveHit(5, UUID.randomUUID(), 1, AttackKind.LIGHT).parried());
        assertFalse(s.receiveHit(9, UUID.randomUUID(), 1, AttackKind.LIGHT).parried());
        s.respawn(20); s.receiveHit(20, UUID.randomUUID(), 1, AttackKind.LIGHT);
        assertTrue(s.launchInfluencePending); s.respawn(30); assertFalse(s.launchInfluencePending);
    }
}
