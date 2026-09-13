package dev.hanks.vanilla;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatchStateTest {
    private final List<UUID> players = java.util.stream.IntStream.range(0, 5).mapToObj(i -> new UUID(0, i + 1)).toList();
    private MatchState round() {
        MatchState state = new MatchState();
        players.subList(0, 4).forEach(state::enqueue); state.takeFour(); return state;
    }
    private void go(MatchState state) { for (int i = 0; i < MatchState.COUNTDOWN_TICKS; i++) state.tick(Map.of()); }
    @Test void queueRequiresFourPreservesFifoAndDoesNotDuplicate() {
        MatchState state = new MatchState();
        players.subList(0, 3).forEach(state::enqueue);
        assertTrue(state.takeFour().isEmpty());
        assertFalse(state.enqueue(players.getFirst()));
        state.enqueue(players.get(3)); state.enqueue(players.get(4));
        assertEquals(players.subList(0, 4), state.takeFour());
        assertEquals(List.of(players.get(4)), state.queue());
        assertTrue(state.takeFour().isEmpty());
        assertFalse(state.ringOut(players.getFirst()));
        go(state); assertEquals(MatchState.Phase.ACTIVE, state.phase());
    }
    @Test void cancelledCountdownKeepsSurvivorsAheadOfWaitingPlayers() {
        MatchState state = round(); state.enqueue(players.get(4));
        state.cancelCountdown(players.get(1));
        assertEquals(List.of(players.get(0), players.get(2), players.get(3), players.get(4)), state.queue());
        assertEquals(state.queue(), state.takeFour());
        assertEquals(3, state.stocks(players.get(4)));
    }
    @Test void threeStocksEliminateAndSpectatorsCannotLoseMore() {
        MatchState state = round(); go(state);
        for (int i = 0; i < 3; i++) assertTrue(state.ringOut(players.get(0)));
        assertFalse(state.ringOut(players.get(0)));
        assertFalse(state.fighting(players.get(0)));
        state.tick(Map.of()); assertEquals(MatchState.Phase.ACTIVE, state.phase());
        state.forfeit(players.get(1)); state.forfeit(players.get(2)); state.tick(Map.of());
        assertEquals(players.get(3), state.winner());
        assertEquals(MatchState.Phase.RESULTS, state.phase());
        assertFalse(state.ringOut(players.get(3)));
    }
    @Test void simultaneousFinalFallsDrawInsteadOfFavoringIterationOrder() {
        MatchState state = round(); go(state);
        players.subList(0, 4).forEach(state::forfeit);
        state.tick(Map.of()); assertNull(state.winner()); assertEquals(MatchState.Phase.RESULTS, state.phase());
    }
    @Test void timeoutRanksStocksThenPercentageWithExactTiesDrawing() {
        MatchState state = round(); go(state); state.ringOut(players.get(0));
        Map<UUID, Integer> damage = Map.of(players.get(0), 0, players.get(1), 80, players.get(2), 16, players.get(3), 50);
        for (int i = 0; i < MatchState.MATCH_TICKS; i++) state.tick(damage);
        assertEquals(players.get(2), state.winner());
        MatchState tied = round(); go(tied);
        for (int i = 0; i < MatchState.MATCH_TICKS; i++) tied.tick(Map.of());
        assertNull(tied.winner()); assertEquals("Time limit", tied.reason());
    }
    @Test void nextRoundRetainsWaitingQueueAndResetsStocks() {
        MatchState state = round(); go(state); state.enqueue(players.get(4));
        state.finish(players.getFirst(), "test");
        for (int i = 0; i < MatchState.RESULT_TICKS; i++) state.tick(Map.of());
        assertEquals(0, state.remaining());
        state.clearRound(); assertEquals(List.of(players.get(4)), state.queue());
        players.subList(0, 3).forEach(state::enqueue); state.takeFour();
        assertEquals(3, state.stocks(players.getFirst()));
        assertNull(state.winner());
    }
    @Test void cancellingPracticeNeverQueuesTheDummy() {
        MatchState state = new MatchState(); state.start(players.subList(0, 2), true);
        state.cancelCountdown(players.getFirst());
        assertTrue(state.queue().isEmpty()); assertEquals(MatchState.Phase.IDLE, state.phase());
    }
}
