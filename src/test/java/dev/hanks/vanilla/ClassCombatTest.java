package dev.hanks.vanilla;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ClassCombatTest {
    @Test void slamHasLandingRiskAndShieldCounterplayInsteadOfAnAirDash() {
        assertFalse(FighterMoves.hasBurst(FighterClass.ZOMBIE));
        assertTrue(FighterMoves.hasBurst(FighterClass.ALEX));
        var ground = FighterMoves.special(FighterClass.ZOMBIE,false,false);
        var air = FighterMoves.special(FighterClass.ZOMBIE,true,false);
        assertTrue(air.damage() > ground.damage());
        var state = new CombatState(); state.beginMove(0,1,air);
        assertTrue(state.slamCommitted(1));
        state.impactAt = -1; state.motionType = 4; state.motionUntil = 20;
        assertTrue(state.slamCommitted(10));
        state.receiveHit(10,UUID.randomUUID(),-1,FighterMoves.light(FighterClass.STEVE,AttackDirection.FORWARD,false));
        assertFalse(state.slamCommitted(11)); assertEquals(0,state.motionUntil);
        var defender = new CombatState(); defender.requestGuard(0,true,true);
        assertTrue(defender.receiveHit(1,UUID.randomUUID(),1,air).blocked());
        assertEquals(0,defender.percent); assertTrue(defender.guard > 0);
    }
    @Test void upAndDownHaveDifferentLaunchChoicesWithoutReplacingRecovery() {
        for (var c : FighterClass.values()) {
            var up = FighterMoves.light(c,AttackDirection.UP,true);
            var down = FighterMoves.light(c,AttackDirection.DOWN,true);
            assertTrue(up.launch(50,1,1).y() > down.launch(50,1,1).y());
            assertEquals(AttackKind.LIGHT,up.kind());
            assertTrue(up.startup() + 2 < up.lockout());
            assertTrue(down.startup() + 2 < down.lockout());
            assertEquals(AttackKind.RECOVERY,FighterMoves.recovery(c).kind());
        }
    }
    @Test void lighterAndHeavierClassesReceiveDifferentKnockbackButSamePercentage() {
        var zombie = new CombatState(); zombie.fighterClass = FighterClass.ZOMBIE;
        var skeleton = new CombatState(); skeleton.fighterClass = FighterClass.SKELETON;
        var hit = FighterMoves.special(FighterClass.STEVE,false,false);
        var a = zombie.receiveHit(0,UUID.randomUUID(),1,hit).launch();
        var b = skeleton.receiveHit(0,UUID.randomUUID(),1,hit).launch();
        assertEquals(zombie.percent,skeleton.percent); assertTrue(a.x() < b.x()); assertTrue(a.y() < b.y());
    }
    @Test void allDirectionalHitsMeetShieldAndCannotBypassLockout() {
        for (var c : FighterClass.values()) for (var direction : AttackDirection.values()) {
            var defender = new CombatState(); assertTrue(defender.requestGuard(0,true,true));
            assertTrue(defender.receiveHit(1,UUID.randomUUID(),1,FighterMoves.light(c,direction,true)).blocked());
            assertEquals(0,defender.percent); assertTrue(defender.guard > 0);
            var attacker = new CombatState(); var move = FighterMoves.light(c,direction,true);
            assertTrue(attacker.beginMove(0,1,move)); attacker.impactAt = -1;
            assertFalse(attacker.beginMove(move.lockout()-1,1,FighterMoves.special(c,false,false)));
            assertTrue(attacker.beginMove(move.lockout(),1,FighterMoves.special(c,false,false)));
        }
    }
    @Test void hitInterruptsChargeMotionAndBufferedInputWithoutRefundingRecovery() {
        var state = new CombatState();
        state.beginMove(0,1,FighterMoves.special(FighterClass.SKELETON,true,false));
        state.motionUntil = 10; state.buffered = AttackIntent.INSTANCE;
        state.receiveHit(1,UUID.randomUUID(),1,FighterMoves.light(FighterClass.STEVE,AttackDirection.FORWARD,false));
        assertEquals(-1,state.impactAt); assertEquals(0,state.motionUntil); assertNull(state.buffered);
        var recovery = new RecoveryState(); assertTrue(recovery.recover(false)); recovery.cancelFastFall();
        assertFalse(recovery.recover(false)); assertFalse(recovery.jump(false)); assertFalse(recovery.burst(false));
    }
    @Test void groundRecoveryNeedsAnActualDepartureBeforeBudgetCanRefresh() {
        var r = new RecoveryState(); assertTrue(r.recover(true));
        for (int tick=0; tick<4; tick++) r.grounded(true,tick);
        assertTrue(r.helpless()); assertFalse(r.available());
        r.grounded(false,4); r.grounded(true,20);
        assertTrue(r.recoveryAvailable()); assertTrue(r.available()); assertTrue(r.burstAvailable());
    }
    @Test void aerialBurstIsOncePerLandingAndIndependentOfUpRecovery() {
        var r = new RecoveryState(); assertTrue(r.burst(false)); assertFalse(r.burst(false));
        assertTrue(r.recoveryAvailable()); assertTrue(r.jump(false)); assertTrue(r.recover(false));
        r.grounded(true,20); assertTrue(r.burst(false));
        r.reset(); assertTrue(r.recoveryAvailable()); assertTrue(r.available()); assertTrue(r.burstAvailable());
    }
    @Test void arrowsHaveBoundedChargeAndBriefHitstun() {
        assertEquals(5,FighterMoves.arrow(0).damage()); assertEquals(10,FighterMoves.arrow(999).damage());
        assertEquals(5,FighterMoves.arrow(8).launch(300,1,1).stun());
        assertTrue(FighterMoves.arrow(8).shieldDamage() < FighterMoves.special(FighterClass.ZOMBIE,false,false).shieldDamage());
    }
    @Test void downChordClaimsPlatformDropButHoldingStillAllowsFastFall() {
        var input = new DownIntent(); input.observe(true,10,true);
        assertFalse(input.takeDrop(11)); input.claimAttack(11);
        assertFalse(input.takeDrop(12)); assertTrue(input.fastFall(12));
        input.observe(false,13,true); input.observe(true,14,true);
        assertTrue(input.takeDrop(16)); assertFalse(input.takeDrop(17));
    }
    @Test void tapDropSurvivesReleaseAndTappedAirAttackDoesNotFastFall() {
        var input = new DownIntent(); input.observe(true,0,true); input.observe(false,1,true);
        assertTrue(input.takeDrop(2));
        input.observe(true,3,false); input.claimAttack(3); input.observe(false,4,false);
        assertFalse(input.fastFall(5)); assertFalse(input.takeDrop(5));
        assertEquals(AttackDirection.FORWARD,AttackDirection.input(true,true));
    }
    @Test void rejectedChordRestoresTheDropWithoutRevivingAnOlderPress() {
        var input = new DownIntent(); input.observe(true,0,true); input.claimAttack(1,10);
        input.rejectAttack(10); assertTrue(input.takeDrop(2));
        input.observe(false,3,true); input.observe(true,4,true); input.claimAttack(4,11);
        input.rejectAttack(10); assertFalse(input.takeDrop(6));
        input.cancelPending(); input.rejectAttack(11); assertFalse(input.takeDrop(7));
    }
}
