package dev.hanks.vanilla;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ClassCombatTest {
    @Test void zombieSpecialWorksInAirWithoutForcingASlamOrGrantingArmor() {
        var ground = FighterMoves.special(FighterClass.ZOMBIE,false,false);
        var air = FighterMoves.special(FighterClass.ZOMBIE,true,false);
        assertEquals(ground.damage(),air.damage()); assertEquals(AttackDirection.FORWARD,air.aim());
        var state = new CombatState(); state.fighterClass=FighterClass.ZOMBIE;
        state.beginMove(0,1,air); state.releaseSpecial(0);
        assertFalse(state.slamCommitted(1)); assertFalse(state.armored(1,true));
        var defender = new CombatState(); defender.requestGuard(0,true,true);
        assertTrue(defender.receiveHit(1,UUID.randomUUID(),1,air).blocked());
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
    @Test void downAttackClaimsTheEntirePressAndNeverDropsOrFastFallsDuringItsAnimation() {
        var input = new DownIntent(); input.observe(true,10,true);
        assertFalse(input.takeDrop(14)); input.claimAttack(14);
        for (int t=15;t<40;t++) { assertFalse(input.takeDrop(t)); assertFalse(input.fastFall(t)); }
        input.observe(false,40,true); input.observe(true,41,true);
        assertFalse(input.takeDrop(45)); assertTrue(input.takeDrop(46)); assertFalse(input.takeDrop(47));
        assertTrue(input.fastFall(46));
    }
    @Test void tapDropSurvivesReleaseAndLateAirAttackClaimsFastFall() {
        var input = new DownIntent(); input.observe(true,0,true); input.observe(false,1,true);
        assertFalse(input.takeDrop(4)); assertTrue(input.takeDrop(5)); assertFalse(input.fastFall(5));
        input.observe(true,10,false); assertTrue(input.fastFall(15)); input.claimAttack(17);
        assertFalse(input.fastFall(18)); assertFalse(input.takeDrop(18));
        assertEquals(AttackDirection.FORWARD,AttackDirection.input(true,true));
    }
    @Test void rejectedChordRestoresTheDropWithoutRevivingAnOlderPress() {
        var input = new DownIntent(); input.observe(true,0,true); input.claimAttack(1,10);
        input.rejectAttack(10); assertTrue(input.takeDrop(5));
        input.observe(false,6,true); input.observe(true,7,true); input.claimAttack(8,11);
        input.rejectAttack(10); assertFalse(input.takeDrop(12));
        input.cancelPending(); input.rejectAttack(11); assertFalse(input.takeDrop(13));
    }
}
