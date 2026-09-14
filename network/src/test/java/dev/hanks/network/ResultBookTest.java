package dev.hanks.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class ResultBookTest {
    static Wire.Ticket ticket(String mode, UUID pool) { return new Wire.Ticket(UUID.randomUUID(), "STEVE", mode, UUID.randomUUID()).forRematch(pool); }
    static Wire.MatchResult result() {
        var a = ticket("DUEL", null); var b = ticket("DUEL", null);
        return new Wire.MatchResult(UUID.randomUUID(), "DUEL", a.player(), List.of(a,b), List.of(
                new Wire.ResultRow(a.player(), "Alice", "STEVE", 1, 2, 3, 1, 250), new Wire.ResultRow(b.player(), "Bob", "STEVE", 2, 0, 1, 3, 110)));
    }
    @Test void unanimousVoteIsBoundToOriginalPlayersAndOnlyCompletesOnce() {
        var book = new ResultBook(); var r = result(); book.receive(r, 0);
        assertThrows(IllegalStateException.class, () -> book.vote(r.id(), UUID.randomUUID(), 1));
        assertFalse(book.vote(r.id(), r.rows().get(0).player(), 1));
        assertFalse(book.vote(r.id(), r.rows().get(0).player(), 2)); assertEquals(1, book.votes(r.id()));
        assertTrue(book.vote(r.id(), r.rows().get(1).player(), 3));
        assertThrows(IllegalStateException.class, () -> book.vote(r.id(), r.rows().get(1).player(), 4));
    }
    @Test void declineDisconnectAndExpiryCloseTheWholeBallot() {
        var book = new ResultBook(); var r = result(); book.receive(r, 0);
        book.leave(r.rows().getFirst().player()); assertFalse(book.open(r.id(), 1));
        assertFalse(book.receive(r, 2)); assertFalse(book.open(r.id(), 2));
        var next = result(); book.receive(next, 0); assertFalse(book.open(next.id(), ResultBook.VOTE_TICKS));
        book.expire(ResultBook.KEEP_TICKS); assertNull(book.result(next.rows().getFirst().player()));
    }
    @Test void oldResultDeliveryCannotReopenCompletedOrDismissedBallot() {
        var book = new ResultBook(); var r = result(); book.receive(r, 0); book.forget(r.rows().getFirst().player());
        assertFalse(book.receive(r, 1)); assertNull(book.result(r.rows().getFirst().player()));
        assertEquals(r, Wire.JSON.fromJson(Wire.JSON.toJson(r), Wire.MatchResult.class));
    }
    @Test void exactRematchNeverFillsWithStrangersOrAnotherRematch() {
        var q = new MatchQueue(); var pool = UUID.randomUUID(); var a = ticket("DUEL",pool); var b = ticket("DUEL",pool);
        q.offer(a); var stranger = ticket("DUEL",null); q.offer(stranger); q.offer(ticket("DUEL",UUID.randomUUID()));
        assertNull(q.reserve()); q.offer(b); assertEquals(List.of(a,b),q.reserve().roster()); assertEquals(2,q.size());
        assertThrows(IllegalArgumentException.class, () -> new Wire.Reservation(UUID.randomUUID(),List.of(a,stranger)));
    }
    @Test void rematchOfferAndWithdrawalAreAtomicAcrossOriginalParties() {
        var registry = new SelectionRegistry(); var pool = UUID.randomUUID(); var a = ticket("DUEL",pool); var b = ticket("DUEL",pool);
        assertFalse(registry.offer(List.of(a))); assertTrue(registry.tickets().isEmpty());
        assertTrue(registry.offer(List.of(a,b))); assertTrue(registry.cancel(a.player())); assertTrue(registry.tickets().isEmpty());
        assertTrue(registry.offer(List.of(a,b))); assertTrue(registry.claim(new Wire.Reservation(UUID.randomUUID(),List.of(a,b))));
        assertFalse(registry.cancel(b.player())); assertEquals(2,registry.tickets().size());
    }
}
