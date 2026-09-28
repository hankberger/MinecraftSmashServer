package dev.hanks.vanilla;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpperKoTest {
    @Test void voluntaryMovementAboveEveryStagesTopIsSafe() {
        for(var stage:BattleStage.values())for(var kind:FighterClass.values()) {
            var state=new CombatState();state.fighterClass=kind;
            assertFalse(stage.fighterOutside(.5,stage.blastTop()+5,.5,state.upwardKnockback(50,MovementRules.JUMP)));
            assertFalse(stage.fighterOutside(.5,stage.blastTop()+5,.5,state.upwardKnockback(50,FighterMoves.recoveryY(kind))));
            assertTrue(stage.outside(.5,stage.blastTop()+5,.5),"Projectiles and teleport destination validation retain their geometric bounds");
        }
    }
    @Test void upwardHitStillKOsAndTheLineItselfIsSafe() {
        var state=new CombatState();state.percent=180;
        var hit=state.receiveHit(10,UUID.randomUUID(),1,FighterMoves.light(FighterClass.IRON_GOLEM,AttackDirection.UP,true),false);
        assertNotNull(hit.launch());assertTrue(state.upwardKnockback(11,hit.launch().y()));
        for(var stage:BattleStage.values()) {
            assertFalse(stage.fighterOutside(.5,stage.blastTop(),.5,true));
            assertTrue(stage.fighterOutside(.5,stage.blastTop()+.01,.5,state.upwardKnockback(11,hit.launch().y())));
        }
    }
    @Test void oldKoCreditAndDownwardHitsCannotKillASafeHighJump() {
        var state=new CombatState();var attacker=UUID.randomUUID();
        state.receiveHit(10,attacker,1,FighterMoves.light(FighterClass.STEVE,AttackDirection.UP,false),false);
        long expired=state.launchUntil;
        assertEquals(attacker,state.creditedAttacker(expired));
        assertFalse(state.upwardKnockback(expired,1.6));
        assertFalse(state.upwardKnockback(11,-1));assertFalse(state.upwardKnockback(11,0));
        state.respawn(12);assertFalse(state.upwardKnockback(13,2));
    }
    @Test void sideVoidAndInvalidPositionsRemainLethalEvenAboveTheTop() {
        for(var stage:BattleStage.values())for(boolean launched:new boolean[]{false,true}) {
            assertTrue(stage.fighterOutside(stage.blastLeft()-.01,stage.blastTop()+4,.5,launched));
            assertTrue(stage.fighterOutside(stage.blastRight()+.01,81,.5,launched));
            assertTrue(stage.fighterOutside(.5,ArenaRules.BLAST_BOTTOM-.01,.5,launched));
            assertTrue(stage.fighterOutside(.5,81,9,launched));
            assertTrue(stage.fighterOutside(Double.NaN,81,.5,launched));
            assertTrue(stage.fighterOutside(.5,Double.POSITIVE_INFINITY,.5,launched));
            assertTrue(stage.fighterOutside(.5,81,Double.NEGATIVE_INFINITY,launched));
        }
    }
}
