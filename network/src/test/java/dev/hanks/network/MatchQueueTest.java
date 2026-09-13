package dev.hanks.network;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.*;

final class MatchQueueTest {
    private static Wire.Ticket ticket(String mode) { return new Wire.Ticket(UUID.randomUUID(), "STEVE", mode, UUID.randomUUID()); }
    @Test void twoMatchesReserveDistinctGroupsInOrder() {
        var q = new MatchQueue(); var tickets = new ArrayList<Wire.Ticket>();
        for (int i = 0; i < 8; i++) { var t = ticket("MATCH"); tickets.add(t); q.offer(t); }
        var first = q.reserve(); var second = q.reserve();
        assertEquals(tickets.subList(0, 4), first.roster()); assertEquals(tickets.subList(4, 8), second.roster());
        assertNotEquals(first.id(), second.id()); assertNull(q.reserve());
    }
    @Test void neverStartsPartialPublicMatch() {
        var q = new MatchQueue(); for (int i = 0; i < 3; i++) q.offer(ticket("MATCH"));
        assertNull(q.reserve()); assertEquals(3, q.size());
    }
    @Test void duplicateSelectionDoesNotDuplicatePlayer() {
        var q = new MatchQueue(); var t = ticket("MATCH"); q.offer(t); q.offer(t);
        assertEquals(1, q.size()); q.remove(t.player()); assertEquals(0, q.size());
    }
    @Test void failedReservationRestoresPriorityAndChoices() {
        var q = new MatchQueue(); for (int i = 0; i < 4; i++) q.offer(ticket("MATCH"));
        var r = q.reserve(); q.offer(ticket("MATCH")); q.restore(r);
        assertEquals(r.roster(), q.reserve().roster()); assertEquals(1, q.size());
    }
    @Test void trainingCanUseIdleArenaWithoutSplittingPublicQueue() {
        var q = new MatchQueue(); q.offer(ticket("MATCH")); var practice = ticket("PRACTICE"); q.offer(practice);
        assertEquals(List.of(practice), q.reserve().roster()); assertEquals(1, q.size());
    }
    @Test void oldestCompleteRequestHasPriority() {
        var q = new MatchQueue(); var training = ticket("SANDBOX"); q.offer(training);
        for (int i = 0; i < 4; i++) q.offer(ticket("MATCH"));
        assertEquals(List.of(training), q.reserve().roster()); assertEquals(4, q.reserve().roster().size());
    }
    @Test void reservationValidatesPlayersModesAndClasses() {
        var t = ticket("MATCH");
        assertThrows(IllegalArgumentException.class, () -> new Wire.Reservation(UUID.randomUUID(), List.of(t)));
        assertThrows(IllegalArgumentException.class, () -> new Wire.Reservation(UUID.randomUUID(), List.of(t,t,t,t)));
        assertThrows(IllegalArgumentException.class, () -> new Wire.Ticket(UUID.randomUUID(), "ADMIN", "MATCH", UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> new Wire.Ticket(UUID.randomUUID(), "STEVE", "INVALID", UUID.randomUUID()));
    }
    @Test void wireRoundTripPreservesReservationIdentity() {
        var r = new Wire.Reservation(UUID.randomUUID(), List.of(ticket("PRACTICE")));
        assertEquals(r, Wire.JSON.fromJson(Wire.JSON.toJson(r), Wire.Reservation.class));
    }
}
